package com.rased.feature.sorting.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.feature.sorting.domain.SortingResult

/** A compact result summary that expands as one complete, tappable card. */
@Composable
internal fun SortingResultCard(result: SortingResult) {
    var expanded by rememberSaveable(result.plate) { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (expanded) SortingStyle.Teal.copy(alpha = 0.45f) else SortingStyle.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 4.dp else 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultSummary(result, expanded)
            if (expanded) {
                HorizontalDivider(color = SortingStyle.Border)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResultField("النوع", result.type)
                    ResultField("نوع المحفظة", result.walletType)
                    ResultField("الشارع", result.street)
                    ResultField("الحي", result.district)
                    ResultField("التاريخ", result.date)
                    ResultField("الملاحظة", result.note)
                }
                Text("اضغط لإخفاء التفاصيل", color = SortingStyle.Teal,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.End))
            } else {
                Text("اضغط لعرض كل التفاصيل", color = SortingStyle.Muted,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun ResultSummary(result: SortingResult, expanded: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("اللوحة", color = SortingStyle.Muted, style = MaterialTheme.typography.labelMedium)
            Text(result.plate, color = SortingStyle.Teal, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!expanded) {
                Text(listOfNotNull(result.type, result.walletType).joinToString(" • ").ifBlank { "بيانات المطابقة" },
                    color = SortingStyle.Muted, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(if (expanded) "⌃" else "⌄", color = SortingStyle.Teal,
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun ResultField(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SortingStyle.Muted)
        Text(value?.takeIf { it.isNotBlank() } ?: "—", style = MaterialTheme.typography.bodyLarge,
            color = SortingStyle.Ink)
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 320, name = "Collapsed")
@Preview(showBackground = true, locale = "ar", widthDp = 360, fontScale = 1.5f, name = "Large font")
@Composable
private fun SortingResultCardPreview() {
    SortingTheme {
        SortingResultCard(SortingResult("ابج1234", "سيارة ملاكي", "ملاحظة طويلة يمكن عرضها بعد فتح البطاقة.",
            "شارع النيل", "وسط المدينة", "2026-09-18", "خاصة"))
    }
}
