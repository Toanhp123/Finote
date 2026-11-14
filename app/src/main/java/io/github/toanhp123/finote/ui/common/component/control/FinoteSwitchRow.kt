package io.github.toanhp123.finote.ui.common.component.control

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews

/**
 * A list row with a switch. The row owns the tap and announces its label with the state;
 * the switch is only the picture.
 */
@Composable
fun FinoteSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteListRow(
        title = title,
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = { FinoteSwitch(checked = checked, onCheckedChange = null) },
    )
}

@FinotePreviews
@Composable
private fun FinoteSwitchRowPreview() {
    FinotePreviewSurface {
        Column {
            FinoteSwitchRow(title = "Off", checked = false, onCheckedChange = {})
            FinoteSwitchRow(title = "On", checked = true, onCheckedChange = {})
        }
    }
}
