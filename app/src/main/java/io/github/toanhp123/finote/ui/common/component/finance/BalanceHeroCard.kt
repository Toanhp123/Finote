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
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val INCOME_ARROW = "↑"

/**
 * Gradient card with one big [amount] under [label] and two chips: [income] and [net]. Every amount is neutral, because
 * red or green text has no contrast on the gradient. The words carry the meaning: pass the absolute value of an amount
 * that would read as a refund and say so in [label]. A negative [net] gets its minus sign from the formatter.
 */
@Composable
fun BalanceHeroCard(
    label: String,
    amount: Long,
    income: Long,
    net: Long,
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
            amount = amount,
            kind = MoneyKind.Neutral,
            style = FinoteTheme.typography.amountLarge.copy(color = hero.onHero),
            shrinkToFit = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
        ) {
            HeroChip(label = stringResource(R.string.balance_income), amount = income, arrow = INCOME_ARROW)
            HeroChip(label = stringResource(R.string.balance_net), amount = net)
        }
    }
}

@Composable
private fun HeroChip(
    label: String,
    amount: Long,
    arrow: String? = null,
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
        // The arrow is a glyph; the label next to it says the same in words.
        if (arrow != null) {
            Text(
                text = arrow,
                modifier = Modifier.clearAndSetSemantics {},
                color = hero.onHero,
                style = FinoteTheme.typography.amountSmall,
            )
        }
        Text(text = label, color = hero.onHero, style = FinoteTheme.typography.material.labelMedium)
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
            label = stringResource(R.string.overview_spent),
            amount = 2_100_000,
            income = 15_000_000,
            net = 12_900_000,
        )
    }
}
