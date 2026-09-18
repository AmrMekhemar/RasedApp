package com.rased.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.RasedTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSorting: () -> Unit, onOpenUnloading: () -> Unit, onOpenChecking: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("راصد", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("أهلاً بك في راصد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("اختر القسم المطلوب")
            FeatureCard("الفرز", "مطابقة ملف الداتا مع المحفظة", onOpenSorting)
            FeatureCard("التفريغ", "قريبًا", onOpenUnloading)
            FeatureCard("التشييك", "قريبًا", onOpenChecking)
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun HomeScreenPreview() {
    RasedTheme { HomeScreen({}, {}, {}) }
}
