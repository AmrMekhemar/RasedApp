package com.rased.feature.sorting.ui

import android.app.Application
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
    private var oldStore: ResultStore? = null
    private val storeMutex = Mutex()
    private var pageJob: Job? = null
    private var requestedStart = 0
    private val disposed = AtomicBoolean(false)
    private val savedFiles = SavedFileStorage(application)
    private val fileMutex = Mutex()
    private var pendingFileOperations = 1
    private var fileOperationJob: Job? = null
    private var activeFileName: String = "الملف"
    private val progressNotificationId = 7412
    private var lastNotificationRows = -1
    private val backgroundIndexJobs = mutableMapOf<String, Job>()
    private val notificationManager by lazy {
        getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        fileOperationJob = viewModelScope.launch {
            fileMutex.withLock {
                try {
                    val restoreErrors = mutableListOf<String>()
                    val (data, wallet) = repository.loadInputs(
                        onFailure = { isData, failure ->
                            Log.e("SortingViewModel", "Cannot restore sorting input (isData=$isData)", failure)
                            val label = if (isData) "الداتا" else "المحفظة"
                            restoreErrors += "تعذر استعادة ملف $label؛ اختر الملف مجددًا. ${failure.message.orEmpty()}"
                        },
                        onProgress = ::importProgress
                    )
                    _state.update { it.copy(
                        dataFileUri = data?.let(savedFiles::uri), dataFileName = data?.displayName,
                        walletFileUri = wallet?.let(savedFiles::uri), walletFileName = wallet?.displayName,
                        message = restoreErrors.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    ) }
                    try {
                        val checking = repository.loadChecking()
                        _state.update { it.copy(checkingFileUri = checking?.let(savedFiles::uri), checkingFileName = checking?.displayName) }
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (failure: Exception) {
                        _state.update { it.copy(message = "تعذر استعادة ملف التشييك؛ اختر الملف مجددًا") }
                    }
                    val additionalData = repository.loadAdditionalData()
                    _state.update { it.copy(additionalDataFileNames = additionalData.map { file -> file.displayName }) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e("SortingViewModel", "Cannot restore saved inputs", failure)
                    _state.update { it.copy(message = "تعذر استعادة الملفات المحفوظة") }
                } finally {
                    finishFileNotification()
                    finishFileOperation()
                    fileOperationJob = null
                }
            }
        }
    }
    fun cancelFileOperation() { fileOperationJob?.cancel() }

    fun setDataFile(uri: Uri?) = saveInputFile(uri, isData = true)
    fun addDataFile(uri: Uri?) = saveInputFile(uri, isData = true, appendData = true)
    fun replaceDataFile(index: Int, uri: Uri?) {
        if (uri == null) return
        saveInputFile(uri, isData = true, dataIndex = index)
    }

    fun importSharedFile(uri: Uri, target: Int) {
        if (_state.value.isLoading || _state.value.isExporting || _state.value.isIndexing) return
        when (target) {
            0 -> saveInputFile(uri, isData = true)
            1 -> importSecondData(uri)
            2 -> saveInputFile(uri, isData = false)
            3 -> saveInputFile(uri, isData = false, isChecking = true)
        }
    }

    private fun importSecondData(uri: Uri) {
        if (_state.value.isIndexing) return
        pendingFileOperations++
        activeFileName = uri.lastPathSegment ?: "الملف"
        showFileProgress(0)
        fileOperationJob = viewModelScope.launch {
            fileMutex.withLock {
                try {
                    val saved = repository.replaceOrAddSecondData(uri, ::importProgress)
                    scheduleBackgroundIndex(saved.slot, isData = true)
                    val additional = repository.loadAdditionalData()
                    _state.update { it.copy(additionalDataFileNames = additional.map { file -> file.displayName }) }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (failure: Exception) {
                    _state.update { it.copy(message = "تعذر استيراد ملف الداتا الثاني. ${failure.message.orEmpty()}") }
                } finally {
                    finishFileNotification()
                    finishFileOperation()
                    fileOperationJob = null
                }
            }
        }
    }
    fun removeDataFile(index: Int) {
        if (_state.value.isLoading || _state.value.isExporting || _state.value.isManagingFiles || _state.value.isIndexing) return
        pendingFileOperations++
        _state.update { it.copy(isManagingFiles = true, message = null) }
        fileOperationJob = viewModelScope.launch {
            fileMutex.withLock {
                try {
                    repository.removeDataFile(index)
                    val files = repository.loadAdditionalData()
                    val primary = savedFiles.get("sorting.data")
                    _state.update { it.copy(
                        dataFileUri = primary?.let(savedFiles::uri), dataFileName = primary?.displayName,
                        additionalDataFileNames = files.map { file -> file.displayName }
                    ) }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (failure: Exception) {
                    _state.update { it.copy(message = "تعذر حذف ملف الداتا. ${failure.message.orEmpty()}") }
                } finally { finishFileOperation(); fileOperationJob = null }
            }
        }
    }
    fun setWalletFile(uri: Uri?) = saveInputFile(uri, isData = false)

    fun setCheckingFile(uri: Uri?) = saveInputFile(uri, isData = false, isChecking = true)

    fun removeWalletFile() {
        if (_state.value.isLoading || _state.value.isExporting || _state.value.isManagingFiles || _state.value.isIndexing) return
        pendingFileOperations++
        _state.update { it.copy(isManagingFiles = true, message = null) }
        backgroundIndexJobs["sorting.wallet"]?.cancel()
        fileOperationJob = viewModelScope.launch {
            fileMutex.withLock {
                try {
                    repository.removeWallet()
                    _state.update { it.copy(walletFileUri = null, walletFileName = null, walletIndexing = null,
                        hasCompletedSorting = false, showResults = false, results = emptyList(), resultCount = 0,
                        resultStart = 0, newCount = 0, oldCount = 0) }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (failure: Exception) {
                    _state.update { it.copy(message = "تعذر إزالة ملف المحفظة. ${failure.message.orEmpty()}") }
                } finally {
                    finishFileOperation()
                    fileOperationJob = null
                }
            }
        }
    }

    fun removeCheckingFile() {
        if (_state.value.isLoading || _state.value.isExporting || _state.value.isManagingFiles || _state.value.isIndexing) return
        pendingFileOperations++
        _state.update { it.copy(isManagingFiles = true, message = null) }
        fileOperationJob = viewModelScope.launch {
            fileMutex.withLock {
                try {
                    repository.removeChecking()
                    pageJob?.cancel()
                    withContext(Dispatchers.IO) {
                        storeMutex.withLock {
                            store?.close()
                            oldStore?.close()
                            store = null
                            oldStore = null
                        }
                    }
                    _state.update { it.copy(checkingFileUri = null, checkingFileName = null,
                        hasCompletedSorting = false, showResults = false, showingOld = false,
                        results = emptyList(), resultCount = 0, resultStart = 0, newCount = 0, oldCount = 0) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    _state.update { it.copy(message = "تعذر إزالة ملف التشييك. ${failure.message.orEmpty()}") }
                } finally {
                    finishFileOperation()
                }
            }
        }
    }

    private fun saveInputFile(uri: Uri?, isData: Boolean, isChecking: Boolean = false, appendData: Boolean = false, dataIndex: Int? = null) {
        if (uri == null) return
        if (_state.value.isLoading || _state.value.isExporting || _state.value.isIndexing) {
            _state.update { it.copy(message = "انتظر انتهاء العملية ثم اختر الملف الجديد") }
            return
        }
        pendingFileOperations++
        activeFileName = uri.lastPathSegment ?: "الملف"
        showFileProgress(0)
        fileOperationJob = viewModelScope.launch {
            fileMutex.withLock {
                _state.update { it.copy(isManagingFiles = true) }
                try {
                    val saved = if (dataIndex != null) repository.replaceDataFile(dataIndex, uri, ::importProgress)
                        else if (appendData) repository.addData(uri, ::importProgress)
                        else if (isChecking) repository.replaceChecking(uri) else repository.replaceInput(uri, isData, ::importProgress)
                    _state.update {
                        if (appendData && saved.slot.contains(".data.extra.")) it.copy(additionalDataFileNames = it.additionalDataFileNames + saved.displayName)
                        else if (isChecking) it.copy(checkingFileUri = savedFiles.uri(saved), checkingFileName = saved.displayName)
                        else if (isData) it.copy(dataFileUri = savedFiles.uri(saved), dataFileName = saved.displayName)
                        else it.copy(walletFileUri = savedFiles.uri(saved), walletFileName = saved.displayName)
                    }
                    scheduleBackgroundIndex(saved.slot, isData = isData, sheetIndex = if (isChecking) 1 else 0)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e("SortingViewModel", "Cannot replace sorting input (isData=$isData)", failure)
                    _state.update { it.copy(message = "تعذر حفظ الملف الجديد؛ لم يتم تغيير الملف السابق. ${failure.message.orEmpty()}") }
                } finally {
                    finishFileNotification()
                    finishFileOperation()
                    fileOperationJob = null
                }
            }
        }
    }
    private fun scheduleBackgroundIndex(slot: String, isData: Boolean, sheetIndex: Int = 0) {
        backgroundIndexJobs[slot]?.cancel()
        updateIndexingProgress(slot, isData, 0, 0, true, sheetIndex == 1)
        backgroundIndexJobs[slot] = viewModelScope.launch(Dispatchers.IO) {
            var rows = 0
            var total = 0
            try {
                repository.indexSavedInput(slot, isData, sheetIndex) { loaded, count ->
                    rows = loaded
                    total = count
                    updateIndexingProgress(slot, isData, loaded, count, true, sheetIndex == 1)
                }
                updateIndexingProgress(slot, isData, rows, total, false, sheetIndex == 1)
            } catch (_: CancellationException) {
                // A newer replacement or removal superseded this background index.
                updateIndexingProgress(slot, isData, rows, total, false, sheetIndex == 1)
            } catch (failure: Exception) {
                updateIndexingProgress(slot, isData, rows, total, false, sheetIndex == 1)
                Log.w("SortingViewModel", "Background index failed for $slot", failure)
            }
        }
    }

    private fun updateIndexingProgress(slot: String, isData: Boolean, rows: Int, total: Int, active: Boolean, isChecking: Boolean = false) {
        _state.update { state ->
            if (isChecking) state.copy(checkingIndexing = IndexingProgress(rows, total, active))
            else if (!isData) state.copy(walletIndexing = IndexingProgress(rows, total, active))
            else if (slot == "sorting.data") state.copy(dataIndexing = IndexingProgress(rows, total, active))
            else state.copy(additionalDataIndexing = state.additionalDataIndexing.toMutableList().apply {
                val index = slot.substringAfterLast('.').toIntOrNull()?.minus(1) ?: return@apply
                while (size <= index) add(null)
                this[index] = IndexingProgress(rows, total, active)
            })
        }
    }
    private fun finishFileOperation() {
        pendingFileOperations--
        _state.update { it.copy(isManagingFiles = pendingFileOperations > 0, fileProgress = null,
            fileProgressRows = 0, fileProgressTotal = 0) }
    }

    private fun importProgress(isData: Boolean, rows: Int, total: Int) {
        showFileProgress(rows, total)
    }

    private fun showFileProgress(rows: Int, total: Int = 0) {
        val text = "جاري إضافة وفهرسة $activeFileName${if (rows > 0) ": $rows صف" else "..."}"
        val current = _state.value
        val loadedRows = if (rows > 0) rows else current.fileProgressRows
        val totalRows = if (total > 0) total else current.fileProgressTotal
        val percentage = if (totalRows > 0) (loadedRows * 100 / totalRows).coerceIn(0, 100) else 0
        val notificationText = if (totalRows > 0) {
            "$activeFileName: $loadedRows / $totalRows rows ($percentage%)"
        } else {
            "$activeFileName: $loadedRows rows"
        }
        _state.update { it.copy(isManagingFiles = true, message = null, fileProgress = text,
            fileProgressRows = if (rows > 0) rows else it.fileProgressRows,
            fileProgressTotal = if (total > 0) total else it.fileProgressTotal) }
        val shouldNotify = rows == 0 || total > 0 || rows - lastNotificationRows >= 5000 ||
            (totalRows > 0 && loadedRows >= totalRows)
        if (!shouldNotify) return
        lastNotificationRows = loadedRows
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                notificationManager.createNotificationChannel(
                    NotificationChannel("sorting_import", "استيراد الملفات", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val notification = if (android.os.Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(getApplication(), "sorting_import")
            } else Notification.Builder(getApplication())
            notification.setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("إضافة ملف")
                .setContentText(notificationText)
                .setOngoing(true)
                .setProgress(totalRows, loadedRows.coerceAtMost(totalRows), totalRows <= 0)
                .build()
                .also { notificationManager.notify(progressNotificationId, it) }
        }.onFailure { Log.d("SortingViewModel", "Progress notification unavailable", it) }
    }

    private fun finishFileNotification() {
        lastNotificationRows = -1
        runCatching { notificationManager.cancel(progressNotificationId) }
    }

    fun setUseTextWallet(value: Boolean) = _state.update { it.copy(useTextWallet = value, message = null) }
    fun setWalletText(value: String) = _state.update { it.copy(walletText = value, message = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun openResults() = _state.update { it.copy(showResults = it.hasCompletedSorting) }
    fun closeResults() = _state.update { it.copy(showResults = false) }

    fun startSorting() {
        val current = _state.value
        if (current.isLoading || current.isExporting || current.isManagingFiles || current.isIndexing) return
        val dataUri = current.dataFileUri
        if (dataUri == null && current.additionalDataFileNames.isEmpty()) {
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
            var pendingOld: ResultStore? = null
            try {
                storeMutex.withLock {
                    store?.close()
                    oldStore?.close()
                    oldStore = null
                    store = null
                }
                val completed = repository.sort(
                    current.walletText.takeIf { current.useTextWallet }, useChecking = current.checkingFileUri != null
                )
                val next = completed.store
                pending = next
                pendingOld = completed.oldStore
                val count = completed.count
                val firstPage = next.readPage(0, SortingStore.PAGE_SIZE * 2)
                storeMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    if (disposed.get()) throw CancellationException()
                    store = next
                    oldStore = completed.oldStore
                    pendingOld = null
                    pending = null
                    _state.update {
                        it.copy(isLoading = false, hasCompletedSorting = true, showResults = true,
                            results = firstPage, resultCount = count, showingOld = false, newCount = count, oldCount = completed.oldCount,
                            message = null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(isLoading = false, message = failure.message ?: "حدث خطأ أثناء الفرز") }
            } finally {
                pending?.close()
                pendingOld?.close()
            }
        }
    }

    fun selectResults(old: Boolean) {
        if (_state.value.isExporting || _state.value.showingOld == old) return
        if (old && _state.value.oldCount == 0) return
        pageJob?.cancel()
        requestedStart = -1
        _state.update { it.copy(showingOld = old, results = emptyList(), resultStart = -1,
            resultCount = if (old) it.oldCount else it.newCount, message = null) }
        loadVisibleRows(0)
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
                    val source = (if (_state.value.showingOld) oldStore else store) ?: return@withLock
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
        if (_state.value.isLoading || _state.value.isExporting || _state.value.isIndexing) return
        val old = _state.value.showingOld
        _state.update { it.copy(isExporting = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                storeMutex.withLock { (if (old) oldStore else store)?.let { action(it) } }
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
                    oldStore?.close()
                    oldStore = null
                store = null
            }
        }
        super.onCleared()
    }
}
