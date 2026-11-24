package io.github.toanhp123.finote.ui.settings.inbox

import io.github.toanhp123.finote.domain.notification.model.AdKeywordPreview
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem

/**
 * [items] are every inbox item, newest first; [shown] and [hidden] split them. [bankNames] maps a package to its bank
 * name. [isLoaded] is false until the first list arrives, so the empty state does not flash. The hidden section starts
 * collapsed. [adSheet] is the open "this is an ad" sheet, null when closed. The state holds [FailedInboxItem] itself,
 * so its `toString()` stays free of notification text.
 */
data class FailedInboxState(
    val items: List<FailedInboxItem> = emptyList(),
    val bankNames: Map<String, String> = emptyMap(),
    val isLoaded: Boolean = false,
    val isHiddenExpanded: Boolean = false,
    val adSheet: AdSheet? = null,
) {
    val shown: List<FailedInboxItem> get() = items.filterNot { it.isHidden }
    val hidden: List<FailedInboxItem> get() = items.filter { it.isHidden }
}

/**
 * The "this is an ad" sheet for the item [itemId], with the keyword as typed. [preview] is null until the first read
 * lands; it may lag the text by the debounce. Its `toString()` hides the keyword, which echoes notification text.
 */
data class AdSheet(
    val itemId: String,
    val keyword: String,
    val preview: AdKeywordPreview? = null,
) {
    override fun toString() = "AdSheet(itemId=$itemId)"
}

sealed interface FailedInboxIntent {
    data class ItemClicked(
        val id: String,
    ) : FailedInboxIntent

    data class DismissClicked(
        val id: String,
    ) : FailedInboxIntent

    data class AdClicked(
        val id: String,
    ) : FailedInboxIntent

    data class RestoreClicked(
        val id: String,
    ) : FailedInboxIntent

    data class AdKeywordChanged(
        val keyword: String,
    ) : FailedInboxIntent

    data object AdSaveClicked : FailedInboxIntent

    data object AdSheetDismissed : FailedInboxIntent

    data class AdUndoClicked(
        val keywordId: String,
    ) : FailedInboxIntent

    data object HiddenToggled : FailedInboxIntent

    data object BackClicked : FailedInboxIntent
}

sealed interface FailedInboxEffect {
    /** The booking sheet (the cash entry sheet prefilled from the item) for the item. */
    data class OpenBooking(
        val itemId: String,
    ) : FailedInboxEffect

    /** The ad keyword is saved: offer Undo, which sends [FailedInboxIntent.AdUndoClicked] with [keywordId]. */
    data class ShowAdLearned(
        val keywordId: String,
    ) : FailedInboxEffect

    /** The item changed or was handled while the user looked at it; the list already shows the truth. */
    data object ShowAlreadyHandled : FailedInboxEffect

    data object ShowActionFailed : FailedInboxEffect

    data object Close : FailedInboxEffect
}
