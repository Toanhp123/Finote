package io.github.toanhp123.finote.domain.notification.model

import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.testLog
import io.github.toanhp123.finote.domain.notification.testRaw
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationToStringTest {
    private val parsed =
        ParsedFields(
            amount = 987_654L,
            type = TransactionType.EXPENSE,
            accountMask = "M-SECRET",
            postBalance = 123_456L,
            merchant = "MER-SECRET",
            note = "N-SECRET",
        )

    @Test
    fun `raw notification hides its text`() {
        val raw = testRaw(packageName = "pkg", title = "T-SECRET", content = "C-SECRET", postTime = 5L)

        assertEquals("RawNotification(packageName=pkg, postTime=5)", raw.toString())
        assertNoText(raw.toString())
    }

    @Test
    fun `notification log hides its text`() {
        val log =
            testLog(
                id = "log1",
                packageName = "pkg",
                title = "T-SECRET",
                content = "C-SECRET",
                contentHash = "H-SECRET",
                status = NotificationStatus.PENDING,
                failureReason = "F-SECRET",
                parsed = parsed,
            )

        assertEquals("NotificationLog(id=log1, packageName=pkg, status=PENDING)", log.toString())
        assertNoText(log.toString())
    }

    @Test
    fun `parsed fields hide amount, mask, balance and text`() {
        assertEquals("ParsedFields(type=EXPENSE, hasMask=true, hasPostBalance=true)", parsed.toString())
        assertNoText(parsed.toString())
    }

    @Test
    fun `settlement decision book hides merchant, note and mask`() {
        val log = testLog(id = "log1", title = "T-SECRET", content = "C-SECRET", parsed = parsed)
        val target = AccountTarget.New("pkg", "M-SECRET", "Bank", AccountType.BANK)
        val book = SettlementDecision.Book(log, null, target, merchant = "MER-SECRET", note = "N-SECRET")

        assertEquals(
            "Book(bankSide=NotificationLog(id=log1, packageName=pkg, status=PENDING), walletSide=null, " +
                "account=New(packageName=pkg, bankName=Bank, type=BANK, hasMask=true))",
            book.toString(),
        )
        assertNoText(book.toString())
    }

    private fun assertNoText(text: String) {
        listOf("SECRET", "987654", "123456").forEach { assertFalse("leaks $it: $text", text.contains(it)) }
    }
}
