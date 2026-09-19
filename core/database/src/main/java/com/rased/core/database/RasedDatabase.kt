package com.rased.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedFile::class], version = 1, exportSchema = true)
abstract class RasedDatabase : RoomDatabase() {
    abstract fun savedFiles(): SavedFileDao

    companion object {
        @Volatile private var instance: RasedDatabase? = null

        fun getInstance(context: Context): RasedDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, RasedDatabase::class.java, "rased.db"
            ).build().also { instance = it }
        }
    }
}
