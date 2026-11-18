package com.sour.duoremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.ui.components.DuoFeedback
import com.sour.duoremote.ui.components.LocalDuoFeedback

/**
 * Visual tokens mirrored from docs/ui/tokens.md.
 * Keep the numeric values in sync with that document.
 */
data class DuoColors(
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceInset: Color,
    val outline: Color,
    val outlineStrong: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val online: Color,
    val warning: Color,
    val error: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val focusRing: Color,
    val focusGap: Color,
    val focusRingGlow: Boolean = true,
    val scrim: Color,
)

val DarkDuoColors = DuoColors(
    bg = Color(0xFF0B0D10),
    surface1 = Color(0xFF15191F),
    surface2 = Color(0xFF1C222B),
    surface3 = Color(0xFF222932),
    surfaceInset = Color(0xFF0F1318),
    outline = Color(0xFF2E3742),
    outlineStrong = Color(0xFF3A4552),
    accent = Color(0xFF6CCBFF),
    onAccent = Color(0xFF00243A),
    accentSoft = Color(0x1F6CCBFF),
    online = Color(0xFF6BE3A4),
    warning = Color(0xFFFFC46B),
    error = Color(0xFFFF8A80),
    textPrimary = Color(0xFFE8EDF4),
    textSecondary = Color(0xFFA7B3C2),
    textTertiary = Color(0xFF6F7C8C),
    focusRing = Color(0xFFEAF6FF),
    focusGap = Color(0xFF0B0D10),
    scrim = Color(0xCC0B0D10),
)

val LightDuoColors = DuoColors(
    bg = Color(0xFFE9EBF0),
    surface1 = Color(0xFFF7F8FA),
    surface2 = Color(0xFFFFFFFF),
    surface3 = Color(0xFFDFE3EA),
    surfaceInset = Color(0xFFD8DDE5),
    outline = Color(0xFFC9D0DA),
    outlineStrong = Color(0xFFAEB8C6),
    accent = Color(0xFF0B7FB5),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0x1F0B7FB5),
    online = Color(0xFF0E8A5A),
    warning = Color(0xFFA86500),
    error = Color(0xFFC0362C),
    textPrimary = Color(0xFF141A21),
    textSecondary = Color(0xFF4B5665),
    textTertiary = Color(0xFF7C8797),
    focusRing = Color(0xFF0B0D10),
    focusGap = Color(0xFFF7F8FA),
    scrim = Color(0xCCE9EBF0),
)

val LocalDuoColors = staticCompositionLocalOf { DarkDuoColors }

object DuoSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp
    val massive = 48.dp
}

object DuoRadius {
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 20.dp
    val pill = 999.dp
}

private val DuoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

val DuoMonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

private val DuoShapes = Shapes(
    extraSmall = RoundedCornerShape(DuoRadius.sm),
    small = RoundedCornerShape(DuoRadius.md),
    medium = RoundedCornerShape(DuoRadius.lg),
    large = RoundedCornerShape(DuoRadius.xl),
    extraLarge = RoundedCornerShape(DuoRadius.xxl),
)

@Composable
fun DuoRemoteTheme(
    theme: String = "dark",
    focusRingStyle: String = "white",
    hapticFeedback: Boolean = false,
    soundFeedback: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val base = if (dark) DarkDuoColors else LightDuoColors
    val colors = if (focusRingStyle == "cyan") {
        base.copy(
            focusRing = Color(0xFF35E0FF),
            focusRingGlow = false,
        )
    } else {
        base
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDuoColors provides colors,
        LocalDuoFeedback provides DuoFeedback(hapticFeedback, soundFeedback),
    ) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    primary = colors.accent,
                    onPrimary = colors.onAccent,
                    secondary = colors.online,
                    background = colors.bg,
                    onBackground = colors.textPrimary,
                    surface = colors.surface1,
                    onSurface = colors.textPrimary,
                    surfaceVariant = colors.surface2,
                    onSurfaceVariant = colors.textSecondary,
                    outline = colors.outline,
                    error = colors.error,
                )
            } else {
                lightColorScheme(
                    primary = colors.accent,
                    onPrimary = colors.onAccent,
                    secondary = colors.online,
                    background = colors.bg,
                    onBackground = colors.textPrimary,
                    surface = colors.surface1,
                    onSurface = colors.textPrimary,
                    surfaceVariant = colors.surface2,
                    onSurfaceVariant = colors.textSecondary,
                    outline = colors.outline,
                    error = colors.error,
                )
            },
            typography = DuoTypography,
            shapes = DuoShapes,
            content = content,
        )
    }
}
