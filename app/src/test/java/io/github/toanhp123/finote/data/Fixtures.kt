package io.github.toanhp123.finote.data

import io.github.toanhp123.finote.data.account.AccountBalanceSnapshotEntity
import io.github.toanhp123.finote.data.account.AccountEntity
import io.github.toanhp123.finote.data.category.CategoryEntity
import io.github.toanhp123.finote.data.transaction.TagEntity
import io.github.toanhp123.finote.data.transaction.TransactionEntity
import io.github.toanhp123.finote.data.transaction.TransactionTagCrossRef
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

// why: test fixture with one named default per entity field the tests vary.
@Suppress("LongParameterList")
fun account(
    id: String = "acc-1",
    name: String = "Test account",
    type: AccountType = AccountType.BANK,
    currentBalance: Long = 1_000_000L,
    sortOrder: Int = 0,
    deletedAt: Long? = null,
    bankPackageName: String? = null,
    accountNumberMask: String? = null,
): AccountEntity =
    AccountEntity(
        id = id,
        name = name,
        nameKey = null,
        type = type,
        initialBalance = currentBalance,
        currentBalance = currentBalance,
        currency = "VND",
        icon = "account_balance",
        color = 0xFF2E7D32.toInt(),
        sortOrder = sortOrder,
        isExcludedFromNetWorth = false,
        isArchived = false,
        isIgnored = false,
        isInitialized = true,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        deletedAt = deletedAt,
        bankPackageName = bankPackageName,
        accountNumberMask = accountNumberMask,
        mergedIntoId = null,
    )

fun category(
    id: String = "cat-1",
    parentId: String? = null,
    name: String = "Test category",
    sortOrder: Int = 0,
    createdAt: Long = 1_700_000_000_000L,
    deletedAt: Long? = null,
): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        nameKey = null,
        icon = "restaurant",
        color = 0xFFEF6C00.toInt(),
        type = CategoryType.EXPENSE,
        parentId = parentId,
        isSystemDefault = false,
        isHidden = false,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = deletedAt,
    )

fun snapshot(
    id: String = "snap-1",
    accountId: String = "acc-1",
    monthYear: String = "2026-11",
): AccountBalanceSnapshotEntity =
    AccountBalanceSnapshotEntity(
        id = id,
        accountId = accountId,
        monthYear = monthYear,
        openingBalance = 1_000_000L,
        closingBalance = 1_150_000L,
        totalIncome = 500_000L,
        totalExpense = 300_000L,
        totalTransferIn = 100_000L,
        totalTransferOut = 150_000L,
        updatedAt = 1_700_000_000_000L,
    )

// why: test fixture with one named default per entity field the tests vary.
@Suppress("LongParameterList")
fun transaction(
    id: String = "tx-1",
    accountId: String = "acc-1",
    toAccountId: String? = null,
    categoryId: String? = null,
    type: TransactionType = TransactionType.EXPENSE,
    timestamp: Long = 1_700_000_000_000L,
    postBalance: Long? = null,
    notificationLogId: String? = null,
): TransactionEntity =
    TransactionEntity(
        id = id,
        amount = 150_000L,
        feeAmount = 1_100L,
        type = type,
        adjustmentDirection = null,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        timestamp = timestamp,
        merchantName = "Test merchant",
        note = "Test note",
        source = TransactionSource.MANUAL,
        isExcludedFromAnalytics = true,
        reimbursementStatus = ReimbursementStatus.PENDING,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        deletedAt = null,
        postBalance = postBalance,
        notificationLogId = notificationLogId,
    )

fun tag(
    id: String = "tag-1",
    name: String = "Test tag",
    deletedAt: Long? = null,
): TagEntity =
    TagEntity(
        id = id,
        name = name,
        color = 0xFF1565C0.toInt(),
        isArchived = false,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        deletedAt = deletedAt,
    )

fun tagRef(
    transactionId: String = "tx-1",
    tagId: String = "tag-1",
): TransactionTagCrossRef = TransactionTagCrossRef(transactionId = transactionId, tagId = tagId)
