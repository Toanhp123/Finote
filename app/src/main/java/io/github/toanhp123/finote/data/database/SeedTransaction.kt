package io.github.toanhp123.finote.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Runs [block] in one `BEGIN IMMEDIATE` transaction on a raw connection. Room runs `onOpen` outside a transaction,
 * so a failed seed must not leave a half-written state behind.
 */
inline fun <T> SQLiteConnection.immediateTransaction(block: () -> T): T {
    execSQL("BEGIN IMMEDIATE TRANSACTION")
    var isCommitted = false
    try {
        val result = block()
        execSQL("COMMIT")
        isCommitted = true
        return result
    } finally {
        // A failing ROLLBACK must not hide the error that is already propagating.
        if (!isCommitted) runCatching { execSQL("ROLLBACK") }
    }
}
