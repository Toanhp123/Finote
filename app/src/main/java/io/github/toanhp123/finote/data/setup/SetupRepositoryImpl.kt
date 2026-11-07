package io.github.toanhp123.finote.data.setup

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.toanhp123.finote.domain.setup.repository.SetupRepository
import javax.inject.Inject

private const val PREFS_NAME = "app_setup"
private const val KEY_SETUP_DONE = "setup_done"

/** One boolean in SharedPreferences. Not worth a DataStore, and the caller needs the answer synchronously. */
class SetupRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SetupRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun isSetupDone(): Boolean = prefs.getBoolean(KEY_SETUP_DONE, false)

        override fun markSetupDone() = prefs.edit { putBoolean(KEY_SETUP_DONE, true) }
    }
