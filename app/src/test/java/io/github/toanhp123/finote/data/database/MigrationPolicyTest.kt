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
import io.github.toanhp123.finote.data.notification.repository.NotificationSettlementRepositoryImpl
import io.github.toanhp123.finote.data.notification.seed.ParserRuleSeeder
import io.github.toanhp123.finote.data.notification.seed.SystemParserRules
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.notification.model.AccountResolution
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.service.resolveAccount
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

private const val PKG = "fake.bank"
private const val MASKLESS_PKG = "fake.maskless"

// why: one named default per v8 account column the identity rows vary.
@Suppress("LongParameterList")
private fun keyedAccountSql(
    id: String,
    mask: String? = null,
    pkg: String? = PKG,
    type: String = "BANK",
    isArchived: Boolean = false,
    createdAt: Long = 1,
    deletedAt: Long? = null,
    mergedIntoId: String? = null,
): String {
    fun text(value: String?) = value?.let { "'$it'" } ?: "NULL"
    return "INSERT INTO accounts (id, name, type, initialBalance, currentBalance, currency, icon, color, sortOrder, " +
        "excludeFromNetWorth, isArchived, isIgnored, isInitialized, createdAt, updatedAt, deletedAt, " +
        "bankPackageName, accountNumberMask, mergedIntoId) VALUES ('$id', 'Fake', '$type', 0, 0, 'VND', 'bank', 0, " +
        "0, 0, ${if (isArchived) 1 else 0}, 0, 0, $createdAt, 1, ${deletedAt ?: "NULL"}, ${text(pkg)}, " +
        "${text(mask)}, ${text(mergedIntoId)})"
}

/** Every shape of v8 bank key. Ids and insert order are chosen so neither can fake the expected owner. */
private val V8_KEYED_ROWS =
    listOf(
        keyedAccountSql("live-1", "1111"),
        keyedAccountSql("merged-dst", "2223"),
        keyedAccountSql("merged-src", "2222", deletedAt = 10, mergedIntoId = "merged-dst"),
        keyedAccountSql("deleted-1", "3333", deletedAt = 5),
        // Two live accounts on one key: the non-archived one wins although it is newer and sorts later.
        keyedAccountSql("dup-a", "4444", isArchived = true),
        keyedAccountSql("dup-b", "4444", createdAt = 2),
        // A live row beats a merged row on the same key.
        keyedAccountSql("s5-a", "5555", deletedAt = 10, mergedIntoId = "live-1"),
        keyedAccountSql("s5-b", "5555"),
        // Two merged rows on one key: the later merge wins.
        keyedAccountSql("m6-a", "6666", deletedAt = 10, mergedIntoId = "live-1"),
        keyedAccountSql("m6-b", "6666", deletedAt = 20, mergedIntoId = "merged-dst"),
        // The merge target was deleted later: the key is free.
        keyedAccountSql("gone-dst", "7778", deletedAt = 30),
        keyedAccountSql("gone-src", "7777", deletedAt = 10, mergedIntoId = "gone-dst"),
        // A live row owns its key even with a stale mergedIntoId.
        keyedAccountSql("stale-live", "8888", mergedIntoId = "live-1"),
        // The merge target is missing: no identifier, so the foreign key cannot break.
        keyedAccountSql("dangling", "6060", deletedAt = 10, mergedIntoId = "missing"),
        keyedAccountSql("wallet-1", "9999", pkg = "fake.wallet", type = "WALLET"),
        keyedAccountSql("card-1", "9999", pkg = "fake.card", type = "CREDIT_CARD"),
        keyedAccountSql("no-mask", pkg = MASKLESS_PKG),
        keyedAccountSql("cash", pkg = null, type = "CASH"),
        keyedAccountSql("empty-mask", "", pkg = "fake.empty"),
        keyedAccountSql("empty-pkg", "1212", pkg = ""),
        "INSERT INTO transactions (id, amount, type, accountId, timestamp, note, source, excludeFromAnalytics, " +
            "createdAt, updatedAt) VALUES ('tx-k', 250000, 'EXPENSE', 'merged-src', 1, '', 'NOTIFICATION_PARSER', " +
            "0, 1, 1)",
        "INSERT INTO account_balance_snapshots (id, accountId, monthYear, openingBalance, closingBalance, " +
            "totalIncome, totalExpense, totalTransferIn, totalTransferOut, updatedAt) VALUES ('snap-k', " +
            "'merged-dst', '2026-11', 0, -250000, 0, 250000, 0, 0, 1)",
    )

