package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun FilePickerCard(
    title: String, description: String, fileName: String?, buttonText: String,
    onPick: () -> Unit, modifier: Modifier = Modifier, stepNumber: String = "١",
    onRemove: (() -> Unit)? = null, removeEnabled: Boolean = true,
    additionalFileNames: List<String> = emptyList(), onAdd: (() -> Unit)? = null,
    addEnabled: Boolean = true, onPickAdditional: ((Int) -> Unit)? = null,
    onRemoveAdditional: ((Int) -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SortingStyle.Border)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            InputStepHeading(stepNumber, title, description)
            FilePickerInline(fileName, buttonText, onPick, additionalFileNames,
                onRemove = onRemove, onPickAdditional = onPickAdditional,
                onRemoveAdditional = onRemoveAdditional, removeEnabled = removeEnabled)
            if (onAdd != null) {
                OutlinedButton(onClick = onAdd, enabled = addEnabled, modifier = Modifier.fillMaxWidth()) {
                    Text("+ إضافة ملف داتا")
                }
            }
        }
    }
}

@Composable
internal fun FilePickerInline(
    fileName: String?, text: String, onPick: () -> Unit,
    additionalFileNames: List<String> = emptyList(), onRemove: (() -> Unit)? = null,
    onPickAdditional: ((Int) -> Unit)? = null, onRemoveAdditional: ((Int) -> Unit)? = null,
    removeEnabled: Boolean = true
) {
    Column(
        Modifier.fillMaxWidth().background(SortingStyle.Background, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (fileName == null) "ملف Excel · XLSX" else "تم اختيار الملف",
            style = MaterialTheme.typography.labelMedium, color = SortingStyle.Teal)
        @Composable
        fun fileRow(name: String?, index: Int, change: () -> Unit, remove: (() -> Unit)?) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name ?: "اختر الملف من جهازك", Modifier.weight(1f), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = SortingStyle.Ink)
                OutlinedButton(onClick = change, enabled = name != null || index == 0,
                    modifier = if (name == null) Modifier.size(44.dp) else Modifier.weight(0.55f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = if (name == null) PaddingValues(0.dp) else ButtonDefaults.ContentPadding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SortingStyle.Teal),
                    border = BorderStroke(1.dp, SortingStyle.Teal.copy(alpha = 0.65f))) {
                    Text(if (name == null) "+" else "تغيير", color = SortingStyle.Teal,
                        style = if (name == null) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.labelMedium)
                }
                if (remove != null && name != null) TextButton(onClick = remove, enabled = removeEnabled) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        fileRow(fileName, 0, onPick, onRemove)
        additionalFileNames.forEachIndexed { index, name ->
            fileRow(name, index + 1, { onPickAdditional?.invoke(index) }, { onRemoveAdditional?.invoke(index) })
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun FilePickerPreview() {
    SortingTheme { FilePickerCard("ملف الداتا", "سيتم قراءة أول شيت في الملف", "data.xlsx", "اختيار ملف الداتا", {}) }
}
