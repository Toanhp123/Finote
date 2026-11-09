package io.github.toanhp123.finote.data.account

import io.github.toanhp123.finote.domain.account.model.Account

fun AccountEntity.toDomain(): Account =
    Account(
        id = id,
        name = name,
        nameKey = nameKey,
        type = type,
        initialBalance = initialBalance,
        currentBalance = currentBalance,
        currency = currency,
        icon = icon,
        color = color,
        sortOrder = sortOrder,
        isExcludedFromNetWorth = isExcludedFromNetWorth,
        isArchived = isArchived,
        isIgnored = isIgnored,
        isInitialized = isInitialized,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        bankPackageName = bankPackageName,
        accountNumberMask = accountNumberMask,
        mergedIntoId = mergedIntoId,
    )

fun Account.toEntity(): AccountEntity =
    AccountEntity(
        id = id,
        name = name,
        nameKey = nameKey,
        type = type,
        initialBalance = initialBalance,
        currentBalance = currentBalance,
        currency = currency,
        icon = icon,
        color = color,
        sortOrder = sortOrder,
        isExcludedFromNetWorth = isExcludedFromNetWorth,
        isArchived = isArchived,
        isIgnored = isIgnored,
        isInitialized = isInitialized,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        bankPackageName = bankPackageName,
        accountNumberMask = accountNumberMask,
        mergedIntoId = mergedIntoId,
    )