/** What the v8 resolver gave for each key of [PKG]: the live owner, else the latest merge's live target, else null. */
private val V8_OWNERS =
    mapOf(
        "1111" to "live-1",
        "2222" to "merged-dst",
        "2223" to "merged-dst",
        "3333" to null,
        "4444" to "dup-b",
        "5555" to "s5-b",
        "6666" to "merged-dst",
        "7777" to null,
        "7778" to null,
        "8888" to "stale-live",
        "6060" to null,
    )

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

    @Test
    fun `every v8 key resolves to the same account after migration`() =
        runTest {
            migrateKeyedRows()
            val db = openWithPolicy()
            val repo = NotificationSettlementRepositoryImpl(db, clock)
            val accounts = repo.getAccounts()
            val identifiers = repo.getIdentifiers()
            db.close()

            V8_OWNERS.forEach { (mask, owner) ->
                val expected =
                    owner?.let { AccountTarget.Existing(it) }
                        ?: AccountTarget.New(PKG, mask, PKG, AccountType.BANK, IdentifierKind.ACCOUNT)
                assertEquals(
                    mask,
                    AccountResolution.Resolved(expected),
                    resolveAccount(PKG, mask, null, accounts, identifiers),
                )
            }
            assertEquals(
                AccountResolution.Resolved(AccountTarget.Existing("no-mask")),
                resolveAccount(MASKLESS_PKG, null, null, accounts, identifiers),
            )
            assertEquals(IdentifierKind.WALLET, identifiers.single { it.accountId == "wallet-1" }.kind)
            assertEquals(IdentifierKind.CARD, identifiers.single { it.accountId == "card-1" }.kind)
        }

    @Test
    fun `migration keeps transactions and snapshots`() =
        runTest {
            migrateKeyedRows()

            assertEquals(250_000L, longColumn("SELECT amount FROM transactions WHERE accountId = 'merged-src'"))
            assertEquals(
                -250_000L,
                longColumn("SELECT closingBalance FROM account_balance_snapshots WHERE accountId = 'merged-dst'"),
            )
            // Every account row survives; the last two rows are the transaction and the snapshot.
            assertEquals(V8_KEYED_ROWS.size - 2L, longColumn("SELECT COUNT(*) FROM accounts"))
        }

    @Test
    fun `merged row with a missing target gets no identifier`() =
        runTest {
            migrateKeyedRows()

            assertEquals(0L, longColumn("SELECT COUNT(*) FROM account_identifiers WHERE mask = '6060'"))
            assertEquals(0L, longColumn("SELECT COUNT(*) FROM pragma_foreign_key_check('account_identifiers')"))
        }

    @Test
    fun `empty mask gets no identifier`() =
        runTest {
            migrateKeyedRows()

            assertEquals(
                0L,
                longColumn(
                    "SELECT COUNT(*) FROM account_identifiers " +
                        "WHERE accountId IN ('empty-mask', 'empty-pkg', 'no-mask', 'cash')",
                ),
            )
        }

    @Test
    fun `accounts has no accountNumberMask column`() =
        runTest {
            migrateKeyedRows()

            assertEquals(
                0L,
                longColumn("SELECT COUNT(*) FROM pragma_table_info('accounts') WHERE name = 'accountNumberMask'"),
            )
        }

    @Test
    fun `after migration and seeding every system rule has its accountType`() =
        runTest {
            // A v8 file whose only system rule is stale: no accountType, older than the seed. The rest are missing.
            val stale = SystemParserRules.first { it.accountType != null }
            helper.createDatabase(8).use { connection ->
                connection.execSQL(
                    "INSERT INTO parser_rules (id, packageName, bankName, ignoreKeywords, priority, isUserCustom, " +
                        "version, regexPattern, isActive, updatedAt) VALUES ('${stale.id}', 'fake.bank', 'Fake', '', " +
                        "0, 0, 0, 'fake-pattern', 1, 1)",
                )
            }
            helper.runMigrationsAndValidate(9, listOf(MIGRATION_8_9)).close()

            val db = openWithPolicy()
            val stored =
                SystemParserRules.map { it.packageName }.distinct().flatMap { db.parserRuleDao().getByPackage(it) }
            db.close()

            assertEquals(SystemParserRules.size, stored.size)
            val byId = stored.associateBy { it.id }
            SystemParserRules.forEach { seed ->
                val row = byId.getValue(seed.id)
                assertEquals(seed.id, seed.accountType, row.accountType)
            }
        }

    @Test
    fun `v9 logs survive migration to 10 with a null key`() =
        runTest {
            helper.createDatabase(9).use { connection ->
                connection.execSQL(
                    "INSERT INTO notification_logs (id, packageName, title, content, contentHash, postTime, " +
                        "receivedAt, status) VALUES " +
                        "('log-9', 'fake.bank', 'Fake', 'fake content', 'fake-hash', 1, 1, 'PARSED')",
                )
            }

            helper.runMigrationsAndValidate(10, listOf(MIGRATION_8_9, MIGRATION_9_10)).close()

            assertTrue(rowExists("notification_logs", "log-9"))
            assertEquals(
                1L,
                longColumn("SELECT COUNT(*) FROM notification_logs WHERE id = 'log-9' AND notificationKey IS NULL"),
            )
        }

    @Test
    fun `migrating 10 to 11 keeps every row and adds an empty keyword table`() =
        runTest {
            helper.createDatabase(10).use { connection -> V8_ROWS.forEach(connection::execSQL) }

            helper.runMigrationsAndValidate(11, listOf(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)).close()

            listOf(
                "accounts" to "acc-a",
                "categories" to "cat-1",
                "notification_logs" to "log-1",
                "parser_rules" to CUSTOM_RULE_ID,
                "transactions" to "tx-1",
                "account_balance_snapshots" to "snap-1",
            ).forEach { (table, id) -> assertTrue("$table/$id", rowExists(table, id)) }
            assertEquals(0L, longColumn("SELECT COUNT(*) FROM user_ignore_keywords"))
        }

    @Test
    fun `migrated schema validates`() = runTest { migrateKeyedRows() }

    private suspend fun migrateKeyedRows() {
        helper.createDatabase(8).use { connection -> V8_KEYED_ROWS.forEach(connection::execSQL) }
        helper.runMigrationsAndValidate(9, listOf(MIGRATION_8_9)).close()
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
