package io.github.toanhp123.finote.ui.overview.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.report.model.MerchantTotal
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.display.FinoteSection
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.format.moneyOf

/** The top merchants of the month, each one opening its report. It draws nothing when there are none. */
@Composable
fun MerchantList(
    merchants: List<MerchantTotal>,
    onMerchantClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (merchants.isEmpty()) return
    FinoteSection(title = stringResource(R.string.overview_section_merchants), modifier = modifier) {
        Column {
            merchants.forEach { merchant ->
                FinoteListRow(
                    title = merchant.name,
                    trailing = {
                        val (amount, kind) = moneyOf(merchant.amount)
                        MoneyText(amount = amount, kind = kind)
                    },
                    onClick = { onMerchantClick(merchant.name) },
                )
            }
        }
    }
}
