package com.rased.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** A feature-owned slot, such as sorting.data, holds one persistent file. */
@Entity(tableName = "saved_files")
data class SavedFile(
    @PrimaryKey val slot: String,
    val fileName: String,
    val displayName: String,
    val sizeBytes: Long
)

@Dao
interface SavedFileDao {
    @Query("SELECT * FROM saved_files WHERE substr(slot, 1, length(:prefix)) = :prefix ORDER BY slot")
    fun listByPrefix(prefix: String): List<SavedFile>
    @Query("SELECT * FROM saved_files WHERE slot = :slot")
    fun get(slot: String): SavedFile?

    @Upsert
    fun upsert(file: SavedFile)

    @Query("DELETE FROM saved_files WHERE slot = :slot")
    fun delete(slot: String)
}
