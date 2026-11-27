package io.github.toanhp123.finote.work

import io.github.toanhp123.finote.domain.budget.usecase.CheckBudgetAlertsUseCase
import io.github.toanhp123.finote.domain.budget.usecase.ObserveDataChangesUseCase
import io.github.toanhp123.finote.domain.common.ApplicationScope
import io.github.toanhp123.finote.domain.common.StorageException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Runs the budget alert check after every database change, for the life of the process. The constructor takes only
 * providers: the database module builds it before the database exists, and [start] resolves them on [scope].
 */
@Singleton
class BudgetAlertWatcher
    @Inject
    constructor(
        private val changes: Provider<ObserveDataChangesUseCase>,
        private val check: Provider<CheckBudgetAlertsUseCase>,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val isStarted = AtomicBoolean(false)

        /** Idempotent. Returns at once: nothing is resolved on the caller's thread. */
        fun start() {
            if (!isStarted.compareAndSet(false, true)) return
            scope.launch {
                try {
                    val check = check.get()
                    // A burst of commits runs one check while the previous one is still working.
                    changes.get()().conflate().collect { runCheck(check) }
                } catch (_: StorageException) {
                    // The change stream failed: alerts stop for this process, the app goes on.
                }
            }
        }

        private suspend fun runCheck(check: CheckBudgetAlertsUseCase) {
            try {
                check()
            } catch (_: StorageException) {
                // The next change runs a fresh check.
            } catch (_: ArithmeticException) {
                // Overflow in the budget math: skip this run, never log the numbers.
            }
        }
    }
