package io.github.toanhp123.finote.data.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds the `isPrimary` flag to accounts. Every existing row reads false until the user picks a primary. */
val MIGRATION_11_12: Migration =
    object : Migration(11, 12) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `isPrimary` INTEGER NOT NULL DEFAULT 0")
        }
    }
