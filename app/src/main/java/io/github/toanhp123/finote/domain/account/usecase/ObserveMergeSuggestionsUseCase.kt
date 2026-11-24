package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountMergeRepository
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.repository.MergeDismissalRepository
import io.github.toanhp123.finote.domain.account.service.mergeCandidatePairs
import io.github.toanhp123.finote.domain.account.service.mergeSuggestions
import io.github.toanhp123.finote.domain.account.service.orderedPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveMergeSuggestionsUseCase
    @Inject
    constructor(
        private val accounts: AccountRepository,
        private val merges: AccountMergeRepository,
        private val dismissals: MergeDismissalRepository,
    ) {
        /** Account id to the id of the account it should merge with. Transfers are counted for candidate pairs only. */
        operator fun invoke(): Flow<Map<String, String>> =
            combine(
                accounts.observeAccounts(),
                accounts.observeIdentifiedAccountIds(),
                dismissals.observe(),
            ) { list, identified, dismissed ->
                val withTransfer =
                    mergeCandidatePairs(list, identified, dismissed)
                        .map { (a, b) -> orderedPair(a.id, b.id) }
                        .filter { (lo, hi) -> merges.countTransfersBetween(lo, hi) > 0 }
                        .toSet()
                mergeSuggestions(list, identified, dismissed) { lo, hi -> (lo to hi) in withTransfer }
            }
    }
