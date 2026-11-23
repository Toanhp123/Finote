package io.github.toanhp123.finote.domain.transaction.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlin.math.abs

/** Two rows pair only when their [pairingTime] is at most this far apart (30 minutes). */
const val TRANSFER_PAIR_WINDOW_MILLIS = 1_800_000L

/** The single time used to compare two rows. Approach C (bank time) changes only this. */
fun pairingTime(row: Transaction): Long = row.timestamp

/** True when the row may still be auto-paired: notification source, live, never touched, not yet paired. */
fun isPairable(row: Transaction): Boolean =
    row.source == TransactionSource.NOTIFICATION_PARSER &&
        row.deletedAt == null &&
        row.pairedIntoId == null &&
        row.updatedAt == row.createdAt &&
        (row.type == TransactionType.EXPENSE || row.type == TransactionType.INCOME)

/**
 * Best partner of [row] among [candidates], or null. Rules: both pairable, one EXPENSE one INCOME, same amount,
 * different accounts, both accounts in [usableAccounts] and not CREDIT_CARD, |pairingTime| diff <= window.
 * Nearest pairingTime wins, then createdAt ascending, then id ascending.
 */
fun findTransferPartner(
    row: Transaction,
    candidates: List<Transaction>,
    usableAccounts: Map<String, Account>,
): Transaction? {
    fun ok(id: String) = usableAccounts[id]?.let { it.type != AccountType.CREDIT_CARD } == true
    if (!isPairable(row) || !ok(row.accountId)) return null
    return candidates
        .filter {
            isPairable(it) &&
                it.type != row.type &&
                it.amount == row.amount &&
                it.accountId != row.accountId &&
                ok(it.accountId) &&
                abs(pairingTime(it) - pairingTime(row)) <= TRANSFER_PAIR_WINDOW_MILLIS
        }.minWithOrNull(
            compareBy<Transaction> { abs(pairingTime(it) - pairingTime(row)) }
                .thenBy { it.createdAt }
                .thenBy { it.id },
        )
}

/** The TRANSFER that replaces [a] and [b] (either order). Sender = the EXPENSE side. */
fun buildPairedTransfer(
    a: Transaction,
    b: Transaction,
    id: String,
    now: Long,
): Transaction {
    val (expense, income) = if (a.type == TransactionType.EXPENSE) a to b else b to a
    return Transaction(
        id = id,
        amount = expense.amount,
        feeAmount = 0,
        type = TransactionType.TRANSFER,
        adjustmentDirection = null,
        accountId = expense.accountId,
        toAccountId = income.accountId,
        timestamp = expense.timestamp,
        deletedAt = null,
        createdAt = now,
        updatedAt = now,
        categoryId = null,
        merchantName = expense.merchantName?.takeIf { it.isNotBlank() } ?: income.merchantName,
        note = expense.note.ifBlank { income.note },
        source = TransactionSource.NOTIFICATION_PARSER,
        postBalance = expense.postBalance,
        notificationLogId = null,
        toPostBalance = income.postBalance,
        toTimestamp = income.timestamp,
    )
}
