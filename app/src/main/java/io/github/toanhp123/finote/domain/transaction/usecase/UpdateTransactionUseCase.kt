package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionEdit
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.service.TransactionRules
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class UpdateTransactionUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Applies [edit] to a live, non-adjustment row. Read, copy, check and write share one transaction.
         * Keeps `id`, `source`, `notificationLogId`, `merchantName`, `createdAt` and `adjustmentDirection`.
         * The accounts the row leaves and the accounts it moves to must both be usable.
         */
        suspend operator fun invoke(
            id: String,
            edit: TransactionEdit,
        ): Outcome<Unit, TransactionError> =
            tx.write {
                val fieldError = TransactionRules.checkFields(edit.fields)
                val existing = if (fieldError == null) repository.getTransaction(id) else null
                when {
                    fieldError != null -> {
                        Outcome.Err(fieldError)
                    }

                    existing == null || existing.deletedAt != null || existing.type == TransactionType.ADJUSTMENT -> {
                        Outcome.Err(TransactionError.NotFound)
                    }

                    else -> {
                        replace(existing, withEdit(existing, edit))
                    }
                }
            }

        private fun withEdit(
            existing: Transaction,
            edit: TransactionEdit,
        ): Transaction {
            val fields = edit.fields
            val old = Instant.ofEpochMilli(existing.timestamp).atZone(clock.zone)
            val isSameDate = edit.date == old.toLocalDate()
            val isExpense = fields.type == TransactionType.EXPENSE
            return existing.copy(
                amount = fields.amount,
                feeAmount = fields.feeAmount,
                type = fields.type,
                accountId = fields.accountId,
                toAccountId = fields.toAccountId,
                categoryId = fields.categoryId,
                note = edit.note.trim(),
                isExcludedFromAnalytics = isExpense && edit.isExcludedFromAnalytics,
                reimbursementStatus = if (isExpense) edit.reimbursementStatus else ReimbursementStatus.NONE,
                timestamp = if (isSameDate) existing.timestamp else movedTimestamp(old.toLocalTime(), edit.date),
                // The bank printed this balance for the old account and day only.
                postBalance = existing.postBalance.takeIf { isSameDate && fields.accountId == existing.accountId },
                updatedAt = clock.millis(),
            )
        }

        /** The new date at the row's old local time; a time still ahead of now on today becomes now. */
        private fun movedTimestamp(
            oldTime: LocalTime,
            date: LocalDate,
        ): Long {
            val moved = date.atTime(oldTime).atZone(clock.zone).toInstant()
            return if (date == LocalDate.now(clock) && moved.isAfter(clock.instant())) {
                clock.millis()
            } else {
                moved.toEpochMilli()
            }
        }

        private suspend fun replace(
            existing: Transaction,
            updated: Transaction,
        ): Outcome<Unit, TransactionError> {
            val old = setOfNotNull(existing.accountId, existing.toAccountId)
            val usable = repository.getUsableAccountIds(old + setOfNotNull(updated.accountId, updated.toAccountId))
            val categoryType = updated.categoryId?.let { repository.getCategoryType(it) }
            val error =
                if (usable.containsAll(old)) {
                    TransactionRules.check(updated, clock.instant(), usable, categoryType)
                } else {
                    TransactionError.AccountUnusable
                }
            return if (error != null) {
                Outcome.Err(error)
            } else {
                try {
                    repository.update(updated)
                    Outcome.Ok(Unit)
                } catch (_: ArithmeticException) {
                    Outcome.Err(TransactionError.AmountOverflow)
                }
            }
        }
    }
