package com.cods.nmono.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.cods.nmono.AppThemeMode

val LightColors = lightColorScheme(
    primary = Color(0xFF0A0A0A), secondary = Color(0xFF6B6B6B),
    background = Color(0xFFFFFFFF), surface = Color(0xFFF5F5F5),
    onPrimary = Color(0xFFFFFFFF), onBackground = Color(0xFF0A0A0A), onSurface = Color(0xFF0A0A0A)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F5), secondary = Color(0xFF8A8A8A),
    background = Color(0xFF000000), surface = Color(0xFF1A1A1A),
    onPrimary = Color(0xFF000000), onBackground = Color(0xFFF5F5F5), onSurface = Color(0xFFF5F5F5)
)

val SepiaColors = lightColorScheme(
    primary = Color(0xFF5B4636), secondary = Color(0xFF8B7355),
    background = Color(0xFFF4ECD8), surface = Color(0xFFEBE0C8),
    onPrimary = Color(0xFFF4ECD8), onBackground = Color(0xFF5B4636), onSurface = Color(0xFF5B4636)
)

val SolarizedColors = lightColorScheme(
    primary = Color(0xFF073642), secondary = Color(0xFF657B83),
    background = Color(0xFFFDF6E3), surface = Color(0xFFEEE8D5),
    onPrimary = Color(0xFFFDF6E3), onBackground = Color(0xFF073642), onSurface = Color(0xFF073642)
)

val ContrastColors = darkColorScheme(
    primary = Color(0xFFFFFF00), secondary = Color(0xFFFFFF00),
    background = Color(0xFF000000), surface = Color(0xFF1A1A00),
    onPrimary = Color(0xFF000000), onBackground = Color(0xFFFFFF00), onSurface = Color(0xFFFFFF00)
)

@Composable
fun NMonoTheme(themeMode: AppThemeMode, content: @Composable () -> Unit) {
    val colorScheme = when (themeMode) {
        AppThemeMode.LIGHT -> LightColors
        AppThemeMode.DARK -> DarkColors
        AppThemeMode.SEPIA -> SepiaColors
        AppThemeMode.SOLARIZED -> SolarizedColors
        AppThemeMode.CONTRAST -> ContrastColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            val isLight = themeMode == AppThemeMode.LIGHT || themeMode == AppThemeMode.SEPIA || themeMode == AppThemeMode.SOLARIZED
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
}
