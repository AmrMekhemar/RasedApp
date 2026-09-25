package com.rased.feature.sorting.ui

import android.content.Intent
import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        uri?.let(viewModel::setDataFile)
    }
    val additionalDataPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::addDataFile)
    }
    var dataEditIndex by remember { mutableIntStateOf(-1) }
    val dataEditPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (dataEditIndex >= 0) viewModel.replaceDataFile(dataEditIndex, uri)
        dataEditIndex = -1
    }
    val walletPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::setWalletFile)
    }
    val checkingPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::setCheckingFile)
    }
    val resultsSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let(viewModel::exportResults)
    }


    BackHandler(enabled = state.showResults, onBack = viewModel::closeResults)

    if (state.showResults) {
        SortingResultsScreen(
            state = state,
            onBack = viewModel::closeResults,
            onCopyResults = viewModel::copyResults,
            onSaveResults = { resultsSaver.launch(if (state.showingOld) "Rased-old-results.xlsx" else "Rased-new-results.xlsx") },
            onShareResults = {
                viewModel.shareResults { uri ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("نتائج راصد", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "مشاركة النتائج Excel"))
                }
            },
            onVisibleRow = viewModel::loadVisibleRows,
            onSelectResults = viewModel::selectResults
        )
    } else {
        SortingScreen(
            state = state,
            onBack = onBack,
            onPickData = { dataPicker.launch(excelMimeTypes) },
            onPickWallet = { walletPicker.launch(excelMimeTypes) },
            onUseTextWalletChange = viewModel::setUseTextWallet,
            onWalletTextChange = viewModel::setWalletText,
            onStartSorting = viewModel::startSorting,
            onShowResults = viewModel::openResults,
            onPickChecking = { checkingPicker.launch(excelMimeTypes) },
            onRemoveChecking = viewModel::removeCheckingFile,
            onAddData = { additionalDataPicker.launch(excelMimeTypes) }
            , onPickAdditionalData = { index ->
                dataEditIndex = index + 1
                dataEditPicker.launch(excelMimeTypes)
            },
            onRemoveData = viewModel::removeDataFile
        )
    }
}
