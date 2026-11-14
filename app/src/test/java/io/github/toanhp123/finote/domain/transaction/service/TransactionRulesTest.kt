package io.github.toanhp123.finote.domain.transaction.service

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class TransactionRulesTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val limit = now.plus(TransactionRules.MAX_FUTURE_DAYS, ChronoUnit.DAYS)

    private fun expense(at: Instant = now) = tx(TransactionType.EXPENSE, 100_000, at = at.toEpochMilli())

    private fun check(
        transaction: Transaction,
        usable: Set<String> = setOf(A, B),
        categoryType: CategoryType? = null,
    ) = TransactionRules.check(transaction, now, usable, categoryType)

    @Test
    fun `constants keep their values`() {
        assertEquals(946_684_800_000L, TransactionRules.MIN_TIMESTAMP_MS)
        assertEquals(366L, TransactionRules.MAX_FUTURE_DAYS)
    }

    @Test
    fun `valid transaction is allowed`() = assertNull(check(expense()))

    @Test
    fun `timestamp bounds are inclusive`() {
        assertNull(check(expense(at = Instant.ofEpochMilli(TransactionRules.MIN_TIMESTAMP_MS))))
        assertNull(check(expense(at = limit)))
    }

    @Test
    fun `timestamp before 2000 is InvalidTimestamp`() =
        assertEquals(
            TransactionError.InvalidTimestamp,
            check(expense(at = Instant.ofEpochMilli(TransactionRules.MIN_TIMESTAMP_MS - 1))),
        )

    @Test
    fun `timestamp beyond 366 days ahead is InvalidTimestamp`() =
        assertEquals(TransactionError.InvalidTimestamp, check(expense(at = limit.plusMillis(1))))

    @Test
    fun `unusable source account is AccountUnusable`() =
        assertEquals(TransactionError.AccountUnusable, check(expense(), usable = setOf(B)))

    @Test
    fun `unusable destination account is AccountUnusable`() {
        val transfer = tx(TransactionType.TRANSFER, 1_000, to = B, at = now.toEpochMilli())
        assertNull(check(transfer))
        assertEquals(TransactionError.AccountUnusable, check(transfer, usable = setOf(A)))
    }

    @Test
    fun `expense needs an expense category`() {
        val food = expense().copy(categoryId = "food")
        assertNull(check(food, categoryType = CategoryType.EXPENSE))
        assertEquals(TransactionError.CategoryTypeMismatch, check(food, categoryType = CategoryType.INCOME))
    }

    @Test
    fun `income needs an income category`() {
        val salary = tx(TransactionType.INCOME, 1_000, at = now.toEpochMilli()).copy(categoryId = "salary")
        assertNull(check(salary, categoryType = CategoryType.INCOME))
        assertEquals(TransactionError.CategoryTypeMismatch, check(salary, categoryType = CategoryType.EXPENSE))
    }

    @Test
    fun `other types accept any existing category`() {
        val transfer =
            tx(TransactionType.TRANSFER, 1_000, to = B, at = now.toEpochMilli()).copy(categoryId = "any")
        assertNull(check(transfer, categoryType = CategoryType.INCOME))
        assertNull(check(transfer, categoryType = CategoryType.EXPENSE))
    }

    @Test
    fun `unknown category is CategoryNotFound`() =
        assertEquals(TransactionError.CategoryNotFound, check(expense().copy(categoryId = "ghost")))

    @Test
    fun `no category skips the category check`() = assertNull(check(expense(), categoryType = CategoryType.INCOME))

    @Test
    fun `checks run in order timestamp, accounts, category`() {
        val bad = expense(at = limit.plusMillis(1)).copy(categoryId = "ghost")
        val inRange = bad.copy(timestamp = now.toEpochMilli())
        assertEquals(TransactionError.InvalidTimestamp, check(bad, usable = emptySet()))
        assertEquals(TransactionError.AccountUnusable, check(inRange, usable = emptySet()))
        assertEquals(TransactionError.CategoryNotFound, check(inRange))
    }
}
