package io.github.toanhp123.finote.data

import android.content.Context
import androidx.core.content.edit
import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.toanhp123.finote.data.account.AccountEntity
import io.github.toanhp123.finote.data.security.DatabaseKeyStore
import io.github.toanhp123.finote.data.security.KeystoreKeyCipher
import io.github.toanhp123.finote.domain.account.model.AccountType
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Clock

/** Runs against the real SQLCipher library and AndroidKeyStore, with its own file, key prefs and Keystore alias. */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile: File = context.getDatabasePath(DB_NAME)
    private val clock: Clock = Clock.systemUTC()

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
            type = AccountType.BANK,
            initialBalance = 1_000_000L,
            currentBalance = 1_000_000L,
            currency = "VND",
            icon = "account_balance",
            color = 0,
            sortOrder = 0,
            isExcludedFromNetWorth = false,
            isArchived = false,
            isInitialized = true,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            deletedAt = null,
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

    private companion object {
        const val DB_NAME = "finote-encryption-test.db"
        const val PREFS_NAME = "db_key_test"

        // Never the app alias: this test would overwrite the real wrapping key.
        const val TEST_ALIAS = "finote_db_key_wrapper_test"
        const val KEY_BYTES = 32
        val PLAIN_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
