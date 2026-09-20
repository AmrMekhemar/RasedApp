package com.rased.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.rased.core.excel.XlsxReader
import com.rased.feature.sorting.data.SortingRepository
import com.rased.feature.sorting.domain.SortingEngine
import com.rased.feature.sorting.ui.components.LocationLinks
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

class HyperlinkRegression(private val context: Context) {
    fun run() = runBlocking {
        val file = File.createTempFile("hyperlink-regression-", ".xlsx", context.cacheDir)
        val target = "https://maps.google.com/?q=30.1,31.2&z=15"
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
                entry("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" r:id="s1"/></sheets></workbook>""")
                entry("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="s1" Target="worksheets/sheet1.xml"/></Relationships>""")
                entry("xl/worksheets/_rels/sheet1.xml.rels", """<Relationships><Relationship Id="link1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${target.replace("&", "&amp;")}" TargetMode="External"/></Relationships>""")
                entry("xl/worksheets/sheet1.xml", """<worksheet xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheetData>
                    <row r="1"><c r="A1" t="inlineStr"><is><t>اللوحة</t></is></c><c r="B1" t="inlineStr"><is><t>الموقع</t></is></c><c r="C1" t="inlineStr"><is><t>الملاحظة</t></is></c></row>
                    <row r="2"><c r="A2" t="inlineStr"><is><t>ابج1234</t></is></c><c r="B2" t="inlineStr"><is><t>افتح الخريطة</t></is></c><c r="C2" t="inlineStr"><is><t>ملاحظة</t></is></c></row>
                    <row r="3"><c r="A3" t="inlineStr"><is><t>دهو5678</t></is></c><c r="B3" t="str"><f>HYPERLINK("https://maps.google.com/?q=Alex","الخريطة")</f><v>الخريطة</v></c></row>
                    <row r="4"><c r="A4" t="inlineStr"><is><t>زحط9999</t></is></c><c r="B4" t="inlineStr"><is><t>https://maps.google.com/?q=Cairo</t></is></c></row>
                    </sheetData><hyperlinks><hyperlink ref="B2" r:id="link1"/><hyperlink ref="C2" r:id="link1"/></hyperlinks></worksheet>""")
            }
            val rows = XlsxReader(context).readSheet(Uri.fromFile(file), null, SortingEngine.plateNames())
            val expected = listOf(target, "https://maps.google.com/?q=Alex", "https://maps.google.com/?q=Cairo")
            check(rows.map { it["الموقع"] } == expected)
            check(rows.first()["الملاحظة"] == "ملاحظة")
            val repository = SortingRepository(context, "hyperlink.regression")
            repository.replaceInput(Uri.fromFile(file), true)
            repository.replaceInput(Uri.fromFile(file), false)
            repository.sort().store.use { check(it.readPage(0, 100).map { row -> row.location } == expected) }
            var opened: Intent? = null
            val capture = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) { opened = intent }
            }
            LocationLinks.open(capture, requireNotNull(LocationLinks.uri(target)))
            check(opened?.action == Intent.ACTION_VIEW && opened?.data.toString() == target)
            check(LocationLinks.uri("www.google.com/maps")?.scheme == "https")
            check(LocationLinks.uri("geo:30.1,31.2") != null)
            check(LocationLinks.uri("موقع غير مرتبط") == null)
            check(LocationLinks.uri("javascript:alert(1)") == null)
        } finally { file.delete() }
    }
}
