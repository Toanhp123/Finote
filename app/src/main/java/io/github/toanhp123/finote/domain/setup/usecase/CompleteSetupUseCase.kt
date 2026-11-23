package io.github.toanhp123.finote.domain.setup.usecase

import io.github.toanhp123.finote.domain.setup.repository.SetupRepository
import javax.inject.Inject

class CompleteSetupUseCase
    @Inject
    constructor(
        private val repository: SetupRepository,
    ) {
        /** Both "Enable" success and "Later" end the first run, so the setup screen is not shown again. */
        operator fun invoke() = repository.markSetupDone()
    }
