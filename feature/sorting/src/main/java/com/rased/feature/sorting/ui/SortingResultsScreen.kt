package com.rased.feature.sorting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
    onVisibleRow: (Int) -> Unit,
    onSelectResults: (Boolean) -> Unit = {}
) {
    SortingTheme {
        FeatureScaffold(
            "نتائج الفرز",
            onBack,
            bottomBar = {
                if (state.oldCount > 0) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    // 48dp is 60% of the standard 80dp bar; system insets remain separate.
                    Row(
                        Modifier.fillMaxWidth()
                            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                            .height(48.dp).selectableGroup()
                    ) {
                        listOf(false, true).forEach { old ->
                            val selected = state.showingOld == old
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Box(
                                    Modifier.fillMaxSize().selectable(
                                        selected = selected,
                                        enabled = !state.isExporting,
                                        role = Role.Tab,
                                        onClick = { onSelectResults(old) }
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (old) "النتائج القديمة (${state.oldCount})"
                                        else "النتائج الحديثة (${state.newCount})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(if (state.showingOld) "النتائج القديمة (${state.oldCount})" else "النتائج الحديثة (${state.newCount})")
                    if (state.resultCount > 0) {
                        ResultsActions(state.isExporting, onCopyResults, onSaveResults, onShareResults)
                    }
                    if (state.message != null || state.resultCount == 0) {
                        SortingStatus(false, state.message ?: "لا توجد لوحات مطابقة")
                    }
                    if (state.resultCount > 0) {
                        key(state.showingOld) {
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
