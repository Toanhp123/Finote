package io.github.toanhp123.finote.ui.transactions

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TransactionRowTextTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val names = mapOf(A to "Vietcombank", B to "MoMo", "cash" to "Tiền mặt")
    private val deleted = "Tài khoản đã xóa"

    private fun label(date: LocalDate) = dayLabel(date, today, Locale.ENGLISH)

    @Test
    fun `today and yesterday have their own labels`() {
        assertEquals(DayLabel.Today, label(today))
        assertEquals(DayLabel.Yesterday, label(LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun `older day in the same year has weekday and day month`() {
        assertEquals(DayLabel.Date("Mon, 02/11"), label(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `day in another year carries the year`() {
        assertEquals(DayLabel.Date("Tue, 04/11/2025"), label(LocalDate.of(2025, 11, 4)))
    }

    @Test
    fun `money kind follows the transaction type`() {
        assertEquals(MoneyKind.Income, moneyKindOf(tx(TransactionType.INCOME, 1)))
        assertEquals(MoneyKind.Income, moneyKindOf(tx(TransactionType.REFUND, 1)))
        assertEquals(MoneyKind.Expense, moneyKindOf(tx(TransactionType.EXPENSE, 1)))
        assertEquals(MoneyKind.Transfer, moneyKindOf(tx(TransactionType.TRANSFER, 1, to = B)))
        assertEquals(MoneyKind.Income, moneyKindOf(adjustment(AdjustmentDirection.IN, 1)))
        assertEquals(MoneyKind.Expense, moneyKindOf(adjustment(AdjustmentDirection.OUT, 1)))
    }

    @Test
    fun `subtitle is the account name and the note`() {
        val expense = tx(TransactionType.EXPENSE, 1, account = "cash")

        assertEquals("Tiền mặt", rowSubtitle(expense, names::get, deleted))
        assertEquals("Tiền mặt · Bún bò", rowSubtitle(expense.copy(note = "Bún bò"), names::get, deleted))
    }

    @Test
    fun `a blank note adds no separator`() {
        val expense = tx(TransactionType.EXPENSE, 1, account = "cash").copy(note = "  ")

        assertEquals("Tiền mặt", rowSubtitle(expense, names::get, deleted))
    }

    @Test
    fun `transfer subtitle shows source and destination`() {
        val transfer = tx(TransactionType.TRANSFER, 1, account = A, to = B)

        assertEquals("Vietcombank → MoMo", rowSubtitle(transfer, names::get, deleted))
    }

    @Test
    fun `a missing account reads as deleted`() {
        val expense = tx(TransactionType.EXPENSE, 1, account = "gone")
        val transfer = tx(TransactionType.TRANSFER, 1, account = A, to = "gone")

        assertEquals(deleted, rowSubtitle(expense, names::get, deleted))
        assertEquals("Vietcombank → $deleted", rowSubtitle(transfer, names::get, deleted))
    }
}
