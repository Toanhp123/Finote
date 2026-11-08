package io.github.toanhp123.finote.data.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import io.github.toanhp123.finote.domain.notification.repository.RuleUpdateSettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

private const val PREFS_NAME = "rule_update"
private const val KEY_AUTO_UPDATE = "auto_update_enabled"
private const val KEY_LAST_CHECKED_AT = "last_checked_at"
private const val KEY_LAST_RESULT = "last_result"
private const val UPDATED_PREFIX = "UPDATED:"

/** Three values in SharedPreferences. Each flow holds its own listener; the prefs keep listeners weakly. */
class RuleUpdateSettingsRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : RuleUpdateSettingsRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun observeIsAutoUpdateEnabled(): Flow<Boolean> = prefs.observe { getBoolean(KEY_AUTO_UPDATE, false) }

        override suspend fun setAutoUpdateEnabled(isEnabled: Boolean) =
            prefs.edit { putBoolean(KEY_AUTO_UPDATE, isEnabled) }

        override fun observeLastCheck(): Flow<RuleUpdateCheck?> =
            prefs.observe {
                val result = getString(KEY_LAST_RESULT, null)?.let(::decodeResult)
                if (result == null || !contains(KEY_LAST_CHECKED_AT)) {
                    null
                } else {
                    RuleUpdateCheck(getLong(KEY_LAST_CHECKED_AT, 0L), result)
                }
            }

        override suspend fun saveLastCheck(check: RuleUpdateCheck) =
            prefs.edit {
                putLong(KEY_LAST_CHECKED_AT, check.checkedAt)
                putString(KEY_LAST_RESULT, encodeResult(check.result))
            }
    }

/** Emits the current value first, then again on each change. The two last-check keys land in one emission. */
private fun <T> SharedPreferences.observe(read: SharedPreferences.() -> T): Flow<T> =
    callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ -> trySend(prefs.read()) }
        registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

private fun encodeResult(result: RuleUpdateResult): String =
    when (result) {
        is RuleUpdateResult.Updated -> "$UPDATED_PREFIX${result.count}"
        RuleUpdateResult.UpToDate -> "UP_TO_DATE"
        RuleUpdateResult.Network -> "NETWORK"
        RuleUpdateResult.Invalid -> "INVALID"
        RuleUpdateResult.Error -> "ERROR"
    }

/** Null for a value this version does not know, so a bad store reads as "no check yet". */
private fun decodeResult(stored: String): RuleUpdateResult? =
    when {
        stored == "UP_TO_DATE" -> RuleUpdateResult.UpToDate
        stored == "NETWORK" -> RuleUpdateResult.Network
        stored == "INVALID" -> RuleUpdateResult.Invalid
        stored == "ERROR" -> RuleUpdateResult.Error
        else -> stored.removePrefixOrNull(UPDATED_PREFIX)?.toIntOrNull()?.let(RuleUpdateResult::Updated)
    }

private fun String.removePrefixOrNull(prefix: String): String? = if (startsWith(prefix)) removePrefix(prefix) else null
