package io.github.toanhp123.finote.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay

@Composable
fun FinoteNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider<NavKey> {
                entry<Home> { HomePlaceholder() }
            },
    )
}

@Composable
private fun HomePlaceholder() {
    Box(modifier = Modifier.fillMaxSize())
}
