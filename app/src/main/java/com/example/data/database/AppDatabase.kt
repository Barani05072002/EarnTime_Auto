package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AppUnlockSessionDao
import com.example.data.dao.ControlledAppDao
import com.example.data.dao.CreditTransactionDao
import com.example.data.dao.HabitDao
import com.example.data.dao.TaskDao
import com.example.data.entities.AppUnlockSessionEntity
import com.example.data.entities.ControlledAppEntity
import com.example.data.entities.CreditTransactionEntity
import com.example.data.entities.HabitEntity
import com.example.data.entities.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        HabitEntity::class,
        ControlledAppEntity::class,
        CreditTransactionEntity::class,
        AppUnlockSessionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun controlledAppDao(): ControlledAppDao
    abstract fun creditTransactionDao(): CreditTransactionDao
    abstract fun appUnlockSessionDao(): AppUnlockSessionDao

    companion object {
        /**
         * Adds paid unlock sessions. Purely additive — tasks, habits, controlled apps and the
         * whole credit ledger are left untouched, so upgrading users keep their balance.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_unlock_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `creditsSpent` INTEGER NOT NULL,
                        `grantedSeconds` INTEGER NOT NULL,
                        `consumedSeconds` INTEGER NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `lastTickAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `endReason` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_unlock_sessions_packageName` " +
                        "ON `app_unlock_sessions` (`packageName`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_unlock_sessions_endedAt` " +
                        "ON `app_unlock_sessions` (`endedAt`)"
                )
            }
        }
    }
}
