package org.rw3h4.echonotex.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 1. Define your complete Light Color Scheme using colors from Color.kt
private val LightColorScheme = lightColorScheme(
    primary = DarkBlue,
    secondary = LightBlue,
    tertiary = LightPurple,
    background = OffWhite,
    surface = OffWhite,
    onPrimary = Color.White,
    onSecondary = DarkBlue,
    onTertiary = DarkBlue,
    onBackground = DarkBlue,
    onSurface = DarkBlue,
)

// 2. Define your complete Dark Color Scheme using the new dark colors
private val DarkColorScheme = darkColorScheme(
    primary = SteelBlue,
    secondary = TealAccent,
    tertiary = SoftCyan,
    background = DeepNavy,
    surface = SteelBlue,
    onPrimary = SoftCyan,
    onSecondary = SoftCyan,
    onTertiary = DeepNavy,
    onBackground = SoftCyan,
    onSurface = SoftCyan
)

@Composable
fun EchoNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 3. Select the correct color scheme
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // 4. Handle system UI colors (like the status bar)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar color to be transparent to see the background
            window.statusBarColor = Color.Transparent.toArgb()
            // Set status bar icons to be light or dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // 5. Apply the MaterialTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Assumes you have a Typography.kt file
        content = content
    )
}