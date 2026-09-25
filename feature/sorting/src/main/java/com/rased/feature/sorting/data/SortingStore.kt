package com.rased.feature.sorting.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.rased.feature.sorting.domain.PlateNormalizer
import com.rased.feature.sorting.domain.SortingEngine
import com.rased.feature.sorting.domain.SortingResult
import java.io.Closeable
import java.io.File
import com.rased.core.excel.XlsxWriter
import java.io.Writer
import java.io.OutputStream

/** Each sort owns a disposable database. No workbook-sized JVM collections. */
class SortingStore(cacheDir: File) : ResultStore {
    private val file = File.createTempFile("sorting-", ".db", cacheDir)
    private val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    private val walletInsert: SQLiteStatement
    private val matchUpdate: SQLiteStatement
    private var closed = false

    init {
        db.execSQL("PRAGMA cache_size = -2048")
        db.execSQL("PRAGMA temp_store = FILE")
        db.execSQL("CREATE TABLE wallet (sequence INTEGER PRIMARY KEY, normalized TEXT UNIQUE NOT NULL, walletType TEXT, plate TEXT, type TEXT, note TEXT, street TEXT, district TEXT, date TEXT, location TEXT, walletLocation TEXT)")
        db.execSQL("CREATE TABLE results (id INTEGER PRIMARY KEY, plate TEXT, type TEXT, note TEXT, street TEXT, district TEXT, date TEXT, walletType TEXT, location TEXT)")
        walletInsert = db.compileStatement("INSERT OR IGNORE INTO wallet(normalized, walletType, walletLocation) VALUES (?, ?, ?)")
        matchUpdate = db.compileStatement("UPDATE wallet SET plate=?, type=?, note=?, street=?, district=?, date=?, location=? WHERE normalized=? AND plate IS NULL")
    }

    fun transaction(block: () -> Unit) {
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addWalletRow(row: Map<String, String>) {
        addWalletPlate(value(row, SortingEngine.plateNames()), value(row, SortingEngine.walletTypeNames()), value(row, SortingEngine.locationNames()))
    }

    fun addWalletPlate(plate: String?, type: String? = null, location: String? = null) {
        val normalized = PlateNormalizer.normalize(plate) ?: return
        walletInsert.bindString(1, normalized)
        walletInsert.bindText(2, type)
        walletInsert.bindText(3, location)
        walletInsert.executeInsert()
    }

    fun matchDataRow(row: Map<String, String>) {
        val plate = value(row, SortingEngine.plateNames())
        val normalized = PlateNormalizer.normalize(plate) ?: return
        matchUpdate.bindText(1, plate)
        matchUpdate.bindText(2, value(row, setOf("النوع")))
        matchUpdate.bindText(3, value(row, setOf("الملاحظة", "ملاحظة", "الملاحظات")))
        matchUpdate.bindText(4, value(row, setOf("الشارع", "شارع")))
        matchUpdate.bindText(5, value(row, setOf("الحي", "حى")))
        matchUpdate.bindText(6, value(row, setOf("التاريخ", "تاريخ")))
        matchUpdate.bindText(7, value(row, SortingEngine.locationNames()))
        matchUpdate.bindString(8, normalized)
        matchUpdate.executeUpdateDelete()
    }

    fun finish(): Int {
        // Dense row ids allow indexed page access without a growing SQL OFFSET.
        db.execSQL("INSERT INTO results(plate,type,note,street,district,date,walletType,location) SELECT plate,type,note,street,district,date,walletType,COALESCE(location,walletLocation) FROM wallet WHERE plate IS NOT NULL ORDER BY sequence")
        db.execSQL("DROP TABLE wallet")
        return db.compileStatement("SELECT COUNT(*) FROM results").use { it.simpleQueryForLong().toInt() }
    }

    @Synchronized
    override fun readPage(start: Int, count: Int): List<SortingResult> {
        check(!closed)
        return db.rawQuery("SELECT plate,type,note,street,district,date,walletType,location FROM results WHERE id > ? ORDER BY id LIMIT ?", arrayOf(start.toString(), count.toString())).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.result()) }
        }
    }

    @Synchronized
    override fun copyText(): String {
        val text = StringBuilder(TSV_HEADER).append('\n')
        forEachResult { result ->
            val row = result.toTsvRow()
            // Clipboard data travels through Binder; keep both heap and IPC bounded.
            check(text.length + row.length < 200_000) { "النتائج كبيرة للنسخ دفعة واحدة. استخدم حفظ النتائج لحفظها كاملة." }
            text.append(row).append('\n')
        }
        return text.toString().trimEnd()
    }

    @Synchronized
    fun writeTsv(writer: Writer) {
        writer.append(TSV_HEADER).append('\n')
        forEachResult { writer.append(it.toTsvRow()).append('\n') }
    }

    @Synchronized
    override fun writeXlsx(output: OutputStream) {
        XlsxWriter.write(output, TSV_HEADER.split('\t')) { writeRow ->
            forEachResult { result ->
                writeRow(listOf(result.plate, result.type, result.note, result.street, result.district, result.date, result.walletType, result.location))
            }
        }
    }

    private fun forEachResult(block: (SortingResult) -> Unit) {
        check(!closed)
        db.rawQuery("SELECT plate,type,note,street,district,date,walletType,location FROM results ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) block(cursor.result())
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        walletInsert.close()
        matchUpdate.close()
        db.close()
        SQLiteDatabase.deleteDatabase(file)
    }

    private fun Cursor.result() = SortingResult(getString(0), getString(1), getString(2), getString(3), getString(4), getString(5), getString(6), getString(7))

    private fun SQLiteStatement.bindText(index: Int, value: String?) {
        if (value == null) bindNull(index) else bindString(index, value)
    }

    private fun value(row: Map<String, String>, aliases: Set<String>): String? =
        row.entries.firstOrNull { it.key.trim().replace(" ", "") in aliases }?.value?.ifBlank { null }

    companion object {
        const val PAGE_SIZE = 100
        private const val TSV_HEADER = "اللوحة\tالنوع\tالملاحظة\tالشارع\tالحي\tالتاريخ\tاللون\tالموقع"
    }
}
