package io.github.toanhp123.finote.domain.account

import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceDriftTest {
    private val account = testAccount(A, initialBalance = 1_000_000L)

    private fun income(
        id: String,
        amount: Long,
        at: Long,
        postBalance: Long? = null,
    ): Transaction = tx(TransactionType.INCOME, amount, at = at).copy(id = id, postBalance = postBalance)

    @Test
    fun `match gives no drift`() {
        val rows = listOf(income("a", 500_000, 1, postBalance = 1_500_000))

        assertNull(detectBalanceDrift(account, rows))
    }

    @Test
    fun `mismatch gives difference`() {
        val rows = listOf(income("a", 500_000, 1, postBalance = 1_700_000))

        assertEquals(BalanceDrift(A, 200_000), detectBalanceDrift(account, rows))
    }

    @Test
    fun `negative difference keeps its sign`() {
        val rows = listOf(income("a", 500_000, 1, postBalance = 1_400_000))

        assertEquals(BalanceDrift(A, -100_000), detectBalanceDrift(account, rows))
    }

    @Test
    fun `stays after a later no-balance notification`() {
        val rows =
            listOf(
                income("a", 500_000, 1, postBalance = 1_700_000),
                income("b", 100_000, 2),
            )

        assertEquals(BalanceDrift(A, 200_000), detectBalanceDrift(account, rows))
    }

    @Test
    fun `gone after a later adjustment`() {
        val adjust =
            adjustment(AdjustmentDirection.IN, 200_000, at = 2).copy(id = "adj")
        val rows = listOf(income("a", 500_000, 1, postBalance = 1_700_000), adjust)

        assertNull(detectBalanceDrift(account, rows))
    }

    @Test
    fun `an adjustment before the post balance does not hide a drift`() {
        val adjust = adjustment(AdjustmentDirection.IN, 50_000, at = 1).copy(id = "adj")
        val rows = listOf(adjust, income("a", 500_000, 2, postBalance = 1_700_000))

        assertEquals(BalanceDrift(A, 150_000), detectBalanceDrift(account, rows))
    }

    @Test
    fun `later manual transaction is not a drift`() {
        val rows =
            listOf(
                income("a", 500_000, 1, postBalance = 1_500_000),
                income("b", 300_000, 2),
            )

        assertNull(detectBalanceDrift(account, rows))
    }

    @Test
    fun `deleted rows and other accounts are ignored`() {
        val rows =
            listOf(
                income("a", 500_000, 1, postBalance = 1_500_000),
                income("gone", 100_000, 0).copy(deletedAt = 5),
                tx(TransactionType.INCOME, 9_000_000, account = B, at = 0).copy(id = "other"),
            )

        assertNull(detectBalanceDrift(account, rows))
    }

    @Test
    fun `credit card never drifts`() {
        val card = account.copy(type = AccountType.CREDIT_CARD)
        val rows = listOf(income("a", 500_000, 1, postBalance = 1_700_000))

        assertNull(detectBalanceDrift(card, rows))
    }

    @Test
    fun `no post balance no drift`() {
        val rows = listOf(income("a", 500_000, 1))

        assertNull(detectBalanceDrift(account, rows))
    }

    @Test
    fun `ties on time are ordered by createdAt then id`() {
        val first = income("a", 500_000, 1).copy(createdAt = 1)
        val second = income("b", 100_000, 1, postBalance = 1_600_000).copy(createdAt = 2)

        assertNull(detectBalanceDrift(account, listOf(second, first)))
    }
}
