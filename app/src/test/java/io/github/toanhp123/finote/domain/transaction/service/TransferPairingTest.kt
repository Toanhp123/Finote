package io.github.toanhp123.finote.domain.transaction.service

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransferPairingTest {
    private val usable = mapOf(A to testAccount(A), B to testAccount(B))

    private fun t(
        hour: Int,
        minute: Int,
        second: Int = 0,
        millis: Int = 0,
    ): Long = ((hour * 60L + minute) * 60 + second) * 1000 + millis

    private fun row(
        id: String,
        type: TransactionType,
        account: String,
        at: Long,
        amount: Long = 500_000,
        createdAt: Long = 1,
    ): Transaction =
        tx(type, amount, account = account, at = at).copy(
            id = id,
            source = TransactionSource.NOTIFICATION_PARSER,
            createdAt = createdAt,
            updatedAt = createdAt,
            postBalance = 7,
        )

    private fun debit(
        at: Long,
        id: String = "d",
        createdAt: Long = 1,
    ) = row(id, TransactionType.EXPENSE, A, at, createdAt = createdAt)

    private fun credit(
        at: Long,
        id: String = "c",
        createdAt: Long = 1,
    ) = row(id, TransactionType.INCOME, B, at, createdAt = createdAt)

    private fun partner(
        row: Transaction,
        vararg candidates: Transaction,
        accounts: Map<String, Account> = usable,
    ) = findTransferPartner(row, candidates.toList(), accounts)

    @Test
    fun `pairs debit with credit five minutes apart`() {
        val credit = credit(t(10, 5))

        assertEquals(credit, partner(debit(t(10, 0)), credit))
    }

    @Test
    fun `pairs when the partner is later than the row and sender is the expense side`() {
        val income = credit(t(10, 2))
        val expense = debit(t(10, 0))

        assertEquals(expense, partner(income, expense))
        val transfer = buildPairedTransfer(income, expense, "new", now = 9)
        assertEquals(A, transfer.accountId)
        assertEquals(t(10, 0), transfer.timestamp)
        assertEquals(B, transfer.toAccountId)
        assertEquals(t(10, 2), transfer.toTimestamp)
    }

    @Test
    fun `window edges`() {
        val row = debit(t(10, 0))

        assertNotNull(partner(row, credit(t(10, 29, 59))))
        assertNotNull(partner(row, credit(t(10, 30))))
        assertNull(partner(row, credit(t(10, 30, 0, 1))))
    }

    @Test
    fun `window edges when the partner is earlier than the row`() {
        val row = debit(t(10, 0))

        assertNotNull(partner(row, credit(t(9, 30))))
        assertNull(partner(row, credit(t(9, 29, 59, 999))))
    }

    @Test
    fun `no pair when the row's own account is a credit card`() {
        val card = mapOf(A to testAccount(A).copy(type = AccountType.CREDIT_CARD), B to testAccount(B))

        assertNull(partner(debit(t(10, 0)), credit(t(10, 1)), accounts = card))
    }

    @Test
    fun `income row finds an expense candidate and the sender is the candidate's account`() {
        val expense = debit(t(10, 1))
        val income = credit(t(10, 0))

        assertEquals(expense, partner(income, expense))
        assertEquals(A, buildPairedTransfer(income, expense, "new", now = 1).accountId)
    }

    @Test
    fun `builder falls back to income merchant when expense merchant is null`() {
        val expense = debit(t(10, 0)).copy(merchantName = null)
        val income = credit(t(10, 2)).copy(merchantName = "Alice")

        assertEquals("Alice", buildPairedTransfer(expense, income, "new", now = 1).merchantName)
    }

    @Test
    fun `no pair for different amount`() {
        val other = row("c", TransactionType.INCOME, B, t(10, 1), amount = 400_000)

        assertNull(partner(debit(t(10, 0)), other))
    }

    @Test
    fun `no pair for same direction`() {
        val other = row("e2", TransactionType.EXPENSE, B, t(10, 1))

        assertNull(partner(debit(t(10, 0)), other))
    }

    @Test
    fun `no pair for same account`() {
        val other = row("c", TransactionType.INCOME, A, t(10, 1))

        assertNull(partner(debit(t(10, 0)), other))
    }

    @Test
    fun `no pair for a touched candidate`() {
        assertNull(partner(debit(t(10, 0)), credit(t(10, 1)).copy(updatedAt = 99)))
    }

    @Test
    fun `no pair for a manual candidate`() {
        assertNull(partner(debit(t(10, 0)), credit(t(10, 1)).copy(source = TransactionSource.MANUAL)))
    }

    @Test
    fun `no pair for an already paired candidate`() {
        val paired = credit(t(10, 1)).copy(deletedAt = 5, pairedIntoId = "p")

        assertNull(partner(debit(t(10, 0)), paired))
    }

    @Test
    fun `no pair when the row itself is not pairable`() {
        assertNull(partner(debit(t(10, 0)).copy(source = TransactionSource.MANUAL), credit(t(10, 1))))
    }

    @Test
    fun `no pair for a credit card account`() {
        val card = mapOf(A to testAccount(A), B to testAccount(B).copy(type = AccountType.CREDIT_CARD))

        assertNull(partner(debit(t(10, 0)), credit(t(10, 1)), accounts = card))
    }

    @Test
    fun `no pair when an account is missing from usable`() {
        assertNull(partner(debit(t(10, 0)), credit(t(10, 1)), accounts = mapOf(A to testAccount(A))))
        assertNull(partner(debit(t(10, 0)), credit(t(10, 1)), accounts = mapOf(B to testAccount(B))))
    }

    @Test
    fun `nearest wins`() {
        val near = credit(t(10, 1), id = "near")
        val far = credit(t(10, 3), id = "far")

        assertEquals(near, partner(debit(t(10, 0)), far, near))
    }

    @Test
    fun `equal distance prefers lower createdAt then lower id`() {
        val older = credit(t(10, 1), id = "z", createdAt = 1)
        val newer = credit(t(10, 1), id = "a", createdAt = 2)
        assertEquals(older, partner(debit(t(10, 0)), newer, older))

        val low = credit(t(10, 1), id = "a", createdAt = 1)
        val high = credit(t(10, 1), id = "b", createdAt = 1)
        assertEquals(low, partner(debit(t(10, 0)), high, low))
    }

    @Test
    fun `builder copies sender and receiver fields`() {
        val expense = debit(t(10, 0)).copy(note = "rent", merchantName = "Landlord", postBalance = 100)
        val income = credit(t(10, 2)).copy(postBalance = 900)

        val transfer = buildPairedTransfer(expense, income, "new", now = 77)

        assertEquals(TransactionType.TRANSFER, transfer.type)
        assertEquals("new", transfer.id)
        assertEquals(500_000L, transfer.amount)
        assertEquals(0L, transfer.feeAmount)
        assertEquals(100L, transfer.postBalance)
        assertEquals(900L, transfer.toPostBalance)
        assertEquals("rent", transfer.note)
        assertEquals("Landlord", transfer.merchantName)
        assertNull(transfer.categoryId)
        assertNull(transfer.notificationLogId)
        assertEquals(TransactionSource.NOTIFICATION_PARSER, transfer.source)
        assertEquals(77L, transfer.createdAt)
        assertEquals(77L, transfer.updatedAt)
    }

    @Test
    fun `builder falls back to income note and merchant`() {
        val expense = debit(t(10, 0)).copy(note = " ", merchantName = "")
        val income = credit(t(10, 2)).copy(note = "from A", merchantName = "Alice")

        val transfer = buildPairedTransfer(income, expense, "new", now = 1)

        assertEquals("from A", transfer.note)
        assertEquals("Alice", transfer.merchantName)
    }

    @Test
    fun `isPairable rejects non expense or income types`() {
        val transfer =
            tx(TransactionType.TRANSFER, 1, account = A, to = B)
                .copy(source = TransactionSource.NOTIFICATION_PARSER)

        assertFalse(isPairable(transfer))
    }
}
