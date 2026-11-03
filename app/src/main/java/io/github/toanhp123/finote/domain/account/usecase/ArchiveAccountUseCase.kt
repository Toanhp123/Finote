package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import java.time.Clock
import javax.inject.Inject

class ArchiveAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val clock: Clock,
    ) {
        /** Archives or restores an account. The repository rejects a missing or deleted one. */
        suspend operator fun invoke(
            id: String,
            archived: Boolean,
        ) {
            repository.setArchived(id, archived, clock.millis())
        }
    }
