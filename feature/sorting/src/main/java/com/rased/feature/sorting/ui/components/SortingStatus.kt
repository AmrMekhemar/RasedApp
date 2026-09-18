package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun SortingStatus(isLoading: Boolean, message: String?) {
    Card(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SortingStyle.Tint)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (isLoading) "جاري مطابقة اللوحات…" else message ?: "ستظهر نتائج المطابقة هنا",
                color = SortingStyle.Ink, style = MaterialTheme.typography.bodyMedium)
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun SortingStatusPreview() { SortingTheme { SortingStatus(true, null) } }
