package com.rased.feature.checking

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rased.core.ui.FeaturePlaceholder
import com.rased.core.ui.RasedTheme

@Composable
fun CheckingScreen(onBack: () -> Unit) {
    FeaturePlaceholder(title = "التشييك", onBack = onBack)
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun CheckingScreenPreview() {
    RasedTheme { CheckingScreen(onBack = {}) }
}
