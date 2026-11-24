package io.github.toanhp123.finote.data.account.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MergeDismissalRepositoryImplTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context
            .getSharedPreferences("merge_dismissals", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `dismiss stores the ordered pair`() =
        runTest {
            val repo = MergeDismissalRepositoryImpl(context)

            repo.observe().test {
                assertEquals(emptySet<Pair<String, String>>(), awaitItem())
                repo.dismiss("b", "a")
                assertEquals(setOf("a" to "b"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismissing the same pair twice keeps one entry`() =
        runTest {
            val repo = MergeDismissalRepositoryImpl(context)
            repo.dismiss("a", "b")
            repo.dismiss("b", "a")

            repo.observe().test {
                assertEquals(setOf("a" to "b"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a new instance reads the stored pairs`() =
        runTest {
            MergeDismissalRepositoryImpl(context).dismiss("a", "b")

            MergeDismissalRepositoryImpl(context).observe().test {
                assertEquals(setOf("a" to "b"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the preferences file holds ids and a flag only`() =
        runTest {
            MergeDismissalRepositoryImpl(context).dismiss("b", "a")

            val all = context.getSharedPreferences("merge_dismissals", Context.MODE_PRIVATE).all
            assertEquals(mapOf<String, Any?>("a|b" to true), all)
        }
}
