package io.github.toanhp123.finote.ui.transactions

/** What a day header says. Today and yesterday are words the screen takes from `strings.xml`. */
sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data class Date(
        val text: String,
    ) : DayLabel
}
