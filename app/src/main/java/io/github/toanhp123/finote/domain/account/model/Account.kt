package io.github.toanhp123.finote.domain.account.model

/**
 * A place that holds money. [nameKey] is the string-resource key of a system account and is null once the user
 * renames it; the UI shows the resource when it is set and [name] otherwise. [initialBalance] and
 * [currentBalance] are VND, signed: a credit card is negative while it carries debt. Every `Long` time field is
 * epoch ms.
 */
data class Account(
    val id: String,
    val name: String,
    val nameKey: String? = null,
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
    /** Package of the bank or wallet app that feeds this account; with [accountNumberMask] it is the match key. */
    val bankPackageName: String? = null,
    /** Last four digits of the account number, as the notifications show them. */
    val accountNumberMask: String? = null,
    /** Set when this account was merged into another; the target account id. */
    val mergedIntoId: String? = null,
    /** True when notifications for this account are dropped instead of recorded. */
    val isIgnored: Boolean = false,
)
