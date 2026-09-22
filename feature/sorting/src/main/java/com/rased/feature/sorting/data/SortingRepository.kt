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

    suspend fun loadInputs(
        onFailure: (Boolean, Exception) -> Unit = { _, _ -> },
        onProgress: (Boolean, Int) -> Unit = { _, _ -> }
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

    suspend fun replaceInput(uri: Uri, isData: Boolean, onProgress: (Boolean, Int) -> Unit = { _, _ -> }): SavedFile =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val job = currentCoroutineContext()
                files.replace("$slotPrefix.${if (isData) "data" else "wallet"}", uri) { saved, _ ->
                    ensureImported(saved, isData, { job.ensureActive() }, onProgress)
                }
            }
        }

    private fun ensureImported(saved: SavedFile, isData: Boolean, checkActive: () -> Unit, progress: (Boolean, Int) -> Unit) {
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
        progress(isData, 0)
        reader.forEachSelectedRow(files.uri(saved), null, SortingEngine.plateNames(), aliases.flatten().toSet(), checkActive) { row ->
            checkActive()
            if (keys == null) keys = aliases.map { names -> row.keys.firstOrNull { normalizeHeader(it) in names } }
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
            if (sequence % 1000L == 0L) progress(isData, sequence.toInt())
        }
        if (dataBatch.isNotEmpty()) dao.insertData(dataBatch)
        if (walletBatch.isNotEmpty()) dao.insertWallet(walletBatch)
        checkActive()
        dao.activate(SortingImport(saved.slot, revision, PARSER_VERSION))
        previous?.takeIf { it.revision != revision }?.let {
            if (isData) dao.deleteData(it.revision) else dao.deleteWallet(it.revision)
        }
        progress(isData, sequence.toInt())
    }

    suspend fun sort(walletText: String? = null): CompletedSorting {
        var completed: CompletedSorting? = null
        try {
            return withContext(Dispatchers.IO) {
                createSnapshot(walletText).also { completed = it }
            }
        } catch (failure: Throwable) {
            // Cancellation can happen while dispatching a finished snapshot back to the UI.
            withContext(NonCancellable + Dispatchers.IO) { completed?.store?.close() }
            throw failure
        }
    }

    private suspend fun createSnapshot(walletText: String?): CompletedSorting = operationMutex.withLock {
            val runId = UUID.randomUUID().toString()
            val job = currentCoroutineContext()
            var result: RoomResultStore? = null
            try {
                var count = 0
                database.runInTransaction {
                    job.ensureActive()
                    val data = requireNotNull(dao.imported("$slotPrefix.data")) { "اختر ملف الداتا أولًا" }
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
                    dao.match(runId, data.revision, walletRevision)
                    if (walletText != null) dao.deleteWallet(runId)
                    count = dao.count(runId)
                    job.ensureActive()
                }
                result = RoomResultStore(dao, runId)
                job.ensureActive()
                CompletedSorting(result, count)
            } catch (failure: Throwable) {
                if (result != null) result.close() else dao.deleteResults(runId)
                throw failure
            }
    }

    fun exportResults(store: ResultStore, uri: Uri) {
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "تعذر حفظ النتائج" }.use(store::writeXlsx)
    }

    private companion object {
        val operationMutex = Mutex()
        const val PARSER_VERSION = 3
        val DATA_COLUMNS = listOf(SortingEngine.plateNames(), setOf("النوع"), setOf("الملاحظة", "ملاحظة", "الملاحظات"),
            setOf("الشارع", "شارع"), setOf("الحي", "حى"), setOf("التاريخ", "تاريخ"), SortingEngine.locationNames())
        fun normalizeHeader(value: String) = value.trim().replace(" ", "")
    }
}

data class CompletedSorting(val store: ResultStore, val count: Int)
