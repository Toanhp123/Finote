package io.github.toanhp123.finote.domain.budget

import io.github.toanhp123.finote.domain.budget.repository.NotificationPromptRepository

class FakeNotificationPromptRepository : NotificationPromptRepository {
    var asked = false

    override fun wasAsked(): Boolean = asked

    override fun markAsked() {
        asked = true
    }
}
