package io.github.toanhp123.finote.domain.account

import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

internal const val A = "acc-a"
internal const val B = "acc-b"
internal const val C = "acc-c"

internal fun tx(
    type: TransactionType,
    amount: Long,
    fee: Long = 0,
    account: String = A,
    to: String? = null,
    at: Long = 0,
) = Transaction(
    id = "tx",
    amount = amount,
    feeAmount = fee,
    type = type,
    adjustmentDirection = null,
    accountId = account,
    toAccountId = to,
    timestamp = at,
    deletedAt = null,
    createdAt = 0,
    updatedAt = 0,
)

internal fun adjustment(
    direction: AdjustmentDirection,
    amount: Long,
    fee: Long = 0,
    at: Long = 0,
) = Transaction(
    id = "tx",
    amount = amount,
    feeAmount = fee,
    type = TransactionType.ADJUSTMENT,
    adjustmentDirection = direction,
    accountId = A,
    toAccountId = null,
    timestamp = at,
    deletedAt = null,
    createdAt = 0,
    updatedAt = 0,
)
