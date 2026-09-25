package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.rased.feature.sorting.domain.SortingResult
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.tooling.preview.Preview
import com.rased.core.ui.RasedTheme

private data class ResultColumn(
    val title: String,
    val width: Dp,
    val value: (SortingResult) -> String?
)

private val resultColumns = listOf(
    ResultColumn("اللوحة", 140.dp) { it.plate },
    ResultColumn("النوع", 160.dp) { it.type },
    ResultColumn("الملاحظة", 280.dp) { it.note },
    ResultColumn("الشارع", 180.dp) { it.street },
    ResultColumn("الحي", 160.dp) { it.district },
    ResultColumn("التاريخ", 180.dp) { it.date },
    ResultColumn("اللون", 180.dp) { it.walletType },
    ResultColumn("الموقع", 220.dp) { it.location }
)

@Composable
fun ResultsTable(
    results: List<SortingResult>,
    resultCount: Int,
    resultStart: Int,
    onVisibleRow: (Int) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    horizontalScroll: ScrollState = rememberScrollState()
) {
    LaunchedEffect(listState, onVisibleRow) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onVisibleRow(it) }
    }
    // One horizontal viewport keeps the header and every row aligned. The
    // bounded LazyColumn composes only visible rows, even for large workbooks.
    Box(Modifier.fillMaxSize().horizontalScroll(horizontalScroll)) {
        Column(
            Modifier.width(resultColumns.fold(0.dp) { width, column -> width + column.width })
                .fillMaxHeight()
        ) {
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                resultColumns.forEach { column ->
                    ResultCell(column.title, column.width, bold = true)
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
                items(count = resultCount) { index ->
                    val result = results.getOrNull(index - resultStart)
                    if (result == null) {
                        Row { resultColumns.forEach { ResultCell("…", it.width) } }
                    } else {
                        ResultCard(result, index)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: SortingResult, index: Int) {
    Row(
        Modifier.background(
            if (index % 2 == 0) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        resultColumns.forEachIndexed { columnIndex, column ->
            if (column.title == "الموقع") {
                Box(Modifier.width(column.width).height(48.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.CenterStart) {
                    LocationLink(result.location, Modifier.fillMaxWidth().padding(12.dp), showLabel = false)
                }
            } else ResultCell(column.value(result).orEmpty(), column.width, bold = columnIndex == 0)
        }
    }
}

@Composable
private fun ResultCell(value: String, width: Dp, bold: Boolean = false) {
    Box(
        modifier = Modifier.width(width).height(48.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Preview(showBackground = true, locale = "ar", widthDp = 420)
@Composable
private fun ResultsTablePreview() {
    RasedTheme {
        ResultsTable(listOf(SortingResult("ابج1234", "سيارة", "مطابق", "النيل", "وسط", "2026-09-18", "خاصة")), 1, 0, {})
    }
}
