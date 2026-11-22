package io.github.toanhp123.finote.domain.notification.model

import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind

/** The account a parsed notification posts to: one that exists, or one to create first. */
sealed interface AccountTarget {
    /** [attach] is an identifier the account does not hold yet; the writer stores it with the booking. */
    data class Existing(
        val accountId: String,
        val attach: AccountIdentifier? = null,
    ) : AccountTarget {
        // The attached mask stays out of logs and crash reports.
        override fun toString() = "Existing(accountId=$accountId, hasAttach=${attach != null})"
    }

    data class New(
        val packageName: String,
        val mask: String?,
        val bankName: String,
        val type: AccountType,
        val kind: IdentifierKind,
    ) : AccountTarget {
        // The mask stays out of logs and crash reports.
        override fun toString() =
            "New(packageName=$packageName, bankName=$bankName, type=$type, kind=$kind, hasMask=${mask != null})"
    }
}
