package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.testRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeParserRuleRepositoryTest {
    @Test
    fun `observe sorts by packageName, priority, id`() =
        runTest {
            val repository = FakeParserRuleRepository()
            repository.stored.value =
                listOf(
                    testRule(id = "z", packageName = "b.pkg", priority = 0),
                    testRule(id = "y", packageName = "a.pkg", priority = 1),
                    testRule(id = "x", packageName = "a.pkg", priority = 1),
                    testRule(id = "w", packageName = "a.pkg", priority = 0),
                )

            assertEquals(listOf("w", "x", "y", "z"), repository.observeRules().first().map { it.id })
        }
}
