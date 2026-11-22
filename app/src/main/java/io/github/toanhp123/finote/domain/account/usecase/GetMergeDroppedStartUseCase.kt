package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountMergeRepository
import javax.inject.Inject

class GetMergeDroppedStartUseCase
    @Inject
    constructor(
        private val repository: AccountMergeRepository,
    ) {
        /** The start balance of [sourceId] that merging it into [targetId] would drop, or null when none is lost. */
        suspend operator fun invoke(
            sourceId: String,
            targetId: String,
        ): Long? = repository.getMergeDroppedStart(sourceId, targetId)
    }
