package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.BankApp
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.notification.usecase.FakeParserRuleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBankAppsUseCaseTest {
    private val rules = FakeParserRuleRepository()

    @Test
    fun `bank apps are distinct active packages sorted by name`() =
        runTest {
            rules.stored.value =
                listOf(
                    testRule(id = "1", packageName = "pkg.z", bankName = "Zeta"),
                    testRule(id = "2", packageName = "pkg.z", bankName = "Zeta", priority = 1),
                    testRule(id = "3", packageName = "pkg.a", bankName = "Alpha"),
                    testRule(id = "4", packageName = "pkg.off", bankName = "Aaa", isActive = false),
                    testRule(id = "5", packageName = "pkg.z", bankName = "Zeta", isActive = false),
                )

            assertEquals(
                listOf(BankApp("pkg.a", "Alpha"), BankApp("pkg.z", "Zeta")),
                ObserveBankAppsUseCase(rules)().first(),
            )
        }
}
