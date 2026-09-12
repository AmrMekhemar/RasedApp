package com.rased.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rased.app.data.XlsxReader
import com.rased.app.domain.SortingEngine
import com.rased.app.domain.PlateNormalizer
import com.rased.app.domain.SortingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SortingUiState(
    val dataFileUri: Uri? = null,
    val walletFileUri: Uri? = null,
    val useTextWallet: Boolean = false,
    val walletText: String = "",
    val isLoading: Boolean = false,
    val results: List<SortingResult> = emptyList(),
    val message: String? = null
)

class SortingViewModel(application: Application) : AndroidViewModel(application) {
    private val reader = XlsxReader(application)
    private val _state = MutableStateFlow(SortingUiState())
    val state: StateFlow<SortingUiState> = _state.asStateFlow()

    fun setDataFile(uri: Uri?) = _state.update { it.copy(dataFileUri = uri, message = null) }
    fun setWalletFile(uri: Uri?) = _state.update { it.copy(walletFileUri = uri, message = null) }
    fun setUseTextWallet(value: Boolean) = _state.update { it.copy(useTextWallet = value, message = null) }
    fun setWalletText(value: String) = _state.update { it.copy(walletText = value, message = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun startSorting() {
        val current = _state.value
        if (current.isLoading) return
        val dataUri = current.dataFileUri
        if (dataUri == null) {
            _state.update { it.copy(message = "اختر ملف الداتا أولًا") }
            return
        }
        if (!current.useTextWallet && current.walletFileUri == null) {
            _state.update { it.copy(message = "اختر ملف المحفظة أو استخدم لصق اللوحات") }
            return
        }
        if (current.useTextWallet && current.walletText.isBlank()) {
            _state.update { it.copy(message = "الصق اللوحات أولًا، كل لوحة في سطر") }
            return
        }

        _state.update { it.copy(isLoading = true, results = emptyList(), message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val walletRows = if (current.useTextWallet) {
                    current.walletText.lineSequence().map { it.trim() }
                        .filter { it.isNotBlank() }.map { mapOf("اللوحة" to it) }.toList()
                } else {
                    reader.readSheet(
                        uri = requireNotNull(current.walletFileUri),
                        sheetName = "ورقة1",
                        headerAliases = SortingEngine.plateNames()
                    )
                }
                val aliases = SortingEngine.plateNames().map { it.replace(" ", "") }.toSet()
                fun plate(row: Map<String, String>): String? = PlateNormalizer.normalize(
                    row.entries.firstOrNull { it.key.trim().replace(" ", "") in aliases }?.value
                )
                val wanted = walletRows.mapNotNull(::plate).toHashSet()
                val seen = hashSetOf<String>()
                val dataRows = reader.readSheet(
                    uri = dataUri,
                    sheetName = "داتا",
                    headerAliases = SortingEngine.plateNames(),
                    retainRow = { row ->
                        val normalized = plate(row)
                        normalized != null && normalized in wanted && seen.add(normalized)
                    }
                )
                if (dataRows.isEmpty()) emptyList() else SortingEngine.sort(dataRows, walletRows)
            }.onSuccess { results ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        results = results,
                        message = if (results.isEmpty()) "لا توجد لوحات مطابقة" else "تم العثور على ${results.size} نتيجة"
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = throwable.message ?: "حدث خطأ أثناء الفرز"
                    )
                }
            }
        }
    }

    fun tsvResults(): String = SortingEngine.resultsToTsv(_state.value.results)
}
