# Account Merge and Ignore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user merge an auto-created account into another account of the same bank app, and ignore an account so its notifications stop booking, from a new Settings → Accounts screen.

**Architecture:**
- `accounts` gains `mergedIntoId` (soft-deleted pointer row) and `isIgnored`; `notification_logs` gains status `IGNORED_ACCOUNT`.
- `domain/notification`: `resolveAccount` follows merged rows and returns `Ignored`; the planner emits `SettlementDecision.Ignore`; the settle use case writes it through `NotificationSettlementRepository.ignore()`.
- `domain/account`: `MergeAccountsUseCase`, `SetAccountIgnoredUseCase`; `AccountRepository.merge()` / `setIgnored()` do all writes in one `withWriteTransaction` and rebuild balances with `AccountBalanceRebuilder`.
- `ui/settings/accounts`: MVI screen (contract, screen, view model) pushed from Settings.

**Tech Stack:** Kotlin, Room 3, Hilt, Compose Material 3, Navigation 3, JUnit4, Robolectric, Turbine, Konsist.

**Spec:** `docs/superpowers/specs/2026-11-09-account-merge-ignore-design.md`

Paths below are relative to `app/src/main/java/io/github/toanhp123/finote/` (main) and
`app/src/test/java/io/github/toanhp123/finote/` (`test/...`).

## Global Constraints

- **Branch:** `feature/account-merge-ignore` from `dev`. The main session commits, after user approval. `coder` never commits. No AI attribution in commit messages.
- **Layers** (`docs/architecture.md`): `domain` has no `android.*`, `androidx.*`, Room or Hilt; `ui` depends on `domain` only; one public class per file; use cases expose one `operator fun invoke`; a `Flow` function is `observeX`; booleans start with `is`/`has`/`should`; `*Repository` is an interface in `domain/<f>/repository`.
- **Money** is `Long` VND. Overflow uses `Math.*Exact` (already inside `BalanceCalculator`).
- **Balance writes:** any change to a balance or snapshot runs inside one `withWriteTransaction`.
- **Soft delete only.** Merge soft-deletes the source; ignore trashes with `deletedAt`.
- **Logging:** never log notification text, amounts, balances or account numbers.
- **Strings:** every new string in `values`, `values-en`, `values-ja`, `values-zh-rCN`. UI only from `docs/design-system.md` components.
- **DB:** version 6 → 7, schema exported (`app/schemas/.../7.json`); no migration (destructive fallback before v1.0).
- **Exact values:** status `IGNORED_ACCOUNT`; existing fail reasons unchanged (`ambiguous_account`, `account_archived`, `invalid_amount`).
- **Verification:** `testDebugUnitTest` through the `gradle-run` skill after every task; `qualityGate` before the branch review.

## Review Focus

1. A maskless notification after the user merged a null-mask account and later has two live accounts of that bank → `ambiguous_account`, never silently the merge target. Test in Task 2.
2. Merge of an initialized target whose cold-start anchor predates the source's transactions → current balance equals the latest bank `postBalance` (no double count, no drift banner). Test in Task 4.
3. An account archived and then ignored → notifications are silent (`IGNORED_ACCOUNT`), not `FAILED`. Test in Task 2.
4. Editing an account through `AccountRepository.update()` after it was ignored → `isIgnored` and `mergedIntoId` survive (update copies stored flags). Test in Task 5.
5. A notification planned onto an account that the user merges away before the write → rolls back, stays `PENDING`, next run books on the target. Test in Task 3.

---

### Task 1: Schema

**Files:**
- Modify: `data/account/AccountEntity.kt`, `domain/account/model/Account.kt`, `data/account/AccountMapper.kt`
- Modify: `domain/notification/model/NotificationStatus.kt`
- Modify: `data/FinoteDatabase.kt` (`version = 7`), new `app/schemas/.../7.json`
- Test: `test/.../data/account/AccountMapperTest.kt`, fixtures in `test/.../data/Fixtures.kt` if they build entities positionally

**Interfaces:**
- Produces:
  - `Account.mergedIntoId: String? = null`, `Account.isIgnored: Boolean = false` (and the same on `AccountEntity`, entity fields without defaults like its neighbours; `isIgnored` placed after `isArchived`).
  - `enum class NotificationStatus { PENDING, CONFIRMED, DEDUPLICATED, PAIRED_TRANSFER, IGNORED_SPAM, IGNORED_ACCOUNT, FAILED }`.

