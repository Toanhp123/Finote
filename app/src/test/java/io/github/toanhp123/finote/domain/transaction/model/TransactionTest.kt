package io.github.toanhp123.finote.domain.transaction.model

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionTest {
    private val expense = tx(TransactionType.EXPENSE, 1)
    private val transfer = tx(TransactionType.TRANSFER, 1, account = A, to = B)

    @Test
    fun `non-transfer with a receiver balance cannot be built`() {
        assertThrows(IllegalArgumentException::class.java) { expense.copy(toPostBalance = 1) }
    }

    @Test
    fun `non-transfer with a receiver time cannot be built`() {
        assertThrows(IllegalArgumentException::class.java) { expense.copy(toTimestamp = 1) }
    }

    @Test
    fun `transfer with receiver balance and time builds`() {
        val row = transfer.copy(toPostBalance = 5, toTimestamp = 7)
        assertEquals(5L, row.toPostBalance)
        assertEquals(7L, row.toTimestamp)
    }

    @Test
    fun `live row with a pair link cannot be built`() {
        assertThrows(IllegalArgumentException::class.java) { expense.copy(pairedIntoId = "t") }
    }

    @Test
    fun `soft-deleted row with a pair link builds`() {
        assertEquals("t", expense.copy(deletedAt = 1, pairedIntoId = "t").pairedIntoId)
    }
}
