package com.rased.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.RasedTheme

@Composable
internal fun FeatureCard(
    title: String,
    subtitle: String,
    symbol: FeatureSymbol,
    accent: Color,
    onClick: () -> Unit,
    comingSoon: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (comingSoon) Color(0xFFE7EBEF) else accent.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (comingSoon) 0.dp else 3.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(48.dp).background(accent.copy(alpha = 0.09f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) { FeatureIcon(symbol, accent, Modifier.size(27.dp)) }
                Text(title, modifier = Modifier.weight(1f), color = HomeStyle.Ink,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (comingSoon) "قريبًا" else "جاهز للعمل",
                    modifier = Modifier.background(accent.copy(alpha = 0.08f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                    color = accent, style = MaterialTheme.typography.labelSmall)
            }
            Text(subtitle, color = HomeStyle.Muted, style = MaterialTheme.typography.bodyMedium)
            if (!comingSoon) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("ابدأ الفرز", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("←", color = accent, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 360)
@Composable
private fun FeatureCardPreview() {
    RasedTheme { FeatureCard("الفرز", "طابق اللوحات واستخرج النتائج في ملف Excel.", FeatureSymbol.Sorting, HomeStyle.Teal, {}) }
}

@Preview(showBackground = true, locale = "ar", widthDp = 320, fontScale = 1.5f)
@Composable
private fun UpcomingFeatureCardPreview() {
    RasedTheme { FeatureCard("التفريغ", "مساحة مخصصة لتفريغ البيانات.", FeatureSymbol.Unloading, HomeStyle.Purple, {}, true) }
}
