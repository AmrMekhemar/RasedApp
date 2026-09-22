package com.rased.feature.sorting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.FeatureScaffold
import com.rased.feature.sorting.domain.SortingResult
import com.rased.feature.sorting.ui.components.ResultsActions
import com.rased.feature.sorting.ui.components.SortingResults
import com.rased.feature.sorting.ui.components.SortingStatus
import com.rased.feature.sorting.ui.components.SortingTheme

@Composable
fun SortingResultsScreen(
    state: SortingUiState,
    onBack: () -> Unit,
    onCopyResults: () -> Unit,
    onSaveResults: () -> Unit,
    onShareResults: () -> Unit,
    onVisibleRow: (Int) -> Unit
) {
    SortingTheme {
        FeatureScaffold("نتائج الفرز", onBack) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (state.resultCount > 0) {
                        ResultsActions(state.isExporting, onCopyResults, onSaveResults, onShareResults)
                    }
                    if (state.message != null || state.resultCount == 0) {
                        SortingStatus(false, state.message ?: "لا توجد لوحات مطابقة")
                    }
                    if (state.resultCount > 0) {
                        SortingResults(
                            state.results, state.resultCount, state.resultStart, onVisibleRow,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 360, heightDp = 800)
@Composable
private fun SortingResultsScreenPreview() {
    SortingResultsScreen(
        SortingUiState(
            resultCount = 1,
            results = listOf(SortingResult("ابج1234", "سيارة", "مطابق", "النيل", "وسط", "2026-09-18", "خاصة"))
        ), {}, {}, {}, {}, {}
    )
}

@Preview(showBackground = true, locale = "ar", name = "No matches")
@Composable
private fun EmptySortingResultsScreenPreview() {
    SortingResultsScreen(SortingUiState(), {}, {}, {}, {}, {})
}
