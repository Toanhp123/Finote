package io.github.toanhp123.finote.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import io.github.toanhp123.finote.domain.notification.usecase.UpdateParserRulesUseCase

/**
 * Runs one rule update. The use case records the outcome; this class only maps it to a WorkManager result.
 * A periodic run retries transient failures, a manual run fails at once so the UI can show the error.
 */
@HiltWorker
class RuleUpdateWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val updateRules: UpdateParserRulesUseCase,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val isManual = inputData.getBoolean(KEY_IS_MANUAL, false)
            return when (updateRules()) {
                is RuleUpdateResult.Updated, RuleUpdateResult.UpToDate -> Result.success()
                RuleUpdateResult.Invalid -> Result.failure()
                RuleUpdateResult.Network, RuleUpdateResult.Error -> if (isManual) Result.failure() else Result.retry()
            }
        }

        companion object {
            const val KEY_IS_MANUAL = "isManual"
        }
    }
