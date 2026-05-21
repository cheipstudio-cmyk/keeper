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

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFF8F9FF),
    secondary = Color(0xFFCBD5E1),
    tertiary = Color(0xFFA8C7FA),
    background = Color(0xFF0F172A),
    surface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFF1F5F9)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF0F172A),
    secondary = Color(0xFF475569),
    tertiary = Color(0xFF062E6F),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
    surfaceVariant = Color(0xFFE7E8F2),
    onSurfaceVariant = Color(0xFF475569),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
