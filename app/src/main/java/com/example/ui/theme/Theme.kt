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
    primary = BhagwaOrange,
    secondary = SoftGold,
    tertiary = SkyCyan,
    background = CosmicDarkBg,
    surface = CosmicSurface,
    onPrimary = Color.White,
    onSecondary = CosmicDarkBg,
    onTertiary = CosmicDarkBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = CosmicCard,
    onSurfaceVariant = TextPrimary,
    outline = BorderColor
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CosmicBlue,
    secondary = BhagwaOrange,
    tertiary = SoftGold,
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF102B52),
    onSurface = Color(0xFF102B52)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to a gorgeous dark theme for safety/privacy aesthetic
  dynamicColor: Boolean = false, // Preserve brand palette instead of standard dynamic Android coloring
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
