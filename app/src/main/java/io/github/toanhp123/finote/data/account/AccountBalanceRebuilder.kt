package io.github.toanhp123.finote.data.account

import io.github.toanhp123.finote.data.transaction.TransactionDao
import io.github.toanhp123.finote.data.transaction.toDomain
import io.github.toanhp123.finote.domain.account.BalanceCalculator
import io.github.toanhp123.finote.domain.account.model.MonthlyBalance
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

/**
 * Recomputes `currentBalance` and the monthly snapshots of accounts from their live transactions.
 * Call it inside a write transaction. Snapshot rows are never deleted: a month that no longer has
 * a transaction before it goes flat (opening = closing = initial balance, totals 0).
 *
 * shortcut: rebuilds from the first month, O(transactions of the account) per write;
 * rebuild from the edited month instead once it measures slow in P5.
 */
internal class AccountBalanceRebuilder(
    private val accounts: AccountDao,
    private val transactions: TransactionDao,
    private val snapshots: AccountBalanceSnapshotDao,
    private val clock: Clock,
) {
    suspend fun rebuild(accountIds: Set<String>) {
        accountIds.sorted().forEach { rebuildAccount(it) }
    }

    private suspend fun rebuildAccount(accountId: String) {
        val account = requireNotNull(accounts.getById(accountId)) { "account does not exist" }
        val live = transactions.getActiveByAccount(accountId).map { it.toDomain() }
        val balance = BalanceCalculator.balance(accountId, account.initialBalance, live)
        val now = clock.millis()
        accounts.update(account.copy(currentBalance = balance, updatedAt = now))
        writeSnapshots(account, live, now)
    }

    private suspend fun writeSnapshots(
        account: AccountEntity,
        live: List<Transaction>,
        now: Long,
    ) {
        val stored = snapshots.getByAccount(account.id).associateBy { it.monthYear }
        val thisMonth = YearMonth.now(clock)
        val until = maxOf(thisMonth, stored.keys.maxOfOrNull { YearMonth.parse(it) } ?: thisMonth)
        val computed =
            BalanceCalculator
                .monthlySnapshots(account.id, account.initialBalance, live, clock.zone, until)
                .associateBy { it.monthYear }
        for (month in computed.keys + stored.keys) {
            val figures = computed[month] ?: flat(account, month)
            val previous = stored[month]
            val row = figures.toEntity(previous?.id ?: UUID.randomUUID().toString(), now)
            when {
                previous == null -> snapshots.insert(row)
                row != previous.copy(updatedAt = now) -> snapshots.update(row)
            }
        }
    }

    private fun flat(
        account: AccountEntity,
        monthYear: String,
    ) = MonthlyBalance(account.id, monthYear, account.initialBalance, account.initialBalance, 0, 0, 0, 0)

    private fun MonthlyBalance.toEntity(
        id: String,
        updatedAt: Long,
    ) = AccountBalanceSnapshotEntity(
        id = id,
        accountId = accountId,
        monthYear = monthYear,
        openingBalance = openingBalance,
        closingBalance = closingBalance,
        totalIncome = totalIncome,
        totalExpense = totalExpense,
        totalTransferIn = totalTransferIn,
        totalTransferOut = totalTransferOut,
        updatedAt = updatedAt,
    )
}
