package io.github.toanhp123.finote.ui.budget.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.budget.model.BudgetState
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.ui.budget.logic.leftOf
import io.github.toanhp123.finote.ui.budget.logic.overByOf
import io.github.toanhp123.finote.ui.budget.logic.perDayOf
import io.github.toanhp123.finote.ui.budget.logic.wordOf
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteProgressBar
import io.github.toanhp123.finote.ui.common.component.display.ProgressTone
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate

private const val PERCENT_BASE = 100f

/**
 * One budget in a card. The whole text area opens its rows; [onEdit] (null in a past month) is a separate button.
 * With [today] the card also shows what is left per day. [leading] is the icon in front of [title].
 */
@Composable
fun BudgetRow(
    status: BudgetStatus,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    today: LocalDate? = null,
    onEdit: (() -> Unit)? = null,
) {
    FinoteCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick),
                verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = FinoteTheme.sizes.touchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
                ) {
                    leading?.invoke()
                    Text(
                        text = title,
                        color = FinoteTheme.scheme.onSurface,
                        style = FinoteTheme.typography.material.titleMedium,
                    )
                }
                BudgetFigures(status = status, today = today)
            }
            if (onEdit != null) {
                FinoteIconButton(
                    iconRes = R.drawable.ic_edit,
                    contentDescription = stringResource(R.string.budget_edit_cd, title),
                    onClick = onEdit,
                )
            }
        }
    }
}

/**
 * Spent over available, the bar, and the state in words. Every amount is its own [MoneyText] in a [FlowRow], so a
 * large font wraps the line instead of clipping a number.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetFigures(
    status: BudgetStatus,
    today: LocalDate?,
) {
    val tone = toneOf(status.state)
    val word = wordOf(status)
    val overBy = overByOf(status)
    val left = leftOf(status)
    val perDay = today?.let { perDayOf(status, it) }
    val gap = Arrangement.spacedBy(FinoteTheme.spacing.xs)
    Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
        FlowRow(
            horizontalArrangement = gap,
            verticalArrangement = gap,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Label(stringResource(R.string.budget_spent))
            MoneyText(amount = status.spent, kind = MoneyKind.Neutral)
            Label("/")
            MoneyText(amount = status.available, kind = MoneyKind.Neutral)
        }
        FinoteProgressBar(fraction = fractionOf(status), tone = tone)
        FlowRow(
            horizontalArrangement = gap,
            verticalArrangement = gap,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            status.percent?.let { Label(stringResource(R.string.overview_share, it)) }
            if (word != null) {
                Text(
                    text = stringResource(word),
                    color = toneColor(tone),
                    style = FinoteTheme.typography.material.labelLarge,
                )
            }
            if (overBy != null) {
                MoneyText(
                    amount = overBy,
                    kind = MoneyKind.Neutral,
                    style = FinoteTheme.typography.amountSmall.copy(color = toneColor(tone)),
                )
            }
            if (left != null) {
                Label(stringResource(R.string.budget_left))
                MoneyText(amount = left, kind = MoneyKind.Neutral)
            }
            if (perDay != null) {
                MoneyText(amount = perDay, kind = MoneyKind.Neutral)
                Label(stringResource(R.string.budget_per_day))
            }
        }
        if (status.budget.isRollover && status.carry != 0L) CarryLine(status.carry)
    }
}

// A carry in is income-colored with "+", a carry out is expense-colored with the minus sign.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarryLine(carry: Long) {
    val gap = Arrangement.spacedBy(FinoteTheme.spacing.xs)
    FlowRow(
        horizontalArrangement = gap,
        verticalArrangement = gap,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Label(stringResource(R.string.budget_carry))
        if (carry > 0) {
            MoneyText(amount = carry, kind = MoneyKind.Income)
        } else {
            MoneyText(amount = Math.negateExact(carry), kind = MoneyKind.Expense)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        color = FinoteTheme.scheme.onSurfaceVariant,
        style = FinoteTheme.typography.material.bodySmall,
    )
}

// Nothing available means nothing to measure: the bar is full, never empty, in the over tone.
private fun fractionOf(status: BudgetStatus): Float =
    status.percent?.let { (it / PERCENT_BASE).coerceIn(0f, 1f) } ?: if (status.available <= 0) 1f else 0f

private fun toneOf(state: BudgetState): ProgressTone =
    when (state) {
        BudgetState.NORMAL -> ProgressTone.Normal
        BudgetState.WARNING -> ProgressTone.Warning
        BudgetState.OVER -> ProgressTone.Over
    }

@Composable
private fun toneColor(tone: ProgressTone): Color =
    when (tone) {
        ProgressTone.Normal -> FinoteTheme.scheme.onSurfaceVariant
        ProgressTone.Warning -> FinoteTheme.colors.warning.color
        ProgressTone.Over -> FinoteTheme.scheme.error
    }
