package com.secondream.keeper.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondream.keeper.data.local.AppDatabase
import com.secondream.keeper.data.model.Note
import com.secondream.keeper.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeeperWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default to CANCELED — only switch to OK once user confirms
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConfigScreen(
                        onConfirm = { selected ->
                            // Persist into the widget prefs
                            val prefs = getSharedPreferences(
                                KeeperWidgetProvider.PREFS,
                                Context.MODE_PRIVATE
                            )
                            prefs.edit()
                                .putString(
                                    KeeperWidgetProvider.keyForWidget(appWidgetId),
                                    selected.joinToString(",")
                                )
                                .apply()

                            // Force a widget refresh now
                            val mgr = AppWidgetManager.getInstance(this@KeeperWidgetConfigActivity)
                            KeeperWidgetProvider.updateAppWidget(
                                this@KeeperWidgetConfigActivity,
                                mgr,
                                appWidgetId
                            )

                            val resultValue = Intent().putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                appWidgetId
                            )
                            setResult(Activity.RESULT_OK, resultValue)
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    onConfirm: (List<Long>) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    val selected = remember { mutableStateListOf<Long>() }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context.applicationContext)
                notes = db.noteDao().getAllNotesSync()
                    .filter { !it.isTrashed && !it.isArchived }
                    .sortedByDescending { it.isPinned }
            }
        } catch (_: Exception) {}
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            text = "Quali note vuoi nel widget?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Massimo 3 — tocca per selezionare",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Nessuna nota disponibile.", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(notes, key = { it.id }) { note ->
                    val isSel = selected.contains(note.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable {
                                if (isSel) selected.remove(note.id)
                                else if (selected.size < 3) selected.add(note.id)
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSel) Icons.Outlined.CheckCircle
                                          else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSel) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = note.title.ifBlank { "Senza titolo" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                            val preview = note.content.ifBlank {
                                note.getChecklist().take(3).joinToString(" • ") { it.text }
                            }
                            if (preview.isNotBlank()) {
                                Text(
                                    text = preview,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Annulla")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onConfirm(selected.toList()) },
                modifier = Modifier.weight(1f),
                enabled = selected.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Aggiungi", fontWeight = FontWeight.Bold)
            }
        }
    }
}
