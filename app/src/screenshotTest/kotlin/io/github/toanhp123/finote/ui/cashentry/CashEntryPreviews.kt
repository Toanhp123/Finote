package io.github.toanhp123.finote.ui.cashentry

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

// The sheet scrolls inside a tall window; these heights are enough to show all of it without a scroll.
internal const val TALL_PHONE = "spec:width=360dp,height=700dp"
private const val PHONE_LARGE_FONT = "spec:width=360dp,height=1000dp"

@Preview(name = "Light", device = TALL_PHONE)
@Preview(name = "Dark", device = TALL_PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", device = PHONE_LARGE_FONT, fontScale = 2f)
internal annotation class CashEntryPreviews
