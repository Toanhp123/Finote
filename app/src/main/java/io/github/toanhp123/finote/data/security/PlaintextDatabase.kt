package io.github.toanhp123.finote.data.security

import android.content.Context
import java.io.File

private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/** An unencrypted SQLite file starts with a fixed 16-byte header. An encrypted one looks random. */
fun isPlaintextDatabase(file: File): Boolean {
    if (!file.isFile) return false
    val header = ByteArray(SQLITE_HEADER.size)
    return file.inputStream().use { it.read(header) } == header.size && header.contentEquals(SQLITE_HEADER)
}

/** Dev devices hold an unencrypted database from before SQLCipher. Safe only before v1.0 (no real user data). */
fun deletePlaintextDatabase(
    context: Context,
    name: String,
) {
    if (isPlaintextDatabase(context.getDatabasePath(name))) context.deleteDatabase(name)
}
