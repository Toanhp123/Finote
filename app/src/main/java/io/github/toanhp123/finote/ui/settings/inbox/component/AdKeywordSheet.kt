package io.github.toanhp123.finote.ui.settings.inbox.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.notification.model.AdKeywordRefusal
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteTextField
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.rememberLastNonNull
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.inbox.AdSheet
import io.github.toanhp123.finote.ui.settings.inbox.FailedInboxIntent

/**
 * The "this is an ad" sheet ([sheet] null hides it). The field starts with the suggested keyword. Under it shows why
 * the phrase is refused, or how many other items it would hide too. Save is off while a refusal shows.
 */
@Composable
fun AdKeywordSheet(
    sheet: AdSheet?,
    onIntent: (FailedInboxIntent) -> Unit,
) {
    // The sheet keeps its content while it slides out.
    val shown = rememberLastNonNull(sheet)
    FinoteBottomSheet(
        isVisible = sheet != null,
        onDismiss = { onIntent(FailedInboxIntent.AdSheetDismissed) },
    ) {
        shown?.let { AdKeywordContent(it, onIntent) }
    }
}

@Composable
private fun ColumnScope.AdKeywordContent(
    sheet: AdSheet,
    onIntent: (FailedInboxIntent) -> Unit,
) {
    val spacing = FinoteTheme.spacing
    val refusal = sheet.preview?.refusal
    Text(
        text = stringResource(R.string.inbox_ad_title),
        modifier = Modifier.padding(bottom = spacing.md).semantics { heading() },
        color = FinoteTheme.scheme.onSurface,
        style = FinoteTheme.typography.material.titleLarge,
    )
    FinoteTextField(
        value = sheet.keyword,
        onValueChange = { onIntent(FailedInboxIntent.AdKeywordChanged(it)) },
        label = stringResource(R.string.inbox_ad_field),
        errorText = refusal?.let { stringResource(it.messageRes()) },
    )
    if (refusal == null) sheet.preview?.let { PreviewLine(it.otherHiddenCount) }
    FinoteButton(
        text = stringResource(R.string.inbox_ad_save),
        onClick = { onIntent(FailedInboxIntent.AdSaveClicked) },
        modifier = Modifier.fillMaxWidth().padding(top = spacing.lg),
        enabled = refusal == null && sheet.keyword.isNotBlank(),
    )
}

@Composable
private fun PreviewLine(count: Int) {
    Text(
        text =
            if (count == 0) {
                stringResource(R.string.inbox_ad_preview_none)
            } else {
                pluralStringResource(R.plurals.inbox_ad_preview, count, count)
            },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = FinoteTheme.scheme.onSurfaceVariant,
        style = FinoteTheme.typography.material.bodyMedium,
    )
}

@StringRes
private fun AdKeywordRefusal.messageRes(): Int =
    when (this) {
        AdKeywordRefusal.TOO_SHORT -> R.string.inbox_ad_refusal_too_short
        AdKeywordRefusal.NOT_IN_SELECTED -> R.string.inbox_ad_refusal_not_in_selected
        AdKeywordRefusal.BANK_NAME -> R.string.inbox_ad_refusal_bank_name
        AdKeywordRefusal.IN_REAL_TRANSACTION -> R.string.inbox_ad_refusal_in_real_transaction
        AdKeywordRefusal.IN_OTHER_MONEY_ITEM -> R.string.inbox_ad_refusal_in_other_money_item
        AdKeywordRefusal.DUPLICATE -> R.string.inbox_ad_refusal_duplicate
    }
