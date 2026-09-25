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
                fun restore(saved: SavedFile?, isData: Boolean): SavedFile? {
                    if (saved == null) return null
                    return try {
                        database.runInTransaction {
                            ensureImported(saved, isData, { job.ensureActive() }, onProgress)
                        }
                        saved
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        // Keep the original file for recovery, but do not let it hide the other input.
                        onFailure(isData, failure)
                        null
                    }
                }
                val data = restore(files.get("$slotPrefix.data"), true)
                val wallet = restore(files.get("$slotPrefix.wallet"), false)
                data to wallet
            }
        }

    suspend fun replaceInput(uri: Uri, isData: Boolean, onProgress: (Boolean, Int, Int) -> Unit = { _, _, _ -> }): SavedFile =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val job = currentCoroutineContext()
                files.replace("$slotPrefix.${if (isData) "data" else "wallet"}", uri) { saved, _ ->
                    ensureImported(saved, isData, { job.ensureActive() }, onProgress)
                }
            }
        }

    suspend fun loadAdditionalData(): List<SavedFile> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val saved = files.listByPrefix("$slotPrefix.data.extra.")
            val job = currentCoroutineContext()
            database.runInTransaction {
                saved.forEach { ensureImported(it, true, { job.ensureActive() }, { _, _, _ -> }) }
            }
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
            files.replace(slot, uri) { saved, _ -> ensureImported(saved, true, { job.ensureActive() }, onProgress) }
        }
    }

    suspend fun replaceDataFile(index: Int, uri: Uri, onProgress: (Boolean, Int, Int) -> Unit = { _, _, _ -> }): SavedFile {
        if (index == 0) return replaceInput(uri, true, onProgress)
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val saved = files.listByPrefix("$slotPrefix.data.extra.").getOrNull(index - 1)
                    ?: error("ملف الداتا غير موجود")
                val job = currentCoroutineContext()
                files.replace(saved.slot, uri) { replacement, _ ->
                    ensureImported(replacement, true, { job.ensureActive() }, onProgress)
                }
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
            database.runInTransaction { ensureImported(saved, false, { job.ensureActive() }, { _, _, _ -> }, sheetIndex = 1) }
            saved
        }
    }

    suspend fun replaceChecking(uri: Uri): SavedFile = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val job = currentCoroutineContext()
            files.replace("$slotPrefix.checking", uri) { saved, _ ->
                ensureImported(saved, false, { job.ensureActive() }, { _, _, _ -> }, sheetIndex = 1)
            }
        }
    }

    private fun ensureImported(saved: SavedFile, isData: Boolean, checkActive: () -> Unit, progress: (Boolean, Int, Int) -> Unit, sheetIndex: Int = 0) {
        val revision = "${saved.fileName}:$PARSER_VERSION"
        val previous = dao.imported(saved.slot)
        if (previous?.revision == revision && previous.parserVersion == PARSER_VERSION) return
        if (previous?.revision == revision) {
            if (isData) dao.deleteData(revision) else dao.deleteWallet(revision)
        }
        val dataBatch = ArrayList<IndexedDataRow>(64)
        val walletBatch = ArrayList<IndexedWalletRow>(64)
        var keys: List<String?>? = null
        var sequence = 0L
        val aliases = if (isData) DATA_COLUMNS else listOf(SortingEngine.plateNames(), SortingEngine.walletTypeNames(), SortingEngine.locationNames())
        progress(isData, 0, 0)
        reader.forEachSelectedRow(files.uri(saved), null, SortingEngine.plateNames(), aliases.flatten().toSet(), checkActive, { row ->
            checkActive()
            if (keys == null) keys = aliases.map { names ->
                val normalizedNames = names.map(::normalizeHeader).toSet()
                row.keys.firstOrNull { normalizeHeader(it) in normalizedNames }
            }
            val values = keys!!.map { key -> key?.let(row::get)?.takeIf { it.isNotBlank() } }
            val plate = values[0]
            val normalized = PlateNormalizer.normalize(plate)
            if (normalized != null) {
                if (isData) dataBatch += IndexedDataRow(revision, normalized, plate!!, values[1], values[2], values[3], values[4], values[5], values[6])
                else walletBatch += IndexedWalletRow(revision, normalized, sequence, values[1], values[2])
            }
            sequence++
            if (dataBatch.size >= 64) { dao.insertData(dataBatch); dataBatch.clear() }
            if (walletBatch.size >= 64) { dao.insertWallet(walletBatch); walletBatch.clear() }
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
                database.runInTransaction {
                    job.ensureActive()
                    check(dataFiles.isNotEmpty()) { "اختر ملف الداتا أولًا" }
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
                        val batch = ArrayList<IndexedWalletRow>(64)
                        walletText.lineSequence().forEachIndexed { index, plate ->
                            job.ensureActive()
                            PlateNormalizer.normalize(plate)?.let { batch += IndexedWalletRow(runId, it, index.toLong(), null) }
                            if (batch.size >= 64) { dao.insertWallet(batch); batch.clear() }
                        }
                        if (batch.isNotEmpty()) dao.insertWallet(batch)
                        runId
                    } else requireNotNull(dao.imported("$slotPrefix.wallet")) { "اختر ملف المحفظة أولًا" }.revision
                    if (useChecking) {
                        val checking = requireNotNull(dao.imported("$slotPrefix.checking")) { "اختر ملف التشييك أولًا" }
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

    fun exportResults(store: ResultStore, uri: Uri) {
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "تعذر حفظ النتائج" }.use(store::writeXlsx)
    }

    private companion object {
        val operationMutex = Mutex()
        const val PARSER_VERSION = 7
        val DATA_COLUMNS = listOf(SortingEngine.plateNames(), setOf("النوع"), setOf("الملاحظة", "ملاحظة", "الملاحظات"),
            setOf("الشارع", "شارع"), setOf("الحي", "حى"), setOf("التاريخ", "تاريخ"), SortingEngine.locationNames())
        fun normalizeHeader(value: String) = ExcelHeaders.normalize(value)
    }
}

data class CompletedSorting(val store: ResultStore, val count: Int, val oldStore: ResultStore? = null, val oldCount: Int = 0)
