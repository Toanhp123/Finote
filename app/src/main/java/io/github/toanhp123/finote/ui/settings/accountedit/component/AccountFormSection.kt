package io.github.toanhp123.finote.ui.settings.accountedit.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.BankApp
import io.github.toanhp123.finote.ui.common.component.control.FinoteChip
import io.github.toanhp123.finote.ui.common.component.control.FinoteSegmented
import io.github.toanhp123.finote.ui.common.component.control.FinoteSwitchRow
import io.github.toanhp123.finote.ui.common.component.control.FinoteTextField
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.common.theme.color.CategoryPalette
import io.github.toanhp123.finote.ui.settings.accountedit.logic.AccountDraft
import io.github.toanhp123.finote.ui.settings.accountedit.logic.labelRes
import io.github.toanhp123.finote.ui.settings.accountedit.logic.parseStartBalance

private const val NAME_MAX_LENGTH = 40

/** The icons an account may take. A name outside the list is shown as is; picking replaces it. */
private val AccountIcons =
    listOf("payments", "work", "home", "storefront", "shopping_bag", "trending_up", "card_giftcard", "stars")

private val CreatableTypes = listOf(AccountType.BANK, AccountType.WALLET, AccountType.CASH, AccountType.SAVINGS)

/**
 * The fields of an account that the user edits. [stored] is null in create mode. The type picker shows in create mode
 * only; the bank app picker never for cash and as a locked row once a bank app is stored; the start balance field in
 * create mode and while the stored start is still provisional.
 */
@Composable
fun AccountFormSection(
    draft: AccountDraft,
    stored: Account?,
    bankApps: List<BankApp>,
    onDraftChange: (AccountDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.lg)) {
        // A seeded account shows its localized name until the user types; an untouched field keeps the stored name.
        val seeded = stored?.takeIf { it.nameKey != null && draft.name == it.name }
        FinoteTextField(
            value = seeded?.let { displayName(it.name, it.nameKey) } ?: draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = stringResource(R.string.account_edit_name),
            modifier = Modifier.fillMaxWidth(),
            maxLength = NAME_MAX_LENGTH,
        )
        if (stored == null) {
            val typeLabels = CreatableTypes.associateWith { stringResource(it.labelRes()) }
            Labeled(R.string.account_edit_type) {
                FinoteSegmented(
                    options = CreatableTypes,
                    selected = draft.type,
                    onSelect = { onDraftChange(draft.copy(type = it)) },
                    label = { typeLabels.getValue(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Labeled(R.string.account_edit_icon) { IconPicker(draft.icon) { onDraftChange(draft.copy(icon = it)) } }
        Labeled(R.string.account_edit_color) { ColorPicker(draft.color) { onDraftChange(draft.copy(color = it)) } }
        if (draft.type != AccountType.CASH) BankAppPicker(draft, stored, bankApps, onDraftChange)
        if (stored == null || !stored.isInitialized) StartBalanceField(draft, onDraftChange)
        FinoteCard(modifier = Modifier.fillMaxWidth()) {
            FinoteSwitchRow(
                title = stringResource(R.string.account_edit_exclude),
                checked = draft.isExcludedFromNetWorth,
                onCheckedChange = { onDraftChange(draft.copy(isExcludedFromNetWorth = it)) },
            )
        }
    }
}

@Composable
private fun Labeled(
    @StringRes labelRes: Int,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
        Text(
            text = stringResource(labelRes),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        content()
    }
}

@Composable
private fun StartBalanceField(
    draft: AccountDraft,
    onDraftChange: (AccountDraft) -> Unit,
) {
    FinoteTextField(
        value = draft.startBalance?.toString().orEmpty(),
        onValueChange = { onDraftChange(draft.copy(startBalance = parseStartBalance(it))) },
        label = stringResource(R.string.account_edit_start_balance),
        modifier = Modifier.fillMaxWidth(),
        // Empty means unknown only for a bank app account; elsewhere it means 0, so no hint.
        placeholder = if (draft.bankPackageName != null) stringResource(R.string.account_edit_start_hint) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun BankAppPicker(
    draft: AccountDraft,
    stored: Account?,
    bankApps: List<BankApp>,
    onDraftChange: (AccountDraft) -> Unit,
) {
    val lockedPackage = stored?.bankPackageName
    Labeled(R.string.account_edit_bank_app) {
        if (lockedPackage != null) {
            FinoteCard(modifier = Modifier.fillMaxWidth()) {
                FinoteListRow(
                    title = bankApps.firstOrNull { it.packageName == lockedPackage }?.bankName ?: lockedPackage,
                    subtitle = stringResource(R.string.account_edit_bank_locked),
                )
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
                FinoteChip(
                    text = stringResource(R.string.account_edit_bank_none),
                    selected = draft.bankPackageName == null,
                    onClick = { onDraftChange(draft.copy(bankPackageName = null)) },
                )
                bankApps.forEach { app ->
                    FinoteChip(
                        text = app.bankName,
                        selected = draft.bankPackageName == app.packageName,
                        onClick = { onDraftChange(draft.copy(bankPackageName = app.packageName)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPicker(
    selected: String,
    onPick: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
        AccountIcons.forEachIndexed { index, name ->
            val description = stringResource(R.string.account_edit_icon_cd, index + 1)
            val isSelected = name == selected
            val scheme = FinoteTheme.scheme
            Box(
                modifier =
                    Modifier
                        .size(FinoteTheme.sizes.touchTarget)
                        .clip(FinoteTheme.shapes.sm)
                        .background(if (isSelected) scheme.primaryContainer else FinoteTheme.colors.card)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(name) })
                        .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes(name)),
                    contentDescription = null,
                    modifier = Modifier.size(FinoteTheme.sizes.iconMd),
                    tint = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ColorPicker(
    selected: Int,
    onPick: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
        CategoryPalette.forEachIndexed { index, color ->
            val argb = color.toArgb()
            val isSelected = argb == selected
            val description = stringResource(R.string.account_edit_color_cd, index + 1)
            Box(
                modifier =
                    Modifier
                        .size(FinoteTheme.sizes.touchTarget)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(argb) })
                        .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Swatch(color = color, isSelected = isSelected)
            }
        }
    }
}

/** The check and the ring tell the picked color apart without relying on color alone. */
@Composable
private fun Swatch(
    color: Color,
    isSelected: Boolean,
) {
    val ring = if (isSelected) FinoteTheme.scheme.onSurface else color
    Box(
        modifier =
            Modifier
                .size(FinoteTheme.sizes.avatar)
                .clip(FinoteTheme.shapes.full)
                .background(color)
                .border(FinoteTheme.sizes.border * 2, ring, FinoteTheme.shapes.full)
                .padding(FinoteTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(FinoteTheme.sizes.iconSm),
                tint = FinoteTheme.colors.hero.onHero,
            )
        }
    }
}
