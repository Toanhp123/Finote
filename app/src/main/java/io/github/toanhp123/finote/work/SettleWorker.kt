package io.github.toanhp123.finote.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.toanhp123.finote.domain.notification.usecase.SettlePendingLogsUseCase
import kotlinx.coroutines.CancellationException

/** Settles the `PENDING` logs once. Retries (linear backoff) while logs remain, so a skipped log gets another run. */
@HiltWorker
class SettleWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val settle: SettlePendingLogsUseCase,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            try {
                if (settle()) Result.retry() else Result.success()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Result.retry()
            }
    }
