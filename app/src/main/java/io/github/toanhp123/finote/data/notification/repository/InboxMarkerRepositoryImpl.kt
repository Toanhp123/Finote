package io.github.toanhp123.finote.data.notification.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.toanhp123.finote.domain.notification.model.InboxMarkers
import io.github.toanhp123.finote.domain.notification.repository.InboxMarkerRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

private const val PREFS_NAME = "inbox_markers"
private const val SEEN_KEY = "seenIds"
private const val BANNER_DISMISSED_KEY = "bannerDismissedIds"

/** Two string sets of item ids in SharedPreferences, like `drift_ack`. A few tiny keys: no DataStore. */
class InboxMarkerRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : InboxMarkerRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun observe(): Flow<InboxMarkers> =
            callbackFlow {
                // SharedPreferences holds listeners weakly; awaitClose keeps this one reachable until close.
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                trySend(read())
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

        override suspend fun markSeen(ids: Set<String>) = write(SEEN_KEY, ids)

        override suspend fun dismissBanner(ids: Set<String>) = write(BANNER_DISMISSED_KEY, ids)

        // A new set every time: SharedPreferences ignores a put of the very set getStringSet returned.
        private fun write(
            key: String,
            ids: Set<String>,
        ) = prefs.edit { putStringSet(key, HashSet(ids)) }

        private fun read() = InboxMarkers(readIds(SEEN_KEY), readIds(BANNER_DISMISSED_KEY))

        private fun readIds(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet().orEmpty()
    }