- [ ] **Step 1: Write failing test** `AccountMapperTest.merge and ignore fields round trip`: entity with `mergedIntoId = "b"`, `isIgnored = true` → domain → entity equal.
- [ ] **Step 2: Run** `testDebugUnitTest --tests '*AccountMapperTest*'` → FAIL (unresolved).
- [ ] **Step 3: Add the fields, mapping, enum value, DB version 7.** Every `AccountEntity(...)` constructor call in main and test code gets `mergedIntoId = null, isIgnored = false` (e.g. `NotificationSettlementRepositoryImpl.newAccount`, `SystemAccountSeeder`).
- [ ] **Step 4: Run** `testDebugUnitTest` → PASS; `7.json` generated.
- [ ] **Step 5: Commit** `feat(db): add merged pointer and ignored flag to accounts`.

### Task 2: Account resolver

**Files:**
- Modify: `domain/notification/AccountResolver.kt`, `domain/notification/model/AccountResolution.kt`
- Test: `test/.../domain/notification/AccountResolverTest.kt`

**Interfaces:**
- Consumes: Task 1 fields.
- Produces: `AccountResolution.Ignored` (`data object`). Signature of `resolveAccount(packageName, mask, rule, accounts)` unchanged; `accounts` is still every row with a bank key, deleted ones included.

Order (spec "Account resolution"): mask present → exact key over live rows and merged rows (`deletedAt != null && mergedIntoId != null`), live wins, else merged row with max `deletedAt`, followed one hop to a live target (missing/deleted target = no match). Mask absent → count live rows of the package only. Nothing → `New`. Found account → ignored ⇒ `Ignored`, else archived ⇒ `Rejected(account_archived)`, else `Existing`.

- [ ] **Step 1: Write failing tests** (existing tests stay green):
  - `mask hits a merged row and lands on its target`: `A(VCB,5678, deleted, mergedIntoId=B)`, `B(VCB,1234)` → `Existing(B)`.
  - `live row beats merged row with the same key`.
  - `latest merged row wins`: two merged rows on `(VCB,5678)` → targets `B` (deletedAt 10) and `C` (deletedAt 20) → `Existing(C)`.
  - `merged row with deleted target is no match` → `New`.
  - `ignored target gives Ignored`; `ignored and archived gives Ignored`.
  - `no mask skips merged null-mask row`: `N(VCB,null, merged→B)`, `B(VCB,1234)`, `C(VCB,9999)` → `Rejected("ambiguous_account")`. (Review Focus 1)
  - `no mask after merge has one live account` → `Existing(B)` when only `B` is live.
  - `no mask counts an ignored account`: `B` live, `C` live ignored → `Rejected("ambiguous_account")`.
- [ ] **Step 2: Run** `--tests '*AccountResolverTest*'` → FAIL.
- [ ] **Step 3: Implement.** Update the KDoc of `resolveAccount` and the comment on the old "first wins" branch.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(notif): follow merged accounts and skip ignored ones`.

### Task 3: Settle ignored notifications

**Files:**
- Modify: `domain/notification/model/SettlementDecision.kt`, `domain/notification/SettlementPlanner.kt`
- Modify: `domain/notification/repository/NotificationSettlementRepository.kt`, `data/notification/NotificationSettlementRepositoryImpl.kt`
- Modify: `domain/notification/usecase/SettlePendingLogsUseCase.kt`, and the fake repository used by its test
- Test: `test/.../domain/notification/SettlementPlannerTest.kt`, `test/.../domain/notification/usecase/SettlePendingLogsUseCaseTest.kt`, `test/.../data/notification/NotificationSettlementRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `AccountResolution.Ignored` (Task 2), `NotificationStatus.IGNORED_ACCOUNT` (Task 1).
- Produces:
  - `data class SettlementDecision.Ignore(val log: NotificationLog, val partner: NotificationLog?)`.
  - `suspend fun NotificationSettlementRepository.ignore(decision: SettlementDecision.Ignore): Boolean` — marks `log` and `partner` `IGNORED_ACCOUNT` via `markSettled(ids, IGNORED_ACCOUNT, null, null)`; true when ≥ 1 row changed.

