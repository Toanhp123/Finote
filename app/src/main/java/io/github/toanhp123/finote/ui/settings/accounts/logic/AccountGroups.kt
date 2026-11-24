package io.github.toanhp123.finote.ui.settings.accounts.logic

import io.github.toanhp123.finote.domain.account.model.Account

internal const val GROUP_KEY_PREFIX = "group:"

/** One top-level row of the accounts list: an account on its own, or a bank with several accounts. */
sealed interface AccountListItem {
    /** Stable list key; a group key cannot equal an account id. */
    val key: String

    val sortOrder: Int

    /** Every account the row stands for, in display order. */
    val accounts: List<Account>

    data class Single(
        val account: Account,
    ) : AccountListItem {
        override val key get() = account.id
        override val sortOrder get() = account.sortOrder
        override val accounts get() = listOf(account)

        // Balances must never reach a log or crash report.
        override fun toString() = "Single(id=${account.id})"
    }

    /** [total] is the sum of the members that are not ignored. */
    data class Group(
        val packageName: String,
        val bankName: String,
        val members: List<Account>,
        val total: Long,
    ) : AccountListItem {
        override val key get() = GROUP_KEY_PREFIX + packageName
        override val sortOrder get() = members.first().sortOrder
        override val accounts get() = members

        override fun toString() = "Group(packageName=$packageName, members=${members.size})"
    }
}

/**
 * The live, not archived [accounts] as list rows. Accounts of one bank form a [AccountListItem.Group]; a bank with one
 * account and an account without a bank are [AccountListItem.Single]. Members and rows follow `sortOrder`, a group by
 * its lowest member. [bankNames] maps a package to its bank name; a missing one shows the package.
 */
fun groupAccounts(
    accounts: List<Account>,
    bankNames: Map<String, String>,
): List<AccountListItem> {
    val (banked, loose) =
        accounts.filter { it.deletedAt == null && !it.isArchived }.partition { it.bankPackageName != null }
    val bankRows =
        banked.groupBy { it.bankPackageName.orEmpty() }.map { (pkg, members) -> bankRow(pkg, members, bankNames) }
    return (bankRows + loose.map { AccountListItem.Single(it) }).sortedBy { it.sortOrder }
}

private fun bankRow(
    packageName: String,
    members: List<Account>,
    bankNames: Map<String, String>,
): AccountListItem {
    val sorted = members.sortedBy { it.sortOrder }
    val total = sorted.filterNot { it.isIgnored }.sumOf { it.currentBalance }
    return if (sorted.size == 1) {
        AccountListItem.Single(sorted.single())
    } else {
        AccountListItem.Group(packageName, bankNames[packageName] ?: packageName, sorted, total)
    }
}

/** The ids in row order, each group's members together in their order: what a finished reorder writes. */
fun reorderedIds(items: List<AccountListItem>): List<String> = items.flatMap { item -> item.accounts.map { it.id } }
