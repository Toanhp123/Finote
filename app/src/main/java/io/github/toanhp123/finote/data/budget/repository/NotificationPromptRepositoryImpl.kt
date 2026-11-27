package io.github.toanhp123.finote.data.budget.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.toanhp123.finote.domain.budget.repository.NotificationPromptRepository
import javax.inject.Inject

private const val PREFS_NAME = "budget_prompt"
private const val KEY_ASKED = "asked"

/** One boolean in SharedPreferences, like `SetupRepositoryImpl`. */
class NotificationPromptRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : NotificationPromptRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun wasAsked(): Boolean = prefs.getBoolean(KEY_ASKED, false)

        override fun markAsked() = prefs.edit { putBoolean(KEY_ASKED, true) }
    }
