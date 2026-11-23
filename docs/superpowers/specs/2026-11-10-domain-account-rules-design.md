# Domain account rules design (`chore/domain-account-rules`)

Cleanup branch 3 of 8 before P2d (roadmap "Dọn dẹp trước P2d"). Audit findings A8, A9, A11, A20 in
`docs/superpowers/handoff/2026-11-10-cleanup-before-p2d.md`. Must land before `feature/transfer-pairing`, because P2d
adds transfers that carry a post balance and the merge anchor reads them wrong today.

## Scope

In this branch, one commit per part, each with green tests:

1. A9: one "usable account" rule and one "transaction touches account" rule in the domain.
2. A8: one anchor rule for the initial balance, used by cold start, merge and drift.
3. A20: `AccountRepository.update()` keeps machine-owned fields.
4. A11: ViewModels call use cases only; storage errors reach the UI as a domain exception.

Out of scope:

- `setInitialBalance` for an account edit screen (no such screen yet; recorded in the roadmap).
- Changing an account's `type` or `currency` (no UI changes them yet; `update()` keeps the stored values).
- The three SQL copies of "touches" in `TransactionDao` stay SQL (they only get a comment).
- Any change to `AccountResolver` outcomes.

## Decisions (brainstorm 2026-11-10)

| # | Question | Decision |
|---|---|---|
| 1 | Can the user add or edit a transaction on an ignored account? | No. Ignored is blocked like archived, everywhere, the write path included. |
| 2 | ViewModels read repositories directly. Amend the rule or add read use cases? | Add use cases. A Konsist rule "no `*ViewModel` imports a repository" is easy to enforce; "reads only" is not. |
| 3 | One branch or two? | One branch, four ordered commits. |

## Part 1 — A9: usable and touches

Add to the domain:

```kotlin
// Account.kt
/** Live, not archived, not ignored: the account can take new or edited transactions and shows in pickers. */
val isUsable: Boolean get() = deletedAt == null && !isArchived && !isIgnored

// Transaction.kt
/** Live, and [accountId] is its source or the destination of a transfer. Same rule as the `TransactionDao` SQL. */
fun touches(accountId: String): Boolean =
    deletedAt == null && (this.accountId == accountId || (type == TransactionType.TRANSFER && toAccountId == accountId))
```

`TransactionRepositoryImpl.update()` already checks both the stored row's accounts and the new ones
(`old.accountIds() + entity.accountIds()`); it keeps doing so with `isUsable`. `requireLiveAccount` is renamed
`requireUsableAccount`.

Replace the hand-written copies:

| Site | Today | After |
|---|---|---|
| `TransactionRepositoryImpl.getActiveAccountIds`, `requireLiveAccount` | allows ignored | `toDomain().isUsable` (blocks ignored) |
| `NotificationSettlementRepositoryImpl.resolveAccount` | three conditions inline | `isUsable` |
| `CashEntryDefaults.pickAccount`, `CashEntryViewModel.onLists`, `TransactionListViewModel` chips, `ObserveBalanceDriftUseCase` | inline filters | `isUsable` (part 4 moves them into `ObserveUsableAccountsUseCase`) |
| `BalanceCalculator.touches`, `BalanceDriftDetector.isOf` | two private copies | `Transaction.touches` |

- Rename `TransactionRepository.getActiveAccountIds` to `getUsableAccountIds`; its KDoc says ignored accounts are left
  out. Rename `ACCOUNT_INACTIVE` text to "account does not exist, is deleted, archived or ignored".
- Each of the three `TransactionDao` queries that encode "touches" gets a one-line comment pointing at
  `Transaction.touches`.
- Kept on purpose: `AccountResolver` gives an ignored match `Ignored` and an archived match `Rejected`; the two need
  different outcomes, so it does not use `isUsable`.

Behavior change: adding or updating a transaction on an ignored account is rejected (`IllegalArgumentException`, the
same path as archived). Soft-deleting a transaction still works; `softDelete` does not check the account.

Tests: `isUsable` and `touches` unit tests; repository tests that `add` and `update` on an ignored account are rejected.

## Part 2 — A8: one anchor rule

Today three sites find "the newest bank balance" differently:

| Site | Anchor row | Order | Skips transfers in |
|---|---|---|---|
| `NotificationSettlementRepositoryImpl.coldStart` | the post balance just booked, rows `timestamp <= postTime` | timestamp only | not needed |
| `AccountRepositoryImpl.withMergedStart` | max row with a post balance | `(timestamp, id)` | **no — P2d debt** |
| `detectBalanceDrift` | last own row with a post balance | `(timestamp, createdAt, id)` | yes |

