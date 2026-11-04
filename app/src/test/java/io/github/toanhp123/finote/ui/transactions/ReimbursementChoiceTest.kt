package io.github.toanhp123.finote.ui.transactions

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
        for (choice in ReimbursementChoice.entries) {
            val written = choice.applyTo(expense)
            assertEquals(choice, written.reimbursementChoice())
        }
        val normal = ReimbursementChoice.NORMAL.applyTo(expense)
        assertEquals(false to ReimbursementStatus.NONE, normal.isExcludedFromAnalytics to normal.reimbursementStatus)
        val pending = ReimbursementChoice.PENDING.applyTo(expense)
        assertEquals(
            true to ReimbursementStatus.PENDING,
            pending.isExcludedFromAnalytics to pending.reimbursementStatus,
        )
        val settled = ReimbursementChoice.SETTLED.applyTo(expense)
        assertEquals(
            true to ReimbursementStatus.SETTLED,
            settled.isExcludedFromAnalytics to settled.reimbursementStatus,
        )
    }

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

    @Test
    fun `applying a choice keeps every other field`() {
        val original = expense.copy(note = "keep", categoryId = "cat")

        assertEquals(
            original.copy(isExcludedFromAnalytics = true, reimbursementStatus = ReimbursementStatus.PENDING),
            ReimbursementChoice.PENDING.applyTo(original),
        )
    }
}
