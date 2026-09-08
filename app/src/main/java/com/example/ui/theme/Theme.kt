package com.example.ui.theme

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
    primary = GoldAccent,
    secondary = EmeraldLight,
    tertiary = GoldLight,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = EmeraldDark,
    onSecondary = Color.White,
    onTertiary = EmeraldDark,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFF0F0F0)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldPrimary,
    secondary = EmeraldLight,
    tertiary = GoldAccent,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = EmeraldDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // For this spiritual app, we disable dynamicTheme by default to maintain the Emerald & Gold motif
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
