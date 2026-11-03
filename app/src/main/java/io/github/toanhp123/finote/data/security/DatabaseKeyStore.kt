package io.github.toanhp123.finote.data.security

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.util.Base64

/**
 * Owns the 32-byte database key. The key is stored wrapped by [cipher] in a private SharedPreferences file.
 * When the key is lost (D10), the database can never be read again, so it is deleted and a new key is made.
 */
class DatabaseKeyStore(
    private val context: Context,
    private val cipher: KeyCipher,
    private val databaseName: String,
    private val prefsName: String = PREFS_NAME,
) {
    @Synchronized
    fun getOrCreateKey(): ByteArray {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.getString(PREF_WRAPPED_KEY, null)?.let { unwrap(prefs, it) }?.let { return it }
        // No wrapped key, or it cannot be opened: any database file is unreadable. Wipe it before the new key exists.
        context.deleteDatabase(databaseName)
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = Base64.getEncoder().encodeToString(cipher.wrap(key))
        save(prefs) { putString(PREF_WRAPPED_KEY, wrapped).remove(PREF_UNWRAP_FAILURES) }
        return key
    }

    private fun unwrap(
        prefs: SharedPreferences,
        encoded: String,
    ): ByteArray? {
        val key =
            try {
                runCatching { Base64.getDecoder().decode(encoded) }
                    .getOrNull()
                    ?.let(cipher::unwrap)
                    ?.takeIf { it.size == KEY_BYTES }
            } catch (error: UnrecoverableKeyException) {
                countFailure(prefs, error)
                null
            }
        if (key != null && prefs.contains(PREF_UNWRAP_FAILURES)) save(prefs) { remove(PREF_UNWRAP_FAILURES) }
        return key
    }

    /** Rethrows until the third failed launch in a row. The third counts as a lost key. */
    private fun countFailure(
        prefs: SharedPreferences,
        error: UnrecoverableKeyException,
    ) {
        val failures = prefs.getInt(PREF_UNWRAP_FAILURES, 0) + 1
        if (failures < MAX_UNWRAP_FAILURES) {
            save(prefs) { putInt(PREF_UNWRAP_FAILURES, failures) }
            throw error
        }
    }

    // commit, not apply: the key and the counter must be on disk before a database is created or the app dies.
    private fun save(
        prefs: SharedPreferences,
        edits: SharedPreferences.Editor.() -> Unit,
    ) {
        check(prefs.edit().apply(edits).commit()) { "Could not save the database key state" }
    }

    companion object {
        /** Excluded from backup in `backup_rules.xml` and `data_extraction_rules.xml`. Keep the names in sync. */
        const val PREFS_NAME = "db_key"
        internal const val PREF_WRAPPED_KEY = "wrapped_key"
        internal const val PREF_UNWRAP_FAILURES = "unwrap_failures"
        private const val KEY_BYTES = 32
        private const val MAX_UNWRAP_FAILURES = 3
    }
}

/** SQLCipher raw-key syntax: `x'<64 hex>'` skips the PBKDF2 step on every connection. */
fun ByteArray.toRawKeyPassphrase(): ByteArray = "x'${toHexString()}'".toByteArray(Charsets.US_ASCII)
