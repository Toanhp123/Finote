package io.github.toanhp123.finote.data.account.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.toanhp123.finote.domain.account.model.DriftKey
import io.github.toanhp123.finote.domain.account.model.digest
import io.github.toanhp123.finote.domain.account.repository.DriftAcknowledgementRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

private const val PREFS_NAME = "drift_ack"

/** One digest string per account id (never a balance) in SharedPreferences. A few tiny keys: no DataStore. */
class DriftAcknowledgementRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DriftAcknowledgementRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun observe(): Flow<Map<String, String>> =
            callbackFlow {
                // SharedPreferences holds listeners weakly; awaitClose keeps this one reachable until close.
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                trySend(read())
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

        override suspend fun acknowledge(
            accountId: String,
            key: DriftKey,
        ) = prefs.edit { putString(accountId, key.digest(accountId)) }

        private fun read(): Map<String, String> =
            buildMap { prefs.all.forEach { (accountId, value) -> (value as? String)?.let { put(accountId, it) } } }
    }
