package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun ResultsActions(count: Int, isExporting: Boolean, onCopy: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().background(SortingStyle.Tint, RoundedCornerShape(20.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("نتائج المطابقة", color = SortingStyle.Teal, style = MaterialTheme.typography.labelLarge)
            Text("$count نتيجة", color = SortingStyle.Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("جاهزة للمراجعة والحفظ", color = SortingStyle.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onSave, enabled = !isExporting, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp)) {
            Text(if (isExporting) "جاري تجهيز النتائج..." else "حفظ النتائج Excel")
        }
        OutlinedButton(onClick = onShare, enabled = !isExporting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp)) { Text("مشاركة جميع النتائج Excel") }
        OutlinedButton(onClick = onCopy, enabled = !isExporting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp)) { Text("نسخ النتائج") }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun ResultsActionsPreview() {
    SortingTheme { ResultsActions(120, false, {}, {}, {}) }
}

@Preview(showBackground = true, locale = "ar", name = "Exporting")
@Composable
private fun ResultsExportingPreview() {
    SortingTheme { ResultsActions(120, true, {}, {}, {}) }
}
