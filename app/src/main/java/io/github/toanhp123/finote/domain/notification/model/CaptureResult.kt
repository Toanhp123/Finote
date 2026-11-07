package io.github.toanhp123.finote.domain.notification.model

sealed interface CaptureResult {
    /** Stored as `PENDING`. */
    data object Stored : CaptureResult

    /** Stored as `IGNORED_SPAM`, metadata only. [reason] is `keyword:<word>` or `channel:<id>`. */
    data class Ignored(
        val reason: String,
    ) : CaptureResult

    /** The same notification is already stored. */
    data object Duplicate : CaptureResult

    /** No rule for this package, nothing stored. */
    data object NotTracked : CaptureResult
}
