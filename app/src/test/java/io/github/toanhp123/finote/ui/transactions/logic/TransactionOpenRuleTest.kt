package io.github.toanhp123.finote.ui.transactions.logic

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionOpenRuleTest {
    private val transfer = tx(TransactionType.TRANSFER, 10_000, account = A, to = B)

    @Test
    fun `a row is openable when its source is live`() {
        assertTrue(tx(TransactionType.EXPENSE, 10_000, account = A).isOpenable(setOf(A)))
        assertFalse(tx(TransactionType.EXPENSE, 10_000, account = A).isOpenable(setOf(B)))
    }

    @Test
    fun `a transfer needs both ends live`() {
        assertTrue(transfer.isOpenable(setOf(A, B)))
        assertFalse(transfer.isOpenable(setOf(A)))
        assertFalse(transfer.isOpenable(setOf(B)))
    }
}
