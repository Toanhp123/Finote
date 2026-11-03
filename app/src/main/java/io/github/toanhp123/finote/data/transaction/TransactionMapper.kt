package io.github.toanhp123.finote.data.transaction

import io.github.toanhp123.finote.domain.transaction.model.Transaction

fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        id = id,
        amount = amount,
        feeAmount = feeAmount,
        type = type,
        adjustmentDirection = adjustmentDirection,
        accountId = accountId,
        toAccountId = toAccountId,
        timestamp = timestamp,
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        categoryId = categoryId,
        merchantName = merchantName,
        note = note,
        source = source,
        isExcludedFromAnalytics = isExcludedFromAnalytics,
        reimbursementStatus = reimbursementStatus,
    )

fun Transaction.toEntity(): TransactionEntity =
    TransactionEntity(
        id = id,
        amount = amount,
        feeAmount = feeAmount,
        type = type,
        adjustmentDirection = adjustmentDirection,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        timestamp = timestamp,
        merchantName = merchantName,
        note = note,
        source = source,
        isExcludedFromAnalytics = isExcludedFromAnalytics,
        reimbursementStatus = reimbursementStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
