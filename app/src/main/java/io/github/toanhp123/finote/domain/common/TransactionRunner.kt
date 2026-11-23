package io.github.toanhp123.finote.domain.common

interface TransactionRunner {
    /** One write transaction. An Err result or an exception rolls everything back. */
    suspend fun <T, E> write(block: suspend () -> Outcome<T, E>): Outcome<T, E>
}
