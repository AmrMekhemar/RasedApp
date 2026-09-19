package com.rased.core.excel

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.io.Closeable
import java.io.RandomAccessFile
import java.util.zip.ZipFile

class XlsxReader(private val context: Context) {

    fun readSheet(
        uri: Uri,
        sheetName: String?,
        headerAliases: Set<String>,
        retainRow: (Map<String, String>) -> Boolean = { true }
    ): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        forEachRow(uri, sheetName, headerAliases) { row ->
            if (retainRow(row)) rows += row
        }
        return rows
    }

    fun forEachRow(
        uri: Uri,
        sheetName: String?,
        headerAliases: Set<String>,
        onRow: (Map<String, String>) -> Unit
    ) {
        // A content URI need not be seekable. Spool the compressed archive to disk,
        // then stream only the workbook metadata and the requested worksheet.
        val file = File.createTempFile("xlsx-", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "تعذر فتح ملف Excel" }
                file.outputStream().use { input.copyTo(it) }
            }
            ZipFile(file).use { zip ->
                val target = findSheetTarget(zip, sheetName)
                    ?: error(if (sheetName == null) "لم يتم العثور على أول شيت في الملف" else "لم يتم العثور على شيت باسم \"$sheetName\"")
                SharedStrings(context.cacheDir).use { sharedStrings ->
                    zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                        zip.getInputStream(entry).use { parseSharedStrings(it, sharedStrings) }
                    }
                    val entry = zip.getEntry(target) ?: error("تعذر قراءة الشيت المطلوب")
                    val aliases = headerAliases.map(::normalizeHeader).toSet()
                    var headers: Map<Int, String>? = null
                    var rowCount = 0
                    zip.getInputStream(entry).use { input ->
                        parseSheet(input, sharedStrings) { row ->
                            rowCount++
                            val currentHeaders = headers
                            if (currentHeaders == null) {
                                if (rowCount <= 25 && row.values.any { normalizeHeader(it) in aliases }) {
                                    headers = row.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
                                } else if (rowCount >= 25) {
                                    error("لم يتم العثور على صف العناوين أو عمود اللوحة")
                                }
                            } else {
                                val mapped = linkedMapOf<String, String>()
                                currentHeaders.forEach { (col, header) -> mapped[header] = row[col].orEmpty().trim() }
                                if (mapped.values.any { it.isNotBlank() }) onRow(mapped)
                            }
                        }
                    }
                    if (headers == null) error("لم يتم العثور على صف العناوين أو عمود اللوحة")
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun findSheetTarget(zip: ZipFile, wantedName: String?): String? {
        val workbook = zip.getEntry("xl/workbook.xml") ?: return null
        val rels = zip.getEntry("xl/_rels/workbook.xml.rels") ?: return null
        val relMap = zip.getInputStream(rels).use { parseWorkbookRelationships(it) }

        var resultRid: String? = null
        zip.getInputStream(workbook).use { input ->
            val parser = newParser(input)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name.endsWith("sheet")) {
                    val name = parser.getAttributeValue(null, "name")
                    val rid = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                        ?: parser.getAttributeValue(null, "r:id")
                        ?: parser.getAttributeValue(null, "id")
                    if (wantedName == null || name?.trim() == wantedName.trim()) {
                        resultRid = rid
                        break
                    }
                }
            }
        }

        val target = resultRid?.let { relMap[it] } ?: return null
        return normalizeWorkbookTarget(target)
    }

    private fun parseWorkbookRelationships(input: InputStream): Map<String, String> {
        val parser = newParser(input)
        val map = mutableMapOf<String, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name.endsWith("Relationship")) {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) map[id] = target
            }
        }
        return map
    }

    private fun normalizeWorkbookTarget(target: String): String {
        val clean = target.removePrefix("/")
        return if (clean.startsWith("xl/")) clean else "xl/$clean"
    }

    private fun parseSharedStrings(input: InputStream, strings: SharedStrings) {
        val parser = newParser(input)
        var inSi = false
        val builder = StringBuilder()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.endsWith("si")) {
                        inSi = true
                        builder.clear()
                    } else if (inSi && parser.name.endsWith("t")) {
                        builder.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.endsWith("si")) {
                        strings.add(builder.toString())
                        inSi = false
                    }
                }
            }
        }
    }

    private fun parseSheet(input: InputStream, sharedStrings: SharedStrings, onRow: (Map<Int, String>) -> Unit) {
        val currentRow = linkedMapOf<Int, String>()
        val parser = newParser(input)

        var currentColumn = -1
        var currentType: String? = null
        var inlineText: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "row" -> currentRow.clear()
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        currentColumn = columnIndexFromCellRef(ref)
                        currentType = parser.getAttributeValue(null, "t")
                        inlineText = null
                    }
                    "v" -> {
                        val value = parser.nextText().trim()
                        val resolved = when (currentType) {
                            "s" -> value.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                            else -> value
                        }
                        if (currentColumn >= 0 && resolved.isNotBlank()) currentRow[currentColumn] = resolved
                    }
                    "t" -> {
                        if (currentType == "inlineStr") inlineText = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "c" -> {
                        if (currentColumn >= 0 && !inlineText.isNullOrBlank()) currentRow[currentColumn] = inlineText.orEmpty()
                    }
                    "row" -> onRow(currentRow)
                }
            }
        }
    }

    // Shared strings can be larger than the worksheet. Keep both their text and
    // offset index on disk, with a small cache for frequently repeated values.
    private class SharedStrings(cacheDir: File) : Closeable {
        private val textFile = File.createTempFile("xlsx-text-", ".tmp", cacheDir)
        private val indexFile = File.createTempFile("xlsx-index-", ".tmp", cacheDir)
        private val text = RandomAccessFile(textFile, "rw")
        private val index = RandomAccessFile(indexFile, "rw")
        private var count = 0
        private val cache = object : LinkedHashMap<Int, String>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean = size > 256
        }

        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            index.writeLong(text.filePointer)
            text.writeInt(bytes.size)
            text.write(bytes)
            count++
        }

        fun getOrNull(position: Int): String? {
            if (position !in 0 until count) return null
            cache[position]?.let { return it }
            index.seek(position.toLong() * 8)
            text.seek(index.readLong())
            val bytes = ByteArray(text.readInt())
            text.readFully(bytes)
            return bytes.toString(Charsets.UTF_8).also { cache[position] = it }
        }

        override fun close() {
            try {
                text.close()
            } finally {
                try {
                    index.close()
                } finally {
                    textFile.delete()
                    indexFile.delete()
                }
            }
        }
    }

    private fun normalizeHeader(value: String): String = value.trim().replace(" ", "")

    private fun columnIndexFromCellRef(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }
        if (letters.isBlank()) return -1
        var result = 0
        letters.uppercase().forEach { ch -> result = result * 26 + (ch - 'A' + 1) }
        return result - 1
    }

    private fun newParser(input: InputStream): XmlPullParser {
        return XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(input, null)
        }
    }
}
