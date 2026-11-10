package io.github.toanhp123.finote.data.account.seed

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.SYSTEM_CASH_ACCOUNT_ID
import java.time.Clock

private const val CASH_COLOR_ARGB = 0xFF2E7D32.toInt()

private const val INSERT_SQL =
    "INSERT INTO accounts (id, name, nameKey, type, initialBalance, currentBalance, currency, icon, color, " +
        "sortOrder, excludeFromNetWorth, isArchived, isIgnored, isInitialized, createdAt, updatedAt, deletedAt) " +
        "VALUES (?, 'Cash', ?, ?, 0, 0, 'VND', 'payments', ?, 0, 0, 0, 0, 0, ?, ?, NULL)"

/**
 * Writes the [SYSTEM_CASH_ACCOUNT_ID] account into a database that has just been created or recreated.
 *
 * It follows the same two-path scheme as `SystemCategorySeeder`. [onOpen] seeds only when the table has no row at all,
 * soft deleted rows included, so a user who deleted the account does not get it back.
 */
class SystemAccountSeeder(
    private val clock: Clock,
) : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) = seed(connection)

    override suspend fun onOpen(connection: SQLiteConnection) {
        if (hasAnyAccount(connection)) return
        // Room runs onOpen outside a transaction, so a failed seed must not leave a half-written row behind.
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        var isCommitted = false
        try {
            seed(connection)
            connection.execSQL("COMMIT")
            isCommitted = true
        } finally {
            // A failing ROLLBACK must not hide the seed error that is already propagating.
            if (!isCommitted) runCatching { connection.execSQL("ROLLBACK") }
        }
    }

    private fun hasAnyAccount(connection: SQLiteConnection): Boolean =
        connection.prepare("SELECT 1 FROM accounts LIMIT 1").use { it.step() }

    private fun seed(connection: SQLiteConnection) {
        val now = clock.millis()
        connection.prepare(INSERT_SQL).use { statement ->
            var index = 0
            statement.bindText(++index, SYSTEM_CASH_ACCOUNT_ID)
            statement.bindText(++index, SYSTEM_CASH_ACCOUNT_ID)
            statement.bindText(++index, AccountType.CASH.name)
            statement.bindLong(++index, CASH_COLOR_ARGB.toLong())
            statement.bindLong(++index, now)
            statement.bindLong(++index, now)
            statement.step()
        }
    }
}
