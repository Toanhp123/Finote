package io.github.toanhp123.finote.ui.settings.inbox

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.notification.model.AdKeywordPreview
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.service.USER_KEYWORD_REASON_PREFIX
import io.github.toanhp123.finote.domain.notification.service.suggestAdKeyword
import io.github.toanhp123.finote.domain.notification.usecase.DeleteAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.DismissInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.LearnAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.MarkInboxSeenUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveAdKeywordsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveFailedInboxUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.domain.notification.usecase.PreviewAdKeywordUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.settings.inbox.logic.inboxActionsOf
import io.github.toanhp123.finote.ui.settings.logic.summarizeBanks
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FailedInboxViewModel
    @Suppress("LongParameterList") // why: Hilt injects one collaborator per use case; grouping them hides the deps
    @Inject
    constructor(
        observeInbox: ObserveFailedInboxUseCase,
        observeRules: ObserveParserRulesUseCase,
        private val observeKeywords: ObserveAdKeywordsUseCase,
        private val markSeen: MarkInboxSeenUseCase,
        private val dismissItem: DismissInboxItemUseCase,
        private val deleteKeyword: DeleteAdKeywordUseCase,
        private val previewKeyword: PreviewAdKeywordUseCase,
        private val learnKeyword: LearnAdKeywordUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<FailedInboxState, FailedInboxIntent, FailedInboxEffect>(FailedInboxState(), isDebuggable) {
        private var previewJob: Job? = null

        init {
            observeInbox()
                .onEach { items ->
                    setState { copy(items = items, isLoaded = true) }
                    // Every list while the screen is open: an item that arrives now is on screen, so it is seen.
                    markSeen(items.filterNot { it.isHidden }.map { it.id }.toSet())
                }.catchStorage {
                    // shortcut: a storage error ends the list and keeps the last one; reopen the screen to retry.
                    // Nothing is logged: the inbox holds notifications.
                }.launchIn(viewModelScope)
            viewModelScope.launch {
                observeRules()
                    .catchStorage {
                        // A bank without a name shows its package. Nothing is logged: rules are data.
                    }.collect { rules ->
                        setState { copy(bankNames = summarizeBanks(rules).associate { it.packageName to it.bankName }) }
                    }
            }
        }

        override fun onIntent(intent: FailedInboxIntent) {
            when (intent) {
                is FailedInboxIntent.ItemClicked -> {
                    withItem(
                        intent.id,
                    ) { if (inboxActionsOf(it).isBookable) sendEffect(FailedInboxEffect.OpenBooking(it.id)) }
                }

                is FailedInboxIntent.AdClicked -> {
                    withItem(intent.id) { if (inboxActionsOf(it).isAdCandidate) openAdSheet(it) }
                }

                is FailedInboxIntent.AdKeywordChanged -> {
                    changeAdKeyword(intent.keyword)
                }

                FailedInboxIntent.AdSaveClicked -> {
                    saveAdKeyword()
                }

                FailedInboxIntent.AdSheetDismissed -> {
                    previewJob?.cancel()
                    setState { copy(adSheet = null) }
                }

                is FailedInboxIntent.AdUndoClicked -> {
                    undoAdKeyword(intent.keywordId)
                }

                is FailedInboxIntent.DismissClicked -> {
                    withItem(intent.id) { if (inboxActionsOf(it).isDismissible) dismiss(it) }
                }

                is FailedInboxIntent.RestoreClicked -> {
                    withItem(intent.id) { if (inboxActionsOf(it).isRestorable) restore(it) }
                }

                FailedInboxIntent.HiddenToggled -> {
                    setState { copy(isHiddenExpanded = !isHiddenExpanded) }
                }

                FailedInboxIntent.BackClicked -> {
                    sendEffect(FailedInboxEffect.Close)
                }
            }
        }

        private fun withItem(
            id: String,
            action: (FailedInboxItem) -> Unit,
        ) {
            state.value.items
                .firstOrNull { it.id == id }
                ?.let(action)
        }

        private fun dismiss(item: FailedInboxItem) {
            launchCatching(onStorageError = ::sendActionFailed) {
                if (!dismissItem(item.id, item.logIds)) sendEffect(FailedInboxEffect.ShowAlreadyHandled)
            }
        }

        /** Restoring is deleting the keyword that hid the item; the list follows when the log comes back. */
        private fun restore(item: FailedInboxItem) {
            launchCatching(onStorageError = ::sendActionFailed) {
                val normalized = item.reason.removePrefix(USER_KEYWORD_REASON_PREFIX)
                val keyword =
                    observeKeywords()
                        .first()[item.packageName]
                        .orEmpty()
                        .firstOrNull { it.normalizedKeyword == normalized }
                if (keyword == null || !deleteKeyword(keyword.id)) sendEffect(FailedInboxEffect.ShowAlreadyHandled)
            }
        }

        private fun openAdSheet(item: FailedInboxItem) {
            setState { copy(adSheet = AdSheet(item.id, suggestAdKeyword(item.title))) }
            schedulePreview()
        }

        private fun changeAdKeyword(keyword: String) {
            if (state.value.adSheet == null) return
            setState { copy(adSheet = adSheet?.copy(keyword = keyword)) }
            schedulePreview()
        }

        /** Reads the refusal or the hidden count once typing has paused; a newer keyword drops the older read. */
        private fun schedulePreview() {
            previewJob?.cancel()
            val sheet = state.value.adSheet ?: return
            previewJob =
                launchCatching(
                    // The line under the field stays as it was; save still refuses what it must. Nothing is logged.
                    onStorageError = {},
                ) {
                    delay(PREVIEW_DEBOUNCE_MS)
                    val preview = previewKeyword(sheet.itemId, sheet.keyword)
                    setState {
                        val current = adSheet
                        if (current != null && current.keyword == sheet.keyword) {
                            copy(adSheet = current.copy(preview = preview))
                        } else {
                            this
                        }
                    }
                }
        }

        private fun saveAdKeyword() {
            val sheet = state.value.adSheet ?: return
            if (sheet.preview?.refusal != null) return
            previewJob?.cancel()
            launchCatching(onStorageError = ::sendActionFailed) {
                when (val outcome = learnKeyword(sheet.itemId, sheet.keyword)) {
                    is Outcome.Ok -> {
                        setState { copy(adSheet = null) }
                        sendEffect(FailedInboxEffect.ShowAdLearned(outcome.value))
                    }

                    is Outcome.Err -> {
                        setState { copy(adSheet = adSheet?.copy(preview = AdKeywordPreview(outcome.error, 0))) }
                    }
                }
            }
        }

        private fun undoAdKeyword(keywordId: String) {
            launchCatching(onStorageError = ::sendActionFailed) {
                if (!deleteKeyword(keywordId)) sendEffect(FailedInboxEffect.ShowAlreadyHandled)
            }
        }

        private fun sendActionFailed() = sendEffect(FailedInboxEffect.ShowActionFailed)
    }

private const val PREVIEW_DEBOUNCE_MS = 150L
