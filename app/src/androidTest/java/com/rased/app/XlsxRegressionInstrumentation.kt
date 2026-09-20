package com.rased.app

import android.app.Activity
import android.app.Instrumentation
import android.net.Uri
import android.os.Bundle
import com.rased.core.excel.XlsxReader
import com.rased.feature.sorting.domain.SortingEngine
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Dependency-free device regression check; run with adb shell am instrument -w. */
class XlsxRegressionInstrumentation : Instrumentation() {
    private var persistenceMode: String? = null
    private var benchmark = false
    private var roomMode: String? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        persistenceMode = arguments?.getString("persistence")
        benchmark = arguments?.getString("benchmark") == "sorting"
        roomMode = arguments?.getString("room")
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            roomMode?.let {
                RoomSortingRegression(targetContext).run(it == "verify")
                result.putString("stream", "PASS: Room sorting ($it), migration, aliases, duplicates, replacements, cancellation, exports\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (benchmark) {
                result.putString("stream", SortingSpeedBenchmark(targetContext).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            persistenceMode?.let { mode ->
                SavedFilesRegression(targetContext).run(mode == "verify")
                result.putString("stream", "PASS: persistent files ($mode), independent replacement, failed replacement retention\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            verifyLargeSheet()
            val largeResult = LargeSortingRegression(this).run()
            result.putString("stream", "PASS: 140 MiB worksheet, shared strings, first match, wallet order, no matches, cleanup\n$largeResult\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun verifyLargeSheet() {
        val directory = targetContext.cacheDir
        val before = directory.list()?.filter { it.startsWith("xlsx-") }?.toSet().orEmpty()
        val workbook = File.createTempFile("regression-", ".xlsx", directory)
        try {
            ZipOutputStream(workbook.outputStream().buffered()).use { zip ->
                fun entry(name: String, value: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(value.toByteArray())
                    zip.closeEntry()
                }
                entry("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="داتا" r:id="rId1"/></sheets></workbook>""")
                entry("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""")
                entry("xl/sharedStrings.xml", "<sst><si><t>اللوحة</t></si><si><t>الملاحظة</t></si><si><r><t>first</t></r><r><t> match</t></r></si></sst>")
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write("<worksheet><sheetData><row><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>".toByteArray())
                val padding = "x".repeat(1024)
                repeat(140_000) { row ->
                    zip.write("<row><c r=\"A${row + 2}\" t=\"inlineStr\"><is><t>ابج1234</t></is></c><c r=\"B${row + 2}\" t=\"inlineStr\"><is><t>$padding</t></is></c></row>".toByteArray())
                }
                zip.write("<row><c r=\"A140002\" t=\"inlineStr\"><is><t>دهو5678</t></is></c><c r=\"B140002\" t=\"s\"><v>2</v></c></row>".toByteArray())
                zip.write("<row><c r=\"A140003\" t=\"inlineStr\"><is><t>دهو5678</t></is></c><c r=\"B140003\" t=\"inlineStr\"><is><t>duplicate</t></is></c></row></sheetData></worksheet>".toByteArray())
                zip.closeEntry()
                // This unrelated entry must never be expanded by the reader.
                entry("xl/worksheets/sheet2.xml", "<not-a-worksheet/>")
            }
            val reader = XlsxReader(targetContext)
            val seen = hashSetOf<String>()
            val rows = reader.readSheet(Uri.fromFile(workbook), "داتا", SortingEngine.plateNames()) {
                it["اللوحة"] in setOf("دهو5678", "ابج1234") && seen.add(it["اللوحة"].orEmpty())
            }
            check(rows.size == 2)
            val wallet = listOf(mapOf("اللوحة" to "دهو5678", "النوع" to "wallet"), mapOf("اللوحة" to "ابج1234"), mapOf("اللوحة" to "دهو5678"))
            val results = SortingEngine.sort(rows, wallet)
            check(results.map { it.plate } == listOf("دهو5678", "ابج1234"))
            check(results.first().note == "first match")
            check(results.first().walletType == "wallet")
            check(reader.readSheet(Uri.fromFile(workbook), "داتا", SortingEngine.plateNames()) { false }.isEmpty())
            check(runCatching { reader.readSheet(Uri.fromFile(workbook), "missing", SortingEngine.plateNames()) }.isFailure)
            check(directory.list()?.filter { it.startsWith("xlsx-") }?.toSet().orEmpty() == before)
        } finally {
            workbook.delete()
        }
    }
}
