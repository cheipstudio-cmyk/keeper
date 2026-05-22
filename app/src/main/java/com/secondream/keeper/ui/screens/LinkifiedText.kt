package com.secondream.keeper.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

private val UrlRegex = Regex(
    "(https?://[\\w\\-._~:/?#\\[\\]@!\$&'()*+,;=%]+)",
    RegexOption.IGNORE_CASE
)

fun buildLinkAnnotatedString(text: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        var lastIndex = 0
        UrlRegex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start > lastIndex) append(text.substring(lastIndex, start))
            pushStringAnnotation(tag = "URL", annotation = match.value)
            withStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            ) { append(match.value) }
            pop()
            lastIndex = end
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }

/**
 * Drop-in replacement for a Text block that auto-detects URLs.
 * Tapping a URL fires an ACTION_VIEW intent. Tapping plain text
 * invokes [onPlainClick] (or, if null, lets the parent receive the tap).
 */
@Composable
fun LinkifiedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    linkColor: Color? = null,
    onPlainClick: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val resolvedLink = linkColor ?: MaterialTheme.colorScheme.primary
    val annotated = remember(text, resolvedLink) {
        buildLinkAnnotatedString(text, resolvedLink)
    }

    var layoutResult: TextLayoutResult? = null
    val hasUrls = annotated.getStringAnnotations(tag = "URL", 0, annotated.length).isNotEmpty()

    val tapModifier = when {
        hasUrls -> Modifier.pointerInput(annotated) {
            detectTapGestures(
                onTap = { pos ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)
                    val urlAnn = annotated
                        .getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()
                    if (urlAnn != null) openUrl(ctx, urlAnn.item)
                    else onPlainClick?.invoke()
                }
            )
        }
        onPlainClick != null -> Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { onPlainClick.invoke() })
        }
        else -> Modifier
    }

    Text(
        text = annotated,
        modifier = modifier.then(tapModifier),
        style = style.copy(color = LocalContentColor.current),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult = it }
    )
}

private fun openUrl(ctx: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        com.secondream.keeper.KeeperApplication.skipNextLock = true
        ctx.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
