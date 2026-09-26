package com.rased.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SavedFile::class, SortingImport::class, IndexedDataRow::class,
    IndexedWalletRow::class, IndexedResult::class], version = 5, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)])
abstract class RasedDatabase : RoomDatabase() {
    abstract fun savedFiles(): SavedFileDao
    abstract fun sorting(): SortingDao

    companion object {
        @Volatile private var instance: RasedDatabase? = null

        fun getInstance(context: Context): RasedDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, RasedDatabase::class.java, "rased.db"
            ).addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // Snapshots belong to a live process; permanent input tables are untouched.
                    db.execSQL("DELETE FROM sorting_results")
                }
            }).build().also { instance = it }
        }
    }
}
