package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UsageEntity::class,
        ChildDeviceEntity::class,
        SyncEntity::class,
        AppInfoEntity::class,
        LimitEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FamilyGuardDatabase : RoomDatabase() {
    abstract fun familyGuardDao(): FamilyGuardDao

    companion object {
        @Volatile
        private var INSTANCE: FamilyGuardDatabase? = null

        fun getInstance(context: Context): FamilyGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FamilyGuardDatabase::class.java,
                    "family_guard_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
