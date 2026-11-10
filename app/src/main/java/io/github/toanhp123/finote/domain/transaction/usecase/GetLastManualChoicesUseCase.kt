package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.model.ManualChoices
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import javax.inject.Inject

class GetLastManualChoicesUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
    ) {
        suspend operator fun invoke(): ManualChoices =
            ManualChoices(
                accountId = repository.getLastManualAccountId(),
                categoryIds =
                    listOf(TransactionType.EXPENSE, TransactionType.INCOME)
                        .associateWith { repository.getLastManualCategoryId(it) },
            )
    }
