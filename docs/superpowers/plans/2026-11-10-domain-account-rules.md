# Domain Account Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One domain rule each for "usable account", "transaction touches account" and "balance anchor"; a safe
`AccountRepository.update()`; ViewModels that call use cases only.

**Architecture:** Pure domain helpers (`Account.isUsable`, `Transaction.touches`, `BalanceAnchor.kt`) replace inline
copies in data, domain and ui. New thin use cases sit between ViewModels and repositories; a data-side helper maps
`SQLException` to a domain `StorageException`. Konsist locks both rules.

**Tech Stack:** Kotlin, Room 3 (`androidx.room3`), Hilt, coroutines/Flow, JUnit4, Robolectric (data tests), Konsist.

**Spec:** `docs/superpowers/specs/2026-11-10-domain-account-rules-design.md`

## Global Constraints

- Branch `chore/domain-account-rules` (already created from `dev`). Four commits, one per spec part, in order A9 → A8 →
  A20 → A11. Each commit leaves `testDebugUnitTest` green.
- The main session commits, and only after the user approves that commit. Implementer subagents never commit.
- Commit types: `feat fix refactor test docs build ci chore perf`. Scopes allowed by `git_guard`: `parser dedup pairing
  balance db notif ui ota build`, or none. `account` is rejected. No AI attribution lines.
- Money is `Long` VND. Use `Math.addExact`/`subtractExact` as the existing balance code does.
- Never log notification content, balances or account numbers.
- Gradle only through the `gradle-run` skill; from Bash pass `'.\gradlew.bat'`. Per task: the named test class(es) via
  `testDebugUnitTest --tests ...`; per commit: `spotlessKotlinCheck detekt testDebugUnitTest`; end: `qualityGate`.
- `TransactionDao` is at the detekt `TooManyFunctions` limit (11): add no function to it. `@Suppress` is not allowed
  for it.
- Test package paths below are under `app/src/test/java/io/github/toanhp123/finote/`; production under
  `app/src/main/java/io/github/toanhp123/finote/`.

## Review Focus

1. An archived or ignored account's old transaction is edited → `update` is rejected (the stored row's accounts are
   checked, not only the new ones). Pinned in Task 2.
2. Two rows with the same `timestamp`, one a bank row with a post balance → cold start anchor and drift check agree, so
   no drift banner right after cold start. Pinned in Task 4.
3. A merge after the user pressed Adjust → balance stays equal to the bank, no drift booked twice. Pinned in Task 4.
4. The last-manual-choice read fails at cash-entry open → the sheet shows empty lists and a disabled Save, no crash.
   Pinned in Task 8.
5. A Room write throws `SQLiteException` while saving from the cash sheet or the list sheet → the save-error state
   shows, no crash. Pinned in Tasks 6 and 8.

---

## Part 1 — A9 (commit 1)

### Task 1: `Account.isUsable` and `Transaction.touches`

**Files:**
- Modify: `domain/account/model/Account.kt`, `domain/transaction/model/Transaction.kt`
- Modify: `domain/account/BalanceCalculator.kt:66-68` (delete private `touches`), `domain/account/BalanceDriftDetector.kt:24,34-35` (delete `isOf`)
- Test: `domain/account/model/AccountUsableTest.kt` (new), `domain/transaction/model/TransactionTouchesTest.kt` (new)

**Interfaces:**
- Produces: `val Account.isUsable: Boolean` (member property); `fun Transaction.touches(accountId: String): Boolean`
  (member function). Bodies are fixed by the spec, Part 1.

- [ ] **Step 1: Write the failing tests**

`AccountUsableTest` (use `testAccount()` from `domain/account/AccountFixtures.kt`):
- `live account is usable` → `testAccount().isUsable` is true.
- `deleted, archived or ignored account is not usable` → each of `copy(deletedAt = 1)`, `copy(isArchived = true)`,
  `copy(isIgnored = true)` gives false.

