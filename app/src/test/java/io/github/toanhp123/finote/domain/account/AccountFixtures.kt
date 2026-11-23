package io.github.toanhp123.finote.domain.account

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType

internal fun testAccount(
    id: String = "acc-1",
    name: String = "Test account",
    initialBalance: Long = 1_000_000L,
    currency: String = "VND",
    type: AccountType = AccountType.BANK,
) = Account(
    id = id,
    name = name,
    type = type,
    initialBalance = initialBalance,
    currentBalance = initialBalance,
    currency = currency,
    icon = "account_balance",
    color = 0,
    sortOrder = 0,
    isExcludedFromNetWorth = false,
    isArchived = false,
    isInitialized = false,
    createdAt = 1L,
    updatedAt = 1L,
    deletedAt = null,
)
