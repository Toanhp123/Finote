package io.github.toanhp123.finote.domain.notification

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Ignored
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Rejected
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Resolved
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountResolverTest {
    private val pkg = "com.VCB"

    @Suppress("LongParameterList") // why: test helper, every field varies across tests
    private fun account(
        id: String = "acc-1",
        mask: String? = "1234",
        archived: Boolean = false,
        deletedAt: Long? = null,
        packageName: String = pkg,
        mergedIntoId: String? = null,
        ignored: Boolean = false,
    ): Account =
        testAccount(id = id).copy(
            bankPackageName = packageName,
            accountNumberMask = mask,
            isArchived = archived,
            deletedAt = deletedAt,
            mergedIntoId = mergedIntoId,
            isIgnored = ignored,
        )

    private fun rule(accountType: AccountType? = null) =
        testRule(
            id = "r",
            packageName = pkg,
            bankName = "Vietcombank",
            updatedAt = 1L,
            accountType = accountType,
        )

    private fun resolve(
        mask: String?,
        accounts: List<Account>,
        rule: ParserRule? = rule(),
    ) = resolveAccount(pkg, mask, rule, accounts)

    private fun existing(id: String) = Resolved(AccountTarget.Existing(id))

    private fun new(
        mask: String?,
        bankName: String = "Vietcombank",
        type: AccountType = AccountType.BANK,
    ) = Resolved(AccountTarget.New(pkg, mask, bankName, type))

    @Test
    fun `mask found`() = assertEquals(existing("acc-1"), resolve("1234", listOf(account())))

    @Test
    fun `mask not found`() = assertEquals(new("1234"), resolve("1234", listOf(account(mask = "9999"))))

    @Test
    fun `mask null with one account of the package`() =
        assertEquals(existing("acc-1"), resolve(null, listOf(account())))

    @Test
    fun `mask null with no account`() = assertEquals(new(null), resolve(null, emptyList()))

    @Test
    fun `mask null with two accounts`() =
        assertEquals(
            Rejected(FAIL_AMBIGUOUS_ACCOUNT),
            resolve(null, listOf(account("a"), account("b", mask = "5678"))),
        )

    @Test
    fun `archived match`() =
        assertEquals(Rejected(FAIL_ACCOUNT_ARCHIVED), resolve("1234", listOf(account(archived = true))))

    @Test
    fun `same mask with one archived and one active account picks the active one`() =
        assertEquals(
            existing("active"),
            resolve("1234", listOf(account("old", archived = true), account("active"))),
        )

    @Test
    fun `mask null with one active and one archived account is ambiguous`() =
        assertEquals(
            Rejected(FAIL_AMBIGUOUS_ACCOUNT),
            resolve(null, listOf(account("active"), account("old", mask = "5678", archived = true))),
        )

    @Test
    fun `mask null with one archived account`() =
        assertEquals(Rejected(FAIL_ACCOUNT_ARCHIVED), resolve(null, listOf(account(archived = true))))

    @Test
    fun `deleted account is ignored`() = assertEquals(new("1234"), resolve("1234", listOf(account(deletedAt = 5L))))

    @Test
    fun `account of another package is ignored`() =
        assertEquals(new("1234"), resolve("1234", listOf(account(packageName = "com.other"))))

    @Test
    fun `wallet rule gives a wallet account`() =
        assertEquals(new("1234", type = AccountType.WALLET), resolve("1234", emptyList(), rule(AccountType.WALLET)))

    @Test
    fun `missing rule`() = assertEquals(new("1234", bankName = pkg), resolve("1234", emptyList(), null))

    @Test
    fun `mask hits a merged row and lands on its target`() =
        assertEquals(
            existing("b"),
            resolve("5678", listOf(account("a", "5678", deletedAt = 5L, mergedIntoId = "b"), account("b"))),
        )

    @Test
    fun `live row beats merged row with the same key`() =
        assertEquals(
            existing("live"),
            resolve(
                "5678",
                listOf(
                    account("a", "5678", deletedAt = 5L, mergedIntoId = "b"),
                    account("b"),
                    account("live", "5678"),
                ),
            ),
        )

    @Test
    fun `latest merged row wins`() =
        assertEquals(
            existing("c"),
            resolve(
                "5678",
                listOf(
                    account("a1", "5678", deletedAt = 10L, mergedIntoId = "b"),
                    account("a2", "5678", deletedAt = 20L, mergedIntoId = "c"),
                    account("b", "1111"),
                    account("c", "2222"),
                ),
            ),
        )

    @Test
    fun `merged row with deleted target is no match`() =
        assertEquals(
            new("5678"),
            resolve(
                "5678",
                listOf(
                    account("a", "5678", deletedAt = 5L, mergedIntoId = "b"),
                    account("b", deletedAt = 6L),
                ),
            ),
        )

    @Test
    fun `merged row with missing target is no match`() =
        assertEquals(
            new("5678"),
            resolve("5678", listOf(account("a", "5678", deletedAt = 5L, mergedIntoId = "gone"))),
        )

    @Test
    fun `ignored target gives Ignored`() = assertEquals(Ignored, resolve("1234", listOf(account(ignored = true))))

    @Test
    fun `ignored and archived gives Ignored`() =
        assertEquals(Ignored, resolve("1234", listOf(account(archived = true, ignored = true))))

    @Test
    fun `merge into an ignored target gives Ignored`() =
        assertEquals(
            Ignored,
            resolve(
                "5678",
                listOf(account("a", "5678", deletedAt = 5L, mergedIntoId = "b"), account("b", ignored = true)),
            ),
        )

    @Test
    fun `no mask skips merged null-mask row`() =
        assertEquals(
            Rejected(FAIL_AMBIGUOUS_ACCOUNT),
            resolve(
                null,
                listOf(
                    account("n", null, deletedAt = 5L, mergedIntoId = "b"),
                    account("b"),
                    account("c", "9999"),
                ),
            ),
        )

    @Test
    fun `no mask after merge has one live account`() =
        assertEquals(
            existing("b"),
            resolve(null, listOf(account("n", null, deletedAt = 5L, mergedIntoId = "b"), account("b"))),
        )

    @Test
    fun `no mask counts an ignored account`() =
        assertEquals(
            Rejected(FAIL_AMBIGUOUS_ACCOUNT),
            resolve(null, listOf(account("b"), account("c", "9999", ignored = true))),
        )
}