Different tie orders mean cold start can anchor on one set of rows and drift check another, so a drift banner can show
right after initialization.

New file `domain/account/BalanceAnchor.kt`:

```kotlin
/** Ledger order shared by every balance rule: timestamp, then createdAt, then id. */
internal val LEDGER_ORDER: Comparator<Transaction>

/**
 * Newest live row whose own account is the account and that carries a bank balance. [ledger] is every touching row up
 * to it; [settledAfter] is the ADJUSTMENT rows of the account ordered after it.
 */
internal data class BalanceAnchor(
    val postBalance: Long,
    val ledger: List<Transaction>,
    val settledAfter: List<Transaction>,
)

/**
 * Null for a credit card, or when no live row with `accountId == account.id` carries a post balance. A transfer
 * received by the account never anchors: its post balance belongs to the sender.
 */
internal fun findBalanceAnchor(account: Account, transactions: List<Transaction>): BalanceAnchor?

/**
 * Initial balance that makes `ledger + settledAfter` end at the anchor's post balance; null when there is no anchor.
 */
fun BalanceCalculator.anchoredInitialBalance(account: Account, transactions: List<Transaction>): Long?
```

- `findBalanceAnchor` filters with `Transaction.touches(account.id)` and sorts by `LEDGER_ORDER`.
- Adjustments after the anchor count as if before it. The drift rule already treats an adjustment after the anchor as
  the user settling that anchor, so the start must include it. Without this, re-anchoring absorbs the drift into the
  start while the adjustment still applies, and the drift is booked twice. Example: start 1,000,000; bank row −100,000
  with post balance 800,000 (drift −100,000); the Adjust button books an OUT 100,000 adjustment; balance 800,000.
  A merge that re-anchors on the bank row alone sets the start to 900,000 and the balance to 700,000, and drift stays
  hidden behind the adjustment. Counting the adjustment keeps the start at 1,000,000 and the balance at 800,000. Today
  only the Adjust button writes adjustments (`ReconcileBalanceUseCase` has no UI caller); a later manual reconcile
  screen must revisit this rule.
- `detectBalanceDrift` uses `findBalanceAnchor` on `ledger`. Its rule "an adjustment after the anchor means already
  settled" stays, and is now `settledAfter.isNotEmpty()`.
- `coldStart(account)`: when the account is not initialized, read its live rows, call `anchoredInitialBalance`; a
  value sets `initialBalance` and `isInitialized = true`. The `postBalance` and `postTime` parameters go away. `book()`
  still rebuilds the account right after, so `currentBalance` covers every live row, those after the anchor included.
- `withMergedStart`: call `anchoredInitialBalance` on the merged rows; null falls back to today's branches (the
  source's start when only the source is initialized, else the target unchanged).
- `initialBalanceFor` becomes private to `BalanceAnchor.kt`.

Behavior change: merge ignores a post balance on a transfer received by the target; equal timestamps order the same at
all three sites.

Tests: `findBalanceAnchor` skips a received transfer, a deleted row and a credit card, and breaks ties by `createdAt`
then `id`; the example above through `merge` ends at 800,000 with no drift; merge with a received transfer carrying a post balance keeps the anchor on the target's own row; a cold
start followed by `detectBalanceDrift` gives null.

## Part 3 — A20: `update()` keeps machine-owned fields

`UpdateAccountUseCase` has no production caller yet. The contract is fixed now so the future edit screen cannot break
matching or the balance.

- `AccountRepositoryImpl.update()` takes only display fields from the argument: `name`, `icon`, `color`,
  `sortOrder`, `isExcludedFromNetWorth`, `updatedAt`. Everything else comes from the stored row: `type` and `currency`
  (they change balance rules; no UI edits them), `bankPackageName`, `accountNumberMask`, `initialBalance`,
  `isInitialized`, and the fields kept today (`currentBalance`, `createdAt`, `deletedAt`, `isArchived`, `isIgnored`,
  `mergedIntoId`). `nameKey` stays the stored one when `name` is unchanged, else becomes null.
- `initialBalance` no longer changes in `update()`, so its `rebuild` branch goes away.
- `UpdateAccountUseCase` stops forcing `isInitialized = true`; it stamps `updatedAt` and validates.
- `AccountRepository.update` KDoc lists the editable fields.
- Roadmap: "the account edit screen needs `setInitialBalance(id, value)` that sets `isInitialized = true` and rebuilds".

