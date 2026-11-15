package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class AddManualTransactionUseCase
    @Inject
    constructor(
        private val add: AddTransactionUseCase,
        private val clock: Clock,
    ) {
        /**
         * Delegates to [AddTransactionUseCase], which opens the write transaction.
         * Today keeps the current time of day; another day is noon in the clock's zone. A blank note stays empty.
         */
        @Suppress("LongParameterList") // why: one parameter per field of the manual form
        suspend operator fun invoke(
            amount: Long,
            type: TransactionType,
            accountId: String,
            categoryId: String?,
            date: LocalDate,
            note: String,
            toAccountId: String? = null,
            feeAmount: Long = 0,
        ): Outcome<Unit, TransactionError> {
            // Refused here, so a bad combination is an error and never an exception from Transaction.init.
            val refusal = refusalOf(type, accountId, categoryId, toAccountId, feeAmount, amount)
            if (refusal != null) return Outcome.Err(refusal)
            return add(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    amount = amount,
                    feeAmount = feeAmount,
                    type = type,
                    adjustmentDirection = null,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    timestamp = timestampOf(date),
                    deletedAt = null,
                    createdAt = 0,
                    updatedAt = 0,
                    categoryId = categoryId,
                    note = note.trim(),
                    source = TransactionSource.MANUAL,
                ),
            )
        }

        private fun refusalOf(
            type: TransactionType,
            accountId: String,
            categoryId: String?,
            toAccountId: String?,
            feeAmount: Long,
            amount: Long,
        ): TransactionError? =
            when {
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

        private fun timestampOf(date: LocalDate): Long =
            if (date == LocalDate.now(clock)) {
                clock.millis()
            } else {
                date
                    .atTime(NOON, 0)
                    .atZone(clock.zone)
                    .toInstant()
                    .toEpochMilli()
            }

        private companion object {
            const val NOON = 12
        }
    }
