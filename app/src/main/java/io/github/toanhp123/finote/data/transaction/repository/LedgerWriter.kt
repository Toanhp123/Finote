package io.github.toanhp123.finote.data.transaction.repository

import io.github.toanhp123.finote.data.account.local.entity.AccountEntity
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.account.repository.AccountBalanceRebuilder
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.data.transaction.mapper.toEntity
import io.github.toanhp123.finote.domain.account.service.BalanceCalculator
import io.github.toanhp123.finote.domain.account.service.anchoredInitialBalance
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import java.time.Clock

/**
 * The one place a ledger row is inserted. Call it inside a write transaction: it inserts, optionally fixes the start
 * of an uninitialized account, then rebuilds the touched accounts. Callers run `TransactionRules` before it.
 */
internal class LedgerWriter(
    db: FinoteDatabase,
    clock: Clock,
) {
    private val accounts = db.accountDao()
    private val transactions = db.transactionDao()
    private val rebuilder = AccountBalanceRebuilder(accounts, transactions, db.accountBalanceSnapshotDao(), clock)

    /**
     * [coldStart] lets the balance anchor of an uninitialized source account fix its start. It is opt-in: the anchor
     * comes from rows already stored, so a manual or adjustment insert must not trigger it.
     */
    suspend fun insert(
        transaction: Transaction,
        coldStart: Boolean = false,
    ) {
        transactions.insert(transaction.toEntity())
        if (coldStart) coldStartAll(transaction)
        rebuild(setOfNotNull(transaction.accountId, transaction.toAccountId))
    }

    /**
     * Inserts [newOriginal] soft-deleted, soft-deletes [partner], both with `pairedIntoId = transfer.id`; inserts
     * [transfer]; cold start for both of its accounts; rebuilds every account of the three rows. The partner keeps
     * its other fields, `updatedAt` included.
     */
    suspend fun insertPaired(
        transfer: Transaction,
        newOriginal: Transaction,
        partner: Transaction,
        now: Long,
    ) {
        transactions.insert(newOriginal.copy(deletedAt = now, pairedIntoId = transfer.id).toEntity())
        transactions.update(partner.copy(deletedAt = now, pairedIntoId = transfer.id).toEntity())
        transactions.insert(transfer.toEntity())
        coldStartAll(transfer)
        rebuild(setOfNotNull(newOriginal.accountId, partner.accountId, transfer.accountId, transfer.toAccountId))
    }

    suspend fun rebuild(accountIds: Set<String>) = rebuilder.rebuild(accountIds)

    private suspend fun coldStartAll(transaction: Transaction) =
        listOfNotNull(transaction.accountId, transaction.toAccountId).forEach {
            coldStart(checkNotNull(accounts.getById(it)))
        }

    /** The balance anchor of an uninitialized account fixes its start. A credit card has no anchor. */
    private suspend fun coldStart(account: AccountEntity) {
        if (account.isInitialized) return
        val rows = transactions.getActiveByAccount(account.id).map { it.toDomain() }
        BalanceCalculator.anchoredInitialBalance(account.toDomain(), rows)?.let {
            accounts.update(account.copy(initialBalance = it, isInitialized = true))
        }
    }
}
