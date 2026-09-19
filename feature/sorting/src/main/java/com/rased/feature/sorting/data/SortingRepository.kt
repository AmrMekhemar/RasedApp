package com.rased.feature.sorting.data

import android.content.Context
import android.net.Uri
import com.rased.core.excel.XlsxReader
import com.rased.feature.sorting.domain.SortingEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Owns file access and matching; the caller owns and closes a completed store. */
internal class SortingRepository(private val context: Context) {
    private val reader = XlsxReader(context)

    suspend fun sort(dataUri: Uri, walletUri: Uri?, walletText: String?): CompletedSorting {
        val store = SortingStore(context.cacheDir)
        try {
            val jobContext = currentCoroutineContext()
            store.transaction {
                if (walletText != null) {
                    walletText.lineSequence().forEach {
                        jobContext.ensureActive()
                        store.addWalletPlate(it)
                    }
                } else {
                    reader.forEachRow(requireNotNull(walletUri), null, SortingEngine.plateNames()) {
                        jobContext.ensureActive()
                        store.addWalletRow(it)
                    }
                }
                reader.forEachRow(dataUri, null, SortingEngine.plateNames()) {
                    jobContext.ensureActive()
                    store.matchDataRow(it)
                }
            }
            val count = store.finish()
            jobContext.ensureActive()
            return CompletedSorting(store, count)
        } catch (failure: Throwable) {
            store.close()
            throw failure
        }
    }

    fun exportResults(store: SortingStore, uri: Uri) {
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "تعذر حفظ النتائج" }.use {
            store.writeXlsx(it)
        }
    }
}

internal data class CompletedSorting(val store: SortingStore, val count: Int)
