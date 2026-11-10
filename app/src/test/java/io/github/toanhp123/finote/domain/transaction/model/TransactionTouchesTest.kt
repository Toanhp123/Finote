package io.github.toanhp123.finote.domain.transaction.model

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.tx
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTouchesTest {
    @Test
    fun `source account is touched`() {
        val row = tx(TransactionType.EXPENSE, 1, account = A)
        assertTrue(row.touches(A))
        assertFalse(row.touches(B))
    }

    @Test
    fun `transfer destination is touched`() {
        assertTrue(tx(TransactionType.TRANSFER, 1, account = A, to = B).touches(B))
    }

    @Test
    fun `non-transfer toAccountId is not touched`() {
        assertFalse(tx(TransactionType.EXPENSE, 1, account = A, to = B).touches(B))
    }

    @Test
    fun `deleted row touches nothing`() {
        assertFalse(tx(TransactionType.EXPENSE, 1).copy(deletedAt = 1).touches(A))
    }
}
