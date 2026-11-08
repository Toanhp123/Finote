package io.github.toanhp123.finote.data.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleUpdateSettingsRepositoryImplTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context
            .getSharedPreferences("rule_update", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `defaults are off and no last check`() =
        runTest {
            val repo = RuleUpdateSettingsRepositoryImpl(context)

            repo.observeIsAutoUpdateEnabled().test { assertEquals(false, awaitItem()) }
            repo.observeLastCheck().test { assertEquals(null, awaitItem()) }
        }

    @Test
    fun `saved check round trips for every result`() =
        runTest {
            val results =
                listOf(
                    RuleUpdateResult.Updated(3),
                    RuleUpdateResult.UpToDate,
                    RuleUpdateResult.Network,
                    RuleUpdateResult.Invalid,
                    RuleUpdateResult.Error,
                )
            results.forEach { result ->
                RuleUpdateSettingsRepositoryImpl(context).saveLastCheck(RuleUpdateCheck(1_234L, result))

                RuleUpdateSettingsRepositoryImpl(context).observeLastCheck().test {
                    assertEquals(RuleUpdateCheck(1_234L, result), awaitItem())
                }
            }
        }

    @Test
    fun `observeLastCheck emits after saveLastCheck`() =
        runTest {
            val repo = RuleUpdateSettingsRepositoryImpl(context)

            repo.observeLastCheck().test {
                assertEquals(null, awaitItem())

                repo.saveLastCheck(RuleUpdateCheck(99L, RuleUpdateResult.UpToDate))

                assertEquals(RuleUpdateCheck(99L, RuleUpdateResult.UpToDate), awaitItem())
            }
        }

    @Test
    fun `observeIsAutoUpdateEnabled emits after the switch`() =
        runTest {
            val repo = RuleUpdateSettingsRepositoryImpl(context)

            repo.observeIsAutoUpdateEnabled().test {
                assertEquals(false, awaitItem())

                repo.setAutoUpdateEnabled(true)

                assertEquals(true, awaitItem())
            }
        }
}
