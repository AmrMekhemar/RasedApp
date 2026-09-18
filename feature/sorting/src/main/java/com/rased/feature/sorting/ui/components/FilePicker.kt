package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.rased.core.ui.RasedTheme

@Composable
internal fun FilePickerCard(title: String, description: String, fileName: String?, buttonText: String, onPick: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF7F7F7))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description)
            FilePickerInline(fileName = fileName, text = buttonText, onPick = onPick)
        }
    }
}

@Composable
internal fun FilePickerInline(fileName: String?, text: String, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPick) { Text(text) }
        Text(
            text = fileName ?: "لم يتم اختيار ملف",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = androidx.compose.ui.graphics.Color.DarkGray
        )
    }
}


@Preview(showBackground = true, locale = "ar")
@Composable
private fun FilePickerPreview() {
    RasedTheme {
        FilePickerCard("ملف الداتا", "يجب أن يحتوي على شيت باسم: داتا", "data.xlsx", "اختيار ملف الداتا", {})
    }
}
