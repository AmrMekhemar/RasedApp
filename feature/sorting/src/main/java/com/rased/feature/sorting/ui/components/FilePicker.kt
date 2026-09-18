package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun FilePickerCard(title: String, description: String, fileName: String?, buttonText: String, onPick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SortingStyle.Border)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            InputStepHeading("١", title, description)
            FilePickerInline(fileName, buttonText, onPick)
        }
    }
}

@Composable
internal fun FilePickerInline(fileName: String?, text: String, onPick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SortingStyle.Background, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (fileName == null) "ملف Excel · XLSX" else "تم اختيار الملف",
            style = MaterialTheme.typography.labelMedium, color = SortingStyle.Teal)
        Text(
            text = fileName ?: "اختر الملف من جهازك",
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, color = SortingStyle.Ink
        )
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SortingStyle.Teal.copy(alpha = 0.35f))) {
            Text(if (fileName == null) text else "تغيير الملف")
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun FilePickerPreview() {
    SortingTheme { FilePickerCard("ملف الداتا", "يحتوي على شيت باسم: داتا", "data.xlsx", "اختيار ملف الداتا", {}) }
}