`TransactionTouchesTest` (use `tx(...)` from `domain/account/TransactionFixtures.kt`):
- `source account is touched` → `tx(EXPENSE, 1, account = A).touches(A)` true, `.touches(B)` false.
- `transfer destination is touched` → `tx(TRANSFER, 1, account = A, to = B).touches(B)` true.
- `non-transfer toAccountId is not touched` → `tx(EXPENSE, 1, account = A, to = B).touches(B)` false (build it with
  `copy` if the model's `init` rejects it; drop the test if it cannot be built).
- `deleted row touches nothing` → `tx(EXPENSE, 1).copy(deletedAt = 1).touches(A)` false.

- [ ] **Step 2: Run, expect compile failure** — `testDebugUnitTest --tests '*AccountUsableTest' --tests '*TransactionTouchesTest'`.
- [ ] **Step 3: Implement** both members with KDoc from the spec. Replace the two private copies with
  `Transaction.touches`; in `detectBalanceDrift` the filter becomes `it.touches(account.id)`.
- [ ] **Step 4: Run** the two new tests plus `*BalanceCalculatorTest`, `*BalanceDriftTest`, `*MonthlySnapshotsTest` → PASS.

### Task 2: Every "usable" site uses `isUsable`; write path blocks ignored

**Files:**
- Modify: `domain/transaction/repository/TransactionRepository.kt:15-16` (rename + KDoc)
- Modify: `data/transaction/TransactionRepositoryImpl.kt:32-35,104-112,145` (rename, `requireUsableAccount`, message)
- Modify: `domain/transaction/usecase/ValidateTransactionUseCase.kt`, `UpdateTransactionUseCase.kt:20-22` (callers of the rename; message text)
- Modify: `data/notification/NotificationSettlementRepositoryImpl.kt:126-128`
- Modify: `ui/cashentry/CashEntryDefaults.kt:15`, `ui/cashentry/CashEntryViewModel.kt:117-120`, `ui/transactions/TransactionListViewModel.kt:76`, `domain/account/usecase/ObserveBalanceDriftUseCase.kt:25`
- Modify: `data/transaction/TransactionDao.kt` — one-line comment above each of the 3 "touches" queries: `// Same rule as Transaction.touches.`
- Modify: `test/.../domain/transaction/usecase/FakeTransactionRepository.kt` (rename; usable = `isUsable`)
- Test: `data/transaction/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `Account.isUsable` (Task 1).
- Produces: `suspend fun TransactionRepository.getUsableAccountIds(accountIds: Set<String>): Set<String>` — the subset
  that exists and `isUsable`. Error text constant: `"account does not exist, is deleted, archived or ignored"`.

- [ ] **Step 1: Write failing tests** in `TransactionRepositoryImplTest`:
- `add on an ignored account is rejected` → `assertFailsWith<IllegalArgumentException>`; no row stored; balance unchanged.
- `update of a row on an ignored account is rejected` → account ignored after the row was added; update to a usable
  account B still throws (stored row's account is checked — Review Focus 1).
- `usable account ids skip ignored accounts` (next to the existing archived/deleted tests at :513-525).
- [ ] **Step 2: Run** `--tests '*TransactionRepositoryImplTest'` → the three FAIL.
- [ ] **Step 3: Implement** the rename and `requireUsableAccount` (`account?.toDomain()?.isUsable == true`), then
  replace every inline filter listed in Files with `isUsable` (`filter { it.isUsable }` / `check(account.toDomain().isUsable)`).
  Keep `AccountResolver` unchanged.
- [ ] **Step 4: Run** `testDebugUnitTest` (whole suite: the rename touches fakes and VM tests) → PASS. Update test
  expectations only where they asserted that an ignored account was writable.
- [ ] **Step 5: Gate and commit (after user approval)**

Run: `spotlessKotlinCheck detekt testDebugUnitTest` → exit 0.
```bash
git add -A app/src
git commit -m "refactor(balance): one usable-account and touches rule"
```
Body: lists `isUsable`, `touches`, rename, and the behavior change "ignored accounts reject manual add and update".

---

## Part 2 — A8 (commit 2)

### Task 3: `BalanceAnchor.kt` and drift on top of it

**Files:**
- Create: `domain/account/BalanceAnchor.kt`
- Modify: `domain/account/BalanceDriftDetector.kt` (use the anchor; `LEDGER_ORDER` moves out)
- Modify: `domain/account/BalanceCalculator.kt:141-146` (`initialBalanceFor` moves into `BalanceAnchor.kt` as `private`)
- Test: `domain/account/BalanceAnchorTest.kt` (new); `BalanceDriftTest.kt` stays green unchanged

**Interfaces:**
- Consumes: `Transaction.touches` (Task 1).
- Produces (exact, from spec Part 2):
  - `internal val LEDGER_ORDER: Comparator<Transaction>` — `timestamp`, `createdAt`, `id`.
  - `internal data class BalanceAnchor(val postBalance: Long, val ledger: List<Transaction>, val settledAfter: List<Transaction>)`
  - `internal fun findBalanceAnchor(account: Account, transactions: List<Transaction>): BalanceAnchor?`
  - `fun BalanceCalculator.anchoredInitialBalance(account: Account, transactions: List<Transaction>): Long?`

`findBalanceAnchor`: credit card → null. `mine` = `transactions.filter { it.touches(account.id) }.sortedWith(LEDGER_ORDER)`;
`p` = last index with `accountId == account.id && postBalance != null`; none → null. `ledger = mine.take(p + 1)`,
`settledAfter = mine.drop(p + 1).filter { it.type == ADJUSTMENT && it.accountId == account.id }`.
`anchoredInitialBalance` = `postBalance - balance(account.id, 0, ledger + settledAfter)` (exact math).
`detectBalanceDrift`: anchor null or `settledAfter.isNotEmpty()` → null; else compare `postBalance` with
`balance(account.id, account.initialBalance, ledger)` as today.

- [ ] **Step 1: Write failing tests** in `BalanceAnchorTest` (rows need distinct `id`s and a `postBalance`; set them with `copy`):
- `received transfer never anchors` → only row is `TRANSFER` from B to A with `postBalance = 5` → `findBalanceAnchor(A)` null.
- `deleted row never anchors` → null.
- `credit card has no anchor` → `type = CREDIT_CARD` → null.
- `ties break by createdAt then id` → two rows `timestamp = 10`; the one with larger `createdAt` carries the post
  balance and is the anchor; a third row with same `timestamp` and `createdAt` but larger `id` is outside `ledger`.
- `adjustment after the anchor is settled into the start` → bank `EXPENSE 100_000`, `postBalance = 800_000`, at 1;
  `adjustment(OUT, 100_000, at = 2)` → `anchoredInitialBalance == 1_000_000`.
- `no adjustment after the anchor` → bank `EXPENSE 100_000`, `postBalance = 800_000` → `900_000`.
- [ ] **Step 2: Run** `--tests '*BalanceAnchorTest'` → compile FAIL.
- [ ] **Step 3: Implement** per Interfaces. Remove `initialBalanceFor` from `BalanceCalculator.kt`.
- [ ] **Step 4: Run** `--tests '*BalanceAnchorTest' --tests '*BalanceDriftTest' --tests '*BalanceCalculatorTest'` → PASS.

### Task 4: Cold start and merge use the anchor

**Files:**
- Modify: `data/notification/NotificationSettlementRepositoryImpl.kt:83,163-177` (`coldStart(account)`)
- Modify: `data/account/AccountRepositoryImpl.kt:130-159` (`withMergedStart`)
- Test: `data/account/AccountMergeTest.kt`, `data/notification/NotificationSettlementRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `BalanceCalculator.anchoredInitialBalance` (Task 3).

- [ ] **Step 1: Write failing tests**
- `AccountMergeTest`: `merge after Adjust keeps balance and books no drift` → target start 1_000_000 initialized; bank
  row `EXPENSE 100_000` with `postBalance = 800_000`; `adjustDrift`; merge an empty source of the same bank → target
  `currentBalance == 800_000`, `initialBalance == 1_000_000`, `detectBalanceDrift(target, rows) == null` (Review Focus 3).
- `AccountMergeTest`: `received transfer post balance does not re-anchor` → target's own bank row `postBalance = 500`,
  a later `TRANSFER` into target carrying `postBalance = 9_999` → start computed from the 500 row.
- `NotificationSettlementRepositoryImplTest`: `cold start then drift is null on equal timestamps` → a manual row and
  the booked bank row share `timestamp`; after `book`, `detectBalanceDrift` on stored rows is null (Review Focus 2).
- [ ] **Step 2: Run** `--tests '*AccountMergeTest' --tests '*NotificationSettlementRepositoryImplTest'` → new tests FAIL.
- [ ] **Step 3: Implement** `coldStart(account: AccountEntity)`: skip when initialized; rows =
  `getActiveByAccount(account.id).map { it.toDomain() }`; non-null `anchoredInitialBalance(account.toDomain(), rows)`
  → `update(copy(initialBalance = it, isInitialized = true))`. `book()` keeps calling `rebuild` after it.
  `withMergedStart`: first branch becomes `anchoredInitialBalance(target.toDomain(), rows)?.let { ... }`; the other two
  branches unchanged. Delete the now-unused `AccountType` import if any.
- [ ] **Step 4: Run** the two classes plus `*TransactionRepositoryAdjustDriftTest` → PASS.
- [ ] **Step 5: Gate and commit (after user approval)**

Run: `spotlessKotlinCheck detekt testDebugUnitTest` → exit 0.
```bash
git add -A app/src
git commit -m "fix(balance): one anchor rule for cold start, merge and drift"
```
Body: received transfers never anchor (P2d debt); adjustments after the anchor count in the start; shared tie order.

---

## Part 3 — A20 (commit 3)

### Task 5: `update()` keeps machine-owned fields

**Files:**
- Modify: `data/account/AccountRepositoryImpl.kt:38-54`
- Modify: `domain/account/repository/AccountRepository.kt:20-27` (KDoc lists editable fields)
- Modify: `domain/account/usecase/UpdateAccountUseCase.kt:15-17`
- Modify: `test/.../domain/account/usecase/FakeAccountRepository.kt` (`update` honors the same contract)
- Modify: `docs/roadmap.md` (P2c/UI debt list): "Màn sửa tài khoản cần `setInitialBalance(id, value)`: đặt `isInitialized = true` và rebuild."
- Test: `data/account/AccountRepositoryImplTest.kt`, `domain/account/usecase/UpdateAccountUseCaseTest.kt`

**Interfaces:**
- Produces: `update(account)` copies only `name`, `icon`, `color`, `sortOrder`, `isExcludedFromNetWorth`, `updatedAt`
  from the argument; `nameKey` = stored one if `name` unchanged else null. No `rebuild` call.

- [ ] **Step 1: Write failing tests**
- `AccountRepositoryImplTest`: `update keeps type, currency, bank key, mask and initial balance` → pass an `Account`
  with all five changed → stored row has the old five, new `name`/`color`.
- `AccountRepositoryImplTest`: `update keeps an uninitialized account uninitialized`.
- `UpdateAccountUseCaseTest`: replace the `isInitialized = true` expectation with `isInitialized` unchanged.
- [ ] **Step 2: Run** `--tests '*AccountRepositoryImplTest' --tests '*UpdateAccountUseCaseTest'` → FAIL.
- [ ] **Step 3: Implement**: build the entity from `old.copy(<editable fields from account>)`; drop the `rebuild` line;
  use case only stamps `updatedAt` and validates.
- [ ] **Step 4: Run** same → PASS.
- [ ] **Step 5: Gate and commit (after user approval)**

Run: `spotlessKotlinCheck detekt testDebugUnitTest` → exit 0.
```bash
git add -A app/src docs/roadmap.md
git commit -m "fix(db): keep machine-owned account fields on update"
```

---

## Part 4 — A11 (commit 4)

### Task 6: `StorageException` and the write helper

**Files:**
- Create: `domain/common/StorageException.kt`
- Modify: `data/transaction/TransactionRepositoryImpl.kt` (all `db.withWriteTransaction` → private `write { }`)
- Test: `data/transaction/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Produces: `class StorageException(cause: Throwable) : RuntimeException(cause)` — KDoc: "A local database read or
  write failed. Carries no message: the content may be financial data."
- Private in `TransactionRepositoryImpl`: `suspend fun <T> write(block: suspend () -> T): T` — runs
  `db.withWriteTransaction(block)`; catches `android.database.SQLException` and throws `StorageException(it)`.

- [ ] **Step 1: Write failing test** `write maps SQLException to StorageException` → close the DB (`db.close()`) or
  insert a duplicate primary key via `add` twice with the same id; `assertFailsWith<StorageException>`. Pick whichever
  Room reports as `SQLiteException` under Robolectric; the duplicate id is preferred (`SQLiteConstraintException`).
- [ ] **Step 2: Run** `--tests '*TransactionRepositoryImplTest'` → FAIL (raw `SQLiteConstraintException`).
- [ ] **Step 3: Implement** per Interfaces. `IllegalArgumentException` from `require` passes through unchanged.
  If the test DB driver throws a type that is not an `android.database.SQLException`, stop and report it; do not
  widen the catch to `Exception`.
- [ ] **Step 4: Run** same → PASS. Fix any existing test that expected the raw SQLite type.

### Task 7: Read use cases for accounts, categories and rules; rewire four ViewModels

**Files:**
- Create in `domain/account/usecase/`: `ObserveAccountsUseCase.kt`, `ObserveUsableAccountsUseCase.kt`, `CountMergeMovesUseCase.kt`, `CountTrashableTransactionsUseCase.kt`
- Create in `domain/category/usecase/`: `ObserveCategoriesUseCase.kt`
- Create in `domain/notification/usecase/`: `ObserveParserRulesUseCase.kt`, `ObserveRuleUpdateStatusUseCase.kt`
- Create: `domain/notification/model/RuleUpdateStatus.kt`
- Modify: `domain/account/repository/AccountRepository.kt:14-15` (KDoc: "ordered by `sortOrder`, then `createdAt`")
- Modify: `domain/account/usecase/ObserveBalanceDriftUseCase.kt` (consume `ObserveUsableAccountsUseCase`; drop `sortedBy`)
- Modify: `ui/settings/accounts/AccountsSettingsViewModel.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/capture/NotificationCaptureSettingsViewModel.kt`, `ui/transactions/TransactionListViewModel.kt`
- Test: one small test per use case next to the existing ones; the four VM tests construct the real use cases around their existing fakes.

**Interfaces** (each `class X @Inject constructor(...)` with one `operator fun invoke`):
- `ObserveAccountsUseCase(repo: AccountRepository)`: `(): Flow<List<Account>>` — `repo.observeAccounts()`.
- `ObserveUsableAccountsUseCase(observeAccounts: ObserveAccountsUseCase)`: `(): Flow<List<Account>>` — filter `isUsable`, order kept.
- `CountMergeMovesUseCase(repo)`: `suspend (accountId: String): Int` — `getLiveTransactionCount`.
- `CountTrashableTransactionsUseCase(repo)`: `suspend (accountId: String): Int` — `getTrashableTransactionCount`.
- `ObserveCategoriesUseCase(repo: CategoryRepository)`: `(): Flow<List<Category>>`.
- `ObserveParserRulesUseCase(repo: ParserRuleRepository)`: `(): Flow<List<ParserRule>>`.
- `data class RuleUpdateStatus(val isAutoUpdateEnabled: Boolean, val lastCheck: RuleUpdateCheck?)`
- `ObserveRuleUpdateStatusUseCase(repo: RuleUpdateSettingsRepository)`: `(): Flow<RuleUpdateStatus>` — `combine` of the two flows.

- [ ] **Step 1: Write failing tests**: `ObserveUsableAccountsUseCaseTest` (`drops deleted, archived and ignored, keeps
  order`) and `ObserveRuleUpdateStatusUseCaseTest` (`combines both values`). The pass-through use cases need no test.
- [ ] **Step 2: Run** them → compile FAIL.
- [ ] **Step 3: Implement** the use cases, then rewire: `AccountsSettingsViewModel` (observe + two counts),
  `SettingsViewModel` and `NotificationCaptureSettingsViewModel` (rules, status — keep each existing `.catch` and the
  `collectInto` shape), `TransactionListViewModel` (`ObserveAccountsUseCase` + `ObserveCategoriesUseCase`; chips =
  `accountList.filter(Account::isUsable)`, already in DAO order).
- [ ] **Step 4: Run** `testDebugUnitTest` → PASS. VM tests change only their constructor wiring.

### Task 8: Cash entry through use cases; ViewModels catch `StorageException`

**Files:**
- Create in `domain/transaction/usecase/`: `GetLastManualChoicesUseCase.kt`, `AddManualTransactionUseCase.kt`
- Create: `domain/transaction/model/ManualChoices.kt`
- Modify: `ui/cashentry/CashEntryViewModel.kt` (constructor, `init`, `save`, drop `timestampOf`/`NOON`/`android.database` import)
- Modify: `ui/transactions/TransactionListViewModel.kt:3,179,195` (`SQLException` → `StorageException`)
- Test: `domain/transaction/usecase/AddManualTransactionUseCaseTest.kt`, `GetLastManualChoicesUseCaseTest.kt` (new); `ui/cashentry/CashEntryViewModelTest.kt`

**Interfaces:**
- `data class ManualChoices(val accountId: String?, val categoryIds: Map<TransactionType, String?>)` — keys `EXPENSE`, `INCOME`.
- `GetLastManualChoicesUseCase(repo: TransactionRepository)`: `suspend (): ManualChoices`.
- `AddManualTransactionUseCase(add: AddTransactionUseCase, clock: Clock)`:
  `suspend (amount: Long, type: TransactionType, accountId: String, categoryId: String?, date: LocalDate, note: String)`.
  Today (`LocalDate.now(clock)`) → `clock.millis()`; another day → `date.atTime(12, 0).atZone(clock.zone)` epoch ms.
  New `UUID`, `feeAmount = 0`, `adjustmentDirection = null`, `toAccountId = null`, `source = MANUAL`,
  `note = note.trim()` (blank stays `""`).
- `CashEntryViewModel` constructor: `ObserveUsableAccountsUseCase`, `ObserveCategoriesUseCase`,
  `GetLastManualChoicesUseCase`, `AddManualTransactionUseCase`, `Clock`. `init` = one flow:
  `combine(flow { emit(getLastManualChoices()) }, observeUsableAccounts(), observeCategories())`, seed choices on the
  first value only, then `.catch { }` (keeps empty lists, Save disabled; logs nothing) and `collect`.
  `save` catches `IllegalArgumentException` and `StorageException`.

- [ ] **Step 1: Write failing tests**
- `AddManualTransactionUseCaseTest` (fixed clock `2026-11-10T08:30Z`, zone `Asia/Ho_Chi_Minh`):
  `today keeps the clock time` → `timestamp == clock.millis()`; `another day is local noon` →
  `2026-11-09` gives `2026-11-09T12:00+07:00` epoch ms; `note is trimmed, blank stays empty`; `source is MANUAL`.
- `GetLastManualChoicesUseCaseTest`: `reads account and both category types`.
- `CashEntryViewModelTest`: `failing last-choice read leaves empty lists and save disabled` → fake throws from
  `getLastManualAccountId` → state `accounts` empty, `isSaveEnabled` false, no crash (Review Focus 4).
- `CashEntryViewModelTest`: `storage failure on save shows save error` → fake `add` throws `StorageException` →
  `hasSaveError` true, `isSaving` false (Review Focus 5).
- [ ] **Step 2: Run** these classes → FAIL.
- [ ] **Step 3: Implement** per Interfaces. `onLists` keeps its selection logic; `lastAccountId` comes from `ManualChoices`.
- [ ] **Step 4: Run** `testDebugUnitTest` → PASS.

### Task 9: Konsist rules and `architecture.md`

**Files:**
- Modify: `test/.../architecture/LayerDependencyKonsistTest.kt`
- Modify: `docs/architecture.md:55,70`

**Interfaces:**
- Consumes: helpers in `architecture/KonsistScopes.kt` (`filesIn`, `assertNoImports`, `productionFiles`, `UI`).

- [ ] **Step 1: Add tests**
- `view models do not use repositories` → files named `*ViewModel.kt` import nothing with segment `repository`
  (`import.name.hasSegment("repository")`); and every class ending `ViewModel` has no primary-constructor parameter
  whose type name ends with `Repository`.
- `ui does not use the database` → `filesIn(UI).assertNoImports(...) { it.isFrom("android.database") || it.isFrom("androidx.sqlite") }`.
- [ ] **Step 2: Prove they bite**: temporarily add `import ...domain.account.repository.AccountRepository` to one VM,
  run `--tests '*LayerDependencyKonsistTest'` → FAIL; remove it → PASS.
- [ ] **Step 3: Docs**: line 55 → "Screen sends `Intent`, ViewModel calls use cases (reads included), …"; line 70 →
  "Take intents, call use cases, emit state. Never a repository".
- [ ] **Step 4: Gate and commit (after user approval)**

Run: `spotlessKotlinCheck detekt testDebugUnitTest` → exit 0.
```bash
git add -A app/src docs/architecture.md
git commit -m "refactor: view models call use cases only"
```

### Task 10: Branch gate

- [ ] Run `qualityGate` through `gradle-run` → `exit_status` 0. Fix findings in the owning task's files, re-run.
- [ ] Hand the branch to `reviewer` (once), then squash into `dev` per `docs/git-workflow.md` after user approval.
