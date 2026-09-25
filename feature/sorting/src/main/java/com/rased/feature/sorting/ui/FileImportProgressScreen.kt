package com.rased.feature.sorting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun FileImportProgressScreen(progress: String, loadedRows: Int, totalRows: Int, onCancel: () -> Unit) {
    BackHandler(enabled = true) { }
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CircularProgressIndicator(strokeWidth = 5.dp)
                    Text("جاري إضافة وفهرسة الملف", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(progress, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val determinate = totalRows > 0
                    val fraction = if (determinate) (loadedRows.toFloat() / totalRows).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(10.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Text(if (determinate) "$loadedRows / $totalRows صف — ${(fraction * 100).toInt()}%"
                    else "$loadedRows صف محمّلة — جاري حساب الإجمالي…", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    Button(onClick = onCancel) { Text("إلغاء العملية") }
                }
            }
        }
    }
}
