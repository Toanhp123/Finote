package io.github.toanhp123.finote.data.database

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.toanhp123.finote.data.account.seed.SystemAccountSeeder
import io.github.toanhp123.finote.data.category.seed.SystemCategorySeeder
import io.github.toanhp123.finote.data.notification.seed.ParserRuleSeeder
import io.github.toanhp123.finote.data.notification.seed.SystemParserRules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val TEST_DB = "migration-test.db"

/** A user edit of a system rule (same id, older version): the seeder must leave it alone on reopen. */
private val CUSTOM_RULE_ID = SystemParserRules.first().id

/** One fake row per v8 table, parents before children. */
private val V8_ROWS =
    listOf(
        insertAccountSql("acc-a"),
        insertAccountSql("acc-b"),
        "INSERT INTO categories (id, name, icon, color, type, isSystemDefault, isHidden, sortOrder, createdAt, " +
            "updatedAt) VALUES ('cat-1', 'Fake', 'more', 0, 'EXPENSE', 0, 0, 0, 1, 1)",
        "INSERT INTO notification_logs (id, packageName, title, content, contentHash, postTime, receivedAt, status) " +
            "VALUES ('log-1', 'fake.bank', 'Fake', 'fake content', 'fake-hash', 1, 1, 'PARSED')",
        "INSERT INTO tags (id, name, color, isArchived, createdAt, updatedAt) VALUES ('tag-1', 'Fake', 0, 0, 1, 1)",
        "INSERT INTO parser_rules (id, packageName, bankName, ignoreKeywords, priority, isUserCustom, version, " +
            "regexPattern, isActive, updatedAt) VALUES ('$CUSTOM_RULE_ID', 'fake.bank', 'Fake', '', 0, 1, 0, " +
            "'fake-pattern', 1, 1)",
        "INSERT INTO transactions (id, amount, type, accountId, toAccountId, categoryId, timestamp, note, source, " +
            "excludeFromAnalytics, createdAt, updatedAt, notificationLogId) VALUES ('tx-1', 150000, 'TRANSFER', " +
            "'acc-a', 'acc-b', 'cat-1', 1, '', 'NOTIFICATION', 0, 1, 1, 'log-1')",
        "INSERT INTO account_balance_snapshots (id, accountId, monthYear, openingBalance, closingBalance, " +
            "totalIncome, totalExpense, totalTransferIn, totalTransferOut, updatedAt) VALUES ('snap-1', 'acc-a', " +
            "'2026-11', 0, 0, 0, 0, 0, 150000, 1)",
        "INSERT INTO transaction_tag_cross_ref (transactionId, tagId) VALUES ('tx-1', 'tag-1')",
    )

private fun insertAccountSql(id: String) =
    "INSERT INTO accounts (id, name, type, initialBalance, currentBalance, currency, icon, color, sortOrder, " +
        "excludeFromNetWorth, isArchived, isIgnored, isInitialized, createdAt, updatedAt) " +
        "VALUES ('$id', 'Fake', 'BANK', 0, 0, 'VND', 'bank', 0, 0, 0, 0, 0, 0, 1, 1)"

@RunWith(AndroidJUnit4::class)
class MigrationPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC)

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            context.getDatabasePath(TEST_DB),
            AndroidSQLiteDriver(),
            FinoteDatabase::class,
        )

    /**
     * The schemas ride in debug assets only, so a release host test cannot load them. Skip by build type, not by a
     * missing schema file: if the debug wiring breaks, these tests must fail, not silently skip.
     */
    @Before
    fun runOnDebugOnly() {
        assumeTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
    }

    @Test
    fun `downgrade crashes and keeps the rows`() =
        runTest {
            helper.createDatabase(DATABASE_VERSION).use { connection ->
                connection.execSQL(insertAccountSql("acc-test"))
                connection.execSQL("PRAGMA user_version = ${DATABASE_VERSION + 1}")
            }

            val error = runCatching { openWithPolicy().close() }.exceptionOrNull()

            assertTrue("$error", error is IllegalStateException)
            assertTrue(rowExists("accounts", "acc-test"))
        }

    @Test
    fun `version 7 is wiped and opens at the current version`() =
        runTest {
            helper.createDatabase(7).use { connection -> connection.execSQL(insertAccountSql("acc-old")) }

            openWithPolicy().close()

            assertFalse(rowExists("accounts", "acc-old"))
            assertEquals(DATABASE_VERSION.toLong(), longColumn("PRAGMA user_version"))
        }

    @Test
    fun `v8 rows survive every migration and the reopen`() =
        runTest {
            helper.createDatabase(8).use { connection ->
                // Parents first, per the foreign keys in 8.json.
                V8_ROWS.forEach(connection::execSQL)
            }

            helper.runMigrationsAndValidate(DATABASE_VERSION, FINOTE_MIGRATIONS).close()
            openWithPolicy().close()

            listOf(
                "accounts" to "acc-a",
                "accounts" to "acc-b",
                "categories" to "cat-1",
                "notification_logs" to "log-1",
                "tags" to "tag-1",
                "parser_rules" to CUSTOM_RULE_ID,
                "transactions" to "tx-1",
                "account_balance_snapshots" to "snap-1",
            ).forEach { (table, id) -> assertTrue("$table/$id", rowExists(table, id)) }
            assertEquals(150_000L, longColumn("SELECT amount FROM transactions WHERE id = 'tx-1'"))
            assertEquals(
                1L,
                longColumn(
                    "SELECT COUNT(*) FROM parser_rules WHERE id = '$CUSTOM_RULE_ID' AND regexPattern = 'fake-pattern'",
                ),
            )
            assertEquals(1L, longColumn("SELECT COUNT(*) FROM transaction_tag_cross_ref WHERE transactionId = 'tx-1'"))
        }

    private suspend fun openWithPolicy(): FinoteDatabase {
        val db =
            Room
                .databaseBuilder<FinoteDatabase>(context, TEST_DB)
                .setDriver(AndroidSQLiteDriver())
                .applyMigrationPolicy()
                .addCallback(SystemCategorySeeder(clock))
                .addCallback(SystemAccountSeeder(clock))
                .addCallback(ParserRuleSeeder())
                .build()
        // Room opens lazily; the first connection runs the migration or the fallback.
        db.useReaderConnection { }
        return db
    }

    /** Reads the file without Room, so it works after Room refused to open it. */
    private fun rowExists(
        table: String,
        id: String,
    ): Boolean = longColumn("SELECT COUNT(*) FROM $table WHERE id = '$id'") == 1L

    private fun longColumn(sql: String): Long =
        AndroidSQLiteDriver().open(context.getDatabasePath(TEST_DB).path).use { connection ->
            connection.prepare(sql).use { statement ->
                statement.step()
                statement.getLong(0)
            }
        }
}
