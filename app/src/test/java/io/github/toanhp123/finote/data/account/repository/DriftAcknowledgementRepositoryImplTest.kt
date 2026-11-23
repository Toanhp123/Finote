package io.github.toanhp123.finote.data.account.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.model.DriftKey
import io.github.toanhp123.finote.domain.account.model.digest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriftAcknowledgementRepositoryImplTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context
            .getSharedPreferences("drift_ack", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `acknowledge then observe emits the key`() =
        runTest {
            val repo = DriftAcknowledgementRepositoryImpl(context)

            repo.observe().test {
                assertEquals(emptyMap<String, String>(), awaitItem())
                repo.acknowledge("acc-1", DriftKey(5L, -1_000L))
                assertEquals(mapOf("acc-1" to DriftKey(5L, -1_000L).digest("acc-1")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `second acknowledge replaces the first`() =
        runTest {
            val repo = DriftAcknowledgementRepositoryImpl(context)
            repo.acknowledge("acc-1", DriftKey(5L, 1_000L))
            repo.acknowledge("acc-2", DriftKey(7L, 2_000L))

            repo.observe().test {
                assertEquals(2, awaitItem().size)
                repo.acknowledge("acc-1", DriftKey(9L, 3_000L))
                assertEquals(
                    mapOf(
                        "acc-1" to DriftKey(9L, 3_000L).digest("acc-1"),
                        "acc-2" to DriftKey(7L, 2_000L).digest("acc-2"),
                    ),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a new instance reads the stored keys`() =
        runTest {
            DriftAcknowledgementRepositoryImpl(context).acknowledge("acc-1", DriftKey(5L, 1_000L))

            DriftAcknowledgementRepositoryImpl(context).observe().test {
                assertEquals(mapOf("acc-1" to DriftKey(5L, 1_000L).digest("acc-1")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the preferences file holds a digest, not the balance`() =
        runTest {
            DriftAcknowledgementRepositoryImpl(context).acknowledge("acc-1", DriftKey(5L, 123_456_789L))

            val stored =
                context
                    .getSharedPreferences("drift_ack", Context.MODE_PRIVATE)
                    .getString("acc-1", null)
            assertEquals(DriftKey(5L, 123_456_789L).digest("acc-1"), stored)
        }
}
