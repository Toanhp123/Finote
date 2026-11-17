package io.github.toanhp123.finote.domain.common

import javax.inject.Qualifier

/** Marks the `Boolean` that is true in the debug and dogfood builds. Provided in `di/BuildModule`. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ToolsEnabled
