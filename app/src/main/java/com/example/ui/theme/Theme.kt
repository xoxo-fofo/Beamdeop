package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = Color(0xFF00354E),
        primaryContainer = Color(0xFF004D71),
        onPrimaryContainer = Color(0xFFCBE6FF),
        secondary = DarkSecondary,
        onSecondary = Color(0xFF003731),
        tertiary = DarkTertiary,
        background = DarkBg,
        onBackground = Color(0xFFE2E8F0),
        surface = DarkSurface,
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = Color(0xFFCBD5E1)
    )

private val LightColorScheme =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCBE6FF),
        onPrimaryContainer = Color(0xFF001E2F),
        secondary = LightSecondary,
        onSecondary = Color.White,
        tertiary = LightTertiary,
        background = LightBg,
        onBackground = Color(0xFF0F172A),
        surface = LightSurface,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = Color(0xFF475569)
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our sleek custom colors by default for consistent branding
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
