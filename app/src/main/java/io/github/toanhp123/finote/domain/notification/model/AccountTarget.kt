package io.github.toanhp123.finote.domain.notification.model

import io.github.toanhp123.finote.domain.account.model.AccountType

/** The account a parsed notification posts to: one that exists, or one to create first. */
sealed interface AccountTarget {
    data class Existing(
        val accountId: String,
    ) : AccountTarget

    data class New(
        val packageName: String,
        val mask: String?,
        val bankName: String,
        val type: AccountType,
    ) : AccountTarget {
        // The mask stays out of logs and crash reports.
        override fun toString() =
            "New(packageName=$packageName, bankName=$bankName, type=$type, hasMask=${mask != null})"
    }
}
