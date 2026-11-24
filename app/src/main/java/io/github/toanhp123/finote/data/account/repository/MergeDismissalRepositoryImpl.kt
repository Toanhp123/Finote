package io.github.toanhp123.finote.data.account.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.toanhp123.finote.domain.account.repository.MergeDismissalRepository
import io.github.toanhp123.finote.domain.account.service.orderedPair
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

private const val PREFS_NAME = "merge_dismissals"
private const val SEPARATOR = '|'

/** One key `"min|max"` per dismissed pair, value true, in SharedPreferences. Ids only: no balance, no mask. */
class MergeDismissalRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : MergeDismissalRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun observe(): Flow<Set<Pair<String, String>>> =
            callbackFlow {
                // SharedPreferences holds listeners weakly; awaitClose keeps this one reachable until close.
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                trySend(read())
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

        override suspend fun dismiss(
            a: String,
            b: String,
        ) {
            val (lo, hi) = orderedPair(a, b)
            prefs.edit { putBoolean("$lo$SEPARATOR$hi", true) }
        }

        private fun read(): Set<Pair<String, String>> =
            prefs.all
                .filterValues { it == true }
                .keys
                .mapNotNullTo(mutableSetOf()) { key ->
                    key.split(SEPARATOR).takeIf { it.size == 2 }?.let { (lo, hi) -> lo to hi }
                }
    }
