package com.rased.app.ui

import android.app.Application
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rased.app.data.XlsxReader
import com.rased.app.domain.SortingEngine
import com.rased.app.data.SortingStore
import com.rased.app.domain.SortingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

data class SortingUiState(
    val dataFileUri: Uri? = null,
    val walletFileUri: Uri? = null,
    val useTextWallet: Boolean = false,
    val walletText: String = "",
    val isLoading: Boolean = false,
    val results: List<SortingResult> = emptyList(),
    val resultCount: Int = 0,
    val resultStart: Int = 0,
    val isExporting: Boolean = false,
    val message: String? = null
)

class SortingViewModel(application: Application) : AndroidViewModel(application) {
    private val reader = XlsxReader(application)
    private val _state = MutableStateFlow(SortingUiState())
    val state: StateFlow<SortingUiState> = _state.asStateFlow()
    private var store: SortingStore? = null
    private val storeMutex = Mutex()
    private var pageJob: Job? = null
    private var requestedStart = 0
    private val disposed = AtomicBoolean(false)

    fun setDataFile(uri: Uri?) = _state.update { it.copy(dataFileUri = uri, message = null) }
    fun setWalletFile(uri: Uri?) = _state.update { it.copy(walletFileUri = uri, message = null) }
    fun setUseTextWallet(value: Boolean) = _state.update { it.copy(useTextWallet = value, message = null) }
    fun setWalletText(value: String) = _state.update { it.copy(walletText = value, message = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun startSorting() {
        val current = _state.value
        if (current.isLoading || current.isExporting) return
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

        pageJob?.cancel()
        requestedStart = 0
        _state.update { it.copy(isLoading = true, results = emptyList(), resultCount = 0, resultStart = 0, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            var pending: SortingStore? = null
            try {
                storeMutex.withLock {
                    store?.close()
                    store = null
                }
                val next = SortingStore(getApplication<Application>().cacheDir)
                pending = next
                val jobContext = currentCoroutineContext()
                next.transaction {
                    if (current.useTextWallet) {
                        current.walletText.lineSequence().forEach {
                            jobContext.ensureActive()
                            next.addWalletPlate(it)
                        }
                    } else {
                        reader.forEachRow(requireNotNull(current.walletFileUri), "ورقة1", SortingEngine.plateNames()) {
                            jobContext.ensureActive()
                            next.addWalletRow(it)
                        }
                    }
                    reader.forEachRow(dataUri, "داتا", SortingEngine.plateNames()) {
                        jobContext.ensureActive()
                        next.matchDataRow(it)
                    }
                }
                val count = next.finish()
                val firstPage = next.readPage(0, SortingStore.PAGE_SIZE * 2)
                storeMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    if (disposed.get()) throw CancellationException()
                    store = next
                    pending = null
                    _state.update {
                        it.copy(isLoading = false, results = firstPage, resultCount = count,
                            message = if (count == 0) "لا توجد لوحات مطابقة" else "تم العثور على $count نتيجة")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(isLoading = false, message = failure.message ?: "حدث خطأ أثناء الفرز") }
            } finally {
                pending?.close()
            }
        }
    }

    fun loadVisibleRows(firstVisibleIndex: Int) {
        val start = firstVisibleIndex / SortingStore.PAGE_SIZE * SortingStore.PAGE_SIZE
        if (_state.value.isLoading || requestedStart == start) return
        requestedStart = start
        pageJob?.cancel()
        if (_state.value.resultStart == start) return
        pageJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                storeMutex.withLock {
                    val source = store ?: return@withLock
                    val rows = source.readPage(start, SortingStore.PAGE_SIZE * 2)
                    currentCoroutineContext().ensureActive()
                    withContext(Dispatchers.Main) {
                        if (requestedStart == start && !_state.value.isLoading) {
                            _state.update { it.copy(results = rows, resultStart = start) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(message = failure.message ?: "تعذر قراءة النتائج") }
            }
        }
    }

    fun copyResults() = withResults { source ->
        val text = source.copyText()
        withContext(Dispatchers.Main) {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("نتائج راصد", text))
        }
    }

    fun exportResults(uri: Uri) = withResults { source ->
        val resolver = getApplication<Application>().contentResolver
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "تعذر حفظ النتائج" }.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            source.writeTsv(writer)
        }
    }

    private fun withResults(action: suspend (SortingStore) -> Unit) {
        if (_state.value.isLoading || _state.value.isExporting) return
        _state.update { it.copy(isExporting = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                storeMutex.withLock { store?.let { action(it) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(message = failure.message ?: "تعذر حفظ النتائج") }
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    override fun onCleared() {
        disposed.set(true)
        pageJob?.cancel()
        // Closing a database can wait for a page/export operation. Keep that I/O
        // off the main thread, including when the screen is being destroyed.
        CoroutineScope(Dispatchers.IO).launch {
            storeMutex.withLock {
                store?.close()
                store = null
            }
        }
        super.onCleared()
    }
}
