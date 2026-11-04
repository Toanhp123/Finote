package io.github.toanhp123.finote.ui.cashentry

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

// The sheet scrolls inside a tall window; these heights are enough to show all of it without a scroll.
internal const val PHONE = "spec:width=360dp,height=700dp"
private const val PHONE_LARGE_FONT = "spec:width=360dp,height=1000dp"

@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", device = PHONE_LARGE_FONT, fontScale = 2f)
internal annotation class CashEntryPreviews
