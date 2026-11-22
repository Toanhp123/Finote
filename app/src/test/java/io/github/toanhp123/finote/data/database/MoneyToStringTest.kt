package io.github.toanhp123.finote.data.database

import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.account.model.MonthlyBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MoneyToStringTest {
    private val accountRow =
        account(name = "N-SECRET", currentBalance = 987_654L, bankPackageName = "pkg")
    private val transactionRow = transaction(postBalance = 123_456L)

    @Test
    fun `accounts hide balances and name`() {
        assertEquals("AccountEntity(id=acc-1, type=BANK)", accountRow.toString())
        assertEquals("Account(id=acc-1, type=BANK)", accountRow.toDomain().toString())
        assertNoMoney(accountRow.toString(), accountRow.toDomain().toString())
    }

    @Test
    fun `transactions hide amount, fee, balance, merchant and note`() {
        assertEquals("TransactionEntity(id=tx-1, type=EXPENSE, source=MANUAL)", transactionRow.toString())
        assertEquals("Transaction(id=tx-1, type=EXPENSE, source=MANUAL)", transactionRow.toDomain().toString())
        assertNoMoney(transactionRow.toString(), transactionRow.toDomain().toString())
    }

    @Test
    fun `balance records hide amounts`() {
        val monthly = MonthlyBalance("acc-1", "2026-11", 1_000_000L, 1_150_000L, 500_000L, 300_000L, 100_000L, 150_000L)

        assertEquals("AccountBalanceSnapshotEntity(accountId=acc-1, monthYear=2026-11)", snapshot().toString())
        assertEquals("MonthlyBalance(accountId=acc-1, monthYear=2026-11)", monthly.toString())
        assertEquals("BalanceDrift(accountId=acc-1)", BalanceDrift("acc-1", 987_654L).toString())
    }

    private fun assertNoMoney(vararg texts: String) {
        val secrets = listOf("SECRET", "987654", "123456", "150000", "1100", "Test merchant", "Test note")
        texts.forEach { text -> secrets.forEach { assertFalse("leaks $it: $text", text.contains(it)) } }
    }
}
