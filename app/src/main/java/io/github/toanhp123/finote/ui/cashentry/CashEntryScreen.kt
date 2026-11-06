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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.component.FinoteButton
import io.github.toanhp123.finote.ui.common.component.FinoteCalendar
import io.github.toanhp123.finote.ui.common.component.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.FinoteSegmented
import io.github.toanhp123.finote.ui.common.component.MoneyText
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import kotlinx.coroutines.delay

// FinoteTheme.motion.standard is a spring with no fixed duration (about 200 ms), so wait a fixed time for the sheet.
private const val SHEET_EXIT_MILLIS = 250L
private const val PAGE_SLIDE_MILLIS = 300
private const val PAGE_FADE_OUT_MILLIS = 90
private const val PAGE_FADE_IN_MILLIS = 210
private const val FALLBACK_HEIGHT_FRACTION = 0.6f
private const val PAGE_SLIDE_DP = 30f
private val TypeOptions = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

@Composable
fun CashEntryRoute(
    viewModel: CashEntryViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isVisible by remember { mutableStateOf(true) }
    // A user dismiss has already moved the sheet away, so there is nothing to wait for.
    var isDismissedByUser by remember { mutableStateOf(false) }
    val currentOnClose by rememberUpdatedState(onClose)
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            CashEntryEffect.Close -> isVisible = false
        }
    }
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            if (!isDismissedByUser) delay(SHEET_EXIT_MILLIS)
            currentOnClose()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val closePage = {
        keyboard?.hide()
        viewModel.onIntent(CashEntryIntent.PageClosed)
    }
    FinoteBottomSheet(
        isVisible = isVisible,
        onDismiss = {
            isDismissedByUser = true
            viewModel.onIntent(CashEntryIntent.Dismissed)
        },
    ) {
        BackHandler(enabled = state.page != CashEntryPage.KEYPAD, onBack = closePage)
        CashEntrySheet(state = state, onIntent = viewModel::onIntent, onPageClose = closePage)
    }
}

/**
 * The content inside the sheet. It has no ViewModel, so previews and screenshot tests can draw it.
 * Every page is as tall as the keypad page, so the sheet keeps its size when the page changes.
 */
@Composable
fun CashEntrySheet(
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
    val slidePx = with(density) { Dp(PAGE_SLIDE_DP).roundToPx() }
    val easing = FinoteTheme.motion.emphasizedDecelerate
    AnimatedContent(
        targetState = state.page,
        transitionSpec = {
            // Shared axis X: the old page fades out first, then the new one fades in, so they never overlap.
            val direction = if (targetState == CashEntryPage.KEYPAD) -1 else 1
            val slide = tween<IntOffset>(PAGE_SLIDE_MILLIS, easing = easing)
            val enter =
                slideInHorizontally(slide) { direction * slidePx } +
                    fadeIn(tween(PAGE_FADE_IN_MILLIS, delayMillis = PAGE_FADE_OUT_MILLIS))
            val exit =
                slideOutHorizontally(slide) { -direction * slidePx } + fadeOut(tween(PAGE_FADE_OUT_MILLIS))
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
private fun pageTitle(page: CashEntryPage): String =
    stringResource(
        when (page) {
            CashEntryPage.ACCOUNT -> R.string.cash_entry_account
            CashEntryPage.CATEGORY -> R.string.cash_entry_category
            CashEntryPage.DATE -> R.string.cash_entry_date
            CashEntryPage.NOTE -> R.string.cash_entry_note
            CashEntryPage.KEYPAD -> error("The keypad page has no title")
        },
    )

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
            NotePage(
                note = state.note,
                onNoteChange = { onIntent(CashEntryIntent.NoteChanged(it)) },
                onDone = onPageClose,
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

/** A page with a header that slides with it: a back button, a centered title, then [content] in the rest. */
@Composable
private fun PickerPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = FinoteTheme.sizes.touchTarget)) {
            FinoteIconButton(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = title,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = FinoteTheme.sizes.touchTarget)
                        .semantics { heading() },
                color = FinoteTheme.scheme.onSurface,
                style = FinoteTheme.typography.material.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun KeypadPage(
    state: CashEntryState,
    onIntent: (CashEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expense = stringResource(R.string.cash_entry_expense)
    val income = stringResource(R.string.cash_entry_income)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        FinoteSegmented(
            options = TypeOptions,
            selected = state.type,
            onSelect = { onIntent(CashEntryIntent.TypeChanged(it)) },
            label = { if (it == TransactionType.INCOME) income else expense },
            modifier = Modifier.fillMaxWidth(),
        )
        AmountHeader(state)
        Selectors(state, onIntent, onDateClick = { onIntent(CashEntryIntent.PageOpened(CashEntryPage.DATE)) })
        CashKeypad(onKey = { onIntent(CashEntryIntent.KeyPressed(it)) })
        FinoteButton(
            text = stringResource(R.string.cash_entry_save),
            onClick = { onIntent(CashEntryIntent.SaveClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isSaveEnabled,
            isLoading = state.isSaving,
        )
    }
}

@Composable
private fun AmountHeader(
    state: CashEntryState,
    modifier: Modifier = Modifier,
) {
    val amount =
        when (val result = state.result) {
            is AmountResult.Ok -> result.amount
            is AmountResult.NotPositive -> result.value
            else -> 0L
        }
    // An unfinished (not positive) result must not look like an amount the user can save.
    val kind =
        when {
            state.result is AmountResult.NotPositive -> MoneyKind.Neutral
            state.type == TransactionType.INCOME -> MoneyKind.Income
            else -> MoneyKind.Expense
        }
    val error = errorMessage(state)
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Text(
            text = state.expression,
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
        )
        MoneyText(
            amount = amount,
            kind = kind,
            style = FinoteTheme.typography.amountLarge,
            shrinkToFit = true,
        )
        // Always one line tall, so the keypad does not jump when an error appears.
        Text(
            text = error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = FinoteTheme.colors.expense.color,
            style = FinoteTheme.typography.material.bodySmall,
            minLines = 1,
        )
    }
}

/** Only an invalid calculation or a failed save shows text; a result that is not positive is just unfinished. */
@Composable
private fun errorMessage(state: CashEntryState): String {
    val result = state.result
    val res =
        when {
            result is AmountResult.Invalid && result.reason == InvalidReason.DIVIDE_BY_ZERO -> {
                R.string.cash_entry_error_divide_by_zero
            }

            result is AmountResult.Invalid -> {
                R.string.cash_entry_error_overflow
            }

            state.hasSaveError -> {
                R.string.cash_entry_error_save
            }

            else -> {
                return ""
            }
        }
    return stringResource(res)
}
