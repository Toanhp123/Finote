package io.github.toanhp123.finote.data.database

import io.github.toanhp123.finote.domain.common.StorageException
import org.junit.Assert.assertTrue

/** Runs [block] and asserts it failed with a [StorageException] caused by a SQLite failure. */
suspend fun assertStorageFailure(block: suspend () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue(failure.toString(), failure is StorageException)
    assertTrue(failure?.cause.toString(), failure?.cause is android.database.SQLException)
}
