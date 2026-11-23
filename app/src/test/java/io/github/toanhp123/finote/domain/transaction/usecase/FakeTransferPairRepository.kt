package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransferPairRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Works on the rows of [transactions]. `unpair` copies the real rule on `stored`; it does not write balances.
 * [failWrite] makes `unpair` throw, for example an `ArithmeticException` from the balance math.
 */
class FakeTransferPairRepository(
    private val transactions: FakeTransactionRepository,
) : TransferPairRepository {
    var failWrite: Throwable? = null
    var unpairs = 0
        private set

    override suspend fun getPairedOriginals(transferId: String): List<Transaction> =
        transactions.stored.values.filter { it.pairedIntoId == transferId }

    override fun observeAutoPairedIds(): Flow<Set<String>> =
        transactions.transactionsFlow.map { rows -> rows.mapNotNullTo(mutableSetOf()) { it.pairedIntoId } }

    override suspend fun unpair(
        transferId: String,
        now: Long,
    ) {
        failWrite?.let { throw it }
        unpairs++
        val stored = transactions.stored
        stored[transferId] = checkNotNull(stored[transferId]).copy(deletedAt = now, updatedAt = now)
        getPairedOriginals(transferId).forEach {
            stored[it.id] = it.copy(deletedAt = null, pairedIntoId = null, updatedAt = maxOf(now, it.createdAt + 1))
        }
    }
}
