package io.github.toanhp123.finote.data.account

import android.content.Context
import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.SYSTEM_CASH_ACCOUNT_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class SystemAccountSeederTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule(seedSystemAccount = true)

    @Test
    fun `new database holds the cash account`() =
        runTest {
            val row = dbRule.db.accountDao().getById(SYSTEM_CASH_ACCOUNT_ID)
            val expected =
                AccountEntity(
                    id = SYSTEM_CASH_ACCOUNT_ID,
                    name = "Cash",
                    nameKey = SYSTEM_CASH_ACCOUNT_ID,
                    type = AccountType.CASH,
                    initialBalance = 0L,
                    currentBalance = 0L,
                    currency = "VND",
                    icon = "payments",
                    color = 0xFF2E7D32.toInt(),
                    sortOrder = 0,
                    isExcludedFromNetWorth = false,
                    isArchived = false,
                    isInitialized = false,
                    createdAt = SEED_TIME,
                    updatedAt = SEED_TIME,
                    deletedAt = null,
                    bankPackageName = null,
                    accountNumberMask = null,
                )
            assertEquals(expected, row)
        }

    @Test
    fun `soft deleted cash account is not seeded again`() =
        runTest {
            val file = freshDatabaseFile("seed-soft-delete.db")
            try {
                openDatabase(file).useAndClose { db ->
                    db.useWriterConnection { connection ->
                        connection.usePrepared("UPDATE accounts SET deletedAt = 1") { it.step() }
                    }
                }
                openDatabase(file).useAndClose { db ->
                    val rows = db.accountDao().observeAccounts().first()
                    assertEquals(emptyList<AccountEntity>(), rows)
                    assertNotNull(db.accountDao().getById(SYSTEM_CASH_ACCOUNT_ID)?.deletedAt)
                    assertEquals(1, countAccounts(db))
                }
            } finally {
                file.delete()
            }
        }

    @Test
    fun `destructive migration from an old version seeds again`() =
        runTest {
            val file = freshDatabaseFile("seed-account-migration.db")
            // user_version = 2 and no tables: Room sees 2 != current, has no migration, drops and recreates.
            AndroidSQLiteDriver().open(file.path).apply {
                execSQL("PRAGMA user_version = 2")
                close()
            }
            try {
                openDatabase(file).useAndClose { db ->
                    val row = db.accountDao().getById(SYSTEM_CASH_ACCOUNT_ID)
                    assertNotNull(row)
                    assertNull(row?.deletedAt)
                    assertEquals(SYSTEM_CASH_ACCOUNT_ID, row?.id)
                }
            } finally {
                file.delete()
            }
        }

    private fun freshDatabaseFile(name: String): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.getDatabasePath(name).apply {
            parentFile?.mkdirs()
            delete()
        }
    }

    private fun openDatabase(file: File): FinoteDatabase =
        Room
            .databaseBuilder<FinoteDatabase>(ApplicationProvider.getApplicationContext(), file.path)
            .setDriver(AndroidSQLiteDriver())
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(SystemAccountSeeder(Clock.systemUTC()))
            .build()

    private suspend fun countAccounts(db: FinoteDatabase): Int =
        db.useWriterConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM accounts") {
                it.step()
                it.getLong(0).toInt()
            }
        }

    private inline fun <T> FinoteDatabase.useAndClose(block: (FinoteDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    private companion object {
        // 2026-11-03T00:00:00Z, the fixed clock of InMemoryDatabaseRule.
        const val SEED_TIME = 1_793_664_000_000L
    }
}
