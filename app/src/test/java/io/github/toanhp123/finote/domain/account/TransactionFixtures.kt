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
) = Transaction("tx", amount, fee, type, null, account, to, at, deletedAt = null)

internal fun adjustment(
    direction: AdjustmentDirection,
    amount: Long,
    fee: Long = 0,
    at: Long = 0,
) = Transaction("tx", amount, fee, TransactionType.ADJUSTMENT, direction, A, null, at, deletedAt = null)
