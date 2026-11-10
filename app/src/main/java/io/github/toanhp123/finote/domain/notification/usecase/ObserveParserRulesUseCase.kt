package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveParserRulesUseCase
    @Inject
    constructor(
        private val repository: ParserRuleRepository,
    ) {
        operator fun invoke(): Flow<List<ParserRule>> = repository.observeRules()
    }
