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

  // Override primary + primaryContainer with the user's chosen accent.
  // In light theme we keep the dark "primary" text color the same — only
  // primaryContainer (the FAB / bottoni evidenziati) and the accent slot
  // become the chosen color.
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
        baseScheme.copy(
          primaryContainer = accentColor.copy(alpha = 0.85f),
          onPrimaryContainer = Color(0xFF1A1A1A)
        )
      }
    } else baseScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