- [ ] **Step 1: Write failing tests:**
  - Planner `ignored account gives Ignore`; `wallet and bank pair on an ignored account ignores both` → `Ignore(bank, wallet)`.
  - Planner `repost of an ignored log is a Repost`: `logIds()` covers `Ignore`, so a repost whose original was ignored in the same run is emitted.
  - Use case `Ignore calls ignore and marks both written`.
  - Repository `ignore marks both logs IGNORED_ACCOUNT` (no `linkedTransactionId`, no `failureReason`).
  - Repository `book into an account ignored after planning rolls back`: `Book(Existing(B))`, then `B.isIgnored = true` in DB → `book` throws, no transaction, log `PENDING`.
  - Repository `book into an account merged away after planning rolls back` (Review Focus 5): `B` soft-deleted with `mergedIntoId = C` → throws, log `PENDING`; then a fresh `planSettlement` + `book` lands on `C`.
- [ ] **Step 2: Run** `--tests '*SettlementPlannerTest*' --tests '*SettlePendingLogsUseCaseTest*' --tests '*NotificationSettlementRepositoryImplTest*'` → FAIL.
- [ ] **Step 3: Implement.** `decide()` maps `Ignored` → `Ignore(bank, wallet)`; `logIds()` returns `log` + `partner`; `execute()` calls `ignore()` and adds both ids to `written`. In `resolveAccount()` of the impl the write-time check becomes `deletedAt == null && !isArchived && !isIgnored`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(notif): drop notifications of ignored accounts`.

### Task 4: Merge

**Files:**
- Modify: `domain/account/repository/AccountRepository.kt`, `data/account/AccountRepositoryImpl.kt`, `data/account/AccountDao.kt`, `data/transaction/TransactionDao.kt`, `data/account/AccountBalanceSnapshotDao.kt`
- Create: `domain/account/model/MergeRejection.kt`, `domain/account/model/AccountMergeRejectedException.kt`, `domain/account/usecase/MergeAccountsUseCase.kt`
- Modify: `test/.../domain/account/usecase/FakeAccountRepository.kt`
- Test: `test/.../data/account/AccountRepositoryImplTest.kt` (or a new `AccountMergeTest.kt` beside it if the file passes the size limit), `test/.../domain/account/usecase/MergeAccountsUseCaseTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class MergeRejection { SAME_ACCOUNT, ACCOUNT_GONE, DIFFERENT_BANK, HAS_TRANSFER }
  class AccountMergeRejectedException(val reason: MergeRejection) : IllegalArgumentException(reason.name)
  // AccountRepository
  /** One write transaction; throws AccountMergeRejectedException, nothing written. */
  suspend fun merge(sourceId: String, targetId: String, now: Long)
  /** Live transactions where the account is source or transfer destination. */
  suspend fun getLiveTransactionCount(id: String): Int
  // MergeAccountsUseCase(repository, clock): suspend operator fun invoke(sourceId: String, targetId: String)
  ```
- DAO additions (names fixed, SQL obvious from the spec): `TransactionDao.countTransfersBetween(a, b): Int` (deleted rows included, either direction), `TransactionDao.moveSource(from, to, now): Int`, `TransactionDao.moveDestination(from, to, now): Int` (all rows, deleted included, `updatedAt = now`), `AccountDao.repointMerged(from, to)`, `AccountBalanceSnapshotDao.deleteByAccount(accountId)`.

Write order inside the transaction (spec "Merge"): checks → move both columns → initial balance rule → repoint merged rows → soft-delete source with `mergedIntoId` → delete source snapshots → `rebuilder.rebuild(setOf(targetId))`.

Initial balance rule: among the target's live transactions after the move, take the one with a `postBalance` and the max `(timestamp, id)`. If found and target type ≠ `CREDIT_CARD`: `initialBalance = BalanceCalculator.initialBalanceFor(target, postBalance, live with timestamp <= that one)`, `isInitialized = true`. Else if only the source `isInitialized`: copy the source's `initialBalance`, `isInitialized = true`. Else keep.

- [ ] **Step 1: Write failing tests:**
  - `merge moves source and destination columns, deleted rows too`.
  - `merge keeps the bank balance` (Review Focus 2): target `B` initialized from a tx on 11-05 with `postBalance = 10_000_000`; source `A` has an expense of `200_000` on 11-03; no transaction after 11-05 → after merge `B.currentBalance == 10_000_000L`.
  - `merge sets an uninitialized target from the latest postBalance of either side`.
  - `merge keeps a credit card start`; `merge without any postBalance copies an initialized source start`; `merge without any postBalance keeps the target start`.
  - `merge deletes source snapshots and rebuilds target snapshots`.
  - `merge soft-deletes the source with mergedIntoId`; `chained merge repoints older rows` (`A→B`, then `B→C` ⇒ `A.mergedIntoId == C`).
  - Rejections, each leaving the DB unchanged: `same id` → `SAME_ACCOUNT`; deleted or unknown → `ACCOUNT_GONE`; other package or null package → `DIFFERENT_BANK`; live transfer → `HAS_TRANSFER`; deleted transfer → `HAS_TRANSFER`.
  - Use case `passes now from the clock`.
- [ ] **Step 2: Run** `--tests '*AccountRepositoryImplTest*' --tests '*MergeAccountsUseCaseTest*'` → FAIL.
- [ ] **Step 3: Implement** DAO queries, `merge`, `getLiveTransactionCount` (reuses `countActiveByAccount`), the use case, and the fake (`merge` moves nothing, records the call; counts from a map).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(account): merge an account into another of the same bank`.

