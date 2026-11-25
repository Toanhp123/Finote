package io.github.toanhp123.finote.ui.cashentry.logic

import io.github.toanhp123.finote.domain.account.model.SYSTEM_CASH_ACCOUNT_ID
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CashEntryDefaultsTest {
    private val cash = testAccount(id = SYSTEM_CASH_ACCOUNT_ID)
    private val bank = testAccount(id = "bank")

    @Test
    fun `last used live account wins`() {
        assertEquals("bank", CashEntryDefaults.pickAccount(listOf(cash, bank), "bank"))
    }

    @Test
    fun `primary wins over last used`() {
        val primary = testAccount(id = "star").copy(isPrimary = true, sortOrder = 9)
        assertEquals("star", CashEntryDefaults.pickAccount(listOf(cash, bank, primary), "bank"))
    }

    @Test
    fun `archived primary is skipped`() {
        val primary = testAccount(id = "star").copy(isPrimary = true, isArchived = true)
        assertEquals("bank", CashEntryDefaults.pickAccount(listOf(cash, bank, primary), "bank"))
    }

    @Test
    fun `archived last used falls back to cash`() {
        val archived = bank.copy(isArchived = true)
        assertEquals(SYSTEM_CASH_ACCOUNT_ID, CashEntryDefaults.pickAccount(listOf(cash, archived), "bank"))
    }

    @Test
    fun `deleted last used falls back to cash`() {
        val deleted = bank.copy(deletedAt = 5L)
        assertEquals(SYSTEM_CASH_ACCOUNT_ID, CashEntryDefaults.pickAccount(listOf(cash, deleted), "bank"))
    }

    @Test
    fun `cash beats a live account with lower sortOrder`() {
        val lowerBank = bank.copy(sortOrder = 0)
        val laterCash = cash.copy(sortOrder = 5)
        val accounts = listOf(lowerBank, laterCash)
        assertEquals(SYSTEM_CASH_ACCOUNT_ID, CashEntryDefaults.pickAccount(accounts, null))
        assertEquals(SYSTEM_CASH_ACCOUNT_ID, CashEntryDefaults.pickAccount(accounts, "dead-id"))
    }

    @Test
    fun `no cash falls back to lowest sortOrder`() {
        val second = testAccount(id = "second").copy(sortOrder = 2)
        val first = testAccount(id = "first").copy(sortOrder = 1)
        assertEquals("first", CashEntryDefaults.pickAccount(listOf(second, first), null))
    }

    @Test
    fun `all archived returns null`() {
        assertNull(CashEntryDefaults.pickAccount(listOf(cash.copy(isArchived = true)), null))
    }

    @Test
    fun `empty list returns null`() {
        assertNull(CashEntryDefaults.pickAccount(emptyList(), null))
    }

    @Test
    fun `category last used kept when live`() {
        val food = testCategory(id = "food")
        assertEquals("food", CashEntryDefaults.pickCategory(listOf(food), "food", CategoryType.EXPENSE))
    }

    @Test
    fun `hidden category returns null`() {
        val hidden = testCategory(id = "food").copy(isHidden = true)
        assertNull(CashEntryDefaults.pickCategory(listOf(hidden), "food", CategoryType.EXPENSE))
    }

    @Test
    fun `deleted category returns null`() {
        val deleted = testCategory(id = "food").copy(deletedAt = 5L)
        assertNull(CashEntryDefaults.pickCategory(listOf(deleted), "food", CategoryType.EXPENSE))
    }

    @Test
    fun `wrong type category returns null`() {
        val food = testCategory(id = "food", type = CategoryType.EXPENSE)
        assertNull(CashEntryDefaults.pickCategory(listOf(food), "food", CategoryType.INCOME))
    }

    @Test
    fun `unknown category id returns null`() {
        assertNull(CashEntryDefaults.pickCategory(listOf(testCategory()), "nope", CategoryType.EXPENSE))
    }

    @Test
    fun `null category id returns null`() {
        assertNull(CashEntryDefaults.pickCategory(listOf(testCategory()), null, CategoryType.EXPENSE))
    }

    @Test
    fun `child of a hidden parent is not picked`() {
        val parent = testCategory(id = "parent").copy(isHidden = true)
        val child = testCategory(id = "child", parentId = "parent")
        assertNull(CashEntryDefaults.pickCategory(listOf(parent, child), "child", CategoryType.EXPENSE))
    }

    @Test
    fun `hidden category is picked when it is the kept one`() {
        val hidden = testCategory(id = "food").copy(isHidden = true)
        assertEquals("food", CashEntryDefaults.pickCategory(listOf(hidden), "food", CategoryType.EXPENSE, "food"))
    }

    @Test
    fun `kept category of the wrong type or deleted is not picked`() {
        val food = testCategory(id = "food").copy(isHidden = true)
        assertNull(CashEntryDefaults.pickCategory(listOf(food), "food", CategoryType.INCOME, "food"))
        assertNull(
            CashEntryDefaults.pickCategory(listOf(food.copy(deletedAt = 5L)), "food", CategoryType.EXPENSE, "food"),
        )
    }
}
