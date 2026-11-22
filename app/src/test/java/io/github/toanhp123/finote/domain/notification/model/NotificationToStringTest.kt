package io.github.toanhp123.finote.domain.notification.model

import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
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
        val raw =
            testRaw(
                packageName = "pkg",
                title = "T-SECRET",
                content = "C-SECRET",
                postTime = 5L,
                notificationKey = "KEY-SECRET",
            )

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
                notificationKey = "KEY-SECRET",
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
        val target = AccountTarget.New("pkg", "M-SECRET", "Bank", AccountType.BANK, IdentifierKind.ACCOUNT)
        val book = SettlementDecision.Book(log, null, target, merchant = "MER-SECRET", note = "N-SECRET", rule = null)

        assertEquals(
            "Book(bankSide=NotificationLog(id=log1, packageName=pkg, status=PENDING), walletSide=null, " +
                "account=New(packageName=pkg, bankName=Bank, type=BANK, kind=ACCOUNT, hasMask=true))",
            book.toString(),
        )
        assertNoText(book.toString())
    }

    @Test
    fun `identifier and attaching target hide the mask`() {
        val identifier = AccountIdentifier("pkg", IdentifierKind.CARD, "M-SECRET", "acc-1")
        val target = AccountTarget.Existing("acc-1", attach = identifier)

        assertEquals("AccountIdentifier(packageName=pkg, kind=CARD, accountId=acc-1)", identifier.toString())
        assertEquals("Existing(accountId=acc-1, hasAttach=true)", target.toString())
        assertNoText(identifier.toString() + target.toString())
    }

    private fun assertNoText(text: String) {
        listOf("SECRET", "987654", "123456").forEach { assertFalse("leaks $it: $text", text.contains(it)) }
    }
}
