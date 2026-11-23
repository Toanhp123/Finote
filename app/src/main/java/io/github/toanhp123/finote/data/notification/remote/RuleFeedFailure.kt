package io.github.toanhp123.finote.data.notification.remote

import io.github.toanhp123.finote.domain.notification.error.RuleFeedError

/** Internal to `remote`: [message] is a short reason code, never feed content, and is never logged. */
internal class RuleFeedFailure(
    val error: RuleFeedError,
    message: String,
) : Exception(message)
