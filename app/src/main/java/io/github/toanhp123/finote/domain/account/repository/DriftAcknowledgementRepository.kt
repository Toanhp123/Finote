package io.github.toanhp123.finote.domain.account.repository

import io.github.toanhp123.finote.domain.account.model.DriftKey
import io.github.toanhp123.finote.domain.account.model.digest
import kotlinx.coroutines.flow.Flow

/**
 * The drift the user already checked with a zero difference, one digest per account id (see [digest]); a balance is
 * never stored. A banner hides while the digest of its key equals the stored one; a new ledger balance or a newer bank
 * row changes the key and the banner returns.
 */
interface DriftAcknowledgementRepository {
    /** The current account id to digest map first, then again on every change. */
    fun observe(): Flow<Map<String, String>>

    /** Replaces the digest stored for [accountId] with the one of [key]. */
    suspend fun acknowledge(
        accountId: String,
        key: DriftKey,
    )
}