### Task 5: Ignore

**Files:**
- Modify: `domain/account/repository/AccountRepository.kt`, `data/account/AccountRepositoryImpl.kt`, `data/transaction/TransactionDao.kt`
- Create: `domain/account/usecase/SetAccountIgnoredUseCase.kt`
- Modify: `domain/account/usecase/ObserveBalanceDriftUseCase.kt`, `ui/cashentry/CashEntryViewModel.kt` (`onLists` filter), `ui/cashentry/CashEntryDefaults.kt` if `pickAccount` filters archived
- Modify: `test/.../domain/account/usecase/FakeAccountRepository.kt`
- Test: `AccountRepositoryImplTest.kt`, `test/.../domain/account/usecase/SetAccountIgnoredUseCaseTest.kt`, the drift use case test, `test/.../ui/cashentry/CashEntryViewModelTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  // AccountRepository
  /** One write transaction. Trashing soft-deletes live non-transfer rows with accountId = id, then rebuilds it. */
  suspend fun setIgnored(id: String, ignored: Boolean, shouldTrashOldTransactions: Boolean, now: Long)
  /** Live non-transfer rows with accountId = id: what setIgnored would trash. */
  suspend fun getTrashableTransactionCount(id: String): Int
  // SetAccountIgnoredUseCase(repository, clock): suspend operator fun invoke(id: String, ignored: Boolean, shouldTrashOldTransactions: Boolean)
  ```
- DAO: `TransactionDao.trashNonTransfers(accountId, now): Int`, `TransactionDao.countNonTransfers(accountId): Int` (live rows, `type != 'TRANSFER'`).

- [ ] **Step 1: Write failing tests:**
  - `ignore without trash keeps transactions and balance`.
  - `ignore with trash soft-deletes non-transfers and rebuilds`; `transfers are not trashed and the other account balance is unchanged`.
  - `follow again clears the flag, trashed rows stay deleted`.
  - `ignore a missing or deleted account is rejected`.
  - `update keeps isIgnored and mergedIntoId` (Review Focus 4): ignore, then `update(account.copy(name = "x", isIgnored = false))` → stored row still ignored.
  - Drift: `ignored account has no drift`.
  - Cash entry: `ignored account is not offered`.
  - Use case `passes now from the clock`.
