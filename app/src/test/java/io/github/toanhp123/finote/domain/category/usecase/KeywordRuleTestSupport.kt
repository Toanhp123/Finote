package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

// why: test fixture with one named default per rule field the tests vary.
@Suppress("LongParameterList")
internal fun testRule(
    id: String = "r-1",
    keyword: String = "grab",
    merchantName: String? = null,
    categoryId: String = "cat-1",
    isUserDefined: Boolean = true,
    isActive: Boolean = true,
    createdAt: Long = 1L,
    updatedAt: Long = 1L,
) = KeywordRule(
    id = id,
    keyword = keyword,
    normalizedKeyword = normalizeKeyword(keyword),
    matchType = KeywordMatchType.CONTAINS,
    merchantName = merchantName,
    categoryId = categoryId,
    priority = 0,
    isUserDefined = isUserDefined,
    isActive = isActive,
    version = 0,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** A row booked from a notification, as the bulk apply reads it. */
internal fun testRow(
    id: String = "t-1",
    merchantName: String? = "GRAB",
    note: String = "",
    type: TransactionType = TransactionType.EXPENSE,
) = Transaction(
    id = id,
    amount = 50_000L,
    feeAmount = 0L,
    type = type,
    adjustmentDirection = null,
    accountId = "acc-1",
    toAccountId = null,
    timestamp = 1L,
    deletedAt = null,
    createdAt = 1L,
    updatedAt = 1L,
    merchantName = merchantName,
    note = note,
    notificationLogId = "log-$id",
)
