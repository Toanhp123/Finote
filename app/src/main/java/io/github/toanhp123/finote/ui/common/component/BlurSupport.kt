package io.github.toanhp123.finote.ui.common.component

import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf

/** Real blur needs `RenderEffect`, which starts at Android 12. Below that, glass surfaces fall back to a solid fill. */
internal val LocalBlurEnabled = staticCompositionLocalOf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
