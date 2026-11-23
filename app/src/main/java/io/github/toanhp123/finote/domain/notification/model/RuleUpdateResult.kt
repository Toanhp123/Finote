package io.github.toanhp123.finote.domain.notification.model

/** Outcome of one rule update run. */
sealed interface RuleUpdateResult {
    /** [count] rules were written, not rows re-parsed. */
    data class Updated(
        val count: Int,
    ) : RuleUpdateResult

    data object UpToDate : RuleUpdateResult

    data object Network : RuleUpdateResult

    data object Invalid : RuleUpdateResult

    data object Error : RuleUpdateResult
}

/** The last run: when it finished ([checkedAt], epoch millis) and how it ended. */
data class RuleUpdateCheck(
    val checkedAt: Long,
    val result: RuleUpdateResult,
)
