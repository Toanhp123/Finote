package io.github.toanhp123.finote.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class PlaintextDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getDatabasePath("plaintext-test.db")

    private fun write(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    private fun plaintextBytes() = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(100)

    @Test
    fun `plaintext header is detected`() {
        write(plaintextBytes())

        assertTrue(isPlaintextDatabase(file))
    }

    @Test
    fun `random bytes are not plaintext`() {
        write(ByteArray(4096).also { SecureRandom().nextBytes(it) })

        assertFalse(isPlaintextDatabase(file))
    }

    @Test
    fun `missing file is not plaintext`() {
        assertFalse(isPlaintextDatabase(file))
    }

    @Test
    fun `file shorter than the header is not plaintext`() {
        write("SQLite".toByteArray(Charsets.US_ASCII))

        assertFalse(isPlaintextDatabase(file))
    }

    @Test
    fun `delete removes a plaintext database only`() {
        write(ByteArray(4096) { 1 })
        deletePlaintextDatabase(context, file.name)
        assertTrue(file.exists())

        write(plaintextBytes())
        deletePlaintextDatabase(context, file.name)
        assertFalse(file.exists())
    }
}
