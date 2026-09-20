package com.rased.core.excel

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.io.Closeable
import java.io.RandomAccessFile
import java.io.DataOutputStream
import java.nio.ByteBuffer
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
    ) = forEachSelectedRow(uri, sheetName, headerAliases, null, {}, onRow)

    fun forEachSelectedRow(
        uri: Uri,
        sheetName: String?,
        headerAliases: Set<String>,
        selectedHeaders: Set<String>?,
        checkActive: () -> Unit,
        onRow: (Map<String, String>) -> Unit
    ) {
        // Private file URIs are seekable already. Only spool external content providers.
        val temporary = uri.scheme != "file"
        val file = if (temporary) File.createTempFile("xlsx-", ".zip", context.cacheDir)
            else File(requireNotNull(uri.path))
        try {
            checkActive()
            if (temporary) context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "تعذر فتح ملف Excel" }
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            ZipFile(file).use { zip ->
                val target = findSheetTarget(zip, sheetName)
                    ?: error(if (sheetName == null) "لم يتم العثور على أول شيت في الملف" else "لم يتم العثور على شيت باسم \"$sheetName\"")
                SharedStrings(context.cacheDir).use { sharedStrings ->
                    zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                        zip.getInputStream(entry).use { parseSharedStrings(it, sharedStrings, checkActive) }
                    }
                    sharedStrings.finishWriting()
                    val entry = zip.getEntry(target) ?: error("تعذر قراءة الشيت المطلوب")
                    val aliases = headerAliases.map(::normalizeHeader).toSet()
                    val selected = selectedHeaders?.map(::normalizeHeader)?.toSet()
                    var headers: Map<Int, String>? = null
                    var rowCount = 0
                    zip.getInputStream(entry).use { input ->
                        parseSheet(input, sharedStrings, { column -> headers?.containsKey(column) != false }, checkActive) { row ->
                            rowCount++
                            val currentHeaders = headers
                            if (currentHeaders == null) {
                                if (rowCount <= 25 && row.values.any { normalizeHeader(it) in aliases }) {
                                    headers = row.mapValues { it.value.trim() }.filterValues {
                                        it.isNotBlank() && (selected == null || normalizeHeader(it) in selected)
                                    }
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
            if (temporary) file.delete()
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

    private fun parseSharedStrings(input: InputStream, strings: SharedStrings, checkActive: () -> Unit) {
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
                        checkActive()
                        strings.add(builder.toString())
                        inSi = false
                    }
                }
            }
        }
    }

    private fun parseSheet(input: InputStream, sharedStrings: SharedStrings,
        selectedColumn: (Int) -> Boolean, checkActive: () -> Unit, onRow: (Map<Int, String>) -> Unit) {
        val currentRow = linkedMapOf<Int, String>()
        val parser = newParser(input)

        var currentColumn = -1
        var currentType: String? = null
        var inlineText: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "row" -> { checkActive(); currentRow.clear() }
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        currentColumn = columnIndexFromCellRef(ref)
                        currentType = parser.getAttributeValue(null, "t")
                        inlineText = null
                    }
                    "v" -> {
                        if (!selectedColumn(currentColumn)) continue
                        val value = parser.nextText().trim()
                        val resolved = when (currentType) {
                            "s" -> value.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                            else -> value
                        }
                        if (currentColumn >= 0 && resolved.isNotBlank()) currentRow[currentColumn] = resolved
                    }
                    "t" -> {
                        if (currentType == "inlineStr" && selectedColumn(currentColumn)) inlineText = parser.nextText().trim()
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
        private val textOutput = DataOutputStream(textFile.outputStream().buffered(64 * 1024))
        private val indexOutput = DataOutputStream(indexFile.outputStream().buffered(64 * 1024))
        private val text = RandomAccessFile(textFile, "r")
        private val index = RandomAccessFile(indexFile, "r")
        private var textOffset = 0L
        private val offsetBuffer = ByteArray(8)
        private val lengthBuffer = ByteArray(4)
        private var writing = true
        private var count = 0
        private val cache = LinkedHashMap<Int, String>(256, 0.75f, true)
        private var cacheBytes = 0

        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            indexOutput.writeLong(textOffset)
            textOutput.writeInt(bytes.size)
            textOutput.write(bytes)
            textOffset += 4 + bytes.size.toLong()
            cacheValue(count, value)
            count++
        }

        fun finishWriting() {
            if (writing) {
                textOutput.close()
                indexOutput.close()
                writing = false
            }
        }

        private fun cacheValue(position: Int, value: String) {
            val cost = value.length * 2 + 128
            if (cost > 4 * 1024 * 1024) return
            cache[position] = value
            cacheBytes += cost
            while (cacheBytes > 4 * 1024 * 1024 || cache.size > 8192) {
                val iterator = cache.entries.iterator()
                val oldest = iterator.next()
                cacheBytes -= oldest.value.length * 2 + 128
                iterator.remove()
            }
        }

        fun getOrNull(position: Int): String? {
            if (position !in 0 until count) return null
            cache[position]?.let { return it }
            index.seek(position.toLong() * 8)
            index.readFully(offsetBuffer)
            text.seek(ByteBuffer.wrap(offsetBuffer).long)
            text.readFully(lengthBuffer)
            val bytes = ByteArray(ByteBuffer.wrap(lengthBuffer).int)
            text.readFully(bytes)
            return bytes.toString(Charsets.UTF_8).also { cacheValue(position, it) }
        }

        override fun close() {
            try {
                try { finishWriting() } finally { text.close() }
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
