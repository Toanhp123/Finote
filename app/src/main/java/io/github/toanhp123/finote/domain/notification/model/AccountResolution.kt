package io.github.toanhp123.finote.domain.notification.model

internal sealed interface AccountResolution {
    data class Resolved(
        val target: AccountTarget,
    ) : AccountResolution

    data class Rejected(
        val reason: String,
    ) : AccountResolution
}
