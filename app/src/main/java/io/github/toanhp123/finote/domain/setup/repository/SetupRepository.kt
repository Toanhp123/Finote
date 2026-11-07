package io.github.toanhp123.finote.domain.setup.repository

/** Whether the first-run setup screen has been completed or skipped. Both calls are synchronous. */
interface SetupRepository {
    fun isSetupDone(): Boolean

    fun markSetupDone()
}
