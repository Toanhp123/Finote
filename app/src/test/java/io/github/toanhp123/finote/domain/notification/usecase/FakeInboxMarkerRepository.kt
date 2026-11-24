package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.InboxMarkers
import io.github.toanhp123.finote.domain.notification.repository.InboxMarkerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Copies from the real repository: each write replaces one id set and observers see the new markers.
 * Does not copy: persistence.
 */
class FakeInboxMarkerRepository : InboxMarkerRepository {
    private val markers = MutableStateFlow(InboxMarkers(emptySet(), emptySet()))

    override fun observe(): Flow<InboxMarkers> = markers

    override suspend fun markSeen(ids: Set<String>) {
        markers.value = markers.value.copy(seenIds = ids.toSet())
    }

    override suspend fun dismissBanner(ids: Set<String>) {
        markers.value = markers.value.copy(bannerDismissedIds = ids.toSet())
    }
}
