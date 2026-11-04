package io.github.toanhp123.finote.ui.cashentry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.transaction.CalcKey
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private val KeyRows =
    listOf(
        listOf(CalcKey.D7, CalcKey.D8, CalcKey.D9, CalcKey.DIVIDE),
        listOf(CalcKey.D4, CalcKey.D5, CalcKey.D6, CalcKey.TIMES),
        listOf(CalcKey.D1, CalcKey.D2, CalcKey.D3, CalcKey.MINUS),
        listOf(CalcKey.TRIPLE_ZERO, CalcKey.D0, CalcKey.BACKSPACE, CalcKey.PLUS),
    )

private val OperatorKeys = setOf(CalcKey.DIVIDE, CalcKey.TIMES, CalcKey.MINUS, CalcKey.PLUS)

/** The 4x4 calculator grid. Every key is at least a touch target tall. */
@Composable
fun CashKeypad(
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
        KeyRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
            ) {
                row.forEach { key -> CashKey(key = key, onClick = { onKey(key) }, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CashKey(
    key: CalcKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = FinoteTheme.scheme
    val isOperator = key in OperatorKeys
    val description = if (key == CalcKey.BACKSPACE) stringResource(R.string.cash_entry_backspace) else null
    Box(
        modifier =
            modifier
                .heightIn(min = FinoteTheme.sizes.touchTarget)
                .clip(FinoteTheme.shapes.md)
                .background(if (isOperator) scheme.secondaryContainer else FinoteTheme.colors.card)
                .border(
                    FinoteTheme.sizes.border,
                    if (isOperator) scheme.secondaryContainer else FinoteTheme.colors.glass.stroke,
                    FinoteTheme.shapes.md,
                ).clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = FinoteTheme.spacing.xs, vertical = FinoteTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label(),
            modifier = Modifier.semantics { if (description != null) contentDescription = description },
            color = if (isOperator) scheme.onSecondaryContainer else scheme.onSurface,
            style = FinoteTheme.typography.material.titleMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// U+2212 minus, U+00D7 times, U+00F7 divide, U+232B erase-to-the-left. Escapes keep tools from rewriting them.
private fun CalcKey.label(): String =
    when (this) {
        CalcKey.TRIPLE_ZERO -> "000"
        CalcKey.BACKSPACE -> "⌫"
        CalcKey.PLUS -> "+"
        CalcKey.MINUS -> "−"
        CalcKey.TIMES -> "×"
        CalcKey.DIVIDE -> "÷"
        else -> name.last().toString()
    }
