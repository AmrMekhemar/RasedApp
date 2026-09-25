package com.rased.feature.sorting.ui

import android.net.Uri
import com.rased.feature.sorting.domain.SortingResult

data class IndexingProgress(val rows: Int = 0, val total: Int = 0, val active: Boolean = true) {
    val percentage: Int get() = if (total > 0) (rows * 100 / total).coerceIn(0, 100) else 0
}
data class SortingUiState(
    val checkingFileUri: Uri? = null,
    val checkingFileName: String? = null,
    val showingOld: Boolean = false,
    val newCount: Int = 0,
    val oldCount: Int = 0,
    val dataFileUri: Uri? = null,
    val walletFileUri: Uri? = null,
    val dataFileName: String? = null,
    val additionalDataFileNames: List<String> = emptyList(),
    val walletFileName: String? = null,
    val dataIndexing: IndexingProgress? = null,
    val additionalDataIndexing: List<IndexingProgress?> = emptyList(),
    val walletIndexing: IndexingProgress? = null,
    val checkingIndexing: IndexingProgress? = null,
    val isManagingFiles: Boolean = true,
    val fileProgress: String? = null,
    val fileProgressRows: Int = 0,
    val fileProgressTotal: Int = 0,
    val useTextWallet: Boolean = false,
    val walletText: String = "",
    val isLoading: Boolean = false,
    val hasCompletedSorting: Boolean = false,
    val showResults: Boolean = false,
    val results: List<SortingResult> = emptyList(),
    val resultCount: Int = 0,
    val resultStart: Int = 0,
    val isExporting: Boolean = false,
    val message: String? = null
) {
    val isIndexing: Boolean
        get() = dataIndexing?.active == true || additionalDataIndexing.any { it?.active == true } ||
            walletIndexing?.active == true || checkingIndexing?.active == true
}
