package io.github.toanhp123.finote.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

internal const val PHONE = "spec:width=360dp,height=640dp"

/** The usual trio for a screen: light, dark, and font scale 2. */
@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", device = PHONE, fontScale = 2f)
internal annotation class ScreenPreviews
