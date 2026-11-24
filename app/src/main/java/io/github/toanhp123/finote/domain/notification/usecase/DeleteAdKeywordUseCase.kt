package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import io.github.toanhp123.finote.domain.notification.repository.UserKeywordRepository
import javax.inject.Inject

class DeleteAdKeywordUseCase
    @Inject
    constructor(
        private val repository: UserKeywordRepository,
        private val tx: TransactionRunner,
        private val reparse: ReparseFailedLogsUseCase,
    ) {
        /**
         * Forgets the keyword: in one write removes it and returns the logs it hid to the inbox, then re-parses them,
         * because a newer rule may read them now. False when the keyword was already gone.
         */
        suspend operator fun invoke(keywordId: String): Boolean {
            val outcome = tx.write<UserIgnoreKeyword?, Nothing> { Outcome.Ok(repository.remove(keywordId)) }
            val removed = (outcome as Outcome.Ok).value ?: return false
            reparse(setOf(removed.packageName))
            return true
        }
    }
