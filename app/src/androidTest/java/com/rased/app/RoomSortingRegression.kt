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
            verifyCheckingSplit()
            verifyPlateOnlyInputs()
            verifyPlateDiscovery()
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

    private suspend fun verifyPlateDiscovery() {
        val repository = SortingRepository(context, "room.discovery")
        val source = File.createTempFile("discovery-", ".xlsx", context.cacheDir)
        try {
            suspend fun verify(expected: Int) {
                repository.replaceInput(Uri.fromFile(source), true)
                repository.replaceInput(Uri.fromFile(source), false)
                repository.replaceChecking(Uri.fromFile(source))
                val result = repository.sort(useChecking = true)
                result.store.use { check(result.count == 0) }
                requireNotNull(result.oldStore).use { check(result.oldCount == expected) }
            }
            for (name in SortingEngine.plateNames() + setOf("PLATE", "Plate_Num")) {
                write(source, listOf(name), listOf(listOf("محد1234")))
                verify(1)
            }
            // Unknown heading, optional columns retained, and spaced Arabic digits.
            write(source, listOf("غير معروف", "الملاحظة"), listOf(listOf("م ح د ١٢٣٤", "note"), listOf("محد5678", "next")))
            verify(2)
            repository.sort().store.use { check(it.readPage(0, 10).first().note == "note") }
            // Headerless: the first plate must not be discarded.
            write(source, listOf("محد1234"), listOf(listOf("م ح د1245"), listOf("م ح د 1235")))
            verify(3)
            // A recognized header takes precedence over a plate-like value in another column.
            write(source, listOf("محد9999", "Plate"), listOf(listOf("محد8888", "محد1234")))
            verify(1)
            repository.sort().store.use { check(it.readPage(0, 10).single().plate == "محد1234") }
            write(source, listOf("unknown"), List(3) { listOf("text") } + listOf(listOf("محد1234")))
            verify(1) // Excel row 5.
            write(source, listOf("unknown"), List(4) { listOf("text") } + listOf(listOf("محد1234")))
            check(runCatching { repository.replaceInput(Uri.fromFile(source), false) }.isFailure)
            for (invalid in listOf("مح1234", "محد123", "محد12345", "text محد1234")) {
                write(source, listOf("unknown"), listOf(listOf(invalid)))
                check(runCatching { repository.replaceInput(Uri.fromFile(source), false) }.isFailure)
            }
            write(source, listOf("one", "two"), listOf(listOf("محد1234", "محد5678")))
            check(runCatching { repository.replaceInput(Uri.fromFile(source), false) }.isFailure)
        } finally { source.delete() }
    }

    private suspend fun verifyPlateOnlyInputs() {
        val repository = SortingRepository(context, "room.plate.only")
        val source = File.createTempFile("plate-only-", ".xlsx", context.cacheDir)
        try {
            val aliases = listOf("اللوحة", "اللوحه", "لوحة", "لوحه", "رقم اللوحة", "رقم اللوحه", "\u200Fرقم\u00A0اللوحة\uFEFF", "اللَّـوحة", "رقم\nاللوحة")
            for (header in aliases) {
                // The plate column may be the sole column and may follow a long preamble.
                write(source, listOf("تقرير"), List(30) { listOf("مقدمة") } + listOf(listOf(header), listOf("أ ب ج ١٢٣٤")))
                repository.replaceInput(Uri.fromFile(source), true)
                repository.replaceInput(Uri.fromFile(source), false)
                repository.replaceChecking(Uri.fromFile(source))
                val result = repository.sort(useChecking = true)
                result.store.use { check(result.count == 0) }
                requireNotNull(result.oldStore).use {
                    check(result.oldCount == 1)
                    val row = it.readPage(0, 10).single()
                    check(row.plate == "أ ب ج ١٢٣٤")
                    check(listOf(row.type, row.note, row.street, row.district, row.date, row.walletType, row.location).all { value -> value == null })
                }
            }
            write(source, listOf("النوع"), listOf(listOf("سيارة")))
            check(runCatching { repository.replaceInput(Uri.fromFile(source), true) }.isFailure)
            check(runCatching { repository.replaceInput(Uri.fromFile(source), false) }.isFailure)
            check(runCatching { repository.replaceChecking(Uri.fromFile(source)) }.isFailure)
        } finally { source.delete() }
    }

    private suspend fun verifyCheckingSplit() {
        val repository = SortingRepository(context, "room.checking.regression")
        val source = File.createTempFile("checking-source-", ".xlsx", context.cacheDir)
        val export = File.createTempFile("checking-export-", ".xlsx", context.cacheDir)
        try {
            write(source, listOf("اللوحة"), listOf(listOf("ابج1234"), listOf("دهو5678"), listOf("زحط9999")))
            repository.replaceInput(Uri.fromFile(source), true)
            repository.replaceInput(Uri.fromFile(source), false)
            write(source, listOf("اللوحة"), listOf(listOf("أ ب ج ١٢٣٤"), listOf("ابج1234"), listOf("زحط9999")))
            repository.replaceChecking(Uri.fromFile(source))
            check(repository.loadChecking() != null)
            val result = repository.sort(useChecking = true)
            result.store.use { recent ->
                requireNotNull(result.oldStore).use { old ->
                    check(result.count == 1 && result.oldCount == 2)
                    check(recent.readPage(0, 10).map { it.plate } == listOf("دهو5678"))
                    check(old.readPage(0, 10).map { it.plate } == listOf("ابج1234", "زحط9999"))
                    check(old.readPage(1, 1).single().plate == "زحط9999")
                    for (store in listOf(recent, old)) {
                        export.outputStream().use(store::writeXlsx)
                        val rows = XlsxReader(context).readSheet(Uri.fromFile(export), null, SortingEngine.plateNames())
                        check(rows.map { it["اللوحة"] } == store.readPage(0, 10).map { it.plate })
                    }
                    check(!recent.copyText().contains("ابج1234"))
                    check(!old.copyText().contains("دهو5678"))
                    write(source, listOf("اللوحة"), emptyList())
                    repository.replaceChecking(Uri.fromFile(source))
                    check(old.readPage(0, 10).size == 2)
                }
            }
            val emptyCheck = repository.sort(useChecking = true)
            emptyCheck.store.use { check(emptyCheck.count == 3) }
            requireNotNull(emptyCheck.oldStore).use { check(emptyCheck.oldCount == 0) }
            val textWallet = repository.sort("ابج1234", useChecking = true)
            textWallet.store.use { check(textWallet.count == 1) }
            requireNotNull(textWallet.oldStore).close()
            val files = SavedFileStorage(context)
            val checking = requireNotNull(files.get("room.checking.regression.checking"))
            val data = files.get("room.checking.regression.data")
            val wallet = files.get("room.checking.regression.wallet")
            repository.removeChecking()
            check(files.get("room.checking.regression.checking") == null)
            check(!File(requireNotNull(files.uri(checking).path)).exists())
            check(files.get("room.checking.regression.data") == data)
            check(files.get("room.checking.regression.wallet") == wallet)
            val restored = SortingRepository(context, "room.checking.regression")
            check(restored.loadChecking() == null)
            restored.loadInputs()
            val withoutChecking = restored.sort()
            withoutChecking.store.use { check(withoutChecking.count == 3) }
            check(withoutChecking.oldCount == 0 && withoutChecking.oldStore == null)
            // Removing again is harmless; selecting a new file still works.
            restored.removeChecking()
            write(source, listOf("اللوحة"), listOf(listOf("ابج1234")))
            restored.replaceChecking(Uri.fromFile(source))
            val addedAgain = restored.sort(useChecking = true)
            addedAgain.store.use { check(addedAgain.count == 2) }
            requireNotNull(addedAgain.oldStore).use { check(addedAgain.oldCount == 1) }
        } finally {
            source.delete()
            export.delete()
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
