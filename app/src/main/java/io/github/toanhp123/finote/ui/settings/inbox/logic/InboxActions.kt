package io.github.toanhp123.finote.ui.settings.inbox.logic

import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.service.FAIL_NO_RULE_MATCHED

/**
 * What the user may do with one item. A keyword-hidden item is `IGNORED_SPAM`, so booking would be refused: it can
 * only be restored. A settlement failure was read by a rule, so it is not an ad. Only `no_rule_matched` offers the ad.
 */
data class InboxActions(
    val isBookable: Boolean,
    val isDismissible: Boolean,
    val isAdCandidate: Boolean,
    val isRestorable: Boolean,
)

fun inboxActionsOf(item: FailedInboxItem): InboxActions =
    if (item.isKeywordHidden) {
        InboxActions(isBookable = false, isDismissible = false, isAdCandidate = false, isRestorable = true)
    } else {
        InboxActions(
            isBookable = true,
            isDismissible = true,
            isAdCandidate = item.reason == FAIL_NO_RULE_MATCHED,
            isRestorable = false,
        )
    }
