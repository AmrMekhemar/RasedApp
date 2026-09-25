package com.rased.feature.sorting.data

import com.rased.core.database.SortingDao
import com.rased.core.excel.XlsxWriter
import com.rased.feature.sorting.domain.SortingResult
import java.io.OutputStream

/** An immutable result snapshot; replacing an input cannot change a running export. */
internal class RoomResultStore(private val dao: SortingDao, private val runId: String) : ResultStore {
    private val firstId = dao.firstId(runId) ?: 0
    private var closed = false

    @Synchronized
    override fun readPage(start: Int, count: Int): List<SortingResult> {
        check(!closed)
        // A snapshot is inserted in one transaction, so its global ids are contiguous.
        return dao.page(runId, firstId + start, count).map {
            SortingResult(it.plate, it.type, it.note, it.street, it.district, it.date, it.walletType, it.location)
        }
    }

    @Synchronized
    override fun copyText(): String {
        val text = StringBuilder(HEADER).append('\n')
        forEachResult {
            val row = it.toTsvRow()
            check(text.length + row.length < 200_000) { "النتائج كبيرة للنسخ دفعة واحدة. استخدم حفظ النتائج لحفظها كاملة." }
            text.append(row).append('\n')
        }
        return text.toString().trimEnd()
    }

    @Synchronized
    override fun writeXlsx(output: OutputStream) {
        check(!closed)
        XlsxWriter.write(output, HEADER.split('\t')) { writeRow ->
            forEachResult { writeRow(listOf(it.plate, it.type, it.note, it.street, it.district, it.date, it.walletType, it.location)) }
        }
    }

    private fun forEachResult(action: (SortingResult) -> Unit) {
        var offset = 0
        while (true) {
            val page = readPage(offset, SortingStore.PAGE_SIZE)
            if (page.isEmpty()) return
            page.forEach(action)
            offset += page.size
        }
    }

    @Synchronized
    override fun close() {
        if (!closed) { dao.deleteResults(runId); closed = true }
    }

    private companion object {
        const val HEADER = "اللوحة\tالنوع\tالملاحظة\tالشارع\tالحي\tالتاريخ\tاللون\tالموقع"
    }
}
