package com.rased.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.rased.core.excel.XlsxReader
import com.rased.feature.sorting.data.SortingStore
import com.rased.feature.sorting.data.SortingRepository
import kotlinx.coroutines.runBlocking
import com.rased.feature.sorting.domain.PlateNormalizer
import com.rased.feature.sorting.domain.SortingEngine
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Test-only SQLite prototype for the indexed tables proposed for Room. */
class SortingSpeedBenchmark(private val context: Context) {
    private val reader = XlsxReader(context)
    private val headers = listOf("اللوحة", "النوع", "الملاحظة", "الشارع", "الحي", "التاريخ")
    private val report = StringBuilder()

    fun run(): String {
        line("Device=${android.os.Build.MODEL}; Android=${android.os.Build.VERSION.RELEASE}; debug build; milliseconds")
        scenario(1_000, 100, warmup = true)
        scenario(10_000, 1_000)
        scenario(100_000, 10_000)
        return report.toString()
    }

    private fun scenario(dataRows: Int, walletRows: Int, warmup: Boolean = false) {
        val data = File.createTempFile("speed-data-", ".xlsx", context.cacheDir)
        val wallet = File.createTempFile("speed-wallet-", ".xlsx", context.cacheDir)
        val database = File.createTempFile("speed-index-", ".db", context.cacheDir)
        try {
            workbook(data, dataRows, false)
            workbook(wallet, walletRows, true)
            line("${if (warmup) "WARMUP" else "CASE"} data=$dataRows wallet=$walletRows compressedBytes=${data.length() + wallet.length()}")
            var expected = ""
            repeat(if (warmup) 1 else 3) { run ->
                val start = now()
                SortingStore(context.cacheDir).use { store ->
                    var walletMs = 0L
                    var dataMs = 0L
                    store.transaction {
                        walletMs = timed { reader.forEachRow(Uri.fromFile(wallet), null, SortingEngine.plateNames(), store::addWalletRow) }
                        dataMs = timed { reader.forEachRow(Uri.fromFile(data), null, SortingEngine.plateNames(), store::matchDataRow) }
                    }
                    val count = store.finish()
                    store.readPage(0, 200)
                    val total = now() - start
                    check(count == walletRows)
                    val digest = MessageDigest.getInstance("SHA-256")
                    for (offset in 0 until count step 200) store.readPage(offset, 200).forEach {
                        digest.update((it.toTsvRow() + "\n").toByteArray())
                    }
                    expected = digest.digest().joinToString("") { "%02x".format(it) }
                    line("baseline run=$run total=$total walletReadAndInsert=$walletMs dataReadAndMatch=$dataMs checksum=$expected")
                }
            }
            SQLiteDatabase.openOrCreateDatabase(database, null).use { db ->
                val importMs = timed {
                    db.execSQL("CREATE TABLE data(normalized TEXT PRIMARY KEY, plate TEXT, type TEXT, note TEXT, street TEXT, district TEXT, date TEXT)")
                    db.execSQL("CREATE TABLE wallet(sequence INTEGER PRIMARY KEY, normalized TEXT UNIQUE, walletType TEXT)")
                    db.beginTransaction()
                    try {
                        db.compileStatement("INSERT OR IGNORE INTO data VALUES(?,?,?,?,?,?,?)").use { insert ->
                            reader.forEachRow(Uri.fromFile(data), null, SortingEngine.plateNames()) { row ->
                                val normalized = PlateNormalizer.normalize(row[headers[0]])
                                if (normalized != null) {
                                    insert.bindString(1, normalized)
                                    headers.forEachIndexed { index, header -> insert.bindString(index + 2, row[header].orEmpty()) }
                                    insert.executeInsert()
                                }
                            }
                        }
                        db.compileStatement("INSERT OR IGNORE INTO wallet(normalized,walletType) VALUES(?,?)").use { insert ->
                            reader.forEachRow(Uri.fromFile(wallet), null, SortingEngine.plateNames()) { row ->
                                val normalized = PlateNormalizer.normalize(row[headers[0]])
                                if (normalized != null) {
                                    insert.bindString(1, normalized)
                                    insert.bindString(2, row[headers[1]].orEmpty())
                                    insert.executeInsert()
                                }
                            }
                        }
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                }
                line("oneTimeIndexedImport=$importMs")
                repeat(if (warmup) 1 else 3) { run ->
                    val elapsed = timed {
                        db.beginTransaction()
                        try {
                            db.execSQL("DROP TABLE IF EXISTS results")
                            db.execSQL("CREATE TABLE results(id INTEGER PRIMARY KEY, plate TEXT, type TEXT, note TEXT, street TEXT, district TEXT, date TEXT, walletType TEXT)")
                            db.execSQL("INSERT INTO results(plate,type,note,street,district,date,walletType) SELECT d.plate,d.type,d.note,d.street,d.district,d.date,w.walletType FROM wallet w JOIN data d ON d.normalized=w.normalized ORDER BY w.sequence")
                            db.setTransactionSuccessful()
                        } finally { db.endTransaction() }
                        db.rawQuery("SELECT COUNT(*) FROM results", null).use { check(it.moveToFirst() && it.getInt(0) == walletRows) }
                        db.rawQuery("SELECT * FROM results WHERE id > 0 ORDER BY id LIMIT 200", null).use { cursor ->
                            while (cursor.moveToNext()) for (i in 1..7) cursor.getString(i)
                        }
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    db.rawQuery("SELECT plate,type,note,street,district,date,walletType FROM results ORDER BY id", null).use { cursor ->
                        while (cursor.moveToNext()) digest.update(((0..6).joinToString("\t") { cursor.getString(it).orEmpty() } + "\n").toByteArray())
                    }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Cached result differs from production" }
                    line("cached run=$run total=$elapsed identicalResults=true")
                }
            }
            runBlocking {
                val repository = SortingRepository(context, "speed.$dataRows")
                val importStart = now()
                repository.replaceInput(Uri.fromFile(data), true)
                repository.replaceInput(Uri.fromFile(wallet), false)
                line("productionRoomImportIncludingDurableCopies=${now() - importStart}")
                repository.loadInputs { _, _ -> error("Unchanged input was imported again") }
                repeat(if (warmup) 1 else 3) { run ->
                    val start = now()
                    val completed = repository.sort()
                    completed.store.use { result ->
                        result.readPage(0, 200)
                        val elapsed = now() - start
                        check(completed.count == walletRows)
                        val digest = MessageDigest.getInstance("SHA-256")
                        for (offset in 0 until completed.count step 200) result.readPage(offset, 200).forEach {
                            digest.update((it.toTsvRow() + "\n").toByteArray())
                        }
                        check(digest.digest().joinToString("") { "%02x".format(it) } == expected)
                        line("productionRoom run=$run total=$elapsed identicalResults=true")
                    }
                }
            }
        } finally {
            data.delete()
            wallet.delete()
            SQLiteDatabase.deleteDatabase(database)
        }
    }

    private fun workbook(file: File, rows: Int, wallet: Boolean) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun write(s: String) = zip.write(s.toByteArray())
            fun entry(name: String, body: () -> Unit) {
                zip.putNextEntry(ZipEntry(name)); body(); zip.closeEntry()
            }
            entry("xl/workbook.xml") { write("""<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="arbitrary" r:id="first"/></sheets></workbook>""") }
            entry("xl/_rels/workbook.xml.rels") { write("""<Relationships><Relationship Id="first" Target="worksheets/first.xml"/></Relationships>""") }
            entry("xl/sharedStrings.xml") {
                write("<sst>")
                headers.forEach { write("<si><t>$it</t></si>") }
                repeat(rows) { write("<si><t>note-$it-${"abcdefgh".repeat(10)}</t></si>") }
                write("</sst>")
            }
            entry("xl/worksheets/first.xml") {
                write("<worksheet><sheetData><row>")
                headers.indices.forEach { write("""<c r="${'A' + it}1" t="s"><v>$it</v></c>""") }
                write("</row>")
                fun row(index: Int, duplicate: Boolean = false, missing: Boolean = false) {
                    val id = if (missing) 900_000 else if (wallet) (rows - index - 1) * 10 else index
                    val values = listOf(plate(id), if (duplicate) "duplicate" else "type", "", "street", "district", "2026-09-19")
                    write("<row>")
                    values.forEachIndexed { col, value ->
                        if (col == 2) write("""<c r="C${index + 2}" t="s"><v>${headers.size + index}</v></c>""")
                        else write("""<c r="${'A' + col}${index + 2}" t="inlineStr"><is><t>$value</t></is></c>""")
                    }
                    write("</row>")
                }
                repeat(rows) { row(it) }
                row(0, duplicate = true)
                if (wallet) row(0, missing = true)
                write("</sheetData></worksheet>")
            }
        }
    }

    private fun plate(id: Int) = "اب" + ('ا'.code + id / 10_000).toChar() + (id % 10_000).toString().padStart(4, '0')
    private fun now() = SystemClock.elapsedRealtime()
    private inline fun timed(block: () -> Unit): Long { val start = now(); block(); return now() - start }
    private fun line(text: String) { report.appendLine(text); Log.i("SortingSpeed", text) }
}
