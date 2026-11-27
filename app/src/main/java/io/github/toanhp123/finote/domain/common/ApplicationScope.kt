package io.github.toanhp123.finote.domain.common

import javax.inject.Qualifier

/** Marks the app-lifetime `CoroutineScope` (supervisor job, default dispatcher). Provided in `di/DispatcherModule`. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
