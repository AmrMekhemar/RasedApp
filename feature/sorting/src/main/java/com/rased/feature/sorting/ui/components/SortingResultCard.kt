package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.RasedTheme
import com.rased.feature.sorting.domain.SortingResult

@Composable
internal fun SortingResultCard(result: SortingResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("اللوحة", style = MaterialTheme.typography.labelMedium)
            Text(result.plate, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            ResultField("النوع", result.type)
            ResultField("نوع المحفظة", result.walletType)
            ResultField("الشارع", result.street)
            ResultField("الحي", result.district)
            ResultField("التاريخ", result.date)
            ResultNote(result.note)
        }
    }
}

@Composable
private fun ResultField(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf { it.isNotBlank() } ?: "—", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ResultNote(note: String?) {
    var expanded by rememberSaveable(note) { mutableStateOf(false) }
    var overflows by remember(note) { mutableStateOf(false) }
    Column {
        Text("الملاحظة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = note?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
        )
        if (expanded || overflows) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "عرض أقل" else "عرض الملاحظة كاملة")
            }
        }
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 320)
@Preview(showBackground = true, locale = "ar", widthDp = 360, fontScale = 1.5f)
@Composable
private fun SortingResultCardPreview() {
    RasedTheme {
        SortingResultCard(SortingResult("ابج1234", "سيارة ملاكي", "ملاحظة طويلة يمكن فتحها لعرض جميع التفاصيل دون الخروج عن عرض الشاشة. ".repeat(4), "شارع النيل بجوار الميدان الرئيسي", "وسط المدينة", "2026-09-18", "خاصة"))
    }
}
