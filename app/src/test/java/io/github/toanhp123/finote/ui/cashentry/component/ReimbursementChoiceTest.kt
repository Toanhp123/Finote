package io.github.toanhp123.finote.ui.cashentry.component

import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReimbursementChoiceTest {
    private val expense = tx(TransactionType.EXPENSE, 1)

    private fun choiceOf(
        excluded: Boolean,
        status: ReimbursementStatus,
    ) = expense.copy(isExcludedFromAnalytics = excluded, reimbursementStatus = status).reimbursementChoice()

    @Test
    fun `each choice maps to its stored pair`() {
        assertEquals(false to ReimbursementStatus.NONE, ReimbursementChoice.NORMAL.pair())
        assertEquals(true to ReimbursementStatus.PENDING, ReimbursementChoice.PENDING.pair())
        assertEquals(true to ReimbursementStatus.SETTLED, ReimbursementChoice.SETTLED.pair())
    }

    private fun ReimbursementChoice.pair() = isExcludedFromAnalytics to status

    @Test
    fun `the stored pairs read back as their choice`() {
        assertEquals(ReimbursementChoice.NORMAL, choiceOf(false, ReimbursementStatus.NONE))
        assertEquals(ReimbursementChoice.PENDING, choiceOf(true, ReimbursementStatus.PENDING))
        assertEquals(ReimbursementChoice.SETTLED, choiceOf(true, ReimbursementStatus.SETTLED))
    }

    @Test
    fun `an odd pair is read by its status`() {
        assertEquals(ReimbursementChoice.NORMAL, choiceOf(true, ReimbursementStatus.NONE))
        assertEquals(ReimbursementChoice.PENDING, choiceOf(false, ReimbursementStatus.PENDING))
        assertEquals(ReimbursementChoice.SETTLED, choiceOf(false, ReimbursementStatus.SETTLED))
    }
}
