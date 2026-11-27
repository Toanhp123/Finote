package io.github.toanhp123.finote.domain.budget.repository

/** Whether the app has asked for the notification permission after the first saved budget. Read at once, no flow. */
interface NotificationPromptRepository {
    fun wasAsked(): Boolean

    fun markAsked()
}
