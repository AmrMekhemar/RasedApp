package com.rased.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.RasedTheme
import com.rased.app.BuildConfig

@Composable
fun HomeScreen(onOpenSorting: () -> Unit, onOpenUnloading: () -> Unit, onOpenChecking: () -> Unit, onLogout: () -> Unit) {
    Scaffold(containerColor = HomeStyle.Background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                HomeHeader()
                OutlinedButton(onClick = onLogout, modifier = Modifier.align(Alignment.End)) {
                    Text("تسجيل الخروج")
                }
                HomeHero()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("أدواتك", color = HomeStyle.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("اختر القسم وابدأ إنجاز مهامك", color = HomeStyle.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FeatureCard("الفرز", "طابق ملف الداتا مع المحفظة، واحفظ نتائجك في Excel.",
                        FeatureSymbol.Sorting, HomeStyle.Teal, onOpenSorting)
                    FeatureCard("التفريغ", "مساحة مخصصة لتفريغ البيانات.",
                        FeatureSymbol.Unloading, HomeStyle.Purple, onOpenUnloading, comingSoon = true)
                    FeatureCard("التشييك", "مساحة مخصصة لمراجعة اللوحات.",
                        FeatureSymbol.Checking, HomeStyle.Amber, onOpenChecking, comingSoon = true)
                }
                Column(modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("راصد · كل التفاصيل في مكان واحد",
                        color = HomeStyle.Muted, style = MaterialTheme.typography.labelMedium)
                    Text("الإصدار ${BuildConfig.VERSION_NAME}",
                        color = HomeStyle.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).background(HomeStyle.Ink, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            FeatureIcon(FeatureSymbol.Radar, Color(0xFF91DFCC), Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("راصد", color = HomeStyle.Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("مساحة عملك الذكية", color = HomeStyle.Muted, style = MaterialTheme.typography.labelMedium)
        }
        Text("الرئيسية", color = HomeStyle.Teal, style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 390, heightDp = 1000)
@Preview(showBackground = true, locale = "ar", widthDp = 320, heightDp = 900, fontScale = 1.5f)
@Preview(showBackground = true, locale = "ar", widthDp = 840, heightDp = 1100)
@Composable
private fun HomeScreenPreview() {
    RasedTheme { HomeScreen({}, {}, {}, {}) }
}
