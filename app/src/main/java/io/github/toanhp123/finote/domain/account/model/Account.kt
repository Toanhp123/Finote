package io.github.toanhp123.finote.domain.account.model

/**
 * A place that holds money. [initialBalance] and [currentBalance] are VND, signed: a credit card
 * is negative while it carries debt. Every `Long` time field is epoch ms.
 */
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val initialBalance: Long,
    val currentBalance: Long,
    val currency: String,
    val icon: String,
    val color: Int,
    val sortOrder: Int,
    val isExcludedFromNetWorth: Boolean,
    val isArchived: Boolean,
    val isInitialized: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
