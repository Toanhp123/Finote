package io.github.toanhp123.finote.ui.overview.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.report.model.BucketTotal
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.ui.common.component.display.ExpandChevron
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.display.FinoteSection
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.format.moneyOf
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.overview.logic.positiveTotal
import io.github.toanhp123.finote.ui.overview.logic.share

private const val TOP_BUCKETS = 5
private const val PERCENT_BASE = 100f

/**
 * Spending by bucket. The top [TOP_BUCKETS] show; a row under them opens the rest in place ([isExpanded]).
 * With [hasShares] each row carries its share and a bar, both measured against the positive buckets.
 */
@Composable
fun BucketList(
    buckets: List<BucketTotal>,
    categoriesById: Map<String, Category>,
    hasShares: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onBucketClick: (ReportFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = positiveTotal(buckets)
    val shown = if (isExpanded) buckets else buckets.take(TOP_BUCKETS)
    FinoteSection(title = stringResource(R.string.overview_section_buckets), modifier = modifier) {
        if (buckets.isEmpty()) {
            Text(
                text = stringResource(R.string.overview_buckets_empty),
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodyMedium,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
                shown.forEach { bucket ->
                    BucketRow(
                        bucket = bucket,
                        category = (bucket.filter as? ReportFilter.ByCategory)?.let { categoriesById[it.categoryId] },
                        share = if (hasShares) share(bucket.amount, total) else null,
                        hasBar = hasShares,
                        onClick = { onBucketClick(bucket.filter) },
                    )
                }
                if (buckets.size > TOP_BUCKETS) {
                    FinoteListRow(
                        title =
                            if (isExpanded) {
                                stringResource(R.string.action_collapse)
                            } else {
                                stringResource(R.string.overview_buckets_view_all, buckets.size - TOP_BUCKETS)
                            },
                        trailing = { ExpandChevron(isExpanded = isExpanded) },
                        onClick = onToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun BucketRow(
    bucket: BucketTotal,
    category: Category?,
    share: Int?,
    hasBar: Boolean,
    onClick: () -> Unit,
) {
    val look = bucketLook(bucket.filter, category)
    Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
        FinoteListRow(
            title = look.title,
            leading = { CategoryIcon(iconRes = look.iconRes, colorArgb = look.colorArgb, contentDescription = null) },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    val (amount, kind) = moneyOf(bucket.amount)
                    MoneyText(amount = amount, kind = kind)
                    if (share != null) {
                        Text(
                            text = stringResource(R.string.overview_share, share),
                            color = FinoteTheme.scheme.onSurfaceVariant,
                            style = FinoteTheme.typography.material.labelSmall,
                        )
                    }
                }
            },
            onClick = onClick,
        )
        if (hasBar) ShareBar(barFraction = (share ?: 0) / PERCENT_BASE)
    }
}

// A negative bucket has no share, so its bar is the empty track.
@Composable
private fun ShareBar(barFraction: Float) {
    val scheme = FinoteTheme.scheme
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(FinoteTheme.sizes.barHeight)
                .clip(FinoteTheme.shapes.full)
                .background(scheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth(barFraction).fillMaxHeight().background(scheme.primary))
    }
}

private class BucketLook(
    val title: String,
    @DrawableRes val iconRes: Int,
    val colorArgb: Int,
)

// A category missing from the list reads as uncategorized, like the calculator's own fallback.
@Composable
private fun bucketLook(
    filter: ReportFilter,
    category: Category?,
): BucketLook {
    val muted = FinoteTheme.scheme.onSurfaceVariant.toArgb()
    return when {
        category != null -> {
            BucketLook(displayName(category.name, category.nameKey), iconRes(category.icon), category.color)
        }

        filter is ReportFilter.TransferFees -> {
            BucketLook(stringResource(R.string.overview_bucket_fees), R.drawable.ic_swap_horiz, muted)
        }

        else -> {
            BucketLook(stringResource(R.string.transactions_uncategorized), R.drawable.ic_more_horiz, muted)
        }
    }
}
