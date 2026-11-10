package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeTransactionRepositoryTest {
    private val repository = FakeTransactionRepository()

    @Test
    fun `observe drops deleted rows and sorts like the DAO`() =
        runTest {
            val base = tx(TransactionType.EXPENSE, 1_000)
            val a = base.copy(id = "a", timestamp = 2, createdAt = 1)
            val b = base.copy(id = "b", timestamp = 3, createdAt = 1)
            val c = base.copy(id = "c", timestamp = 2, createdAt = 1)
            val d = base.copy(id = "d", timestamp = 9, createdAt = 1, deletedAt = 5)
            repository.transactionsFlow.value = listOf(a, d, c, b)

            assertEquals(listOf("b", "c", "a"), repository.observeTransactions(null).first().map { it.id })
        }

    @Test
    fun `observe filters by account like touches`() =
        runTest {
            val transfer = tx(TransactionType.TRANSFER, 1_000, account = "acc-1", to = "acc-2").copy(id = "t")
            val other = tx(TransactionType.EXPENSE, 1_000, account = "acc-3").copy(id = "o")
            repository.transactionsFlow.value = listOf(transfer, other)

            assertEquals(listOf("t"), repository.observeTransactions("acc-2").first().map { it.id })
        }
}
