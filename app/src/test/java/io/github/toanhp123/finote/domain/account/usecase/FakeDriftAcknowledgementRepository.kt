package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.DriftKey
import io.github.toanhp123.finote.domain.account.model.digest
import io.github.toanhp123.finote.domain.account.repository.DriftAcknowledgementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Copies from the real repository: one digest per account, a new acknowledgement replaces the old one. */
class FakeDriftAcknowledgementRepository : DriftAcknowledgementRepository {
    val acknowledged = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun observe(): Flow<Map<String, String>> = acknowledged

    override suspend fun acknowledge(
        accountId: String,
        key: DriftKey,
    ) {
        acknowledged.value += accountId to key.digest(accountId)
    }
}
