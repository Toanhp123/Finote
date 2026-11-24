package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveAccountIdentifierUseCaseTest {
    private val repo = FakeAccountRepository()
    private val runner = FakeTransactionRunner()
    private val remove = RemoveAccountIdentifierUseCase(repo, runner)
    private val key = AccountIdentifier("vcb", IdentifierKind.ACCOUNT, "1234", "acc-1")

    @Test
    fun `remove deletes the key`() =
        runTest {
            repo.stored["acc-1"] = testAccount(id = "acc-1")
            repo.identifiers += key

            assertEquals(Outcome.Ok(Unit), remove(key))

            assertTrue(repo.identifiers.isEmpty())
            assertEquals(1, runner.writes)
        }

    @Test
    fun `archived owner keeps its key`() =
        runTest {
            repo.stored["acc-1"] = testAccount(id = "acc-1").copy(isArchived = true)
            repo.identifiers += key

            assertEquals(Outcome.Err(AccountError.Archived), remove(key))

            assertEquals(listOf(key), repo.identifiers)
        }

    @Test
    fun `observe lists the keys of one account`() =
        runTest {
            repo.identifiers += key
            repo.identifiers += key.copy(mask = "5678", accountId = "acc-2")

            assertEquals(listOf(key), ObserveAccountIdentifiersUseCase(repo)("acc-1").first())
        }
}
