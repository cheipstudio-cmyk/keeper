package com.secondream.keeper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

// Keeper palette: Pixel-style dark with Keep-yellow accent.
private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFFFCA28),                  // warm Keep yellow for accents
    onPrimary = Color(0xFF1F1F1F),
    primaryContainer = Color(0xFFFFCA28),          // FAB: bright yellow, clearly visible on dark
    onPrimaryContainer = Color(0xFF1F1F1F),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF1F1F1F),
    tertiary = Color(0xFF7DD3FC),                  // soft cyan accent
    onTertiary = Color(0xFF0F172A),
    background = Color(0xFF0B0F19),                // very dark navy
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0B0F19),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1F2733),            // raised cards & bottom bar
    onSurfaceVariant = Color(0xFFA0AEC0),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1F2733),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1F1F1F)
  )

// Light: warm white with Keep yellow accent — Pixel-style airy spacing
private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF1A1A1A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE082),          // FAB: warm soft yellow
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF5F6368),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1976D2),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),                // soft off-white
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),                   // clean white cards
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFE8EAED),
    error = Color(0xFFD93025),
    onError = Color(0xFFFFFFFF)
  )

/**
 * Adjusts an accent so it stays readable on a near-white background.
 * Computes perceived luminance; if too light, darkens proportionally.
 */
private fun Color.adjustedForLightTheme(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance > 0.62f) {
        val factor = 0.52f
        Color(
            red = (red * factor).coerceIn(0f, 1f),
            green = (green * factor).coerceIn(0f, 1f),
            blue = (blue * factor).coerceIn(0f, 1f),
            alpha = alpha
        )
    } else this
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  // User-chosen accent color (defaults to Keep yellow when null)
  accentColor: Color? = null,
  content: @Composable () -> Unit,
) {
  val baseScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val colorScheme =
    if (accentColor != null) {
      if (darkTheme) {
        baseScheme.copy(
          primary = accentColor,
          primaryContainer = accentColor,
          onPrimary = Color(0xFF1F1F1F),
          onPrimaryContainer = Color(0xFF1F1F1F)
        )
      } else {
        // Auto-darken pastel accents (Keep yellow, etc.) so they stay
        // legible on a near-white background instead of disappearing.
        val effective = accentColor.adjustedForLightTheme()
        baseScheme.copy(
          primary = effective,
          primaryContainer = effective,
          onPrimary = Color(0xFFFFFFFF),
          onPrimaryContainer = Color(0xFFFFFFFF)
        )
      }
    } else baseScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
