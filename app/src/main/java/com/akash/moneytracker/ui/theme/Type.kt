package com.akash.moneytracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.akash.moneytracker.R

@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
    ).map { w ->
        Font(
            R.font.inter,
            weight = w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
        )
    }
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

val AppTypography = Typography(
    displayLarge = style(48, 52, FontWeight.Bold, -1.0),
    displayMedium = style(36, 40, FontWeight.Bold, -0.6),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.4),
    headlineMedium = style(24, 30, FontWeight.SemiBold, -0.3),
    headlineSmall = style(20, 26, FontWeight.SemiBold, -0.2),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium, 0.2),
    labelSmall = style(11, 14, FontWeight.Medium, 0.3),
)
