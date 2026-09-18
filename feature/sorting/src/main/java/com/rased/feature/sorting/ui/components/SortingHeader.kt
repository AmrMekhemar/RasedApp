package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun SortingHeader() {
    Column(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(SortingStyle.Ink, Color(0xFF164F55))), RoundedCornerShape(24.dp)
        ).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("من البيانات إلى النتائج", color = Color(0xFF9BDCD1), style = MaterialTheme.typography.labelLarge)
        Text("فرز منظّم. خطوات أبسط.", color = Color.White,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("اختر ملف الداتا، ثم أضف المحفظة لعرض اللوحات المطابقة.",
            color = Color(0xFFD2E3E7), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun InputStepHeading(number: String, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).background(SortingStyle.Tint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(number, color = SortingStyle.Teal, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SortingStyle.Ink)
            Text(description, style = MaterialTheme.typography.bodySmall, color = SortingStyle.Muted)
        }
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 360)
@Composable
private fun SortingHeaderPreview() { SortingTheme { SortingHeader() } }
