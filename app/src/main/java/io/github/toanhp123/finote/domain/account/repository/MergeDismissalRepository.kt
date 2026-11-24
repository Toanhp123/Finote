package io.github.toanhp123.finote.domain.account.repository

import kotlinx.coroutines.flow.Flow

/** Merge suggestions the user turned down. A pair is stored as `(min, max)` ids; no balance or mask is kept. */
interface MergeDismissalRepository {
    /** The dismissed pairs first, then again on every change. */
    fun observe(): Flow<Set<Pair<String, String>>>

    /** Stores the pair as `(min, max)`; the order of [a] and [b] does not matter. */
    suspend fun dismiss(
        a: String,
        b: String,
    )
}
