package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentCoverageTest {
    private val logTime = 100L

    private fun balanceRow(at: Long) = tx(TransactionType.EXPENSE, 10, at = at).copy(id = "b", postBalance = 5)

    private fun adjustmentAt(at: Long) = adjustment(AdjustmentDirection.IN, 10, at = at).copy(id = "a")

    private fun covered(vararg rows: Transaction) = isCoveredByAdjustment(A, logTime, rows.toList())

    @Test
    fun `balance between log and adjustment is covered`() {
        assertTrue(covered(balanceRow(150), adjustmentAt(200)))
    }

    @Test
    fun `adjustment with no balance between is not covered`() {
        assertFalse(covered(balanceRow(50), adjustmentAt(200)))
    }

    @Test
    fun `adjustment at the log time is not covered`() {
        assertFalse(covered(balanceRow(100), adjustmentAt(100)))
    }

    @Test
    fun `balance at the adjustment time is covered`() {
        assertTrue(covered(balanceRow(200), adjustmentAt(200)))
    }

    @Test
    fun `deleted adjustment is not covered`() {
        assertFalse(covered(balanceRow(150), adjustmentAt(200).copy(deletedAt = 300)))
    }

    @Test
    fun `deleted balance row is not covered`() {
        assertFalse(covered(balanceRow(150).copy(deletedAt = 300), adjustmentAt(200)))
    }

    @Test
    fun `adjustment of another account is not covered`() {
        assertFalse(covered(balanceRow(150), adjustmentAt(200).copy(accountId = B)))
    }

    @Test
    fun `received transfer balance counts`() {
        val received =
            tx(TransactionType.TRANSFER, 10, account = B, to = A, at = 120)
                .copy(id = "t", toTimestamp = 150, toPostBalance = 5)

        assertTrue(covered(received, adjustmentAt(200)))
    }

    @Test
    fun `sender balance on a transfer into the account does not count`() {
        val sentOnly =
            tx(TransactionType.TRANSFER, 10, account = B, to = A, at = 120)
                .copy(id = "t", toTimestamp = 150, postBalance = 5)

        assertFalse(covered(sentOnly, adjustmentAt(200)))
    }
}
