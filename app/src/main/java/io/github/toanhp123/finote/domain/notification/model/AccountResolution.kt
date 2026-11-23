package io.github.toanhp123.finote.domain.notification.model

internal sealed interface AccountResolution {
    data class Resolved(
        val target: AccountTarget,
    ) : AccountResolution

    data class Rejected(
        val reason: String,
    ) : AccountResolution

    /** The account is ignored by the user: the notification stays silent. */
    data object Ignored : AccountResolution
}
