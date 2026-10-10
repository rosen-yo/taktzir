package com.taktzir.news.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

// A quiet, paper-like palette: one muted accent, everything else neutral.
private val Light = lightColorScheme(
    primary = Color(0xFF2F4A7A),
    onPrimary = Color.White,
    secondary = Color(0xFF6B6B70),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFAF9F6),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFEFEDE8),
    onSurfaceVariant = Color(0xFF6B6B70),
    surfaceContainer = Color(0xFFF3F1EC),
    surfaceContainerLow = Color(0xFFF6F4F0),
    surfaceContainerHigh = Color(0xFFEFEDE8),
    surfaceContainerHighest = Color(0xFFE9E6E0),
    outline = Color(0xFFD8D5CE),
    outlineVariant = Color(0xFFE6E3DD),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DB4E0),
    onPrimary = Color(0xFF10203F),
    secondary = Color(0xFF9A9AA0),
    background = Color(0xFF141416),
    onBackground = Color(0xFFE8E6E1),
    surface = Color(0xFF141416),
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFF9A9AA0),
    surfaceContainer = Color(0xFF1C1C1F),
    surfaceContainerLow = Color(0xFF19191B),
    surfaceContainerHigh = Color(0xFF232326),
    surfaceContainerHighest = Color(0xFF2A2A2E),
    outline = Color(0xFF3A3A3F),
    outlineVariant = Color(0xFF2C2C30),
    error = Color(0xFFF2B8B5),
)

/** Subtle colors for market moves. */
object MarketColors {
    val up = Color(0xFF2E7D5B)
    val down = Color(0xFFB4483C)
}

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp),
        labelMedium = labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.2.sp),
    )
}

@Composable
fun TaktzirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
    ) {
        // The app is Hebrew-first: always lay out right-to-left.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

/** Use for text that may be English or Hebrew, so each aligns naturally. */
val TextStyle.auto: TextStyle get() = copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content)
