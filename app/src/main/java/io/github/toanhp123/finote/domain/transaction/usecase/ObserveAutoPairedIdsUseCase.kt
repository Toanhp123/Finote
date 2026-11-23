package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.repository.TransferPairRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAutoPairedIdsUseCase
    @Inject
    constructor(
        private val repository: TransferPairRepository,
    ) {
        /** Ids of the transfers made by auto-pairing. May hold a deleted transfer; the list shows live rows only. */
        operator fun invoke(): Flow<Set<String>> = repository.observeAutoPairedIds()
    }
