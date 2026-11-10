package io.github.toanhp123.finote.data.database

import android.database.SQLException
import androidx.room3.withWriteTransaction
import io.github.toanhp123.finote.domain.common.StorageException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** Runs a storage call. A database failure becomes [StorageException]; any other exception passes through. */
suspend fun <T> storage(block: suspend () -> T): T =
    try {
        block()
    } catch (e: SQLException) {
        throw StorageException(e)
    }

/** Runs [block] in one write transaction, with the same error mapping as [storage]. */
suspend fun <T> FinoteDatabase.storageWrite(block: suspend () -> T): T = storage { withWriteTransaction { block() } }

/** Maps a database failure in the flow to [StorageException]. */
fun <T> Flow<T>.mapStorageErrors(): Flow<T> = catch { if (it is SQLException) throw StorageException(it) else throw it }
