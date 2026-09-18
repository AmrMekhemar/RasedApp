package com.rased.app

import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.rased.feature.sorting.data.SortingStore
import com.rased.core.excel.XlsxReader
import com.rased.feature.sorting.domain.SortingEngine
import com.rased.feature.sorting.ui.components.ResultsTable
import com.rased.feature.sorting.ui.components.SortingResults
import com.rased.feature.sorting.ui.SortingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exercises the production ViewModel, not just the XML reader. */
class LargeSortingRegression(private val instrumentation: Instrumentation) {
    fun run(): String {
        val context = instrumentation.targetContext
        val workbook = File.createTempFile("large-regression-", ".xlsx", context.cacheDir)
        val export = File.createTempFile("large-export-", ".xlsx", context.cacheDir)
        val owner = ViewModelStore()
        val runtime = Runtime.getRuntime()
        val peak = AtomicLong()
        val sampler = Executors.newSingleThreadScheduledExecutor()
        sampler.scheduleAtFixedRate({
            val used = runtime.totalMemory() - runtime.freeMemory()
            peak.updateAndGet { maxOf(it, used) }
        }, 0, 20, TimeUnit.MILLISECONDS)
        var activity: MainActivity? = null
        try {
            makeWorkbook(workbook)
            check(workbook.length() > 15_000_000) { "Fixture must exceed 15 MB compressed: ${workbook.length()}" }
            lateinit var model: SortingViewModel
            instrumentation.runOnMainSync {
                model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[SortingViewModel::class.java]
                model.setDataFile(Uri.fromFile(workbook))
                model.setWalletFile(Uri.fromFile(workbook))
                model.startSorting()
                model.startSorting() // Rapid duplicate taps must not start another job.
            }
            val completed = runBlocking {
                withTimeout(300_000) { model.state.first { !it.isLoading } }
            }
            check(completed.resultCount == ROWS) { "Wrong result count: ${completed.resultCount}; ${completed.message}" }
            check(completed.results.size == SortingStore.PAGE_SIZE * 2)
            check(completed.results.first().plate == plate(ROWS - 1))
            check(completed.results.first().walletType == "wallet")
            check(completed.results.first().note?.startsWith("first-${ROWS - 1}-") == true)

            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val vertical = LazyListState()
            val horizontal = ScrollState(0)
            instrumentation.runOnMainSync {
                activity.setContent {
                    val state by model.state.collectAsState()
                    MaterialTheme {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            ResultsTable(state.results, state.resultCount, state.resultStart, model::loadVisibleRows, vertical, horizontal)
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            for (index in listOf(14_900, 29_900, 100, 0)) {
                runBlocking {
                    withContext(Dispatchers.Main) { vertical.scrollToItem(index) }
                    withTimeout(20_000) { model.state.first { it.resultStart == index } }
                }
                instrumentation.waitForIdleSync()
                check(model.state.value.results.size <= SortingStore.PAGE_SIZE * 2)
                check(model.state.value.results.first().plate == plate(ROWS - 1 - index))
            }
            runBlocking { withContext(Dispatchers.Main) { horizontal.scrollTo(600) } }
            instrumentation.waitForIdleSync()
            check(horizontal.value > 0)
            // The phone layout must retain paging when cards replace table rows.
            val cards = LazyListState()
            instrumentation.runOnMainSync {
                activity.setContent {
                    val state by model.state.collectAsState()
                    MaterialTheme {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Box(Modifier.width(320.dp)) {
                                SortingResults(state.results, state.resultCount, state.resultStart, model::loadVisibleRows, cards)
                            }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            for (index in listOf(29_900, 0)) {
                runBlocking {
                    withContext(Dispatchers.Main) { cards.scrollToItem(index) }
                    withTimeout(20_000) { model.state.first { it.resultStart == index } }
                }
                instrumentation.waitForIdleSync()
                check(model.state.value.results.first().plate == plate(ROWS - 1 - index))
                check(model.state.value.results.size <= SortingStore.PAGE_SIZE * 2)
            }
            // A quick reversal must cancel a pending page instead of replacing
            // the already visible first page with stale rows from far below.
            instrumentation.runOnMainSync {
                model.loadVisibleRows(20_000)
                model.loadVisibleRows(0)
            }
            runBlocking { delay(250) }
            check(model.state.value.resultStart == 0)

            instrumentation.runOnMainSync { model.copyResults() }
            val copyState = runBlocking { withTimeout(20_000) { model.state.first { !it.isExporting } } }
            check(copyState.message?.contains("حفظ النتائج") == true) { "Large clipboard copy must be bounded" }
            instrumentation.runOnMainSync { model.exportResults(Uri.fromFile(export)) }
            val exported = runBlocking { withTimeout(60_000) { model.state.first { !it.isExporting } } }
            check(exported.message == null) { exported.message.orEmpty() }
            var exportedRows = 0
            XlsxReader(context).forEachRow(Uri.fromFile(export), "النتائج", SortingEngine.plateNames()) {
                check(!it["اللوحة"].isNullOrBlank())
                exportedRows++
            }
            check(exportedRows == ROWS)

            // Database matching keeps first occurrences and preserves wallet order.
            SortingStore(context.cacheDir).use { store ->
                store.transaction {
                    store.addWalletPlate("ا ب ج ١٢٣٤", "first wallet")
                    store.addWalletPlate("ابج1234", "duplicate wallet")
                    store.addWalletPlate("دهو5678")
                    store.matchDataRow(mapOf("اللوحة" to "دهو5678"))
                    store.matchDataRow(mapOf("اللوحة" to "ابج1234", "الملاحظة" to "first data"))
                    store.matchDataRow(mapOf("اللوحة" to "ابج1234", "الملاحظة" to "duplicate data"))
                }
                check(store.finish() == 2)
                val page = store.readPage(0, 100)
                check(page.map { it.plate } == listOf("ابج1234", "دهو5678"))
                check(page.first().note == "first data" && page.first().walletType == "first wallet")
                check(store.copyText().contains("first data"))
            }
            SortingStore(context.cacheDir).use { store ->
                store.transaction {
                    store.addWalletPlate("ابج1234")
                    store.matchDataRow(mapOf("اللوحة" to "دهو5678"))
                }
                check(store.finish() == 0 && store.readPage(0, 100).isEmpty())
            }
            val fileMb = workbook.length() / 1_000_000
            val heapMb = peak.get() / 1_048_576
            return "PASS: ${fileMb} MB XLSX, $ROWS distinct matches, table and phone-card paging both directions, horizontal scroll, bounded copy, full export; peak sampled heap ${heapMb} MiB / ${runtime.maxMemory() / 1_048_576} MiB"
        } finally {
            instrumentation.runOnMainSync {
                activity?.finish()
                owner.clear()
            }
            sampler.shutdownNow()
            workbook.delete()
            export.delete()
        }
    }

    private fun makeWorkbook(file: File) {
        val random = Random(12345)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val common = "x".repeat(4096)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun write(value: String) = zip.write(value.toByteArray(Charsets.UTF_8))
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                write(value)
                zip.closeEntry()
            }
            entry("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="داتا" r:id="data"/><sheet name="ورقة1" r:id="wallet"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="data" Target="worksheets/data.xml"/><Relationship Id="wallet" Target="worksheets/wallet.xml"/></Relationships>""")
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            write("<sst><si><t>اللوحة</t></si><si><t>الملاحظة</t></si><si><t>النوع</t></si>")
            repeat(ROWS) { index ->
                val noise = CharArray(768) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
                write("<si><t>first-$index-$common$noise</t></si>")
            }
            write("</sst>")
            zip.closeEntry()
            for (sheet in listOf("data", "wallet")) {
                zip.putNextEntry(ZipEntry("xl/worksheets/$sheet.xml"))
                write("<worksheet><sheetData><row><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c></row>")
                repeat(ROWS) { row ->
                    val index = if (sheet == "data") row else ROWS - row - 1
                    write("<row><c r=\"A${row + 2}\" t=\"inlineStr\"><is><t>${plate(index)}</t></is></c><c r=\"B${row + 2}\" t=\"s\"><v>${index + 3}</v></c><c r=\"C${row + 2}\" t=\"inlineStr\"><is><t>wallet</t></is></c></row>")
                }
                write("</sheetData></worksheet>")
                zip.closeEntry()
            }
        }
    }

    private fun plate(index: Int) = listOf("ابج", "دهو", "زحط")[index / 10_000] + (index % 10_000).toString().padStart(4, '0')

    companion object { private const val ROWS = 30_000 }
}
