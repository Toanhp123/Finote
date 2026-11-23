package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisionalStartTest {
    private val account = testAccount(A, initialBalance = 0)

    private fun m(n: Long) = n * 60_000L

    /** Bank row on account A: [amount] is signed, [post] is the bank balance after it. */
    private fun bank(
        id: String,
        amount: Long,
        at: Long,
        post: Long,
    ): Transaction =
        tx(if (amount > 0) TransactionType.INCOME else TransactionType.EXPENSE, Math.abs(amount), at = at)
            .copy(id = id, createdAt = at, postBalance = post)

    private fun start(vararg rows: Transaction) = provisionalStart(account, rows.toList())

    @Test fun `single row gives its before balance`() = assertEquals(1_000L, start(bank("a", -100, m(1), 900)))

    @Test fun `swapped pair gives the real start`() =
        assertEquals(1_000L, start(bank("b", 300, m(1), 1_200), bank("a", -100, m(2), 900)))

    @Test fun `pure cycle keeps the earliest bank row`() =
        assertEquals(900L, start(bank("b", 100, m(1), 1_000), bank("a", -100, m(2), 900)))

    @Test fun `cycle plus a third row gives the real start`() =
        assertEquals(
            1_000L,
            start(bank("b", 100, m(1), 1_000), bank("a", -100, m(2), 900), bank("c", -50, m(3), 950)),
        )

    @Test fun `gap keeps the first balance row`() =
        assertEquals(1_000L, start(bank("a", -100, m(1), 900), bank("b", 300, m(2), 1_150)))

    @Test fun `manual row skips the trail`() {
        val manual = tx(TransactionType.EXPENSE, 40, at = m(0)).copy(id = "m", createdAt = m(0))
        // Q(first balance row) = 1_000, minus the manual row before it (-40).
        assertEquals(1_040L, start(manual, bank("a", -100, m(1), 900)))
    }

    @Test fun `adjustment skips the trail`() {
        val adj = adjustment(AdjustmentDirection.OUT, 40, at = m(0)).copy(id = "j", createdAt = m(0))
        assertEquals(1_040L, start(adj, bank("a", -100, m(1), 900)))
    }

    @Test fun `credit card gives null`() =
        assertNull(
            provisionalStart(
                testAccount(A, type = AccountType.CREDIT_CARD),
                listOf(bank("a", -100, m(1), 900)),
            ),
        )

    @Test fun `no balance row gives null`() =
        assertNull(start(tx(TransactionType.EXPENSE, 40, at = m(0)).copy(id = "m", createdAt = m(0))))

    @Test fun `isStartLocked is false at a 29 minute span`() =
        assertFalse(isStartLocked(A, listOf(bank("a", -100, m(0), 900), bank("b", -50, m(29), 850))))

    @Test fun `isStartLocked is true at a 30 minute span`() =
        assertTrue(isStartLocked(A, listOf(bank("a", -100, m(0), 900), bank("b", -50, m(30), 850))))

    @Test fun `backdated manual row does not lock`() {
        val manual = tx(TransactionType.EXPENSE, 40, at = m(-24 * 60)).copy(id = "m", createdAt = m(-24 * 60))
        assertFalse(isStartLocked(A, listOf(manual, bank("a", -100, m(0), 900), bank("b", -50, m(5), 850))))
    }

    @Test fun `a single balance row does not lock`() = assertFalse(isStartLocked(A, listOf(bank("a", -100, m(1), 900))))
}
