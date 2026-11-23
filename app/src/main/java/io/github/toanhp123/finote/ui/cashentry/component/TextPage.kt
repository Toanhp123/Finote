package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteTextField
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

@Composable
fun TextPage(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        FinoteTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            maxLength = maxLength,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
        FinoteButton(
            text = stringResource(R.string.cash_entry_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
