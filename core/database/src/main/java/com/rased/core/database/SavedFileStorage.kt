package com.rased.core.database

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Durable file copies; Room stores metadata rather than large Excel blobs. */
class SavedFileStorage(context: Context) {
    private val context = context.applicationContext
    private val directory = File(this.context.filesDir, "saved_files")
    private val dao get() = RasedDatabase.getInstance(context).savedFiles()

    suspend fun get(slot: String): SavedFile? = withContext(Dispatchers.IO) { dao.get(slot) }

    fun uri(file: SavedFile): Uri = Uri.fromFile(File(directory, file.fileName))

    suspend fun replace(
        slot: String,
        source: Uri,
        beforeCommit: (SavedFile, Uri) -> Unit = { _, _ -> }
    ): SavedFile = withContext(Dispatchers.IO) {
        replacementMutex.withLock {
            check(directory.isDirectory || directory.mkdirs()) { "تعذر إنشاء مجلد الملفات" }
            val previous = dao.get(slot)
            val file = File.createTempFile("input-", ".xlsx", directory)
            var committed = false
            try {
                val displayName = runCatching {
                    context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull() ?: source.lastPathSegment ?: "Excel.xlsx"
                val job = currentCoroutineContext()
                requireNotNull(context.contentResolver.openInputStream(source)) { "تعذر فتح الملف" }.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            job.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                job.ensureActive()
                val saved = SavedFile(slot, file.name, displayName, file.length())
                // Commit only after the entire copy is durable. A failed copy keeps the old selection.
                RasedDatabase.getInstance(context).runInTransaction {
                    beforeCommit(saved, uri(saved))
                    job.ensureActive()
                    dao.upsert(saved)
                }
                committed = true
                previous?.let { File(directory, it.fileName).delete() }
                saved
            } finally {
                if (!committed) file.delete()
            }
        }
    }

    private companion object {
        val replacementMutex = Mutex()
    }
}
