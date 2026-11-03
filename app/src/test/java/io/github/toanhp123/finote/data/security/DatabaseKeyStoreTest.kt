package io.github.toanhp123.finote.data.security

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.UnrecoverableKeyException
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class DatabaseKeyStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cipher = FakeKeyCipher()
    private val dbFile = context.getDatabasePath(DB_NAME)

    private fun keyStore() = DatabaseKeyStore(context, cipher, DB_NAME)

    private fun createDbFile() {
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(ByteArray(64) { it.toByte() })
    }

    @Test
    fun `first run makes and stores a 32 byte key`() {
        val key = keyStore().getOrCreateKey()

        assertEquals(32, key.size)
        assertEquals(1, cipher.wrapCount)
        assertFalse(context.getSharedPreferences(DatabaseKeyStore.PREFS_NAME, Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun `second run returns the same key and keeps the database`() {
        val first = keyStore().getOrCreateKey()
        createDbFile()

        val second = keyStore().getOrCreateKey()

        assertArrayEquals(first, second)
        assertEquals(1, cipher.wrapCount)
        assertTrue(dbFile.exists())
    }

    @Test
    fun `unwrap failure wipes the database and makes a new key`() {
        val first = keyStore().getOrCreateKey()
        createDbFile()
        cipher.isKeyLost = true

        val second = keyStore().getOrCreateKey()

        assertNotEquals(first.toList(), second.toList())
        assertEquals(2, cipher.wrapCount)
        assertFalse(dbFile.exists())
        assertArrayEquals(second, keyStore().getOrCreateKey())
    }

    @Test
    fun `database without a wrapped key is wiped`() {
        createDbFile()

        val key = keyStore().getOrCreateKey()

        assertEquals(32, key.size)
        assertFalse(dbFile.exists())
    }

    @Test
    fun `corrupt base64 is a lost key`() {
        keyStore().getOrCreateKey()
        createDbFile()
        storeWrapped("not base64 !!!")

        assertEquals(32, keyStore().getOrCreateKey().size)
        assertEquals(2, cipher.wrapCount)
        assertFalse(dbFile.exists())
    }

    @Test
    fun `wrong size key is a lost key`() {
        keyStore().getOrCreateKey()
        createDbFile()
        storeWrapped(Base64.getEncoder().encodeToString(ByteArray(16)))

        assertEquals(32, keyStore().getOrCreateKey().size)
        assertEquals(2, cipher.wrapCount)
        assertFalse(dbFile.exists())
    }

    @Test
    fun `transient keystore failure keeps the database and the key`() {
        keyStore().getOrCreateKey()
        createDbFile()
        cipher.failure = IllegalStateException("Keystore unavailable")

        repeat(UNRECOVERABLE_LIMIT + 1) {
            assertThrows(IllegalStateException::class.java) { keyStore().getOrCreateKey() }
        }

        assertTrue(dbFile.exists())
        assertEquals(1, cipher.wrapCount)
    }

    @Test
    fun `unrecoverable key fails twice, then the third launch wipes`() {
        val first = keyStore().getOrCreateKey()
        createDbFile()
        cipher.failure = UnrecoverableKeyException()

        repeat(UNRECOVERABLE_LIMIT - 1) {
            assertThrows(UnrecoverableKeyException::class.java) { keyStore().getOrCreateKey() }
            assertTrue(dbFile.exists())
            assertEquals(1, cipher.wrapCount)
        }
        val second = keyStore().getOrCreateKey()

        assertNotEquals(first.toList(), second.toList())
        assertEquals(2, cipher.wrapCount)
        assertFalse(dbFile.exists())
        cipher.failure = null
        assertArrayEquals(second, keyStore().getOrCreateKey())
    }

    @Test
    fun `a successful unwrap resets the unrecoverable counter`() {
        val first = keyStore().getOrCreateKey()
        createDbFile()
        cipher.failure = UnrecoverableKeyException()
        repeat(UNRECOVERABLE_LIMIT - 1) {
            assertThrows(UnrecoverableKeyException::class.java) { keyStore().getOrCreateKey() }
        }
        cipher.failure = null
        assertArrayEquals(first, keyStore().getOrCreateKey())

        cipher.failure = UnrecoverableKeyException()
        repeat(UNRECOVERABLE_LIMIT - 1) {
            assertThrows(UnrecoverableKeyException::class.java) { keyStore().getOrCreateKey() }
        }

        assertTrue(dbFile.exists())
        assertEquals(1, cipher.wrapCount)
    }

    private fun storeWrapped(value: String) {
        context
            .getSharedPreferences(DatabaseKeyStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) { putString(DatabaseKeyStore.PREF_WRAPPED_KEY, value) }
    }

    @Test
    fun `raw key is x quote, 64 lowercase hex, quote`() {
        val passphrase = ByteArray(32) { 0xAB.toByte() }.toRawKeyPassphrase()

        assertEquals("x'" + "ab".repeat(32) + "'", String(passphrase, Charsets.US_ASCII))
    }

    private companion object {
        const val DB_NAME = "key-store-test.db"
        const val UNRECOVERABLE_LIMIT = 3
    }
}
