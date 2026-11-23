package io.github.toanhp123.finote.ui.common.component.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val INCOME_ARROW = "↑"
private const val EXPENSE_ARROW = "↓"

/** Gradient card with the total [balance] and two chips for [income] and [expense]. */
@Composable
fun BalanceHeroCard(
    label: String,
    balance: Long,
    income: Long,
    expense: Long,
    modifier: Modifier = Modifier,
) {
    val hero = FinoteTheme.colors.hero
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FinoteTheme.shapes.xl)
                .background(FinoteTheme.brushes.hero)
                .padding(FinoteTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
    ) {
        Text(text = label, color = hero.onHero, style = FinoteTheme.typography.material.labelMedium)
        MoneyText(
            amount = balance,
            kind = MoneyKind.Neutral,
            style = FinoteTheme.typography.amountLarge.copy(color = hero.onHero),
            shrinkToFit = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
        ) {
            HeroChip(arrow = INCOME_ARROW, description = stringResource(R.string.balance_income), amount = income)
            HeroChip(arrow = EXPENSE_ARROW, description = stringResource(R.string.balance_expense), amount = expense)
        }
    }
}

@Composable
private fun HeroChip(
    arrow: String,
    description: String,
    amount: Long,
) {
    val hero = FinoteTheme.colors.hero
    Row(
        modifier =
            Modifier
                .clip(FinoteTheme.shapes.full)
                .background(hero.chipContainer)
                .padding(horizontal = FinoteTheme.spacing.md, vertical = FinoteTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The arrow is a glyph. Its spoken form is the label, which a screen reader reads before the amount.
        Text(
            text = arrow,
            modifier = Modifier.semantics { contentDescription = description },
            color = hero.onHero,
            style = FinoteTheme.typography.amountSmall,
        )
        MoneyText(
            amount = amount,
            kind = MoneyKind.Neutral,
            style = FinoteTheme.typography.amountSmall.copy(color = hero.onHero),
        )
    }
}

@FinotePreviews
@Composable
private fun BalanceHeroCardPreview() {
    FinotePreviewSurface {
        BalanceHeroCard(
            label = stringResource(R.string.balance_total),
            balance = 12_450_000,
            income = 15_000_000,
            expense = 2_100_000,
        )
    }
}
