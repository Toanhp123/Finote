package io.github.toanhp123.finote.domain.transaction.usecase

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
        /** Today keeps the current time of day; another day is noon in the clock's zone. A blank note stays empty. */
        suspend operator fun invoke(
            amount: Long,
            type: TransactionType,
            accountId: String,
            categoryId: String?,
            date: LocalDate,
            note: String,
        ) {
            add(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    amount = amount,
                    feeAmount = 0,
                    type = type,
                    adjustmentDirection = null,
                    accountId = accountId,
                    toAccountId = null,
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
