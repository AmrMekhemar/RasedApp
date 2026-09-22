package com.rased.feature.sorting.ui

import android.app.Application
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rased.feature.sorting.data.SortingRepository
import com.rased.feature.sorting.data.SortingStore
import com.rased.feature.sorting.data.ResultStore
import com.rased.core.database.SavedFileStorage
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

class SortingViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: SortingRepository = SortingRepository(application)
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SortingUiState())
    val state: StateFlow<SortingUiState> = _state.asStateFlow()
    private var store: ResultStore? = null
    private val storeMutex = Mutex()
    private var pageJob: Job? = null
    private var requestedStart = 0
    private val disposed = AtomicBoolean(false)
    private val savedFiles = SavedFileStorage(application)
    private val fileMutex = Mutex()
    private var pendingFileOperations = 1

    init {
        viewModelScope.launch {
            fileMutex.withLock {
                try {
                    val (data, wallet) = repository.loadInputs(::importProgress)
                    _state.update { it.copy(
                        dataFileUri = data?.let(savedFiles::uri), dataFileName = data?.displayName,
                        walletFileUri = wallet?.let(savedFiles::uri), walletFileName = wallet?.displayName
                    ) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    _state.update { it.copy(message = "تعذر استعادة الملفات المحفوظة") }
                } finally {
                    finishFileOperation()
                }
            }
        }
    }

    fun setDataFile(uri: Uri?) = saveInputFile(uri, isData = true)
    fun setWalletFile(uri: Uri?) = saveInputFile(uri, isData = false)

    private fun saveInputFile(uri: Uri?, isData: Boolean) {
        if (uri == null) return
        if (_state.value.isLoading || _state.value.isExporting) {
            _state.update { it.copy(message = "انتظر انتهاء العملية ثم اختر الملف الجديد") }
            return
        }
        pendingFileOperations++
        _state.update { it.copy(isManagingFiles = true, message = null) }
        viewModelScope.launch {
            fileMutex.withLock {
                _state.update { it.copy(isManagingFiles = true) }
                try {
                    val saved = repository.replaceInput(uri, isData, ::importProgress)
                    _state.update {
                        if (isData) it.copy(dataFileUri = savedFiles.uri(saved), dataFileName = saved.displayName)
                        else it.copy(walletFileUri = savedFiles.uri(saved), walletFileName = saved.displayName)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    _state.update { it.copy(message = "تعذر حفظ الملف الجديد؛ لم يتم تغيير الملف السابق") }
                } finally {
                    finishFileOperation()
                }
            }
        }
    }
    private fun finishFileOperation() {
        pendingFileOperations--
        _state.update { it.copy(isManagingFiles = pendingFileOperations > 0, fileProgress = null) }
    }

    private fun importProgress(isData: Boolean, rows: Int) {
        val label = if (isData) "الداتا" else "المحفظة"
        _state.update { it.copy(fileProgress = "جاري استيراد $label: $rows صف") }
    }

    fun setUseTextWallet(value: Boolean) = _state.update { it.copy(useTextWallet = value, message = null) }
    fun setWalletText(value: String) = _state.update { it.copy(walletText = value, message = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun openResults() = _state.update { it.copy(showResults = it.hasCompletedSorting) }
    fun closeResults() = _state.update { it.copy(showResults = false) }

    fun startSorting() {
        val current = _state.value
        if (current.isLoading || current.isExporting || current.isManagingFiles) return
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
        _state.update { it.copy(isLoading = true, hasCompletedSorting = false, showResults = false,
            results = emptyList(), resultCount = 0, resultStart = 0, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            var pending: ResultStore? = null
            try {
                storeMutex.withLock {
                    store?.close()
                    store = null
                }
                val completed = repository.sort(
                    current.walletText.takeIf { current.useTextWallet }
                )
                val next = completed.store
                pending = next
                val count = completed.count
                val firstPage = next.readPage(0, SortingStore.PAGE_SIZE * 2)
                storeMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    if (disposed.get()) throw CancellationException()
                    store = next
                    pending = null
                    _state.update {
                        it.copy(isLoading = false, hasCompletedSorting = true, showResults = true,
                            results = firstPage, resultCount = count,
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
        repository.exportResults(source, uri)
    }

    fun shareResults(onReady: (Uri) -> Unit) = withResults { source ->
        val context = getApplication<Application>()
        val directory = File(context.cacheDir, "shared_results").apply { mkdirs() }
        val file = File.createTempFile("Rased-results-", ".xlsx", directory)
        try {
            file.outputStream().use { source.writeXlsx(it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.results", file)
            withContext(Dispatchers.Main) { onReady(uri) }
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    private fun withResults(action: suspend (ResultStore) -> Unit) {
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
