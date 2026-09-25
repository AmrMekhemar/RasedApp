package com.rased.feature.sorting.data

import android.content.Context
import android.net.Uri
import com.rased.core.database.IndexedDataRow
import com.rased.core.database.IndexedWalletRow
import com.rased.core.database.RasedDatabase
import com.rased.core.database.SavedFile
import com.rased.core.database.SavedFileStorage
import com.rased.core.database.SortingImport
import com.rased.core.excel.XlsxReader
import com.rased.core.excel.ExcelHeaders
import com.rased.feature.sorting.domain.PlateNormalizer
import com.rased.feature.sorting.domain.SortingEngine
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Imports changed files once; subsequent matches use the indexed Room rows. */
class SortingRepository(private val context: Context, private val slotPrefix: String = "sorting") {
    private val reader = XlsxReader(context)
    private val files = SavedFileStorage(context)
    private val database get() = RasedDatabase.getInstance(context)
    private val dao get() = database.sorting()

    suspend fun hasPrimaryData(): Boolean = files.get("$slotPrefix.data") != null

    suspend fun loadInputs(
        onFailure: (Boolean, Exception) -> Unit = { _, _ -> },
        onProgress: (Boolean, Int, Int) -> Unit = { _, _, _ -> }
    ): Pair<SavedFile?, SavedFile?> =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val job = currentCoroutineContext()
                // Match the old fast import behavior: restoring a saved file only reads metadata.
                val data = files.get("$slotPrefix.data")
                val wallet = files.get("$slotPrefix.wallet")
                data to wallet
            }
        }

    suspend fun replaceInput(uri: Uri, isData: Boolean, onProgress: (Boolean, Int, Int) -> Unit = { _, _, _ -> }): SavedFile =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val job = currentCoroutineContext()
                files.replace("$slotPrefix.${if (isData) "data" else "wallet"}", uri)
            }
        }

    suspend fun indexSavedInput(slot: String, isData: Boolean, sheetIndex: Int = 0) = withContext(Dispatchers.IO) {
        val saved = files.get(slot) ?: return@withContext
        val job = currentCoroutineContext()
        database.runInTransaction {
            ensureImported(saved, isData, { job.ensureActive() }, { _, _, _ -> }, sheetIndex)
        }
    }

    suspend fun loadAdditionalData(): List<SavedFile> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val saved = files.listByPrefix("$slotPrefix.data.extra.")
            val job = currentCoroutineContext()
            Unit
            saved
        }
    }

    suspend fun addData(uri: Uri, onProgress: (Boolean, Int, Int) -> Unit = { _, _, _ -> }): SavedFile = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val prefix = "$slotPrefix.data.extra."
            val last = files.listByPrefix(prefix).lastOrNull()?.slot?.removePrefix(prefix)?.toLong() ?: 0L
            val slot = if (files.get("$slotPrefix.data") == null) "$slotPrefix.data"
                else prefix + (last + 1).toString().padStart(10, '0')
            val job = currentCoroutineContext()
            files.replace(slot, uri)
        }
    }

    suspend fun replaceDataFile(index: Int, uri: Uri, onProgress: (Boolean, Int, Int) -> Unit = { _, _, _ -> }): SavedFile {
        if (index == 0) return replaceInput(uri, true, onProgress)
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val saved = files.listByPrefix("$slotPrefix.data.extra.").getOrNull(index - 1)
                    ?: error("ظ…ظ„ظپ ط§ظ„ط¯ط§طھط§ ط؛ظٹط± ظ…ظˆط¬ظˆط¯")
                val job = currentCoroutineContext()
                files.replace(saved.slot, uri)
            }
        }
    }

    suspend fun replaceOrAddSecondData(uri: Uri, onProgress: (Boolean, Int, Int) -> Unit = { _, _, _ -> }): SavedFile {
        return if (files.listByPrefix("$slotPrefix.data.extra.").isEmpty()) addData(uri, onProgress)
        else replaceDataFile(1, uri, onProgress)
    }

    suspend fun removeDataFile(index: Int) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val saved = if (index == 0) files.get("$slotPrefix.data")
            else files.listByPrefix("$slotPrefix.data.extra.").getOrNull(index - 1)
            saved?.let {
                files.remove(it.slot) {
                    dao.imported(it.slot)?.let { imported -> dao.deleteData(imported.revision) }
                    dao.deleteImport(it.slot)
                }
            }
        }
    }

    suspend fun removeChecking() = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val slot = "$slotPrefix.checking"
            files.remove(slot) {
                dao.imported(slot)?.let { dao.deleteWallet(it.revision) }
                dao.deleteImport(slot)
            }
        }
    }

    suspend fun loadChecking(): SavedFile? = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val saved = files.get("$slotPrefix.checking") ?: return@withLock null
            val job = currentCoroutineContext()
            Unit
            saved
        }
    }

    suspend fun replaceChecking(uri: Uri): SavedFile = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val job = currentCoroutineContext()
            files.replace("$slotPrefix.checking", uri)
        }
    }

    private fun ensureImported(saved: SavedFile, isData: Boolean, checkActive: () -> Unit, progress: (Boolean, Int, Int) -> Unit, sheetIndex: Int = 0) {
        val revision = "${saved.fileName}:$PARSER_VERSION"
        val previous = dao.imported(saved.slot)
        if (previous?.revision == revision && previous.parserVersion == PARSER_VERSION) return
        if (previous?.revision == revision) {
            if (isData) dao.deleteData(revision) else dao.deleteWallet(revision)
        }
        val dataBatch = ArrayList<IndexedDataRow>(IMPORT_BATCH_SIZE)
        val walletBatch = ArrayList<IndexedWalletRow>(IMPORT_BATCH_SIZE)
        var keys: List<String?>? = null
        var sequence = 0L
        val aliases = if (isData) DATA_COLUMNS else listOf(SortingEngine.plateNames(), SortingEngine.walletTypeNames(), SortingEngine.locationNames())
        progress(isData, 0, 0)
        reader.forEachSelectedRow(files.uri(saved), null, SortingEngine.plateNames(), aliases.flatten().toSet(), checkActive, { row ->
            checkActive()
            if (keys == null) keys = aliases.mapIndexed { index, names ->
                val normalizedNames = names.map(::normalizeHeader).toSet()
                row.keys.firstOrNull { normalizeHeader(it) in normalizedNames }
                    ?: row.keys.firstOrNull { !isData && index == 1 && normalizeHeader(it).contains(normalizeHeader("ظ„ظˆظ†")) }
            }
            val values = keys!!.map { key -> key?.let(row::get)?.takeIf { it.isNotBlank() } }
            val plate = values[0]
            val normalized = PlateNormalizer.normalize(plate)
            if (normalized != null) {
                if (isData) dataBatch += IndexedDataRow(revision, normalized, plate!!, values[1], values[2], values[3], values[4], values[5], values[6])
                else walletBatch += IndexedWalletRow(revision, normalized, sequence, values[1], values[2])
            }
            sequence++
            if (dataBatch.size >= IMPORT_BATCH_SIZE) { dao.insertData(dataBatch); dataBatch.clear() }
            if (walletBatch.size >= IMPORT_BATCH_SIZE) { dao.insertWallet(walletBatch); walletBatch.clear() }
            if (sequence % 1000L == 0L) progress(isData, sequence.toInt(), 0)
        }, visibleSheetIndex = sheetIndex, onTotalRows = { total -> progress(isData, 0, total) })
        if (dataBatch.isNotEmpty()) dao.insertData(dataBatch)
        if (walletBatch.isNotEmpty()) dao.insertWallet(walletBatch)
        checkActive()
        dao.activate(SortingImport(saved.slot, revision, PARSER_VERSION))
        previous?.takeIf { it.revision != revision }?.let {
            if (isData) dao.deleteData(it.revision) else dao.deleteWallet(it.revision)
        }
        progress(isData, sequence.toInt(), sequence.toInt())
    }

    suspend fun sort(walletText: String? = null, useChecking: Boolean = false): CompletedSorting {
        var completed: CompletedSorting? = null
        try {
            return withContext(Dispatchers.IO) {
                createSnapshot(walletText, useChecking).also { completed = it }
            }
        } catch (failure: Throwable) {
            // Cancellation can happen while dispatching a finished snapshot back to the UI.
            withContext(NonCancellable + Dispatchers.IO) { completed?.let { it.store.close(); it.oldStore?.close() } }
            throw failure
        }
    }

    private suspend fun createSnapshot(walletText: String?, useChecking: Boolean): CompletedSorting = operationMutex.withLock {
            val runId = UUID.randomUUID().toString()
            val oldRunId = UUID.randomUUID().toString()
            val job = currentCoroutineContext()
            var result: RoomResultStore? = null
            try {
                var count = 0
                val dataFiles = listOfNotNull(files.get("$slotPrefix.data")) + files.listByPrefix("$slotPrefix.data.extra.")
                val walletSaved = files.get("$slotPrefix.wallet")
                val checkingSaved = files.get("$slotPrefix.checking")
                if (!useChecking && walletText == null && dataFiles.isNotEmpty() && walletSaved != null) {
                    return@withLock sortFast(dataFiles, walletSaved, job)
                }
                database.runInTransaction {
                    job.ensureActive()
                    check(dataFiles.isNotEmpty()) { "ط§ط®طھط± ظ…ظ„ظپ ط§ظ„ط¯ط§طھط§ ط£ظˆظ„ظ‹ط§" }
                    val revisions = dataFiles.map { saved ->
                        ensureImported(saved, true, { job.ensureActive() }, { _, _, _ -> })
                        requireNotNull(dao.imported(saved.slot)).revision
                    }
                    val dataRevision = if (revisions.size == 1) revisions.single() else {
                        // First added file wins for repeated plates, matching the existing first-row rule.
                        revisions.forEach { job.ensureActive(); dao.mergeData(it, runId) }
                        runId
                    }
                    val walletRevision = if (walletText != null) {
                        val batch = ArrayList<IndexedWalletRow>(IMPORT_BATCH_SIZE)
                        walletText.lineSequence().forEachIndexed { index, plate ->
                            job.ensureActive()
                            PlateNormalizer.normalize(plate)?.let { batch += IndexedWalletRow(runId, it, index.toLong(), null) }
                            if (batch.size >= IMPORT_BATCH_SIZE) { dao.insertWallet(batch); batch.clear() }
                        }
                        if (batch.isNotEmpty()) dao.insertWallet(batch)
                        runId
                    } else {
                        val wallet = requireNotNull(walletSaved) { "ط§ط®طھط± ظ…ظ„ظپ ط§ظ„ظ…ط­ظپط¸ط© ط£ظˆظ„ظ‹ط§" }
                        ensureImported(wallet, false, { job.ensureActive() }, { _, _, _ -> })
                        requireNotNull(dao.imported(wallet.slot)).revision
                    }
                    if (useChecking) {
                        val checkingFile = requireNotNull(checkingSaved) { "ط§ط®طھط± ظ…ظ„ظپ ط§ظ„طھط´ظٹظٹظƒ ط£ظˆظ„ظ‹ط§" }
                        ensureImported(checkingFile, false, { job.ensureActive() }, { _, _, _ -> }, sheetIndex = 1)
                        val checking = requireNotNull(dao.imported("$slotPrefix.checking")) { "ط§ط®طھط± ظ…ظ„ظپ ط§ظ„طھط´ظٹظٹظƒ ط£ظˆظ„ظ‹ط§" }
                        dao.matchChecked(runId, dataRevision, walletRevision, checking.revision, false)
                        dao.matchChecked(oldRunId, dataRevision, walletRevision, checking.revision, true)
                    } else dao.match(runId, dataRevision, walletRevision)
                    if (revisions.size > 1) dao.deleteData(runId)
                    if (walletText != null) dao.deleteWallet(runId)
                    count = dao.count(runId)
                    job.ensureActive()
                }
                result = RoomResultStore(dao, runId)
                job.ensureActive()
                CompletedSorting(result, count, if (useChecking) RoomResultStore(dao, oldRunId) else null, dao.count(oldRunId))
            } catch (failure: Throwable) {
                if (result != null) result.close() else dao.deleteResults(runId)
                dao.deleteResults(oldRunId)
                throw failure
            }
    }

    private fun sortFast(dataFiles: List<SavedFile>, wallet: SavedFile, job: kotlin.coroutines.CoroutineContext): CompletedSorting {
        val store = SortingStore(context.cacheDir)
        try {
            store.transaction {
                reader.forEachSelectedRow(files.uri(wallet), null, SortingEngine.plateNames(),
                    (SortingEngine.plateNames() + SortingEngine.walletTypeNames() + SortingEngine.locationNames()).toSet(),
                    { job.ensureActive() }, store::addWalletRow)
                dataFiles.forEach { data ->
                    reader.forEachSelectedRow(files.uri(data), null, SortingEngine.plateNames(),
                        DATA_COLUMNS.flatten().toSet(), { job.ensureActive() }, store::matchDataRow)
                }
            }
            val count = store.finish()
            job.ensureActive()
            return CompletedSorting(store, count)
        } catch (failure: Throwable) {
            store.close()
            throw failure
        }
    }
    fun exportResults(store: ResultStore, uri: Uri) {
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "طھط¹ط°ط± ط­ظپط¸ ط§ظ„ظ†طھط§ط¦ط¬" }.use(store::writeXlsx)
    }

    private companion object {
        val operationMutex = Mutex()
        const val PARSER_VERSION = 8
        const val IMPORT_BATCH_SIZE = 512
        val DATA_COLUMNS = listOf(SortingEngine.plateNames(), setOf("ط§ظ„ظ†ظˆط¹"), setOf("ط§ظ„ظ…ظ„ط§ط­ط¸ط©", "ظ…ظ„ط§ط­ط¸ط©", "ط§ظ„ظ…ظ„ط§ط­ط¸ط§طھ"),
            setOf("ط§ظ„ط´ط§ط±ط¹", "ط´ط§ط±ط¹"), setOf("ط§ظ„ط­ظٹ", "ط­ظ‰"), setOf("ط§ظ„طھط§ط±ظٹط®", "طھط§ط±ظٹط®"), SortingEngine.locationNames())
        fun normalizeHeader(value: String) = ExcelHeaders.normalize(value)
    }
}

data class CompletedSorting(val store: ResultStore, val count: Int, val oldStore: ResultStore? = null, val oldCount: Int = 0)
