package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.MergeDismissalRepository
import javax.inject.Inject

class DismissMergeSuggestionUseCase
    @Inject
    constructor(
        private val dismissals: MergeDismissalRepository,
    ) {
        suspend operator fun invoke(
            a: String,
            b: String,
        ) = dismissals.dismiss(a, b)
    }
