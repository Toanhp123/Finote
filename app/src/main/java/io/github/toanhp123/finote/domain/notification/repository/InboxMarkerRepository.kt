package io.github.toanhp123.finote.domain.notification.repository

import io.github.toanhp123.finote.domain.notification.model.InboxMarkers
import kotlinx.coroutines.flow.Flow

/** Which inbox items the user has seen, and which the banner was closed for. Ids only, never text. */
interface InboxMarkerRepository {
    /** The current markers first, then again on every change. */
    fun observe(): Flow<InboxMarkers>

    /** Replaces the seen ids with exactly [ids]; the caller passes the items now in the inbox, which prunes old ids. */
    suspend fun markSeen(ids: Set<String>)

    /** Replaces the banner-dismissed ids with exactly [ids]. */
    suspend fun dismissBanner(ids: Set<String>)
}
