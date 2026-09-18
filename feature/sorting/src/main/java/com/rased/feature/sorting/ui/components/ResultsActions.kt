package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.rased.core.ui.RasedTheme

@Composable
internal fun ResultsActions(count: Int, isExporting: Boolean, onCopy: () -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("النتائج: $count", fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onCopy, enabled = !isExporting, modifier = Modifier.fillMaxWidth()) { Text("نسخ النتائج") }
        OutlinedButton(onClick = onSave, enabled = !isExporting, modifier = Modifier.fillMaxWidth()) {
            Text(if (isExporting) "جاري تجهيز النتائج..." else "حفظ النتائج Excel")
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun ResultsActionsPreview() {
    RasedTheme { ResultsActions(120, false, {}, {}) }
}

@Preview(showBackground = true, locale = "ar", name = "Exporting")
@Composable
private fun ResultsExportingPreview() {
    RasedTheme { ResultsActions(120, true, {}, {}) }
}
