package com.secondream.keeper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.secondream.keeper.MainActivity
import com.secondream.keeper.R
import com.secondream.keeper.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class KeeperWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ed = prefs.edit()
        for (id in appWidgetIds) {
            ed.remove(keyForWidget(id))
        }
        ed.apply()
    }

    companion object {
        const val PREFS = "keeper_widget_prefs"
        const val EXTRA_NOTE_ID = "keeper_widget_note_id"

        fun keyForWidget(appWidgetId: Int) = "widget_${appWidgetId}_note_ids"

        /** Update all widgets — call this from the ViewModel when notes change. */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, KeeperWidgetProvider::class.java)
            )
            for (id in ids) updateAppWidget(context, mgr, id)
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Match the app's theme preference (system / light / dark)
            val appPrefs = context.getSharedPreferences("keep_notes_prefs", Context.MODE_PRIVATE)
            val themePref = appPrefs.getString("dark_theme", "system") ?: "system"
            val systemNight = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> systemNight
            }

            // Use a dedicated layout per theme — more reliable than trying
            // to swap backgrounds at runtime through RemoteViews (which is
            // restricted on older Android versions).
            val layoutRes = if (isDark) R.layout.widget_keeper_dark else R.layout.widget_keeper
            val views = RemoteViews(context.packageName, layoutRes)

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(keyForWidget(appWidgetId), "") ?: ""
            val noteIds = raw.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .take(3)

            // Tap on header "+" -> open the app to create a new note
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                appWidgetId * 10,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add, pi)

            // Fetch notes synchronously (we're already in a background thread
            // when called from a worker, but onUpdate runs on the main thread —
            // we use runBlocking + Dispatchers.IO to be safe).
            val notes = try {
                runBlocking(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val all = db.noteDao().getAllNotesSync()
                    noteIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
                }
            } catch (e: Exception) {
                emptyList()
            }

            val containers = listOf(
                Triple(R.id.widget_note_1_container, R.id.widget_note_1_title, R.id.widget_note_1_body),
                Triple(R.id.widget_note_2_container, R.id.widget_note_2_title, R.id.widget_note_2_body),
                Triple(R.id.widget_note_3_container, R.id.widget_note_3_title, R.id.widget_note_3_body)
            )

            for ((index, triple) in containers.withIndex()) {
                val (cId, tId, bId) = triple
                if (index < notes.size) {
                    val note = notes[index]
                    views.setViewVisibility(cId, View.VISIBLE)
                    val title = note.title.ifBlank { "Nota senza titolo" }
                    views.setTextViewText(tId, title)
                    val body = note.content.ifBlank {
                        note.getChecklist().take(3).joinToString(" • ") { it.text }
                    }
                    views.setTextViewText(bId, body)

                    // Tap on this note → open the app deep-linked to this note
                    val noteIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_NOTE_ID, note.id)
                        action = "com.secondream.keeper.OPEN_NOTE_${note.id}"
                    }
                    val notePi = PendingIntent.getActivity(
                        context,
                        appWidgetId * 100 + index,
                        noteIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(cId, notePi)
                } else {
                    views.setViewVisibility(cId, View.GONE)
                }
            }

            views.setViewVisibility(
                R.id.widget_empty,
                if (notes.isEmpty()) View.VISIBLE else View.GONE
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
