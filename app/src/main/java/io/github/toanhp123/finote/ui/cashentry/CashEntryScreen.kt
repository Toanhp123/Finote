package io.github.toanhp123.finote.ui.cashentry

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.cashentry.component.AccountList
import io.github.toanhp123.finote.ui.cashentry.component.CategoryList
import io.github.toanhp123.finote.ui.cashentry.component.DeleteConfirmDialog
import io.github.toanhp123.finote.ui.cashentry.component.KeypadPage
import io.github.toanhp123.finote.ui.cashentry.component.PickerPage
import io.github.toanhp123.finote.ui.cashentry.component.TextPage
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.display.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteCalendar
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val FALLBACK_HEIGHT_FRACTION = 0.6f

@Composable
fun CashEntryRoute(
    viewModel: CashEntryViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val closePage = {
        keyboard?.hide()
        viewModel.onIntent(CashEntryIntent.PageClosed)
    }
    // The row cannot be edited and the sheet was never shown: leave at once, without the exit animation.
    ObserveEffects(viewModel.effects) { if (it is CashEntryEffect.Close) onClose() }
    FinoteBottomSheet(
        // Edit mode stays hidden until the row filled the form, so the create defaults never flash.
        isVisible = state.isVisible && !state.isLoading,
        onDismiss = { viewModel.onIntent(CashEntryIntent.Dismissed) },
        // A sheet hidden from the first composition also reports "settled hidden"; that is not a close.
        onDismissComplete = { if (!state.isLoading) onClose() },
    ) {
        BackHandler(enabled = state.page != CashEntryPage.KEYPAD, onBack = closePage)
        CashEntryScreen(state = state, onIntent = viewModel::onIntent, onPageClose = closePage)
    }
    DeleteConfirmDialog(isVisible = state.isDeleteConfirmOpen, onIntent = viewModel::onIntent)
}

/**
 * The content inside the sheet. It has no ViewModel, so previews and screenshot tests can draw it.
 * Every page is as tall as the keypad page, so the sheet keeps its size when the page changes.
 */
@Composable
fun CashEntryScreen(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    onPageClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saved as px, so the height is known again after a restore, once the keypad page was measured.
    var keypadHeightPx by rememberSaveable { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Before the keypad is measured (for example a restore on a picker page) use a share of the screen.
    val pageHeight =
        if (keypadHeightPx > 0) {
            with(density) { keypadHeightPx.toDp() }
        } else {
            with(density) { (LocalWindowInfo.current.containerSize.height * FALLBACK_HEIGHT_FRACTION).toDp() }
        }
    val motion = FinoteTheme.motion
    val slidePx = with(density) { motion.pageSlideOffset.roundToPx() }
    AnimatedContent(
        targetState = state.page,
        transitionSpec = {
            // Shared axis X: the old page fades out first, then the new one fades in, so they never overlap.
            val direction = if (targetState == CashEntryPage.KEYPAD) -1 else 1
            val slide = tween<IntOffset>(motion.pageSlideMillis, easing = motion.emphasizedDecelerate)
            val enter =
                slideInHorizontally(slide) { direction * slidePx } +
                    fadeIn(tween(motion.pageFadeInMillis, delayMillis = motion.pageFadeOutMillis))
            val exit =
                slideOutHorizontally(slide) { -direction * slidePx } + fadeOut(tween(motion.pageFadeOutMillis))
            (enter togetherWith exit).using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        label = "CashEntryPageTransition",
        modifier = modifier,
    ) { page ->
        if (page == CashEntryPage.KEYPAD) {
            KeypadPage(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.onSizeChanged { keypadHeightPx = it.height },
            )
        } else {
            PickerPage(title = pageTitle(page), onBack = onPageClose, modifier = Modifier.height(pageHeight)) {
                PageContent(page = page, state = state, onIntent = onIntent, onPageClose = onPageClose)
            }
        }
    }
}

@Composable
private fun PageContent(
    page: CashEntryPage,
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    onPageClose: () -> Unit,
) {
    when (page) {
        CashEntryPage.ACCOUNT -> {
            AccountList(
                accounts = state.accounts,
                selectedId = state.selectedAccountId,
                onPick = { onIntent(CashEntryIntent.AccountPicked(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        CashEntryPage.DESTINATION -> {
            if (state.destinationChoices.isEmpty()) {
                // Fewer than two usable accounts: say why, and Save stays disabled.
                FinoteEmptyState(
                    iconRes = R.drawable.ic_swap_horiz,
                    title = stringResource(R.string.cash_entry_no_destination),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AccountList(
                    accounts = state.destinationChoices,
                    selectedId = state.destinationId,
                    onPick = { onIntent(CashEntryIntent.DestinationPicked(it)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        CashEntryPage.FEE -> {
            // One digits-only field on the system number keyboard. The ViewModel keeps the digits and the cap.
            TextPage(
                value = if (state.fee > 0) state.fee.toString() else "",
                onValueChange = { onIntent(CashEntryIntent.FeeChanged(it)) },
                label = stringResource(R.string.cash_entry_fee),
                onDone = onPageClose,
                keyboardType = KeyboardType.Number,
            )
        }

        CashEntryPage.CATEGORY -> {
            CategoryList(
                categories = state.categories,
                selectedId = state.selectedCategoryIds[state.type],
                onPick = { onIntent(CashEntryIntent.CategoryPicked(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        CashEntryPage.DATE -> {
            FinoteCalendar(
                selected = state.date,
                onPick = { onIntent(CashEntryIntent.DatePicked(it)) },
                modifier = Modifier.escapeSheetPadding(FinoteTheme.spacing.lg),
            )
        }

        CashEntryPage.NOTE -> {
            // The system keyboard is up here, so the calculator keypad is not shown.
            TextPage(
                value = state.note,
                onValueChange = { onIntent(CashEntryIntent.NoteChanged(it)) },
                label = stringResource(R.string.cash_entry_note),
                onDone = onPageClose,
                maxLength = CashEntryViewModel.MAX_NOTE_LENGTH,
            )
        }

        CashEntryPage.KEYPAD -> {
            error("The keypad page is not a picker page")
        }
    }
}

/** Is as wide as the sheet content plus [sidePadding] on each side, so a full-width child can fill the screen. */
private fun Modifier.escapeSheetPadding(sidePadding: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = sidePadding.roundToPx()
        val width = constraints.maxWidth + 2 * extra
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
    }

@Composable
private fun pageTitle(page: CashEntryPage): String =
    stringResource(
        when (page) {
            CashEntryPage.ACCOUNT -> R.string.cash_entry_account
            CashEntryPage.DESTINATION -> R.string.cash_entry_to_account
            CashEntryPage.CATEGORY -> R.string.cash_entry_category
            CashEntryPage.FEE -> R.string.cash_entry_fee
            CashEntryPage.DATE -> R.string.cash_entry_date
            CashEntryPage.NOTE -> R.string.cash_entry_note
            CashEntryPage.KEYPAD -> error("The keypad page has no title")
        },
    )
