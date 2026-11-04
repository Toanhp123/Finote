package io.github.toanhp123.finote.domain.transaction

enum class CalcKey(
    /** The digit this key types, or null for a key that is not a single digit. */
    val digit: Char? = null,
) {
    D0('0'),
    D1('1'),
    D2('2'),
    D3('3'),
    D4('4'),
    D5('5'),
    D6('6'),
    D7('7'),
    D8('8'),
    D9('9'),
    TRIPLE_ZERO,
    BACKSPACE,
    PLUS,
    MINUS,
    TIMES,
    DIVIDE,
}
