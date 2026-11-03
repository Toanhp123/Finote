package io.github.toanhp123.finote.ui.common.component

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Light, dark and 200% font scale. Every shared component carries it. */
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", fontScale = 2f)
annotation class FinotePreviews
