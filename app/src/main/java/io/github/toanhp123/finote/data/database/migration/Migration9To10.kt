package io.github.toanhp123.finote.data.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds `notification_logs.notificationKey`. Old rows keep `NULL`: the platform key was not stored before. */
val MIGRATION_9_10: Migration =
    object : Migration(9, 10) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `notification_logs` ADD COLUMN `notificationKey` TEXT")
        }
    }
