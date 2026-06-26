package com.example.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.app.data.db.dao.BillDao
import com.example.app.data.db.dao.CustomerDao
import com.example.app.data.db.entity.BillEntity
import com.example.app.data.db.entity.BillItemEntity
import com.example.app.data.db.entity.CustomerEntity

/**
 * KhataDatabase — single-instance Room database.
 *
 * Bump [version] and add a [androidx.room.migration.Migration] whenever
 * you change any @Entity schema. Never use `fallbackToDestructiveMigration`
 * in production — data loss is permanent.
 */
@Database(
    entities = [
        CustomerEntity::class,
        BillEntity::class,
        BillItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KhataDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun billDao(): BillDao

    companion object {
        @Volatile private var INSTANCE: KhataDatabase? = null

        fun getInstance(context: Context): KhataDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                KhataDatabase::class.java,
                "khata.db"
            )
            // Add migration objects here when bumping the version:
            // .addMigrations(MIGRATION_1_2)
            .build()
    }
}
