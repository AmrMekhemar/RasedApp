package com.rased.feature.sorting.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rased.core.ui.RasedTheme

internal object SortingStyle {
    val Ink = Color(0xFF172C3C)
    val Teal = Color(0xFF087F79)
    val Muted = Color(0xFF697A88)
    val Background = Color(0xFFF5F7FA)
    val Border = Color(0xFFE0E8ED)
    val Tint = Color(0xFFEDF7F5)
}

@Composable
internal fun SortingTheme(content: @Composable () -> Unit) {
    RasedTheme {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = SortingStyle.Teal,
                onPrimary = Color.White,
                primaryContainer = SortingStyle.Tint,
                onPrimaryContainer = SortingStyle.Teal,
                background = SortingStyle.Background,
                surface = Color.White,
                onSurface = SortingStyle.Ink,
                onBackground = SortingStyle.Ink,
                onSurfaceVariant = SortingStyle.Muted,
                surfaceVariant = SortingStyle.Tint,
                outlineVariant = SortingStyle.Border
            ),
            content = content
        )
    }
}
