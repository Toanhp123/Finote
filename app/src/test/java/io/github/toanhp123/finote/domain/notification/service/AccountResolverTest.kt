package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Ignored
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Rejected
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Resolved
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.testRule
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountResolverTest {
    private val pkg = "com.VCB"

    @Suppress("LongParameterList") // why: test helper, every field varies across tests
    private fun account(
        id: String = "acc-1",
        type: AccountType = AccountType.BANK,
        archived: Boolean = false,
        deletedAt: Long? = null,
        packageName: String = pkg,
        ignored: Boolean = false,
    ): Account =
        testAccount(id = id).copy(
            type = type,
            bankPackageName = packageName,
            isArchived = archived,
            deletedAt = deletedAt,
            isIgnored = ignored,
        )

    private fun identifier(
        accountId: String = "acc-1",
        mask: String = "1234",
        kind: IdentifierKind = IdentifierKind.ACCOUNT,
        packageName: String = pkg,
    ) = AccountIdentifier(packageName, kind, mask, accountId)

    private fun rule(
        accountType: AccountType? = null,
        maskKind: IdentifierKind? = null,
    ) = testRule(
        id = "r",
        packageName = pkg,
        bankName = "Vietcombank",
        updatedAt = 1L,
        accountType = accountType,
        maskKind = maskKind,
    )

    private fun resolve(
        mask: String?,
        accounts: List<Account>,
        identifiers: List<AccountIdentifier> = emptyList(),
        rule: ParserRule? = rule(),
    ) = resolveAccount(pkg, mask, rule, accounts, identifiers)

    private fun existing(
        id: String,
        attach: AccountIdentifier? = null,
    ) = Resolved(AccountTarget.Existing(id, attach))

    private fun new(
        mask: String?,
        bankName: String = "Vietcombank",
        type: AccountType = AccountType.BANK,
        kind: IdentifierKind = IdentifierKind.ACCOUNT,
    ) = Resolved(AccountTarget.New(pkg, mask, bankName, type, kind))

    @Test
    fun `mask found on live owner`() =
        assertEquals(existing("acc-1"), resolve("1234", listOf(account()), listOf(identifier())))

    @Test
    fun `mask found on ignored owner`() =
        assertEquals(Ignored, resolve("1234", listOf(account(ignored = true)), listOf(identifier())))

    @Test
    fun `mask found on archived owner`() =
        assertEquals(
            Rejected(FAIL_ACCOUNT_ARCHIVED),
            resolve("1234", listOf(account(archived = true)), listOf(identifier())),
        )

    @Test
    fun `mask found on owner both ignored and archived is Ignored`() =
        assertEquals(Ignored, resolve("1234", listOf(account(archived = true, ignored = true)), listOf(identifier())))

    @Test
    fun `identifier of a deleted owner is free`() =
        assertEquals(new("1234"), resolve("1234", listOf(account(deletedAt = 5L)), listOf(identifier())))

    @Test
    fun `identifier of another package is not matched`() =
        assertEquals(
            new("1234"),
            resolve(
                "1234",
                listOf(account("o", packageName = "com.other")),
                listOf(identifier("o", packageName = "com.other")),
            ),
        )

    @Test
    fun `N-05 attaches to the one identifier-less account of the type`() =
        assertEquals(
            existing("acc-1", attach = AccountIdentifier(pkg, IdentifierKind.ACCOUNT, "1234", "acc-1")),
            resolve("1234", listOf(account())),
        )

    @Test
    fun `N-05 does not attach across types`() =
        assertEquals(
            new("1234", type = AccountType.CREDIT_CARD, kind = IdentifierKind.CARD),
            resolve("1234", listOf(account()), rule = rule(AccountType.CREDIT_CARD)),
        )

    @Test
    fun `N-05 ignores a deleted account`() = assertEquals(new("1234"), resolve("1234", listOf(account(deletedAt = 5L))))

    @Test
    fun `second sub-account gets its own account`() =
        assertEquals(new("5678"), resolve("5678", listOf(account()), listOf(identifier(mask = "1234"))))

    @Test
    fun `N-05 skips an account that holds an identifier of another kind`() =
        assertEquals(
            new("5678"),
            resolve("5678", listOf(account()), listOf(identifier(mask = "1234", kind = IdentifierKind.CARD))),
        )

    @Test
    fun `N-05 with two accounts of the type creates`() =
        assertEquals(new("1234"), resolve("1234", listOf(account("a"), account("b"))))

    @Test
    fun `N-05 on an ignored account ignores without attaching`() =
        assertEquals(Ignored, resolve("1234", listOf(account(ignored = true))))

    @Test
    fun `N-05 on an archived account is rejected`() =
        assertEquals(Rejected(FAIL_ACCOUNT_ARCHIVED), resolve("1234", listOf(account(archived = true))))

    @Test
    fun `mask-less picks the one account of the rule's type`() =
        assertEquals(
            existing("bank"),
            resolve(null, listOf(account("bank"), account("card", type = AccountType.CREDIT_CARD))),
        )

    @Test
    fun `mask-less with two of the type is ambiguous`() =
        assertEquals(Rejected(FAIL_AMBIGUOUS_ACCOUNT), resolve(null, listOf(account("a"), account("b"))))

    @Test
    fun `mask-less counts an archived and an ignored account of the type`() =
        assertEquals(
            Rejected(FAIL_AMBIGUOUS_ACCOUNT),
            resolve(null, listOf(account("a", archived = true), account("b", ignored = true))),
        )

    @Test
    fun `mask-less with none of the type creates`() =
        assertEquals(
            new(null, type = AccountType.CREDIT_CARD, kind = IdentifierKind.CARD),
            resolve(null, listOf(account()), rule = rule(AccountType.CREDIT_CARD)),
        )

    @Test
    fun `mask-less with no account creates`() = assertEquals(new(null), resolve(null, emptyList()))

    @Test
    fun `mask-less ignores deleted accounts`() =
        assertEquals(existing("live"), resolve(null, listOf(account("old", deletedAt = 5L), account("live"))))

    @Test
    fun `mask-less on the one archived account is rejected`() =
        assertEquals(Rejected(FAIL_ACCOUNT_ARCHIVED), resolve(null, listOf(account(archived = true))))

    @Test
    fun `wallet rule gives a wallet account`() =
        assertEquals(
            new("1234", type = AccountType.WALLET, kind = IdentifierKind.WALLET),
            resolve("1234", emptyList(), rule = rule(AccountType.WALLET)),
        )

    @Test
    fun `missing rule`() = assertEquals(new("1234", bankName = pkg), resolve("1234", emptyList(), rule = null))

    @Test
    fun `maskKind overrides accountType`() =
        assertEquals(
            existing("card"),
            resolve(
                "1234",
                listOf(account("acc"), account("card")),
                listOf(identifier("acc"), identifier("card", kind = IdentifierKind.CARD)),
                rule(AccountType.BANK, IdentifierKind.CARD),
            ),
        )

    @Test
    fun `maskKind gives the kind of a new account`() =
        assertEquals(
            new("1234", kind = IdentifierKind.CARD),
            resolve("1234", emptyList(), rule = rule(AccountType.BANK, IdentifierKind.CARD)),
        )

    @Test
    fun `kind is part of the key`() =
        assertEquals(
            new("1234", kind = IdentifierKind.CARD),
            resolve("1234", listOf(account()), listOf(identifier()), rule(maskKind = IdentifierKind.CARD)),
        )
}