- [ ] **Step 2: Run** the touched test classes → FAIL.
- [ ] **Step 3: Implement.** `update()` copies `isIgnored` and `mergedIntoId` from the stored row like `isArchived`. Drift and cash entry filter `!it.isIgnored` next to `!it.isArchived`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(account): ignore an account and stop booking it`.

### Task 6: Accounts screen

**Files:**
- Create: `ui/settings/accounts/AccountsSettingsContract.kt`, `AccountsSettingsScreen.kt`, `AccountsSettingsViewModel.kt` (split the screen file by component if it passes the size limit)
- Modify: `ui/navigation/NavKeys.kt` (`data object AccountsSettings : NavKey`), `ui/navigation/FinoteNavHost.kt`, `ui/settings/SettingsScreen.kt`, `SettingsContract.kt`, `SettingsViewModel.kt`
- Modify: `res/values*/strings.xml` (4 locales)
- Test: `test/.../ui/settings/accounts/AccountsSettingsViewModelTest.kt`, `test/.../ui/settings/SettingsViewModelTest.kt`; screenshot preview per `docs/design-system.md` "Screenshot workflow"

**Interfaces:**
- Consumes: `AccountRepository.observeAccounts()`, `getLiveTransactionCount`, `getTrashableTransactionCount` (Tasks 4–5), `MergeAccountsUseCase`, `SetAccountIgnoredUseCase`, `AccountMergeRejectedException`.
- Produces:
  ```kotlin
  data class AccountsSettingsState(
      val accounts: List<Account> = emptyList(),   // live; followed first by sortOrder, ignored last
      val selected: Account? = null,               // bottom sheet open
      val mergeTargets: List<Account> = emptyList(),// live, same bankPackageName, id != selected
      val pendingMerge: Pair<Account, Account>? = null, val mergeCount: Int = 0,
      val isIgnoreDialogOpen: Boolean = false, val trashableCount: Int = 0,
  )
  sealed interface AccountsSettingsIntent {
      data class AccountClicked(val id: String); data object SheetDismissed
      data class MergeTargetPicked(val targetId: String); data object MergeConfirmed
      data object IgnoreClicked; data class IgnoreConfirmed(val shouldTrash: Boolean)
      data object FollowAgainClicked; data object DialogDismissed
  }
  sealed interface AccountsSettingsEffect {
      data object Merged; data object Ignored; data object FollowingAgain
      data object MergeBlockedByTransfer; data object ActionFailed
  }
  ```
  Settings gets `SettingsIntent.AccountsClicked` → `SettingsEffect.OpenAccountsSettings`.

Behaviour (spec "UI"): an account without `bankPackageName` opens nothing; "Merge into…" disabled with a hint when `mergeTargets` is empty; merge dialog shows names, balances and `mergeCount`; ignore checkbox hidden when `trashableCount == 0`; follow again runs at once. Effects become snackbars in the route.

- [ ] **Step 1: Write failing VM tests** (Turbine, `FakeAccountRepository`):
  - `ignored accounts are listed last`.
  - `cash account opens no sheet`.
  - `merge targets are live accounts of the same bank only`.
  - `confirm merge calls the use case and emits Merged`; `transfer rejection emits MergeBlockedByTransfer`; `other failure emits ActionFailed`.
  - `ignore dialog carries the trashable count`; `confirm ignore passes shouldTrash`; `follow again emits FollowingAgain`.
  - Settings: `AccountsClicked emits OpenAccountsSettings`.
- [ ] **Step 2: Run** `--tests '*AccountsSettingsViewModelTest*' --tests '*SettingsViewModelTest*'` → FAIL.
- [ ] **Step 3: Implement** the VM (`BaseViewModel`, collect like `NotificationCaptureSettingsViewModel`), the screen with `FinoteListRow`, `FinoteBottomSheet`, `FinoteDialog`, `MoneyText`, snackbar, the nav entry and the Settings row (new "Accounts" group above capture), and strings in 4 locales.
- [ ] **Step 4: Run** `testDebugUnitTest` → PASS; render the screenshot preview and look at it.
- [ ] **Step 5: Commit** `feat(ui): add accounts settings with merge and ignore`.

### Task 7: Gate and roadmap

**Files:**
- Modify: `docs/roadmap.md` (status line under P2c follow-up)

- [ ] **Step 1: Run** `qualityGate` through `gradle-run` → `exit_status` 0.
- [ ] **Step 2: Update roadmap** with the date, "code done, not tried on a device", and the P2c debt line "Một `New` có mask…" marked as repairable by merge.
- [ ] **Step 3: Commit** `docs(roadmap): mark account merge and ignore done`.
- [ ] **Step 4: Run `reviewer`** on the whole branch, then squash into `dev` after user approval.
