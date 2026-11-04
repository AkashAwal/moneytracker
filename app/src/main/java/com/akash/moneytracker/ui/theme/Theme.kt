package com.akash.moneytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** App-specific colors that Material's scheme has no slot for. */
@Immutable
data class ExtraColors(
    val positive: Color,
    val negative: Color,
    val accent: Color,
    val hairline: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Green, Red, Amber, Hairline)
}

val MaterialTheme.extra: ExtraColors
    @Composable get() = LocalExtraColors.current

private val LightScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FD),
    onPrimaryContainer = Color(0xFF004A94),
    secondary = Grey,
    tertiary = Amber,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFFFFF1D6),
    onTertiaryContainer = Color(0xFF5C3B00),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Grey,
    surfaceVariant = Color(0xFFEFEFF4),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFEFEFF4),
    surfaceContainerHighest = Color(0xFFE8E8ED),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Hairline,
    error = Red,
)

private val DarkScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0A2A4D),
    onPrimaryContainer = Color(0xFFBBDCFF),
    secondary = DarkGrey,
    tertiary = Amber,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3A2A08),
    onTertiaryContainer = Color(0xFFFFD991),
    background = DarkCanvas,
    onBackground = DarkInk,
    surface = DarkCard,
    onSurface = DarkInk,
    onSurfaceVariant = DarkGrey,
    surfaceVariant = DarkCardHigh,
    surfaceContainer = DarkCard,
    surfaceContainerLow = DarkCard,
    surfaceContainerHigh = DarkCardHigh,
    surfaceContainerHighest = DarkCardHigh,
    outline = Color(0xFF48484A),
    outlineVariant = DarkHairline,
    error = RedDark,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MoneyTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extra = if (darkTheme) {
        ExtraColors(GreenDark, RedDark, Amber, DarkHairline)
    } else {
        ExtraColors(Green, Red, Amber, Hairline)
    }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
