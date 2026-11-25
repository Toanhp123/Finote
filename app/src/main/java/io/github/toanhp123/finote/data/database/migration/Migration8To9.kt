package io.github.toanhp123.finote.data.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.defaultKind

/** Same type → kind mapping as the resolver, so every migrated key keeps matching its rule. */
private val KIND_OF_TYPE =
    AccountType.entries.joinToString(" ", "CASE type ", " ELSE '${null.defaultKind()}' END") {
        "WHEN '${it.name}' THEN '${it.defaultKind()}'"
    }

/** Rows that carry a full v8 key. */
private const val KEYED =
    "bankPackageName IS NOT NULL AND bankPackageName != '' " +
        "AND accountNumberMask IS NOT NULL AND accountNumberMask != ''"

private fun copyKeys(
    owner: String,
    where: String,
    orderBy: String,
) = "INSERT OR IGNORE INTO account_identifiers (packageName, kind, mask, accountId) " +
    "SELECT bankPackageName, $KIND_OF_TYPE, accountNumberMask, $owner FROM accounts " +
    "WHERE $KEYED AND $where ORDER BY $orderBy"

/**
 * Moves `accounts.accountNumberMask` into `account_identifiers` and adds `parser_rules.maskKind`.
 * Each key gets the owner the v8 resolver picked: a live row owns its key; a merged row gives it to its merge target
 * (the latest merge wins); an unmerged deleted row keeps it, which leaves the key free. The passes run in that order
 * and `INSERT OR IGNORE` keeps the first owner of a key.
 */
val MIGRATION_8_9: Migration =
    object : Migration(8, 9) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `account_identifiers` (`packageName` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `mask` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`packageName`, `kind`, `mask`), FOREIGN KEY(`accountId`) REFERENCES " +
                    "`accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_account_identifiers_accountId` ON `account_identifiers` " +
                    "(`accountId`)",
            )
            // Two live rows on one key: the first non-archived one, as the v8 resolver did.
            connection.execSQL(copyKeys("id", "deletedAt IS NULL", "isArchived, createdAt, id"))
            // A target that no longer exists would break the foreign key: that row gets no identifier, its key is free.
            connection.execSQL(
                copyKeys(
                    "mergedIntoId",
                    "deletedAt IS NOT NULL AND mergedIntoId IN (SELECT id FROM accounts)",
                    "deletedAt DESC, id",
                ),
            )
            connection.execSQL(copyKeys("id", "deletedAt IS NOT NULL AND mergedIntoId IS NULL", "deletedAt DESC, id"))
            // Never rebuild `accounts` by copy: the foreign keys of transactions and snapshots point at it.
            connection.execSQL("DROP INDEX IF EXISTS `index_accounts_bankPackageName_accountNumberMask`")
            connection.execSQL("ALTER TABLE `accounts` DROP COLUMN `accountNumberMask`")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_accounts_bankPackageName` ON `accounts` (`bankPackageName`)",
            )
            connection.execSQL("ALTER TABLE `parser_rules` ADD COLUMN `maskKind` TEXT")
        }
    }
