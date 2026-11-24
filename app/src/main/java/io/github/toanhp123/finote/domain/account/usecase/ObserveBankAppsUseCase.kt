package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.BankApp
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveBankAppsUseCase
    @Inject
    constructor(
        private val rules: ParserRuleRepository,
    ) {
        /** One entry per package of the active rules, sorted by bank name. */
        operator fun invoke(): Flow<List<BankApp>> =
            rules.observeRules().map { list ->
                list
                    .filter { it.isActive }
                    .distinctBy { it.packageName }
                    .map { BankApp(it.packageName, it.bankName) }
                    .sortedBy { it.bankName }
            }
    }
