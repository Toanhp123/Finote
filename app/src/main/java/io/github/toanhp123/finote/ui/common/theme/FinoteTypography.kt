package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Amount styles use tabular figures so digits line up in a column. */
data class FinoteTypography(
    val material: Typography = Typography(),
    val amountLarge: TextStyle = amountStyle(32, FontWeight.Bold),
    val amountMedium: TextStyle = amountStyle(20, FontWeight.SemiBold),
    val amountSmall: TextStyle = amountStyle(14, FontWeight.SemiBold),
)

private fun amountStyle(
    size: Int,
    weight: FontWeight,
) = TextStyle(fontSize = size.sp, fontWeight = weight, fontFeatureSettings = "tnum")
