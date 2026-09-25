package com.rased.feature.sorting.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.FeatureScaffold
import com.rased.feature.sorting.ui.components.FilePickerCard
import com.rased.feature.sorting.ui.components.SortingHeader
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
    onShowResults: () -> Unit,
    onPickChecking: () -> Unit = {},
    onRemoveChecking: () -> Unit = {},
    onAddData: () -> Unit = {},
    onPickAdditionalData: (Int) -> Unit = {},
    onRemoveData: (Int) -> Unit = {}
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
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val cardWidth = if (maxWidth >= 600.dp) (maxWidth - 12.dp) / 2 else maxWidth * 0.9f
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                item(key = "input_cards") {
                                    Row(
                                        modifier = Modifier.height(IntrinsicSize.Min),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        FilePickerCard(
                                            title = "ملف الداتا",
                                            description = "سيتم قراءة أول شيت ظاهر في الملف",
                                            fileName = state.dataFileName ?: state.dataFileUri?.lastPathSegment,
                                            buttonText = "اختيار ملف الداتا",
                                            onPick = onPickData,
                                            additionalFileNames = state.additionalDataFileNames,
                                            onAdd = if (state.additionalDataFileNames.size < 1) onAddData else null,
                                            addEnabled = !state.isLoading && !state.isExporting && !state.isManagingFiles,
                                            onPickAdditional = onPickAdditionalData,
                                            onRemoveAdditional = { index -> onRemoveData(index + 1) },
                                            onRemove = { onRemoveData(0) },
                                            removeEnabled = !state.isLoading && !state.isExporting && !state.isManagingFiles,
                                            modifier = Modifier.width(cardWidth).fillMaxHeight()
                                        )
                                        WalletInputCard(
                                            state.useTextWallet, state.walletText, state.walletFileName ?: state.walletFileUri?.lastPathSegment,
                                            onUseTextWalletChange, onWalletTextChange, onPickWallet,
                                            modifier = Modifier.width(cardWidth).fillMaxHeight()
                                        )
                                        FilePickerCard(
                                            title = "ملف التشييك (اختياري)",
                                            description = "بدون هذا الملف تظهر كل النتائج كحديثة",
                                            fileName = state.checkingFileName,
                                            buttonText = "اختيار ملف التشييك",
                                            onPick = onPickChecking,
                                            stepNumber = "٣",
                                            onRemove = onRemoveChecking,
                                            removeEnabled = !state.isLoading && !state.isExporting && !state.isManagingFiles,
                                            modifier = Modifier.width(cardWidth).fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
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
                    if (state.isManagingFiles && state.fileProgress != null) {
                        item { SortingStatus(true, state.fileProgress) }
                    }
                    if (state.isLoading || state.message != null) {
                        item { SortingStatus(state.isLoading, state.message) }
                    }

                    if (state.hasCompletedSorting) {
                        item {
                            OutlinedButton(onClick = onShowResults, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text("عرض النتائج (${state.newCount + state.oldCount})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSortingScreen(state: SortingUiState) {
    SortingScreen(state, {}, {}, {}, {}, {}, {}, {})
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
