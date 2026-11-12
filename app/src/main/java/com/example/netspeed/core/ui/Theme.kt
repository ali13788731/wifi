package com.example.netspeed.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Colors that carry meaning across the whole app: one per network. */
@Immutable
data class NetColors(val wifi: Color, val mobile: Color, val ok: Color, val warn: Color)

val LocalNetColors = staticCompositionLocalOf {
    NetColors(Color(0xFF2F80FF), Color(0xFFFF8A3D), Color(0xFF22B573), Color(0xFFE5A100))
}

private val Light = lightColorScheme(
    primary = Color(0xFF0B5FFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF), onPrimaryContainer = Color(0xFF001B4D),
    secondary = Color(0xFF5B6B8C), surface = Color(0xFFF7F8FC), background = Color(0xFFF7F8FC),
    surfaceVariant = Color(0xFFE9ECF5), error = Color(0xFFD32F2F)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4FF), onPrimary = Color(0xFF002A75),
    primaryContainer = Color(0xFF173B8F), onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFB4C2E2), surface = Color(0xFF12161D), background = Color(0xFF0E1116),
    surfaceVariant = Color(0xFF1D232D), error = Color(0xFFFF8A80)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun DualNetTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        // keep the brand palette; Material-You colors only if the user is on Android 12+ AND chose system theme
        mode == "system" && Build.VERSION.SDK_INT >= 31 && false ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val net = if (dark) NetColors(Color(0xFF6FA8FF), Color(0xFFFFA25E), Color(0xFF4CD39A), Color(0xFFFFC94D))
    else NetColors(Color(0xFF1F6BFF), Color(0xFFF07A1E), Color(0xFF14A46B), Color(0xFFC98A00))
    androidx.compose.runtime.CompositionLocalProvider(LocalNetColors provides net) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
    }
}
