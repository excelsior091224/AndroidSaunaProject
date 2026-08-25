package com.totonoi.sauna.shared.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SaunaSessionEntity::class], version = 1, exportSchema = false)
abstract class SaunaDatabase : RoomDatabase() {
    abstract fun saunaDao(): SaunaDao

    companion object {
        @Volatile private var instance: SaunaDatabase? = null

        fun getInstance(context: Context): SaunaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SaunaDatabase::class.java,
                    "sauna.db",
                ).build().also { instance = it }
            }
    }
}
