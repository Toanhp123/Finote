package io.github.toanhp123.finote.domain.notification.model

import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/** One regex of a bank, plus the filters (keywords and channels) that every rule of the bank carries. */
data class ParserRule(
    val id: String,
    val packageName: String,
    val bankName: String,
    /** Null means every channel is accepted. */
    val channelIdAllowlist: List<String>?,
    val ignoreKeywords: List<String>,
    val priority: Int,
    val isUserCustom: Boolean,
    val version: Int,
    /** Named groups: sign, amount, mask, balance, merchant, note, time. Flags are inline. */
    val regexPattern: String,
    /** Used when the pattern has no `sign` group, or the group did not match. Only INCOME or EXPENSE. */
    val fallbackTransactionType: TransactionType?,
    val dateFormatPattern: String?,
    /** An inactive rule is not used to parse, but it still filters. */
    val isActive: Boolean,
    val updatedAt: Long,
    /** Type of the account created for this rule's notifications. Null means [AccountType.BANK]. */
    val accountType: AccountType? = null,
)
