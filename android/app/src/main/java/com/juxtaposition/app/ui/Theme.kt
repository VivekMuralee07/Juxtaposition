package com.juxtaposition.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFD9480F), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6D5), onPrimaryContainer = Color(0xFF4A1A04),
    secondary = Color(0xFF2F7D5B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF3E8), onSecondaryContainer = Color(0xFF0F3D2A),
    tertiary = Color(0xFF8B5E3C),
    background = Color(0xFFFFF8F3), onBackground = Color(0xFF2B2118),
    surface = Color.White, onSurface = Color(0xFF2B2118),
    surfaceVariant = Color(0xFFF6ECE4), onSurfaceVariant = Color(0xFF75665A),
    outline = Color(0xFFD9C8BA), outlineVariant = Color(0xFFEFE3D9),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A4C), onPrimary = Color(0xFF3A1500),
    primaryContainer = Color(0xFF5C2A0E), onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFF7FD1A8), onSecondary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF1D4D37), onSecondaryContainer = Color(0xFFCFF5E1),
    tertiary = Color(0xFFD6A77F),
    background = Color(0xFF15110E), onBackground = Color(0xFFF1E7DF),
    surface = Color(0xFF1F1A16), onSurface = Color(0xFFF1E7DF),
    surfaceVariant = Color(0xFF2B241F), onSurfaceVariant = Color(0xFFBBAB9E),
    outline = Color(0xFF5A4D43), outlineVariant = Color(0xFF362E28),
    error = Color(0xFFFFB4AB),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AppTypography = Typography(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
)

@Composable
fun JuxtaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes, typography = AppTypography, content = content,
    )
}

// ---- place styling shared by the list, radar and detail sheet ----
fun typeColor(type: String) = when (type) {
    "fast_food" -> Color(0xFFF2A007)
    "cafe" -> Color(0xFF29B6A8)
    else -> Color(0xFFE4572E)
}
fun typeEmoji(type: String) = when (type) { "fast_food" -> "🍔"; "cafe" -> "☕"; else -> "🍽️" }

/** Closer is greener; far away is neutral. */
fun distanceBandColor(km: Double, neutral: Color) = when {
    km < 0.5 -> Color(0xFF2F9E6B)
    km < 1.5 -> Color(0xFFD08A00)
    else -> neutral
}

fun distanceText(km: Double) = if (km < 1) "${Math.round(km * 1000)} m" else "%.1f km".format(km)

@Suppress("unused") val Caption = TextStyle(fontSize = 11.sp)
