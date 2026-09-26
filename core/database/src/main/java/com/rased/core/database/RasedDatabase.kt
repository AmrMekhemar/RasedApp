package com.rased.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SavedFile::class, SortingImport::class, IndexedDataRow::class,
    IndexedWalletRow::class, IndexedResult::class], version = 6, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)])
abstract class RasedDatabase : RoomDatabase() {
    abstract fun savedFiles(): SavedFileDao
    abstract fun sorting(): SortingDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The old index discarded duplicate rows. Rebuild it from the saved workbooks.
                db.execSQL("DELETE FROM sorting_imports WHERE revision IN (SELECT DISTINCT revision FROM sorting_data)")
                db.execSQL("DROP TABLE sorting_data")
                db.execSQL("CREATE TABLE sorting_data (revision TEXT NOT NULL, normalized TEXT NOT NULL, plate TEXT NOT NULL, type TEXT, note TEXT, street TEXT, district TEXT, date TEXT, location TEXT, sequence INTEGER NOT NULL, PRIMARY KEY(revision, sequence))")
                db.execSQL("CREATE INDEX index_sorting_data_revision_normalized ON sorting_data(revision, normalized)")
            }
        }
        @Volatile private var instance: RasedDatabase? = null

        fun getInstance(context: Context): RasedDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, RasedDatabase::class.java, "rased.db"
            ).addMigrations(MIGRATION_5_6).addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // Snapshots belong to a live process; permanent input tables are untouched.
                    db.execSQL("DELETE FROM sorting_results")
                }
            }).build().also { instance = it }
        }
    }
}
