package com.rased.feature.unloading

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rased.core.ui.FeaturePlaceholder
import com.rased.core.ui.RasedTheme

@Composable
fun UnloadingScreen(onBack: () -> Unit) {
    FeaturePlaceholder(title = "التفريغ", onBack = onBack)
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun UnloadingScreenPreview() {
    RasedTheme { UnloadingScreen(onBack = {}) }
}
