package com.rased.app

import android.content.Context
import android.net.Uri
import com.rased.core.database.SavedFileStorage
import java.io.File
import kotlinx.coroutines.runBlocking

/** Dedicated slots leave the user's actual sorting inputs untouched. */
class SavedFilesRegression(private val context: Context) {
    fun run(verifyAfterRestart: Boolean) = runBlocking {
        val storage = SavedFileStorage(context)
        fun contents(uri: Uri) = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        if (!verifyAfterRestart) {
            val source = File.createTempFile("saved-file-regression-", ".xlsx", context.cacheDir)
            try {
                source.writeText("original data")
                val old = storage.replace("regression.data", Uri.fromFile(source))
                source.writeText("wallet")
                storage.replace("regression.wallet", Uri.fromFile(source))
                source.writeText("replacement data")
                val replacement = storage.replace("regression.data", Uri.fromFile(source))
                check(contents(storage.uri(replacement)) == "replacement data")
                check(!File(requireNotNull(storage.uri(old).path)).exists())
                source.delete()
                check(runCatching { storage.replace("regression.data", Uri.fromFile(source)) }.isFailure)
                check(storage.get("regression.data") == replacement)
            } finally {
                source.delete()
            }
        }
        val restored = SavedFileStorage(context)
        check(contents(restored.uri(requireNotNull(restored.get("regression.data")))) == "replacement data")
        check(contents(restored.uri(requireNotNull(restored.get("regression.wallet")))) == "wallet")
        check(restored.get("regression.data")!!.displayName.endsWith(".xlsx"))
        check(restored.get("regression.data")!!.sizeBytes == "replacement data".toByteArray().size.toLong())
    }
}
