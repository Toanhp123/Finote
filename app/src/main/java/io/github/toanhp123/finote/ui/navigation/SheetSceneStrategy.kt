package io.github.toanhp123.finote.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Draws a top entry marked with [sheet] as an overlay: the entries below stay composed. The entry draws no UI in the
 * overlay itself, because its content (the bottom sheet) opens its own modal window and handles Back.
 */
class SheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull()
        return if (last != null && SheetKey in last.metadata) {
            SheetScene(last.contentKey, last, entries.dropLast(1))
        } else {
            null
        }
    }

    companion object {
        /** Entry metadata that marks an entry as a sheet. */
        fun sheet(): Map<String, Any> = metadata { put(SheetKey, true) }
    }
}

private data object SheetKey : NavMetadataKey<Boolean>

// A data class: NavDisplay keys the overlay on the scene, so a rebuilt scene must equal the old one.
private data class SheetScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
) : OverlayScene<T> {
    override val overlaidEntries: List<NavEntry<T>> = previousEntries
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = { entry.Content() }
}
