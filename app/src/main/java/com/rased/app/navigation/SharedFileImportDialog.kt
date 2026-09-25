package com.rased.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
internal fun SharedFileImportDialog(
    uri: Uri,
    hasPrimaryData: Boolean,
    onImport: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("استيراد ملف مشترك", style = MaterialTheme.typography.titleLarge)
            Text("اختر مكان حفظ الملف:", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { onImport(0) }, modifier = Modifier.fillMaxWidth()) { Text("تحديث أو إضافة ملف الداتا الأول") }
            if (hasPrimaryData) Button(onClick = { onImport(1) }, modifier = Modifier.fillMaxWidth()) { Text("تحديث أو إضافة ملف الداتا الثاني") }
            Button(onClick = { onImport(2) }, modifier = Modifier.fillMaxWidth()) { Text("تحديث أو إضافة المحفظة") }
            Button(onClick = { onImport(3) }, modifier = Modifier.fillMaxWidth()) { Text("تحديث أو إضافة ملف التشييك") }
        }
    }
}
