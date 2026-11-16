package com.omrmaster.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExamEntity::class, StudentResultEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OmrDatabase : RoomDatabase() {
    abstract fun examDao(): ExamDao
    abstract fun studentResultDao(): StudentResultDao

    companion object {
        @Volatile
        private var INSTANCE: OmrDatabase? = null

        fun getInstance(context: Context): OmrDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OmrDatabase::class.java,
                    "omr_master_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
