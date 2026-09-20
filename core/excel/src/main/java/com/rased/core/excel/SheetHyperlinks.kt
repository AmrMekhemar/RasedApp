package com.rased.core.excel

import android.database.sqlite.SQLiteDatabase
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Disk-backed because a worksheet can contain hundreds of thousands of links. */
internal class SheetHyperlinks(cacheDir: File) : Closeable {
    private val file = File.createTempFile("xlsx-links-", ".db", cacheDir)
    private val db = SQLiteDatabase.openOrCreateDatabase(file, null)

    init {
        db.execSQL("PRAGMA cache_size = -1024")
        db.execSQL("CREATE TABLE relationships(id TEXT PRIMARY KEY, target TEXT NOT NULL)")
        db.execSQL("CREATE TABLE links(ref TEXT PRIMARY KEY, rid TEXT NOT NULL)")
    }

    fun load(zip: ZipFile, sheet: String, checkActive: () -> Unit) {
        val rels = zip.getEntry(sheet.substringBeforeLast('/') + "/_rels/" + sheet.substringAfterLast('/') + ".rels") ?: return
        db.beginTransaction()
        try {
            db.compileStatement("INSERT OR REPLACE INTO relationships VALUES (?, ?)").use { insert ->
                zip.getInputStream(rels).use { input ->
                    tags(input, checkActive) { parser ->
                        if (parser.name.substringAfter(':') == "Relationship" &&
                            parser.getAttributeValue(null, "Type").orEmpty().endsWith("/hyperlink") &&
                            parser.getAttributeValue(null, "TargetMode") == "External") {
                            val id = parser.getAttributeValue(null, "Id")
                            val target = parser.getAttributeValue(null, "Target")
                            if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                                insert.bindString(1, id); insert.bindString(2, target); insert.executeInsert()
                            }
                        }
                    }
                }
            }
            val hasLinks = db.compileStatement("SELECT COUNT(*) FROM relationships").use { it.simpleQueryForLong() > 0 }
            if (hasLinks) db.compileStatement("INSERT OR REPLACE INTO links VALUES (?, ?)").use { insert ->
                zip.getInputStream(requireNotNull(zip.getEntry(sheet))).use { input ->
                    tags(input, checkActive) { parser ->
                        if (parser.name.substringAfter(':') == "hyperlink") {
                            val ref = parser.getAttributeValue(null, "ref")
                            val rid = (0 until parser.attributeCount).firstOrNull {
                                parser.getAttributeName(it).substringAfter(':') == "id"
                            }?.let { parser.getAttributeValue(it) }
                            if (!ref.isNullOrBlank() && !rid.isNullOrBlank()) {
                                insert.bindString(1, ref); insert.bindString(2, rid); insert.executeInsert()
                            }
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun target(ref: String): String? = db.rawQuery(
        "SELECT target FROM links JOIN relationships ON relationships.id = links.rid WHERE ref = ?", arrayOf(ref)
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun tags(input: InputStream, checkActive: () -> Unit, action: (XmlPullParser) -> Unit) {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(input, null) }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) { checkActive(); action(parser) }
        }
    }

    override fun close() {
        try { db.close() } finally { SQLiteDatabase.deleteDatabase(file) }
    }
}
