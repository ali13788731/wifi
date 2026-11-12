package com.example.netspeed.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colors that carry meaning across the whole app: one per network. */
@Immutable
data class NetColors(val wifi: Color, val mobile: Color, val ok: Color, val warn: Color)

val LocalNetColors = staticCompositionLocalOf {
    NetColors(Color(0xFF2F80FF), Color(0xFFFF8A3D), Color(0xFF22B573), Color(0xFFE5A100))
}

/** Brand colours used by the hero cards, progress bars, splash and the nav pill. */
object Brand {
    val Blue = Color(0xFF3B6DFF)
    val Violet = Color(0xFF7C4DFF)
    val Cyan = Color(0xFF22D3EE)
    val Ink = Color(0xFF070B1A)
    val gradient: Brush get() = Brush.linearGradient(listOf(Blue, Violet))
    val gradientWide: Brush get() = Brush.linearGradient(listOf(Color(0xFF2457F5), Violet, Color(0xFFB04DFF)))
}

private val Light = lightColorScheme(
    primary = Color(0xFF3B6DFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EBFF), onPrimaryContainer = Color(0xFF0A2A8A),
    secondary = Color(0xFF5B6B8C), onSurface = Color(0xFF111827), onSurfaceVariant = Color(0xFF6B7385),
    background = Color(0xFFF3F5FB), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFF0F2FA), surfaceContainerHigh = Color(0xFFE9ECF7),
    outlineVariant = Color(0xFFE3E7F2), error = Color(0xFFE5484D)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FB0FF), onPrimary = Color(0xFF05215F),
    primaryContainer = Color(0xFF1E2F6B), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFB4C2E2), onSurface = Color(0xFFEAEEF8), onSurfaceVariant = Color(0xFF9AA3B8),
    background = Color(0xFF080B13), surface = Color(0xFF0E131E), surfaceVariant = Color(0xFF141A28),
    surfaceContainerLow = Color(0xFF111726), surfaceContainer = Color(0xFF161D2E), surfaceContainerHigh = Color(0xFF1C2438),
    outlineVariant = Color(0xFF252E44), error = Color(0xFFFF8A8F)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun DualNetTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val net = if (dark) NetColors(Color(0xFF6FA8FF), Color(0xFFFFA25E), Color(0xFF4CD39A), Color(0xFFFFC94D))
    else NetColors(Color(0xFF1F6BFF), Color(0xFFF07A1E), Color(0xFF14A46B), Color(0xFFC98A00))
    CompositionLocalProvider(LocalNetColors provides net) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light, shapes = AppShapes, typography = AppTypography, content = content
        )
    }
}
