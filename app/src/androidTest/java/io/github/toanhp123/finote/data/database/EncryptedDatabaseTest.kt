package io.github.toanhp123.finote.data.database

import android.content.Context
import androidx.core.content.edit
import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.toanhp123.finote.data.account.local.entity.AccountEntity
import io.github.toanhp123.finote.data.security.DatabaseKeyStore
import io.github.toanhp123.finote.data.security.KeystoreKeyCipher
import io.github.toanhp123.finote.data.security.toRawKeyPassphrase
import io.github.toanhp123.finote.domain.account.model.AccountType
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Runs against the real SQLCipher library and AndroidKeyStore, with its own file, key prefs and Keystore alias. */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile: File = context.getDatabasePath(DB_NAME)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)

    private fun keyStore() = DatabaseKeyStore(context, KeystoreKeyCipher(TEST_ALIAS), DB_NAME, PREFS_NAME)

    @After
    fun cleanUp() {
        context.deleteDatabase(DB_NAME)
        context.deleteSharedPreferences(PREFS_NAME)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(TEST_ALIAS)
    }

    private fun account() =
        AccountEntity(
            id = "acc-1",
            name = "Test account",
            nameKey = null,
            type = AccountType.BANK,
            initialBalance = 1_000_000L,
            currentBalance = 1_000_000L,
            currency = "VND",
            icon = "account_balance",
            color = 0,
            sortOrder = 0,
            isExcludedFromNetWorth = false,
            isArchived = false,
            isIgnored = false,
            isInitialized = true,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            deletedAt = null,
            bankPackageName = null,
            mergedIntoId = null,
        )

    @Test
    fun fileIsEncryptedAndReopensWithSameKey() =
        runBlocking {
            val first = createFinoteDatabase(context, clock, DB_NAME, keyStore())
            first.accountDao().insert(account())
            first.close()

            assertFalse(dbFile.readBytes().copyOf(PLAIN_HEADER.size).contentEquals(PLAIN_HEADER))

            val second = createFinoteDatabase(context, clock, DB_NAME, keyStore())
            assertNotNull(second.accountDao().getById("acc-1"))
            second.close()
        }

    @Test
    fun wrongKeyFails() {
        runBlocking {
            val db = createFinoteDatabase(context, clock, DB_NAME, keyStore())
            db.accountDao().insert(account())
            db.close()
        }

        val wrongKey = "x'" + "00".repeat(KEY_BYTES) + "'"
        val wrong =
            Room
                .databaseBuilder<FinoteDatabase>(context, DB_NAME)
                .setDriver(SQLCipherDriver(wrongKey.toByteArray(Charsets.US_ASCII), null, null))
                .build()
        assertThrows(Exception::class.java) { runBlocking { wrong.accountDao().getById("acc-1") } }
        wrong.close()
    }

    @Test
    fun lostKeyStartsAnEmptyDatabase() =
        runBlocking {
            val first = createFinoteDatabase(context, clock, DB_NAME, keyStore())
            first.accountDao().insert(account())
            first.close()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) { clear() }

            val second = createFinoteDatabase(context, clock, DB_NAME, keyStore())
            assertNull(second.accountDao().getById("acc-1"))
            second.close()
        }

    @Test
    fun lostAliasWithKeptPrefsStartsAnEmptyUsableDatabase() =
        runBlocking {
            val first = createFinoteDatabase(context, clock, DB_NAME, keyStore())
            first.accountDao().insert(account())
            first.close()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(TEST_ALIAS)

            val second = createFinoteDatabase(context, clock, DB_NAME, keyStore())
            assertNull(second.accountDao().getById("acc-1"))
            second.accountDao().insert(account())
            assertNotNull(second.accountDao().getById("acc-1"))
            second.close()
        }

    /** The v8 schema from the exported `8.json`, which the debug APK carries as an asset. */
    private fun SQLiteConnection.createV8Schema() {
        val schema =
            context.assets
                .open("io.github.toanhp123.finote.data.database.FinoteDatabase/8.json")
                .bufferedReader()
                .use { JSONObject(it.readText()) }
                .getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            execSQL(entity.getString("createSql").replace(TABLE_TOKEN, table))
            entity.optJSONArray("indices")?.let { indices ->
                for (j in 0 until indices.length()) {
                    execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_TOKEN, table))
                }
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) execSQL(setup.getString(i))
        execSQL("PRAGMA user_version = 8")
    }

    private fun v8AccountSql(
        id: String,
        mask: String,
        deletedAt: Long?,
        mergedIntoId: String?,
    ) = "INSERT INTO accounts (id, name, type, initialBalance, currentBalance, currency, icon, color, sortOrder, " +
        "excludeFromNetWorth, isArchived, isIgnored, isInitialized, createdAt, updatedAt, deletedAt, " +
        "bankPackageName, accountNumberMask, mergedIntoId) VALUES ('$id', 'Fake', 'BANK', 0, 0, 'VND', 'bank', 0, 0, " +
        "0, 0, 0, 0, 1, 1, ${deletedAt ?: "NULL"}, '$V8_PKG', '$mask', ${mergedIntoId?.let { "'$it'" } ?: "NULL"})"

    private suspend fun FinoteDatabase.longOf(sql: String): Long =
        useReaderConnection { connection ->
            connection.usePrepared(sql) {
                it.step()
                it.getLong(0)
            }
        }

    /** The real upgrade path: a v8 file encrypted with the stored key, opened by the production factory. */
    @Test
    fun v8FileUpgradesOnSqlCipher() =
        runBlocking {
            System.loadLibrary("sqlcipher")
            dbFile.parentFile?.mkdirs()
            SQLCipherDriver(keyStore().getOrCreateKey().toRawKeyPassphrase(), null, null).open(dbFile.path).use {
                it.createV8Schema()
                it.execSQL(v8AccountSql("live-1", "1111", deletedAt = null, mergedIntoId = null))
                it.execSQL(v8AccountSql("merged-src", "2222", deletedAt = 10, mergedIntoId = "live-1"))
                it.execSQL(
                    "INSERT INTO transactions (id, amount, type, accountId, timestamp, note, source, " +
                        "excludeFromAnalytics, createdAt, updatedAt) " +
                        "VALUES ('tx-1', 250000, 'EXPENSE', 'merged-src', 1, '', 'NOTIFICATION_PARSER', 0, 1, 1)",
                )
                it.execSQL(
                    "INSERT INTO account_balance_snapshots (id, accountId, monthYear, openingBalance, " +
                        "closingBalance, totalIncome, totalExpense, totalTransferIn, totalTransferOut, updatedAt) " +
                        "VALUES ('snap-1', 'live-1', '2026-11', 0, -250000, 0, 250000, 0, 0, 1)",
                )
            }

            val db = createFinoteDatabase(context, clock, DB_NAME, keyStore())

            assertEquals(
                setOf("1111" to "live-1", "2222" to "live-1"),
                db
                    .accountIdentifierDao()
                    .getAll()
                    .map { it.mask to it.accountId }
                    .toSet(),
            )
            assertEquals(
                0L,
                db.longOf("SELECT COUNT(*) FROM pragma_table_info('accounts') WHERE name = 'accountNumberMask'"),
            )
            assertEquals(250_000L, db.longOf("SELECT amount FROM transactions WHERE id = 'tx-1'"))
            assertEquals(
                -250_000L,
                db.longOf("SELECT closingBalance FROM account_balance_snapshots WHERE id = 'snap-1'"),
            )
            assertEquals(9L, db.longOf("PRAGMA user_version"))
            db.close()
        }

    private companion object {
        const val TABLE_TOKEN = "\${TABLE_NAME}"
        const val V8_PKG = "fake.bank"
        const val DB_NAME = "finote-encryption-test.db"
        const val PREFS_NAME = "db_key_test"

        // Never the app alias: this test would overwrite the real wrapping key.
        const val TEST_ALIAS = "finote_db_key_wrapper_test"
        const val KEY_BYTES = 32
        val PLAIN_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
