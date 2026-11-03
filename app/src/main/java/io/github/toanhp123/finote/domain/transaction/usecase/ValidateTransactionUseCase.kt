package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Checks a transaction before it is written. Throws [IllegalArgumentException] with a reason. */
class ValidateTransactionUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(transaction: Transaction) {
            validateTimestamp(transaction.timestamp)
            validateAccounts(transaction)
            validateCategory(transaction)
        }

        private fun validateTimestamp(timestamp: Long) {
            val max = clock.instant().plus(MAX_FUTURE_DAYS, ChronoUnit.DAYS).toEpochMilli()
            require(timestamp in MIN_TIMESTAMP_MS..max) { "timestamp is outside the allowed range" }
        }

        private suspend fun validateAccounts(transaction: Transaction) {
            val wanted = setOfNotNull(transaction.accountId, transaction.toAccountId)
            val active = repository.getActiveAccountIds(wanted)
            require(active.containsAll(wanted)) { "account does not exist, is deleted or archived" }
        }

        private suspend fun validateCategory(transaction: Transaction) {
            val categoryId = transaction.categoryId ?: return
            val expected =
                when (transaction.type) {
                    TransactionType.EXPENSE -> CategoryType.EXPENSE
                    TransactionType.INCOME -> CategoryType.INCOME
                    else -> null
                }
            val actual = repository.getCategoryType(categoryId)
            requireNotNull(actual) { "category does not exist" }
            require(expected == null || actual == expected) { "category type does not match the transaction type" }
        }

        companion object {
            /** 2000-01-01T00:00:00Z. */
            const val MIN_TIMESTAMP_MS = 946_684_800_000L
            const val MAX_FUTURE_DAYS = 366L
        }
    }
