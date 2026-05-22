package com.secondream.keeper.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Renders [text] inline-detecting http/https URLs. Found URLs are underlined
 * and accent-colored, and clicking one opens the system browser.
 *
 * If no URLs are present, this still renders a normal clickable text block
 * (with no click behavior on plain text).
 */
private val UrlRegex = Regex("(https?://[\\w\\-._~:/?#\\[\\]@!\$&'()*+,;=%]+)", RegexOption.IGNORE_CASE)

fun buildLinkAnnotatedString(
    text: String,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    UrlRegex.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (start > lastIndex) {
            append(text.substring(lastIndex, start))
        }
        pushStringAnnotation(tag = "URL", annotation = match.value)
        withStyle(
            SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(match.value)
        }
        pop()
        lastIndex = end
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

/**
 * Drop-in replacement for a Text block that should auto-detect URLs.
 * Tapping a URL fires an ACTION_VIEW intent.
 */
@Composable
fun LinkifiedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    linkColor: Color? = null
) {
    val ctx = LocalContext.current
    val resolvedLinkColor = linkColor ?: androidx.compose.material3.MaterialTheme.colorScheme.primary
    val annotated = buildLinkAnnotatedString(text, resolvedLinkColor)

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = LocalContentColor.current),
        maxLines = maxLines,
        overflow = overflow
    ) { offset ->
        annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
            .firstOrNull()?.let { ann ->
                openUrl(ctx, ann.item)
            }
    }
}

private fun openUrl(ctx: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
