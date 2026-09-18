package com.rased.feature.sorting.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rased.core.ui.RasedTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rased.core.ui.FeatureScaffold
import com.rased.feature.sorting.domain.SortingResult
import com.rased.feature.sorting.ui.components.FilePickerCard
import com.rased.feature.sorting.ui.components.WalletInputCard
import com.rased.feature.sorting.ui.components.ResultsActions
import com.rased.feature.sorting.ui.components.SortingResults

/** Pure UI: state flows down and user actions flow up to the route. */
@Composable
fun SortingScreen(
    state: SortingUiState,
    onBack: () -> Unit,
    onPickData: () -> Unit,
    onPickWallet: () -> Unit,
    onUseTextWalletChange: (Boolean) -> Unit,
    onWalletTextChange: (String) -> Unit,
    onStartSorting: () -> Unit,
    onCopyResults: () -> Unit,
    onSaveResults: () -> Unit,
    onVisibleRow: (Int) -> Unit
) {
    FeatureScaffold("قسم الفرز", onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FilePickerCard(
                    title = "ملف الداتا",
                    description = "يجب أن يحتوي على شيت باسم: داتا",
                    fileName = state.dataFileUri?.lastPathSegment,
                    buttonText = "اختيار ملف الداتا",
                    onPick = onPickData
                )
            }

            item {
                WalletInputCard(
                    state.useTextWallet, state.walletText, state.walletFileUri?.lastPathSegment,
                    onUseTextWalletChange, onWalletTextChange, onPickWallet
                )
            }

            item {
                Button(
                    onClick = onStartSorting,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isExporting
                ) { Text(if (state.isLoading) "جاري الفرز..." else "ابدأ الفرز") }
            }

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            state.message?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF1F1F1))) {
                        Text(message, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            if (state.resultCount > 0) {
                item { ResultsActions(state.resultCount, state.isExporting, onCopyResults, onSaveResults) }
                item { SortingResults(state.results, state.resultCount, state.resultStart, onVisibleRow) }
            }
        }
    }
}

@Composable
private fun PreviewSortingScreen(state: SortingUiState) {
    RasedTheme { SortingScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, locale = "ar", name = "Empty")
@Composable
private fun SortingEmptyPreview() = PreviewSortingScreen(SortingUiState())

@Preview(showBackground = true, locale = "ar", name = "Loading")
@Composable
private fun SortingLoadingPreview() = PreviewSortingScreen(SortingUiState(isLoading = true))

@Preview(showBackground = true, locale = "ar", name = "Error")
@Composable
private fun SortingErrorPreview() = PreviewSortingScreen(SortingUiState(message = "تعذر فتح ملف Excel"))

@Preview(showBackground = true, locale = "ar", name = "Results", heightDp = 1200)
@Composable
private fun SortingResultsPreview() = PreviewSortingScreen(
    SortingUiState(
        resultCount = 1,
        results = listOf(SortingResult("ابج1234", "سيارة", "مطابق", "النيل", "وسط", "2026-09-18", "خاصة"))
    )
)
