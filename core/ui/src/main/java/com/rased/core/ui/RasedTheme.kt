package com.rased.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

private val RasedTypography = Typography().let { defaults ->
    defaults.copy(
        bodyMedium = defaults.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodySmall = defaults.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
        labelLarge = defaults.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
        labelMedium = defaults.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
        labelSmall = defaults.labelSmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    )
}

@Composable
fun RasedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = RasedTypography,
        colorScheme = lightColorScheme(
            background = Color.White,
            surface = Color.White,
            primary = Color(0xFF111111)
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
