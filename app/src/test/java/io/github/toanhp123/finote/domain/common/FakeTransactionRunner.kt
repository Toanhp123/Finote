package io.github.toanhp123.finote.domain.common

/** Runs the block directly and counts calls. No rollback: tests assert the fake repository stayed untouched. */
class FakeTransactionRunner : TransactionRunner {
    var writes = 0
        private set

    override suspend fun <T, E> write(block: suspend () -> Outcome<T, E>): Outcome<T, E> {
        writes++
        return block()
    }
}
