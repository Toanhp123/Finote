package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.MergeDismissalRepository
import io.github.toanhp123.finote.domain.account.service.orderedPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Copies from the real repository: a pair is kept as (min, max). */
class FakeMergeDismissalRepository : MergeDismissalRepository {
    val dismissed = MutableStateFlow<Set<Pair<String, String>>>(emptySet())

    override fun observe(): Flow<Set<Pair<String, String>>> = dismissed

    override suspend fun dismiss(
        a: String,
        b: String,
    ) {
        dismissed.value += orderedPair(a, b)
    }
}
