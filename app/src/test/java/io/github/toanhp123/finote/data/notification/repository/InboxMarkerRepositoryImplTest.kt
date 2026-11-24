package io.github.toanhp123.finote.data.notification.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.toanhp123.finote.domain.notification.model.InboxMarkers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxMarkerRepositoryImplTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context
            .getSharedPreferences("inbox_markers", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `starts empty and emits each write`() =
        runTest {
            val repo = InboxMarkerRepositoryImpl(context)

            repo.observe().test {
                assertEquals(InboxMarkers(emptySet(), emptySet()), awaitItem())
                repo.markSeen(setOf("a"))
                assertEquals(InboxMarkers(setOf("a"), emptySet()), awaitItem())
                repo.dismissBanner(setOf("b"))
                assertEquals(InboxMarkers(setOf("a"), setOf("b")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a write replaces the old ids and a second write persists`() =
        runTest {
            val repo = InboxMarkerRepositoryImpl(context)
            repo.markSeen(setOf("a", "b"))
            repo.markSeen(setOf("b", "c"))

            repo.observe().test {
                assertEquals(setOf("b", "c"), awaitItem().seenIds)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a new instance reads the stored ids`() =
        runTest {
            InboxMarkerRepositoryImpl(context).apply {
                markSeen(setOf("a"))
                dismissBanner(setOf("a", "b"))
            }

            InboxMarkerRepositoryImpl(context).observe().test {
                assertEquals(InboxMarkers(setOf("a"), setOf("a", "b")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `marker file holds ids only`() =
        runTest {
            InboxMarkerRepositoryImpl(context).apply {
                markSeen(setOf("log-1"))
                dismissBanner(setOf("log-1", "log-2"))
            }

            val stored =
                context
                    .getSharedPreferences("inbox_markers", Context.MODE_PRIVATE)
                    .all
            assertEquals(
                mapOf<String, Any>("seenIds" to setOf("log-1"), "bannerDismissedIds" to setOf("log-1", "log-2")),
                stored,
            )
        }
}
