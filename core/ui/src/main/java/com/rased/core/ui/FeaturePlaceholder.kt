package com.rased.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun FeaturePlaceholder(title: String, onBack: () -> Unit) {
    FeatureScaffold(title, onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("قريبًا", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun FeaturePlaceholderPreview() {
    RasedTheme { FeaturePlaceholder("التفريغ", {}) }
}
