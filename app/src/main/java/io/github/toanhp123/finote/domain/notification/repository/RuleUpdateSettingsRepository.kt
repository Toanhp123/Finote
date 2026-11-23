package io.github.toanhp123.finote.domain.notification.repository

import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import kotlinx.coroutines.flow.Flow

interface RuleUpdateSettingsRepository {
    fun observeIsAutoUpdateEnabled(): Flow<Boolean>

    suspend fun setAutoUpdateEnabled(isEnabled: Boolean)

    /** Null until the first run. */
    fun observeLastCheck(): Flow<RuleUpdateCheck?>

    suspend fun saveLastCheck(check: RuleUpdateCheck)
}