Tests: `update` with a different type, currency, bank key, mask or initial balance keeps the stored values; `update` of an
uninitialized account leaves it uninitialized; `UpdateAccountUseCaseTest` updated.

## Part 4 — A11: ViewModels call use cases only

New use cases, one job each:

| Use case | Replaces | Used by |
|---|---|---|
| `ObserveAccountsUseCase` | `AccountRepository.observeAccounts()` | `AccountsSettingsViewModel`, `TransactionListViewModel` |
| `ObserveUsableAccountsUseCase` | `observeAccounts()` + `isUsable` filter | `CashEntryViewModel`, `ObserveBalanceDriftUseCase` |
| `CountMergeMovesUseCase` | `AccountRepository.getLiveTransactionCount` | `AccountsSettingsViewModel` |
| `CountTrashableTransactionsUseCase` | `AccountRepository.getTrashableTransactionCount` | `AccountsSettingsViewModel` |
| `ObserveCategoriesUseCase` | `CategoryRepository.observeCategories()` | `CashEntryViewModel`, `TransactionListViewModel` |
| `GetLastManualChoicesUseCase` | `getLastManualAccountId` + `getLastManualCategoryId` per type | `CashEntryViewModel` |
| `AddManualTransactionUseCase` | the `Transaction` the ViewModel builds + noon rule | `CashEntryViewModel` |
| `ObserveParserRulesUseCase` | `ParserRuleRepository.observeRules()` | `SettingsViewModel`, `NotificationCaptureSettingsViewModel` |
| `ObserveRuleUpdateStatusUseCase` | `observeIsAutoUpdateEnabled()` + `observeLastCheck()` | `NotificationCaptureSettingsViewModel` |

- `AddManualTransactionUseCase(amount, type, accountId, categoryId, date, note)`: new UUID; today keeps the current
  time, another day is 12:00 in the clock's zone; `source = MANUAL`; trims the note, a blank note stays `""` as today;
  delegates to `AddTransactionUseCase`.
- `GetLastManualChoicesUseCase` returns the last account id and the last category id per manual type.
- `ObserveRuleUpdateStatusUseCase` returns one flow of a small data class with both values.
- `CashEntryDefaults` stays in `ui` (it is the screen's preselection); it uses `isUsable`.
- Order: `AccountRepository.observeAccounts()` KDoc states the DAO order (`sortOrder`, then `createdAt`); the use
  cases keep it and do not sort again.
- `TransactionListViewModel` collects `ObserveAccountsUseCase` once and derives its chips with
  `filter(Account::isUsable)`. Two account flows could emit out of step and reset the chosen chip.

Storage errors:

- Add `domain/common/StorageException` (plain Kotlin, no Android import).
- In `TransactionRepositoryImpl`, write methods run through a private `write { }` helper that wraps
  `withWriteTransaction` and rethrows `android.database.SQLException` as `StorageException`. `androidx.sqlite`'s
  `SQLiteException` is an alias of that type on Android.
- `CashEntryViewModel` and `TransactionListViewModel` catch `StorageException`; no `android.database` import remains in
  `ui`. `AccountsSettingsViewModel` already catches `Exception` and is unchanged.
- `CashEntryViewModel.init` runs as one flow: `flow { emit(getLastManualChoices()) }` combined with the account and
  category flows, then `.catch`. A `.catch` does not see a suspend call made before the flow starts, so the last-choice
  read must be inside it. On failure the lists stay empty and Save stays disabled. Nothing is logged.
- `AccountRepositoryImpl` writes are not wrapped: `AccountsSettingsViewModel` already turns any non-cancellation
  failure into an effect.

Konsist (`app/src/test/java/io/github/toanhp123/finote/architecture/`):

- No `*ViewModel.kt` file imports a package containing `.repository.`, and no `*ViewModel` constructor parameter has
  such a type.
- No file in `ui` imports `android.database..` or `androidx.sqlite..`.
- `docs/architecture.md` lines 55 and 70: "ViewModel calls use cases, reads included; it never touches a repository".

Tests: `AddManualTransactionUseCase` (today vs another day, zone, trimmed note, source), `ObserveUsableAccountsUseCase`,
`GetLastManualChoicesUseCase`, the `write` helper maps `SQLException`. ViewModel tests keep their fake repositories and
wrap them in the real use cases, to change as little test code as possible.

## Verification

- After each part: `testDebugUnitTest` through `gradle-run`.
- Before review: `qualityGate` through `gradle-run`.
- Then `reviewer` once on the branch, then squash into `dev`.
