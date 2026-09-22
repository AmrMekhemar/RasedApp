package com.rased.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import com.rased.core.database.RasedDatabase
import com.rased.core.database.SavedFileStorage
import com.rased.core.excel.XlsxReader
import com.rased.core.excel.XlsxWriter
import com.rased.feature.sorting.data.SortingRepository
import com.rased.feature.sorting.domain.SortingEngine
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/** Production repository checks in dedicated slots, preserving the user's inputs. */
class RoomSortingRegression(private val context: Context) {
    fun run(verifyAfterRestart: Boolean) = runBlocking {
        if (!verifyAfterRestart) {
            verifyMigration()
            verifyInputRecovery()
        }
        val repository = SortingRepository(context, "room.regression")
        val files = SavedFileStorage(context)
        val dao = RasedDatabase.getInstance(context).sorting()
        if (!verifyAfterRestart) {
            val source = File.createTempFile("room-source-", ".xlsx", context.cacheDir)
            val export = File.createTempFile("room-export-", ".xlsx", context.cacheDir)
            try {
                write(source, listOf(" لوحه ", "النوع", " ملاحظة ", "شارع", "حى", "تاريخ", " الموقع "), listOf(
                    listOf("أ ب ج ١٢٣٤", "car", "first", "Nile", "Cairo", "2026-09-19", "data-location"),
                    listOf("ابج1234", "duplicate", "wrong", "", "", ""),
                    listOf("دهو5678", "bus", "second", "Sea", "Alex", "2026-09-18"),
                    listOf("invalid", "invalid", "ignored", "", "", "")
                ))
                // Emulate the pre-upgrade metadata-only file: loadInputs must import it once.
                files.replace("room.regression.data", Uri.fromFile(source))
                write(source, listOf("لوحة", "الموديل", "الموقع"), listOf(
                    listOf("د ه و ٥٦٧٨", "wallet-first", "wallet-location"), listOf("ابج1234", "wallet-second", "ignored-location"),
                    listOf("دهو5678", "wrong"), listOf("زحط9999", "missing")
                ))
                repository.replaceInput(Uri.fromFile(source), false)
                repository.loadInputs()
                val dataSaved = files.get("room.regression.data")!!
                val walletSaved = files.get("room.regression.wallet")!!
                val dataRevision = dao.imported("room.regression.data")!!
                val walletRevision = dao.imported("room.regression.wallet")!!
                source.delete()
                repository.loadInputs { _, _ -> error("Unexpected re-import") }
                val completed = repository.sort()
                completed.store.use { snapshot ->
                    check(completed.count == 2)
                    val expected = snapshot.readPage(0, 100)
                    check(expected.map { it.note } == listOf("second", "first"))
                    check(expected.map { it.walletType } == listOf("wallet-first", "wallet-second"))
                    check(expected.map { it.location } == listOf("wallet-location", "data-location"))
                    check(snapshot.copyText().contains("الموقع"))
                    check(expected[1].plate == "أ ب ج ١٢٣٤")
                    check(snapshot.readPage(1, 1) == expected.drop(1))
                    check(snapshot.readPage(2, 100).isEmpty())
                    check(snapshot.copyText().contains("first"))
                    export.outputStream().use(snapshot::writeXlsx)
                    val exported = XlsxReader(context).readSheet(Uri.fromFile(export), null, SortingEngine.plateNames())
                    check(exported.map { it["الموقع"] } == listOf("wallet-location", "data-location"))
                    check(exported.map { it["الملاحظة"] } == listOf("second", "first"))
                    check(exported.map { it["نوع المحفظة"] } == listOf("wallet-first", "wallet-second"))

                    source.writeText("not a workbook")
                    check(runCatching { repository.replaceInput(Uri.fromFile(source), true) }.isFailure)
                    check(files.get("room.regression.data") == dataSaved)
                    check(dao.imported("room.regression.data") == dataRevision)
                    check(File(requireNotNull(files.uri(dataSaved).path)).exists())

                    write(source, listOf("اللوحة", "النوع"), listOf(listOf("ابج1234", "changed")))
                    val failure = runCatching {
                        repository.replaceInput(Uri.fromFile(source), true) { _, rows ->
                            if (rows > 0) throw CancellationException("Simulate cancellation before commit")
                        }
                    }.exceptionOrNull()
                    check(failure is CancellationException)
                    check(files.get("room.regression.data") == dataSaved)
                    check(dao.imported("room.regression.data") == dataRevision)

                    repository.sort("ابج1234\nأ ب ج ١٢٣٤\nدهو5678").store.use {
                        check(it.readPage(0, 100).map { row -> row.note } == listOf("first", "second"))
                        check(it.readPage(0, 100).all { row -> row.walletType == null })
                        check(it.readPage(0, 100).map { row -> row.location } == listOf("data-location", null))
                    }
                    check(files.get("room.regression.wallet") == walletSaved)
                    check(dao.imported("room.regression.wallet") == walletRevision)
                    repository.sort("زحط9999").store.use { check(it.readPage(0, 100).isEmpty()) }
                    repository.sort("").store.use { check(it.readPage(0, 100).isEmpty()) }

                    // Replace only the wallet; the old snapshot stays immutable.
                    write(source, listOf("اللوحة", "الماركة"), listOf(listOf("ابج1234", "replacement")))
                    repository.replaceInput(Uri.fromFile(source), false)
                    check(dao.imported("room.regression.data") == dataRevision)
                    check(files.get("room.regression.data") == dataSaved)
                    check(snapshot.readPage(0, 100) == expected)
                    val newWalletRevision = dao.imported("room.regression.wallet")

                    write(source, listOf("اللوحة", "الملاحظة"), listOf(listOf("ابج1234", "updated")))
                    repository.replaceInput(Uri.fromFile(source), true)
                    check(dao.imported("room.regression.wallet") == newWalletRevision)
                    check(!File(requireNotNull(files.uri(dataSaved).path)).exists())
                    check(snapshot.readPage(0, 100) == expected)
                }
            } finally {
                source.delete()
                export.delete()
            }
        }
        repository.loadInputs { _, _ -> error("Re-import after process restart") }
        val restored = repository.sort()
        restored.store.use {
            check(restored.count == 1)
            check(it.readPage(0, 100).single().note == "updated")
            check(it.readPage(0, 100).single().walletType == "replacement")
        }
    }

