package com.rased.feature.sorting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.FeatureScaffold
import com.rased.feature.sorting.domain.SortingResult
import com.rased.feature.sorting.ui.components.FilePickerCard
import com.rased.feature.sorting.ui.components.ResultsActions
import com.rased.feature.sorting.ui.components.SortingHeader
import com.rased.feature.sorting.ui.components.SortingResults
import com.rased.feature.sorting.ui.components.SortingStatus
import com.rased.feature.sorting.ui.components.SortingTheme
import com.rased.feature.sorting.ui.components.WalletInputCard

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
    onShareResults: () -> Unit,
    onVisibleRow: (Int) -> Unit
) {
    SortingTheme {
        FeatureScaffold("قسم الفرز", onBack) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item { SortingHeader() }
                    item {
                        FilePickerCard(
                            title = "ملف الداتا",
                            description = "سيتم قراءة أول شيت في الملف",
                            fileName = state.dataFileName ?: state.dataFileUri?.lastPathSegment,
                            buttonText = "اختيار ملف الداتا",
                            onPick = onPickData
                        )
                    }

                    item {
                        WalletInputCard(
                            state.useTextWallet, state.walletText, state.walletFileName ?: state.walletFileUri?.lastPathSegment,
                            onUseTextWalletChange, onWalletTextChange, onPickWallet
                        )
                    }

                    item {
                        Button(
                            onClick = onStartSorting,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !state.isLoading && !state.isExporting && !state.isManagingFiles
                        ) { Text(if (state.isLoading) "جاري الفرز..." else "ابدأ الفرز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    }

                    if (state.isManagingFiles) {
                        item { Text(state.fileProgress ?: "جاري تجهيز الملفات المحفوظة...", color = MaterialTheme.colorScheme.primary) }
                    }
                    if (state.isLoading || state.message != null || state.resultCount == 0) {
                        item { SortingStatus(state.isLoading, state.message) }
                    }

                    if (state.resultCount > 0) {
                        item { ResultsActions(state.resultCount, state.isExporting, onCopyResults, onSaveResults, onShareResults) }
                        item { SortingResults(state.results, state.resultCount, state.resultStart, onVisibleRow) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSortingScreen(state: SortingUiState) {
    SortingScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
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
