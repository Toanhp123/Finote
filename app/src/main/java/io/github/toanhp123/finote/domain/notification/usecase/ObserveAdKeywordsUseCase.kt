package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import io.github.toanhp123.finote.domain.notification.repository.UserKeywordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveAdKeywordsUseCase
    @Inject
    constructor(
        private val repository: UserKeywordRepository,
    ) {
        /** The taught keywords by package name. */
        operator fun invoke(): Flow<Map<String, List<UserIgnoreKeyword>>> =
            repository.observeAll().map { keywords -> keywords.groupBy { it.packageName } }
    }
