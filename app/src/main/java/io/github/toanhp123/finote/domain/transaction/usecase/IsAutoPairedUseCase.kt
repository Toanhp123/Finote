package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.repository.TransferPairRepository
import javax.inject.Inject

class IsAutoPairedUseCase
    @Inject
    constructor(
        private val repository: TransferPairRepository,
    ) {
        /** True when original rows point at [id], whatever its type is now. */
        suspend operator fun invoke(id: String): Boolean = repository.getPairedOriginals(id).isNotEmpty()
    }
