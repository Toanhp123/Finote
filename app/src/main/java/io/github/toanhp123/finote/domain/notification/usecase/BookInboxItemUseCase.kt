package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.notification.model.AdjustmentToken
import io.github.toanhp123.finote.domain.notification.model.BookInboxResult
import io.github.toanhp123.finote.domain.notification.model.InboxBooking
import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class BookInboxItemUseCase
    @Inject
    constructor(
        private val repository: FailedInboxRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Books the item as a manual transaction. Pass the [confirmation] the sheet got from a
         * [BookInboxResult.NeedsAdjustmentConfirmation] once the user agrees. Any result but `Booked` rolls back.
         */
        suspend operator fun invoke(
            booking: InboxBooking,
            confirmation: AdjustmentToken? = null,
        ): BookInboxResult {
            val outcome =
                tx.write<BookInboxResult, BookInboxResult> {
                    val result = repository.book(booking, confirmation, UUID.randomUUID().toString(), clock.millis())
                    if (result == BookInboxResult.Booked) Outcome.Ok(result) else Outcome.Err(result)
                }
            return when (outcome) {
                is Outcome.Ok -> outcome.value
                is Outcome.Err -> outcome.error
            }
        }
    }
