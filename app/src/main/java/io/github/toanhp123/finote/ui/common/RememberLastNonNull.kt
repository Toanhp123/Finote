package io.github.toanhp123.finote.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.node.Ref

/** Keeps the last non-null value after [value] becomes null, so overlay content stays drawn while it leaves. */
@Composable
fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { Ref<T>() }
    SideEffect { if (value != null) last.value = value }
    return value ?: last.value
}
