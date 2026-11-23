package io.github.toanhp123.finote.domain.common

import javax.inject.Qualifier

/** Marks the `CoroutineDispatcher` for CPU-bound work. Provided in `di/DispatcherModule`. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher
