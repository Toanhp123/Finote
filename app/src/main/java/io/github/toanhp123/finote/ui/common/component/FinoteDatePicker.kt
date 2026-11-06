package io.github.toanhp123.finote.ui.common.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import kotlinx.coroutines.flow.drop
import java.time.LocalDate

/**
 * Picks one day in a bottom sheet. The picker starts on [selected] each time it opens, and [onConfirm]
 * gets the chosen day; [onDismiss] covers Cancel, a swipe down, a tap outside and Back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinoteDatePicker(
    isVisible: Boolean,
    selected: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalSheet(
        isVisible = isVisible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = FinoteTheme.spacing.lg),
    ) {
        DatePickerContent(selected = selected, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerContent(
    selected: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = selected.toUtcEpochMillis())
    // The picker draws some icons with the content color instead of its own colors, and nothing here sets it.
    CompositionLocalProvider(LocalContentColor provides FinoteTheme.scheme.onSurface) {
        DatePicker(state = state, modifier = Modifier.fillMaxWidth(), colors = finoteDatePickerColors())
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = FinoteTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinoteButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss,
            style = FinoteButtonStyle.Ghost,
        )
        FinoteButton(
            text = stringResource(R.string.action_ok),
            onClick = { onConfirm(state.selectedDateMillis?.utcEpochMillisToLocalDate() ?: selected) },
        )
    }
}

/**
 * A bare calendar with no title, headline, mode toggle or buttons, for a page of its own. It starts on [selected];
 * [onPick] is called when the user taps another day (a tap on the selected day changes nothing).
 * It needs the full width of a phone screen: inside [FinoteBottomSheet] the caller must undo the side padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinoteCalendar(
    selected: LocalDate,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = selected.toUtcEpochMillis())
    val currentOnPick by rememberUpdatedState(onPick)
    LaunchedEffect(state) {
        // drop(1): the initial value is not a tap.
        // shortcut: tapping the already-selected day does not fire, so the user presses back to keep the same day;
        // upgrade (for example a day-click callback) if users complain.
        snapshotFlow { state.selectedDateMillis }.drop(1).collect { millis ->
            if (millis != null) currentOnPick(millis.utcEpochMillisToLocalDate())
        }
    }
    CompositionLocalProvider(LocalContentColor provides FinoteTheme.scheme.onSurface) {
        DatePicker(
            state = state,
            modifier = modifier.fillMaxWidth(),
            title = null,
            headline = null,
            showModeToggle = false,
            colors = finoteDatePickerColors(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun finoteDatePickerColors(): DatePickerColors {
    val scheme = FinoteTheme.scheme
    return DatePickerDefaults.colors(
        containerColor = Color.Transparent,
        titleContentColor = scheme.onSurfaceVariant,
        headlineContentColor = scheme.onSurface,
        weekdayContentColor = scheme.onSurfaceVariant,
        subheadContentColor = scheme.onSurfaceVariant,
        navigationContentColor = scheme.onSurface,
        yearContentColor = scheme.onSurface,
        currentYearContentColor = scheme.primary,
        selectedYearContentColor = scheme.onPrimary,
        selectedYearContainerColor = scheme.primary,
        dayContentColor = scheme.onSurface,
        selectedDayContentColor = scheme.onPrimary,
        selectedDayContainerColor = scheme.primary,
        todayContentColor = scheme.primary,
        todayDateBorderColor = scheme.primary,
    )
}

@FinotePreviews
@Composable
private fun FinoteDatePickerPreview() {
    FinotePreviewSurface {
        FinoteDatePicker(isVisible = true, selected = LocalDate.now(), onConfirm = {}, onDismiss = {})
    }
}
