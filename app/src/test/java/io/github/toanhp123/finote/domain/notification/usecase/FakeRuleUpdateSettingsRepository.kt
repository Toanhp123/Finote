package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.repository.RuleUpdateSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Copies from the real repository: the auto-update flag and the last check are saved and observed.
 * Does not copy: persistence, nor the real default (off); this fake starts with auto-update on.
 */
class FakeRuleUpdateSettingsRepository : RuleUpdateSettingsRepository {
    private val isEnabled = MutableStateFlow(true)
    val lastCheck = MutableStateFlow<RuleUpdateCheck?>(null)

    override fun observeIsAutoUpdateEnabled(): Flow<Boolean> = isEnabled

    override suspend fun setAutoUpdateEnabled(isEnabled: Boolean) {
        this.isEnabled.value = isEnabled
    }

    override fun observeLastCheck(): Flow<RuleUpdateCheck?> = lastCheck

    override suspend fun saveLastCheck(check: RuleUpdateCheck) {
        lastCheck.value = check
    }
}
