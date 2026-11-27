package io.github.toanhp123.finote.data.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds the empty `budgets` and `budget_months` tables. Nothing existing is touched. */
val MIGRATION_13_14: Migration =
    object : Migration(13, 14) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `categoryId` TEXT, " +
                    "`limitAmount` INTEGER NOT NULL, `monthYear` TEXT NOT NULL, `isRollover` INTEGER NOT NULL, " +
                    "`alertThreshold` INTEGER NOT NULL, `lastAlertSentThreshold` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_monthYear_categoryId` " +
                    "ON `budgets` (`monthYear`, `categoryId`)",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `budget_months` (`monthYear` TEXT NOT NULL, PRIMARY KEY(`monthYear`))",
            )
        }
    }
