package io.github.toanhp123.finote.domain.account

import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {
    private fun balance(
        vararg txs: Transaction,
        initial: Long = 1_000_000,
        account: String = A,
    ) = BalanceCalculator.balance(account, initial, txs.toList())

    @Test
    fun `income and refund add`() {
        assertEquals(1_300_000L, balance(tx(TransactionType.INCOME, 200_000), tx(TransactionType.REFUND, 100_000)))
    }

    @Test
    fun `expense subtracts`() {
        assertEquals(700_000L, balance(tx(TransactionType.EXPENSE, 300_000)))
    }

    @Test
    fun `adjustment in adds and out subtracts`() {
        val up = adjustment(AdjustmentDirection.IN, 50_000)
        val down = adjustment(AdjustmentDirection.OUT, 20_000)
        assertEquals(1_050_000L, balance(up))
        assertEquals(980_000L, balance(down))
    }

    @Test
    fun `fee is charged for any type`() {
        assertEquals(1_199_000L, balance(tx(TransactionType.INCOME, 200_000, fee = 1_000)))
        assertEquals(699_000L, balance(tx(TransactionType.EXPENSE, 300_000, fee = 1_000)))
        val adj = adjustment(AdjustmentDirection.IN, 10_000, fee = 500)
        assertEquals(1_009_500L, balance(adj))
    }

    @Test
    fun `transfer charges amount and fee to source only`() {
        val transfer = tx(TransactionType.TRANSFER, 400_000, fee = 2_000, to = B)
        assertEquals(598_000L, balance(transfer, account = A))
        assertEquals(1_400_000L, balance(transfer, account = B))
    }

    @Test
    fun `credit card balance goes negative`() {
        assertEquals(-250_000L, balance(tx(TransactionType.EXPENSE, 250_000), initial = 0))
    }

    @Test
    fun `zero initial balance with no transactions`() {
        assertEquals(0L, balance(initial = 0))
    }

    @Test
    fun `deleted transactions are ignored`() {
        assertEquals(1_000_000L, balance(tx(TransactionType.EXPENSE, 300_000).copy(deletedAt = 5L)))
    }

    @Test
    fun `transactions of other accounts are ignored`() {
        val other = tx(TransactionType.EXPENSE, 300_000, account = C)
        val transfer = tx(TransactionType.TRANSFER, 100_000, account = B, to = C)
        assertEquals(1_000_000L, balance(other, transfer))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `adjustment without direction is invalid`() {
        tx(TransactionType.ADJUSTMENT, 10_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `direction on a non adjustment is invalid`() {
        tx(TransactionType.INCOME, 10_000).copy(adjustmentDirection = AdjustmentDirection.IN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transfer without destination is invalid`() {
        tx(TransactionType.TRANSFER, 10_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transfer to the same account is invalid`() {
        tx(TransactionType.TRANSFER, 10_000, to = A)
    }

    @Test(expected = ArithmeticException::class)
    fun `overflow throws`() {
        balance(tx(TransactionType.INCOME, 1), initial = Long.MAX_VALUE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive amount is invalid`() {
        tx(TransactionType.EXPENSE, 0)
    }
}
