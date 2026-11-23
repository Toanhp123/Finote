package io.github.toanhp123.finote.domain.common

/** Result of a domain operation that can fail in an expected way. */
sealed interface Outcome<out T, out E> {
    data class Ok<T>(
        val value: T,
    ) : Outcome<T, Nothing>

    data class Err<E>(
        val error: E,
    ) : Outcome<Nothing, E>
}
