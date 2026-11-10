package io.github.toanhp123.finote.domain.account

import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceAnchorTest {
    private val account = testAccount(A, initialBalance = 0L)

    @Test
    fun `received transfer never anchors`() {
        val received = tx(TransactionType.TRANSFER, 100, account = B, to = A, at = 1).copy(postBalance = 5)

        assertNull(findBalanceAnchor(account, listOf(received)))
    }

    @Test
    fun `deleted row never anchors`() {
        val deleted = tx(TransactionType.EXPENSE, 100, at = 1).copy(postBalance = 5, deletedAt = 9)

        assertNull(findBalanceAnchor(account, listOf(deleted)))
    }

    @Test
    fun `credit card has no anchor`() {
        val card = account.copy(type = AccountType.CREDIT_CARD)
        val row = tx(TransactionType.EXPENSE, 100, at = 1).copy(postBalance = 5)

        assertNull(findBalanceAnchor(card, listOf(row)))
    }

    @Test
    fun `ties break by createdAt then id`() {
        val older = tx(TransactionType.EXPENSE, 10, at = 10).copy(id = "z", createdAt = 1)
        val anchor = tx(TransactionType.EXPENSE, 20, at = 10).copy(id = "a", createdAt = 2, postBalance = 7)
        val after = tx(TransactionType.EXPENSE, 30, at = 10).copy(id = "b", createdAt = 2)

        val found = checkNotNull(findBalanceAnchor(account, listOf(after, anchor, older)))

        assertEquals(7L, found.postBalance)
        assertEquals(listOf("z", "a"), found.ledger.map { it.id })
        assertEquals(emptyList<String>(), found.settledAfter.map { it.id })
    }

    @Test
    fun `adjustment after the anchor is settled into the start`() {
        val bank = tx(TransactionType.EXPENSE, 100_000, at = 1).copy(postBalance = 800_000)
        val settled = adjustment(AdjustmentDirection.OUT, 100_000, at = 2).copy(id = "adj")

        assertEquals(1_000_000L, BalanceCalculator.anchoredInitialBalance(account, listOf(bank, settled)))
    }

    @Test
    fun `no adjustment after the anchor`() {
        val bank = tx(TransactionType.EXPENSE, 100_000, at = 1).copy(postBalance = 800_000)

        assertEquals(900_000L, BalanceCalculator.anchoredInitialBalance(account, listOf(bank)))
    }

    @Test
    fun `no anchor gives no initial balance`() {
        assertNull(BalanceCalculator.anchoredInitialBalance(account, listOf(tx(TransactionType.EXPENSE, 1))))
    }
}
