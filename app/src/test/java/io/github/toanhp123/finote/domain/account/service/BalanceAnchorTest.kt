package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceAnchorTest {
    private val account = testAccount(A, initialBalance = 0L)

    // Minutes since 00:00, so 10:02 reads as `t(10, 2)`.
    private fun t(
        hour: Int,
        minute: Int,
    ): Long = hour * 60L + minute

    private fun transferIn(
        id: String,
        sentAt: Long,
        receivedAt: Long,
        toPostBalance: Long? = null,
    ) = tx(TransactionType.TRANSFER, 100, account = B, to = A, at = sentAt)
        .copy(id = id, toTimestamp = receivedAt, toPostBalance = toPostBalance)

    private fun mine(
        id: String,
        at: Long,
        postBalance: Long? = null,
    ) = tx(TransactionType.EXPENSE, 10, at = at).copy(id = id, postBalance = postBalance)

    @Test
    fun `received transfer anchors receiver when order agrees`() {
        val received = transferIn("t", t(10, 0), t(10, 5), toPostBalance = 5)

        assertEquals(5L, findBalanceAnchor(account, listOf(received))?.postBalance)
    }

    @Test
    fun `received transfer without toPostBalance never anchors`() {
        val manual = tx(TransactionType.TRANSFER, 100, account = B, to = A, at = 1)
        val sentPostBalance = manual.copy(id = "s", postBalance = 5)

        assertNull(findBalanceAnchor(account, listOf(manual, sentPostBalance)))
    }

    @Test
    fun `late receiver row between the two times disqualifies`() {
        val transfer = transferIn("t", t(10, 0), t(10, 5), toPostBalance = 5)
        val between = mine("m", t(10, 2))
        val older = mine("o", t(9, 0), postBalance = 9)

        assertNull(findBalanceAnchor(account, listOf(transfer, between)))
        assertEquals(9L, findBalanceAnchor(account, listOf(transfer, between, older))?.postBalance)
    }

    @Test
    fun `credit earlier than debit with a row between disqualifies`() {
        val transfer = transferIn("t", t(10, 5), t(10, 0), toPostBalance = 5)

        assertNull(findBalanceAnchor(account, listOf(transfer, mine("m", t(10, 2)))))
    }

    @Test
    fun `row at exactly toTimestamp disqualifies`() {
        val transfer = transferIn("t", t(10, 0), t(10, 5), toPostBalance = 5)

        assertNull(findBalanceAnchor(account, listOf(transfer, mine("m", t(10, 5)))))
    }

    @Test
    fun `another paired transfer whose receiver time crosses disqualifies`() {
        val transfer = transferIn("t", t(10, 0), t(10, 5), toPostBalance = 5)
        val crossing = transferIn("c", t(10, 10), t(10, 3))
        val agreeing = transferIn("c", t(9, 58), t(10, 3))

        assertNull(findBalanceAnchor(account, listOf(transfer, crossing)))
        assertEquals(5L, findBalanceAnchor(account, listOf(transfer, agreeing))?.postBalance)
    }

    @Test
    fun `normal anchor after a crossing transfer`() {
        val expense = mine("e", t(10, 4), postBalance = 7)
        val crossing = transferIn("c", t(9, 58), t(10, 6))

        assertNull(findBalanceAnchor(account, listOf(expense, crossing)))
    }

    @Test
    fun `sender rows do not count`() {
        val transfer = transferIn("t", t(10, 0), t(10, 5), toPostBalance = 5)
        val senderRow = tx(TransactionType.EXPENSE, 10, account = B, at = t(10, 2)).copy(id = "b")

        assertEquals(5L, findBalanceAnchor(account, listOf(transfer, senderRow))?.postBalance)
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
