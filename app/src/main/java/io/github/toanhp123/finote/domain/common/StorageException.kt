package io.github.toanhp123.finote.domain.common

/** A local database read or write failed. Carries no message: the content may be financial data. */
class StorageException(
    cause: Throwable,
) : RuntimeException(cause)
