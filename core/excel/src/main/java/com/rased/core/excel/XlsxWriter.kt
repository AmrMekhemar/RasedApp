package com.rased.core.excel

import android.util.Xml
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Streams rows directly into the workbook without collecting them in memory. */
object XlsxWriter {
    fun write(output: OutputStream, headers: List<String>, rows: ((List<String?>) -> Unit) -> Unit) {
        ZipOutputStream(output.buffered()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            entry("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="النتائج" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val xml = Xml.newSerializer()
            xml.setOutput(zip, "UTF-8")
            xml.startDocument("UTF-8", true)
            xml.startTag(null, "worksheet").attribute(null, "xmlns", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
            xml.startTag(null, "sheetViews")
            xml.startTag(null, "sheetView").attribute(null, "workbookViewId", "0").attribute(null, "rightToLeft", "1")
            xml.endTag(null, "sheetView").endTag(null, "sheetViews")
            xml.startTag(null, "sheetData")
            var rowNumber = 0
            fun row(values: List<String?>) {
                check(++rowNumber <= 1_048_576) { "عدد النتائج يتجاوز سعة ورقة Excel" }
                xml.startTag(null, "row").attribute(null, "r", rowNumber.toString())
                values.forEachIndexed { index, value ->
                    val text = value.orEmpty()
                    check(text.length <= 32_767) { "نص إحدى الخلايا يتجاوز الحد المسموح في Excel" }
                    xml.startTag(null, "c").attribute(null, "r", "${'A' + index}$rowNumber").attribute(null, "t", "inlineStr")
                    xml.startTag(null, "is").startTag(null, "t").attribute("http://www.w3.org/XML/1998/namespace", "space", "preserve")
                    // OOXML escapes preserve control characters and literal escape sequences.
                    val escaped = text.replace(Regex("_x[0-9a-fA-F]{4}_")) { "_x005F_" + it.value.drop(1) }
                    xml.text(buildString {
                        escaped.forEach { char ->
                            if (char.code < 32 || char == '\uFFFE' || char == '\uFFFF') {
                                append("_x%04X_".format(char.code))
                            } else append(char)
                        }
                    })
                    xml.endTag(null, "t").endTag(null, "is").endTag(null, "c")
                }
                xml.endTag(null, "row")
            }
            row(headers)
            rows(::row)
            xml.endTag(null, "sheetData").endTag(null, "worksheet")
            xml.endDocument()
            xml.flush()
            zip.closeEntry()
        }
    }
}
