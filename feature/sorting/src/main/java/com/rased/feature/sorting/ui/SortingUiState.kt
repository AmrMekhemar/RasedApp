package com.rased.feature.sorting.ui

import android.net.Uri
import com.rased.feature.sorting.domain.SortingResult

data class SortingUiState(
    val dataFileUri: Uri? = null,
    val walletFileUri: Uri? = null,
    val dataFileName: String? = null,
    val walletFileName: String? = null,
    val isManagingFiles: Boolean = true,
    val useTextWallet: Boolean = false,
    val walletText: String = "",
    val isLoading: Boolean = false,
    val results: List<SortingResult> = emptyList(),
    val resultCount: Int = 0,
    val resultStart: Int = 0,
    val isExporting: Boolean = false,
    val message: String? = null
)
