package io.github.toanhp123.finote.data.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds the empty `user_ignore_keywords` table. Nothing existing is touched. */
val MIGRATION_10_11: Migration =
    object : Migration(10, 11) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_ignore_keywords` (`id` TEXT NOT NULL, `packageName` TEXT NOT NULL, " +
                    "`keyword` TEXT NOT NULL, `normalizedKeyword` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_user_ignore_keywords_packageName` " +
                    "ON `user_ignore_keywords` (`packageName`)",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_user_ignore_keywords_packageName_normalizedKeyword` " +
                    "ON `user_ignore_keywords` (`packageName`, `normalizedKeyword`)",
            )
        }
    }
