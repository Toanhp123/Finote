package io.github.toanhp123.finote.ui.transactions.component

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class TransactionRowTextTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val names = mapOf(A to "Vietcombank", B to "MoMo", "cash" to "Tiền mặt")
    private val deleted = "Tài khoản đã xóa"
    private val pendingLabel = "Chờ trả"
    private val settledLabel = "Đã trả"

    private fun label(date: LocalDate) = dayLabel(date, today, Locale.ENGLISH)

    @Test
    fun `today and yesterday have their own labels`() {
        assertEquals(DayLabel.Today, label(today))
        assertEquals(DayLabel.Yesterday, label(LocalDate.of(2026, 11, 4)))
    }

    @Test
    fun `older day in the same year has weekday and day month`() {
        assertEquals(DayLabel.Date("Mon, 11/02"), label(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `day label follows the locale`() {
        assertEquals(
            DayLabel.Date("Th 2, 02/11"),
            dayLabel(LocalDate.of(2026, 11, 2), today, Locale.forLanguageTag("vi")),
        )
    }

    @Test
    fun `vietnamese day in another year carries the year`() {
        assertEquals(
            DayLabel.Date("Th 3, 04/11/2025"),
            dayLabel(LocalDate.of(2025, 11, 4), today, Locale.forLanguageTag("vi")),
        )
    }

    @Test
    fun `japanese day label this year and other year`() {
        assertEquals(DayLabel.Date("11/02(月)"), dayLabel(LocalDate.of(2026, 11, 2), today, Locale.JAPANESE))
        assertEquals(DayLabel.Date("2025/11/04(火)"), dayLabel(LocalDate.of(2025, 11, 4), today, Locale.JAPANESE))
    }

    @Test
    fun `simplified chinese day label this year and other year`() {
        assertEquals(DayLabel.Date("11/02周一"), dayLabel(LocalDate.of(2026, 11, 2), today, Locale.SIMPLIFIED_CHINESE))
        assertEquals(
            DayLabel.Date("2025/11/04周二"),
            dayLabel(LocalDate.of(2025, 11, 4), today, Locale.SIMPLIFIED_CHINESE),
        )
    }

    @Test
    fun `a date across the year boundary carries the year`() {
        val label = dayLabel(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 2), Locale.ENGLISH)

        assertTrue((label as DayLabel.Date).text.contains("2025"))
    }

    @Test
    fun `day in another year carries the year`() {
        assertEquals(DayLabel.Date("Tue, 11/04/2025"), label(LocalDate.of(2025, 11, 4)))
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

        assertEquals("Tiền mặt", rowSubtitle(expense, names::get, deleted, pendingLabel, settledLabel))
        assertEquals(
            "Tiền mặt · Bún bò",
            rowSubtitle(expense.copy(note = "Bún bò"), names::get, deleted, pendingLabel, settledLabel),
        )
    }

    @Test
    fun `a blank note adds no separator`() {
        val expense = tx(TransactionType.EXPENSE, 1, account = "cash").copy(note = "  ")

        assertEquals("Tiền mặt", rowSubtitle(expense, names::get, deleted, pendingLabel, settledLabel))
    }

    @Test
    fun `transfer subtitle shows source and destination`() {
        val transfer = tx(TransactionType.TRANSFER, 1, account = A, to = B)

        assertEquals("Vietcombank → MoMo", rowSubtitle(transfer, names::get, deleted, pendingLabel, settledLabel))
    }

    @Test
    fun `a missing account reads as deleted`() {
        val expense = tx(TransactionType.EXPENSE, 1, account = "gone")
        val transfer = tx(TransactionType.TRANSFER, 1, account = A, to = "gone")

        assertEquals(deleted, rowSubtitle(expense, names::get, deleted, pendingLabel, settledLabel))
        assertEquals("Vietcombank → $deleted", rowSubtitle(transfer, names::get, deleted, pendingLabel, settledLabel))
    }

    @Test
    fun `a reimbursement status adds its label after the account and before the note`() {
        val expense = tx(TransactionType.EXPENSE, 1, account = "cash")
        val pending = expense.copy(reimbursementStatus = ReimbursementStatus.PENDING)
        val settled = expense.copy(note = "Bún bò", reimbursementStatus = ReimbursementStatus.SETTLED)

        assertEquals("Tiền mặt · Chờ trả", rowSubtitle(pending, names::get, deleted, pendingLabel, settledLabel))
        assertEquals(
            "Tiền mặt · Đã trả · Bún bò",
            rowSubtitle(settled, names::get, deleted, pendingLabel, settledLabel),
        )
    }
}
