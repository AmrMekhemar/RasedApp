package com.rased.feature.sorting.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

/** Connects Android file pickers and the ViewModel to the stateless screen. */
@Composable
fun SortingRoute(onBack: () -> Unit, viewModel: SortingViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val excelMimeTypes = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/octet-stream",
        "application/zip",
        "*/*"
    )

    val dataPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.takeReadPermission(it); viewModel.setDataFile(it) }
    }
    val walletPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.takeReadPermission(it); viewModel.setWalletFile(it) }
    }
    val resultsSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let(viewModel::exportResults)
    }


    SortingScreen(
        state = state,
        onBack = onBack,
        onPickData = { dataPicker.launch(excelMimeTypes) },
        onPickWallet = { walletPicker.launch(excelMimeTypes) },
        onUseTextWalletChange = viewModel::setUseTextWallet,
        onWalletTextChange = viewModel::setWalletText,
        onStartSorting = viewModel::startSorting,
        onCopyResults = viewModel::copyResults,
        onSaveResults = { resultsSaver.launch("Rased-results.xlsx") },
        onVisibleRow = viewModel::loadVisibleRows
    )
}

private fun Context.takeReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
