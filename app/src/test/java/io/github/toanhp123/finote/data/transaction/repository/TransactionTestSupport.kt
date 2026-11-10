package io.github.toanhp123.finote.data.transaction.repository

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import java.time.Instant

/** A live domain transaction on [accountId]; shared by the repository tests. */
internal fun domainTx(
    id: String = "tx-1",
    type: TransactionType = TransactionType.EXPENSE,
    amount: Long = 200_000L,
    accountId: String = "acc-1",
    toAccountId: String? = null,
    at: String = "2026-09-10T00:00:00Z",
) = Transaction(
    id = id,
    amount = amount,
    feeAmount = 0L,
    type = type,
    adjustmentDirection = null,
    accountId = accountId,
    toAccountId = toAccountId,
    timestamp = Instant.parse(at).toEpochMilli(),
    deletedAt = null,
    createdAt = 1L,
    updatedAt = 1L,
)
