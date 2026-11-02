package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.ui.graphics.Color

// Income, expense and transfer keep their hue under Dynamic Color.
internal val LightIncome = MoneyColorRole(Color(0xFF1B6D24), Color(0xFFFFFFFF), Color(0xFFA3F69C), Color(0xFF002204))
internal val DarkIncome = MoneyColorRole(Color(0xFF88D982), Color(0xFF003909), Color(0xFF005313), Color(0xFFA3F69C))

internal val LightExpense = MoneyColorRole(Color(0xFFBA1A1A), Color(0xFFFFFFFF), Color(0xFFFFDAD6), Color(0xFF410002))
internal val DarkExpense = MoneyColorRole(Color(0xFFFFB4AB), Color(0xFF690005), Color(0xFF93000A), Color(0xFFFFDAD6))

internal val LightTransfer = MoneyColorRole(Color(0xFF345CA8), Color(0xFFFFFFFF), Color(0xFFD9E2FF), Color(0xFF001A43))
internal val DarkTransfer = MoneyColorRole(Color(0xFFB0C6FF), Color(0xFF002D6E), Color(0xFF1A438F), Color(0xFFD9E2FF))
