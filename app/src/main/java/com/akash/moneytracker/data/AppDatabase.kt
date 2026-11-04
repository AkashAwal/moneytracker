package com.akash.moneytracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class, TransactionEntity::class, BudgetEntity::class,
        RecurringEntity::class, SuggestionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun budgets(): BudgetDao
    abstract fun recurring(): RecurringDao
    abstract fun suggestions(): SuggestionDao

    companion object {
        /** Adds the time of day; existing rows get the local time they were logged at. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN minuteOfDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE transactions SET minuteOfDay = " +
                        "CAST(strftime('%H', createdAt / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60 + " +
                        "CAST(strftime('%M', createdAt / 1000, 'unixepoch', 'localtime') AS INTEGER)"
                )
            }
        }

        /** Adds category budgets and recurring transactions. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN recurringId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (`categoryId` INTEGER NOT NULL, `limitPaise` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`categoryId`), FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`amountPaise` INTEGER NOT NULL, `type` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, `minuteOfDay` INTEGER NOT NULL, " +
                        "`lastGeneratedMonth` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_categoryId` ON `recurring` (`categoryId`)")
            }
        }

        /** Adds transaction tags and the SMS suggestions inbox. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `suggestions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`amountPaise` INTEGER NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `minuteOfDay` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_suggestions_fingerprint` ON `suggestions` (`fingerprint`)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "moneytracker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
