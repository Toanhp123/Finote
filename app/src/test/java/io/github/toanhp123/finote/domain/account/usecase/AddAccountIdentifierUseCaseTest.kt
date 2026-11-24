package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddAccountIdentifierUseCaseTest {
    private val repo = FakeAccountRepository()
    private val mergeRepo = FakeAccountMergeRepository()
    private val runner = FakeTransactionRunner()
    private val add = AddAccountIdentifierUseCase(repo, mergeRepo, runner)

    private fun bank(id: String) = testAccount(id = id).copy(bankPackageName = "vcb")

    private fun seed(account: Account) {
        repo.stored[account.id] = account
    }

    private fun key(
        accountId: String,
        mask: String = "1234",
    ) = AccountIdentifier("vcb", IdentifierKind.ACCOUNT, mask, accountId)

    @Test
    fun `mask keeps leading zeros`() =
        runTest {
            seed(bank("acc-1"))

            assertEquals(Outcome.Ok(Unit), add("acc-1", IdentifierKind.ACCOUNT, "0042"))

            assertEquals(listOf(key("acc-1", "0042")), repo.identifiers)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `mask must be four digits`() =
        runTest {
            seed(bank("acc-1"))

            listOf("123", "12a4", "12345", "").forEach {
                assertEquals(Outcome.Err(AccountError.InvalidMask), add("acc-1", IdentifierKind.ACCOUNT, it))
            }
            assertTrue(repo.identifiers.isEmpty())
        }

    @Test
    fun `missing or deleted account is not found`() =
        runTest {
            seed(bank("acc-2").copy(deletedAt = 5L))

            assertEquals(Outcome.Err(AccountError.NotFound), add("ghost", IdentifierKind.ACCOUNT, "1234"))
            assertEquals(Outcome.Err(AccountError.NotFound), add("acc-2", IdentifierKind.ACCOUNT, "1234"))
            assertTrue(repo.identifiers.isEmpty())
        }

    @Test
    fun `cash account cannot take a mask`() =
        runTest {
            seed(testAccount(id = "cash", type = AccountType.CASH).copy(bankPackageName = "vcb"))
            seed(testAccount(id = "nobank"))

            assertEquals(Outcome.Err(AccountError.NoBankApp), add("cash", IdentifierKind.ACCOUNT, "1234"))
            assertEquals(Outcome.Err(AccountError.NoBankApp), add("nobank", IdentifierKind.ACCOUNT, "1234"))
            assertTrue(repo.identifiers.isEmpty())
        }

    @Test
    fun `archived account cannot take a mask`() =
        runTest {
            seed(bank("acc-1").copy(isArchived = true))

            assertEquals(Outcome.Err(AccountError.Archived), add("acc-1", IdentifierKind.ACCOUNT, "1234"))
            assertTrue(repo.identifiers.isEmpty())
        }

    @Test
    fun `own key is a no-op`() =
        runTest {
            seed(bank("acc-1"))
            repo.identifiers += key("acc-1")

            assertEquals(Outcome.Ok(Unit), add("acc-1", IdentifierKind.ACCOUNT, "1234"))

            assertEquals(listOf(key("acc-1")), repo.identifiers)
            assertEquals(0, repo.writes)
        }

    @Test
    fun `taken key offers a merge`() =
        runTest {
            seed(bank("acc-1"))
            seed(bank("acc-2"))
            repo.identifiers += key("acc-2")

            val result = add("acc-1", IdentifierKind.ACCOUNT, "1234")

            assertEquals(Outcome.Err(AccountError.IdentifierTaken(bank("acc-2"), null)), result)
            assertEquals(listOf(key("acc-2")), repo.identifiers)
        }

    @Test
    fun `taken key with a transfer between names the reason`() =
        runTest {
            seed(bank("acc-1"))
            seed(bank("acc-2"))
            repo.identifiers += key("acc-2")
            mergeRepo.transferCounts[setOf("acc-1", "acc-2")] = 1

            val result = add("acc-1", IdentifierKind.ACCOUNT, "1234")

            assertEquals(Outcome.Err(AccountError.IdentifierTaken(bank("acc-2"), MergeRejection.HAS_TRANSFER)), result)
        }

    @Test
    fun `taken key on an archived holder names it`() =
        runTest {
            seed(bank("acc-1"))
            val holder = bank("acc-2").copy(isArchived = true)
            seed(holder)
            repo.identifiers += key("acc-2")

            val result = add("acc-1", IdentifierKind.ACCOUNT, "1234")

            assertEquals(
                Outcome.Err(AccountError.IdentifierTaken(holder, MergeRejection.ACCOUNT_ARCHIVED)),
                result,
            )
        }

    @Test
    fun `key of a deleted owner is taken over`() =
        runTest {
            seed(bank("acc-1"))
            seed(bank("acc-2").copy(deletedAt = 5L))
            repo.identifiers += key("acc-2")

            assertEquals(Outcome.Ok(Unit), add("acc-1", IdentifierKind.ACCOUNT, "1234"))

            assertEquals(listOf(key("acc-1")), repo.identifiers)
        }
}
