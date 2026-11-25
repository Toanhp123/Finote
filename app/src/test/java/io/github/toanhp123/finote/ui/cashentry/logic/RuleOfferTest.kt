package io.github.toanhp123.finote.ui.cashentry.logic

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** When the sheet offers a keyword rule after a save, and what it prefills. */
class RuleOfferTest {
    private fun row(
        logId: String? = "log-1",
        categoryId: String? = "c1",
        merchant: String? = "MOMO HO CHI MINH",
    ) = Transaction(
        id = "t1",
        amount = 50_000L,
        feeAmount = 0L,
        type = TransactionType.EXPENSE,
        adjustmentDirection = null,
        accountId = "cash",
        toAccountId = null,
        timestamp = 0L,
        deletedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
        categoryId = categoryId,
        note = "",
        merchantName = merchant,
        notificationLogId = logId,
    )

    @Test
    fun `no offer without a log id`() {
        assertNull(row(logId = null).ruleOffer(TransactionType.EXPENSE, "note", "c2"))
    }

    @Test
    fun `no offer when the category did not change`() {
        // Edit: the row's own category. From-log: the suggestion the sheet opened with.
        assertNull(row(categoryId = "c1").ruleOffer(TransactionType.EXPENSE, "", "c1"))
        assertNull(ruleOfferFor("log-1", TransactionType.EXPENSE, "Cafe", "", "food", "food"))
    }

    @Test
    fun `no offer when the new category is null`() {
        assertNull(row().ruleOffer(TransactionType.EXPENSE, "", null))
    }

    @Test
    fun `no offer without merchant and note`() {
        assertNull(row(merchant = null).ruleOffer(TransactionType.EXPENSE, "  ", "c2"))
    }

    @Test
    fun `uses the note typed on the form`() {
        val offer = row(merchant = null).ruleOffer(TransactionType.INCOME, "GRAB chuyen tien", "i1")

        assertEquals("GRAB", offer?.keyword)
        assertEquals(CategoryType.INCOME, offer?.type)
        assertEquals("i1", offer?.categoryId)
    }

    @Test
    fun `prefill uses the merchant, then the guess`() {
        val fromMerchant = row().ruleOffer(TransactionType.EXPENSE, "some note", "c2")
        val fromNote = row(merchant = " ").ruleOffer(TransactionType.EXPENSE, "highlands coffee chuyen khoan", "c2")

        assertEquals("MOMO HO CHI MINH", fromMerchant?.keyword)
        assertEquals("Momo Ho Chi Minh", fromMerchant?.merchantName)
        assertEquals("highlands coffee", fromNote?.keyword)
        assertEquals("Highlands Coffee", fromNote?.merchantName)
        assertNull(fromMerchant?.existingRuleId)
        assertFalse(fromMerchant?.isSaving ?: true)
    }

    @Test
    fun `an answer for an older keyword is dropped`() {
        val offer = row().ruleOffer(TransactionType.EXPENSE, "", "c2")!!

        assertEquals("r1", offer.withLookedUp(offer.keyword, offer.keyword, "r1").existingRuleId)
        assertNull(offer.withLookedUp("other", "other", "r1").existingRuleId)
    }

    @Test
    fun `the offer hides keyword and name from logs`() {
        val text = row().ruleOffer(TransactionType.EXPENSE, "", "c2").toString()

        assertFalse(text.contains("MOMO", ignoreCase = true))
    }
}
