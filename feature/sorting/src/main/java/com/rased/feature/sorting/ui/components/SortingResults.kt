package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.RasedTheme
import com.rased.feature.sorting.domain.SortingResult
import kotlinx.coroutines.flow.distinctUntilChanged

/** Uses available content width, including split-screen, rather than device type. */
@Composable
fun SortingResults(
    results: List<SortingResult>,
    resultCount: Int,
    resultStart: Int,
    onVisibleRow: (Int) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            LaunchedEffect(listState, onVisibleRow) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { onVisibleRow(it) }
            }
            // Keep the list bounded inside the screen's scrolling input form.
            // Paging still loads only a small window, even with thousands of cards.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(520.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(count = resultCount, key = { it }) { index ->
                    val result = results.getOrNull(index - resultStart)
                    if (result == null) {
                        Text("جاري تحميل النتيجة…", Modifier.fillMaxWidth().height(360.dp).padding(16.dp))
                    } else {
                        SortingResultCard(result)
                    }
                }
            }
        } else {
            ResultsTable(results, resultCount, resultStart, onVisibleRow, listState)
        }
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 320, name = "Phone")
@Preview(showBackground = true, locale = "ar", widthDp = 840, name = "Tablet")
@Composable
private fun SortingResultsPreview() {
    RasedTheme {
        SortingResults(listOf(SortingResult("ابج1234", "سيارة", "مطابق", "النيل", "وسط", "2026-09-18", "خاصة")), 1, 0, {})
    }
}
