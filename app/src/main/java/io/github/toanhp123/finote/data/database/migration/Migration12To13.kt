package io.github.toanhp123.finote.data.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds the empty `category_keyword_rules` table. The system rules arrive from `KeywordRuleSeeder` on open. */
val MIGRATION_12_13: Migration =
    object : Migration(12, 13) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `category_keyword_rules` (`id` TEXT NOT NULL, `keyword` TEXT NOT NULL, " +
                    "`normalizedKeyword` TEXT NOT NULL, `matchType` TEXT NOT NULL, `normalizedMerchantName` TEXT, " +
                    "`categoryId` TEXT NOT NULL, `priority` INTEGER NOT NULL, `isUserDefined` INTEGER NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, `version` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`categoryId`) " +
                    "REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_category_keyword_rules_categoryId` " +
                    "ON `category_keyword_rules` (`categoryId`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_category_keyword_rules_normalizedKeyword` " +
                    "ON `category_keyword_rules` (`normalizedKeyword`)",
            )
        }
    }