    private fun write(file: File, headers: List<String>, rows: List<List<String>>) {
        file.outputStream().use { output -> XlsxWriter.write(output, headers) { emit -> rows.forEach(emit) } }
    }

    private suspend fun verifyInputRecovery() {
        val files = SavedFileStorage(context)
        val source = File.createTempFile("recovery-source-", ".xlsx", context.cacheDir)
        try {
            for (brokenData in listOf(true, false)) {
                val prefix = "room.recovery.$brokenData"
                val repository = SortingRepository(context, prefix)
                val brokenSlot = "$prefix.${if (brokenData) "data" else "wallet"}"
                val healthySlot = "$prefix.${if (brokenData) "wallet" else "data"}"
                write(source, listOf("اللوحة"), listOf(listOf("ابج1234")))
                // Both files need importing, as after upgrading the parser or restoring metadata.
                val healthy = files.replace(healthySlot, Uri.fromFile(source))
                source.writeText("damaged workbook")
                files.replace(brokenSlot, Uri.fromFile(source))
                val failures = mutableListOf<Boolean>()
                val restored = repository.loadInputs(onFailure = { isData, _ -> failures += isData })
                check(failures == listOf(brokenData))
                check((if (brokenData) restored.first else restored.second) == null)
                check((if (brokenData) restored.second else restored.first) == healthy)

                // A replacement must recover the session without clearing either input or the database.
                write(source, listOf("اللوحة"), listOf(listOf("ابج1234")))
                repository.replaceInput(Uri.fromFile(source), brokenData)
                check(files.get(healthySlot) == healthy)
                val completed = repository.sort()
                completed.store.use { check(completed.count == 1) }
                repository.loadInputs(onFailure = { _, failure -> throw failure })
            }
        } finally {
            source.delete()
        }
    }

    private fun verifyMigration() {
        val name = "room-migration-regression.db"
        context.deleteDatabase(name)
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
                old.execSQL("CREATE TABLE saved_files(slot TEXT NOT NULL PRIMARY KEY, fileName TEXT NOT NULL, displayName TEXT NOT NULL, sizeBytes INTEGER NOT NULL)")
                old.execSQL("INSERT INTO saved_files VALUES('sorting.data','old.xlsx','Original.xlsx',42)")
                old.version = 1
            }
            val migrated = Room.databaseBuilder(context, RasedDatabase::class.java, name).build()
            try {
                check(migrated.savedFiles().get("sorting.data")!!.displayName == "Original.xlsx")
                check(migrated.sorting().imported("sorting.data") == null)
                check(migrated.openHelper.readableDatabase.version == 3)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
