package io.github.toanhp123.finote.domain.transaction.service

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionFields
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Pure transaction rules. `null` means allowed. */
object TransactionRules {
    /** 2000-01-01T00:00:00Z. */
    const val MIN_TIMESTAMP_MS = 946_684_800_000L
    const val MAX_FUTURE_DAYS = 366L

    /** True when [timestamp] is from 2000-01-01 up to [MAX_FUTURE_DAYS] after [now], both inclusive. */
    fun isValidTimestamp(
        timestamp: Long,
        now: Instant,
    ): Boolean = timestamp in MIN_TIMESTAMP_MS..now.plus(MAX_FUTURE_DAYS, ChronoUnit.DAYS).toEpochMilli()

    /**
     * Checks in this order: timestamp, accounts, category.
     * [usableAccountIds] is the part of the transaction's accounts that exist and are usable.
     * [categoryType] is the stored type of `transaction.categoryId`, null when that category does not exist.
     */
    fun check(
        transaction: Transaction,
        now: Instant,
        usableAccountIds: Set<String>,
        categoryType: CategoryType?,
    ): TransactionError? {
        val wanted = setOfNotNull(transaction.accountId, transaction.toAccountId)
        return when {
            !isValidTimestamp(transaction.timestamp, now) -> TransactionError.InvalidTimestamp
            !usableAccountIds.containsAll(wanted) -> TransactionError.AccountUnusable
            else -> checkCategory(transaction, categoryType)
        }
    }

    /**
     * Checks that [fields] fit together, so `Transaction.init` is never the first to refuse them.
     * Order: amount, type, then the transfer fields.
     */
    fun checkFields(fields: TransactionFields): TransactionError? =
        with(fields) {
            when {
                amount <= 0 -> {
                    TransactionError.InvalidAmount
                }

                type == TransactionType.ADJUSTMENT -> {
                    TransactionError.UnsupportedType
                }

                feeAmount < 0 -> {
                    TransactionError.InvalidTransfer
                }

                type != TransactionType.TRANSFER -> {
                    TransactionError.InvalidTransfer.takeIf { toAccountId != null || feeAmount != 0L }
                }

                toAccountId == null || toAccountId == accountId -> {
                    TransactionError.InvalidTransfer
                }

                categoryId != null -> {
                    TransactionError.InvalidTransfer
                }

                // The source pays amount + fee; refuse before the balance math would overflow.
                amount > Long.MAX_VALUE - feeAmount -> {
                    TransactionError.AmountOverflow
                }

                else -> {
                    null
                }
            }
        }

    private fun checkCategory(
        transaction: Transaction,
        categoryType: CategoryType?,
    ): TransactionError? {
        val expected =
            when (transaction.type) {
                TransactionType.EXPENSE -> CategoryType.EXPENSE
                TransactionType.INCOME -> CategoryType.INCOME
                else -> null
            }
        return when {
            transaction.categoryId == null -> null
            categoryType == null -> TransactionError.CategoryNotFound
            expected != null && categoryType != expected -> TransactionError.CategoryTypeMismatch
            else -> null
        }
    }
}
