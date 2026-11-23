package io.github.toanhp123.finote.data.database

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import javax.inject.Inject

/**
 * Runs [TransactionRunner.write] in one Room write transaction. An `Err` result rolls back
 * by throwing a private signal from inside the transaction and catching it outside.
 *
 * Room 3.0.3 source: `withWriteTransaction` calls `useWriterConnection` and then
 * `withTransaction(IMMEDIATE)` (`RoomDatabase.kt`). The outermost call runs `BEGIN IMMEDIATE TRANSACTION`.
 * A nested call finds the connection already confined to the coroutine and runs `SAVEPOINT '<depth>'`
 * instead of a new transaction; it ends with `RELEASE SAVEPOINT`, or `ROLLBACK TO SAVEPOINT` on failure
 * (`ConnectionPoolImpl.kt`, `beginTransaction` and `endTransaction`). So an inner `storageWrite` joins the
 * outer transaction, and a failure that escapes the outer block rolls back everything.
 */
class RoomTransactionRunner
    @Inject
    constructor(
        private val db: FinoteDatabase,
    ) : TransactionRunner {
        private class RollbackSignal : Throwable(null, null, false, false)

        override suspend fun <T, E> write(block: suspend () -> Outcome<T, E>): Outcome<T, E> {
            val signal = RollbackSignal()
            var rolledBack: Outcome<T, E>? = null
            return try {
                db.storageWrite {
                    val outcome = block()
                    if (outcome is Outcome.Err<*>) {
                        rolledBack = outcome
                        throw signal
                    }
                    outcome
                }
            } catch (e: RollbackSignal) {
                if (e !== signal) throw e
                checkNotNull(rolledBack)
            }
        }
    }
