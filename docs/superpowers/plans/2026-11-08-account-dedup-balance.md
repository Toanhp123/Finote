# Account Dedup Balance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn parsed `PENDING` notification logs into transactions on the right account, 30 s after they arrive, with wallet/bank dedup, repost dedup, cold-start initial balance and a balance drift banner.

**Architecture:**
- `domain/notification` gains a pure planner (`planSettlement`, `resolveAccount`) that turns pending logs into decisions (`Book`, `Fail`, `Repost`), the `SettlePendingLogsUseCase` that runs it, and two interfaces (`NotificationSettlementRepository`, `SettleScheduler`).
- `domain/account` gains pure balance helpers (`BalanceCalculator.initialBalanceFor`, `detectBalanceDrift`) and `ObserveBalanceDriftUseCase`.
- `data/notification` writes each decision in one write transaction (account, transaction, cold start, rebuild, log status).
- `work` gains `SettleWorker` and `WorkSettleScheduler` (unique work `settle-notifications`).
- `ui/transactions` shows one drift banner per account with an "Adjust" button.

**Tech Stack:** Kotlin, Room 3, Hilt, WorkManager + `hilt-work`, kotlinx-serialization, Compose Material 3, JUnit4, Robolectric, Turbine, Konsist.

**Spec:** `docs/superpowers/specs/2026-11-08-account-dedup-balance-design.md`

## Global Constraints

- **Branch:** `feature/account-dedup-balance` from `dev`. The main session commits, after user approval. `coder` never commits. No AI attribution in commit messages.
- **Layers** (`docs/architecture.md`): `domain` has no `android.*`, `androidx.*`, Room or Hilt; `work` and `ui` depend on `domain` only; one public class per file; use cases expose one `operator fun invoke`; a `Flow` function is `observeX`; booleans start with `is`/`has`/`should`; `*Repository` is an interface in `domain/<f>/repository`.
- **Money** is `Long` VND. Overflow uses `Math.*Exact`.
- **Logging:** never log notification text, amounts, balances or account numbers. Ids and status codes only.
- **Strings:** every new string in `values`, `values-en`, `values-ja`, `values-zh-rCN`.
- **DB:** version 5 → 6, schema exported; no migration (destructive fallback before v1.0).
- **Windows:** pair `30_000` ms; due when `postTime <= now - 30_000`; repost `600_000` ms with `parsedPostBalance`, `3_000` ms without.
- **Work:** unique name `settle-notifications`; after capture: initial delay 35 s, `ExistingWorkPolicy.REPLACE`; from app open: no delay, `REPLACE`; backoff `BackoffPolicy.LINEAR`, 30 s.
- **Fail reasons** (exact strings): `ambiguous_account`, `account_archived`, `invalid_amount`.
- **New account:** name `"<bankName> ••<mask>"` or `bankName` when mask is null; `currency = "VND"`; `icon = type.name.lowercase()`; `color = 0xFF546E7A.toInt()`; `sortOrder = 0`; `initialBalance = 0`; `isInitialized = false`.
- **Verification:** `testDebugUnitTest` through the `gradle-run` skill after every task; `qualityGate` before the branch review.

## Review Focus

1. **The process dies between capture and the worker.** Opening the app must book every due log. Test: Task 5, `settle books due logs with no scheduler involvement`, and Task 6, `settleNow enqueues with no delay`.
2. **The rule that parsed a log was removed by OTA.** `matchedRuleId` is missing from the rule map: book as `BANK` with `bankName = packageName`, never crash. Test: Task 3, `missing rule books as bank named after the package`.
3. **A soft-deleted account has the same key.** It must not be matched; a new account is created. Test: Task 2, `deleted account is ignored`.
4. **The user deletes an auto-booked transaction.** It must not come back on the next run. Test: Task 4, `deleted notification transaction is not booked again`.
5. **A rescan after reboot brings hours-old logs.** They are due at once and still pair and repost-check normally. Test: Task 3, `old rescanned logs pair at once`.

---

### Task 1: Schema and rule account type

**Files:**
- Modify: `data/account/AccountEntity.kt`, `domain/account/model/Account.kt`, `data/account/AccountMapper.kt`
- Modify: `data/transaction/TransactionEntity.kt`, `domain/transaction/model/Transaction.kt`, `data/transaction/TransactionMapper.kt`
- Modify: `data/notification/ParserRuleEntity.kt`, `domain/notification/model/ParserRule.kt`, `data/notification/ParserRuleMapper.kt`, `data/notification/RuleFeedCodec.kt` (`RuleDto`), `data/notification/SystemParserRule.kt`
- Modify: `domain/notification/model/NotificationLog.kt`, `data/notification/NotificationLogMapper.kt` (map existing `linkedTransactionId`)
- Modify: `data/FinoteDatabase.kt` (`version = 6`), new `app/schemas/.../6.json`, `rules/v1/feed.json` (re-signed)
- Test: `data/account/AccountMapperTest.kt`, `data/transaction/TransactionDaoTest.kt`, `data/notification/ParserRuleMapperTest.kt`, `data/notification/RuleFeedCodecTest.kt`, fixtures in `test/.../data/Fixtures.kt` and `NotificationFixtures.kt`

**Interfaces:**
- Produces:
  - `Account.bankPackageName: String? = null`, `Account.accountNumberMask: String? = null`; entity index `Index(value = ["bankPackageName", "accountNumberMask"])` (not unique).
  - `Transaction.postBalance: Long? = null`, `Transaction.notificationLogId: String? = null`; entity FK to `NotificationLogEntity.id`, `onDelete = SET_NULL`, `Index(value = ["notificationLogId"])`.
  - `ParserRule.accountType: AccountType? = null` (null = `BANK`); `ParserRuleEntity.accountType: String?`; `RuleDto.accountType: String? = null`.
  - `NotificationLog.linkedTransactionId: String? = null`.
  - `SystemParserRules`: every `momo_*` rule `accountType = "WALLET"`; `mb_card_v1`, `tcb_card_v1` `"CREDIT_CARD"`; others null.

- [ ] **Step 1: Write failing tests**
  - `AccountMapperTest.bank key round trips`: entity ↔ domain keeps `bankPackageName = "com.VCB"`, `accountNumberMask = "1234"`.
  - `TransactionDaoTest.post balance and log link round trip`: insert a log, then a transaction with `postBalance = 1_500_000L`, `notificationLogId = log.id`; read back equal.
  - `TransactionDaoTest.deleting the log clears the link`: delete the log row → `notificationLogId == null`, transaction kept.
  - `ParserRuleMapperTest.account type maps and bad value drops the row`: `"WALLET"` → `AccountType.WALLET`; null → null; `"SPACESHIP"` → `toDomain() == null`.
  - `RuleFeedCodecTest.feed without accountType decodes to null` and `feed with accountType decodes it`.
  - `SystemParserRulesTest.account types`: every rule with prefix `momo_` is `"WALLET"`; `mb_card_v1`, `tcb_card_v1` are `"CREDIT_CARD"`; the rest null.
- [ ] **Step 2: Run** `testDebugUnitTest --tests '*AccountMapperTest*' --tests '*TransactionDaoTest*' --tests '*ParserRuleMapperTest*' --tests '*RuleFeedCodecTest*' --tests '*SystemParserRulesTest*'` → FAIL (unresolved members).
- [ ] **Step 3: Add the fields, mappers, index, FK, DB version 6.** Unknown `accountType` string in the entity makes `toDomain()` return null, like a bad `fallbackTransactionType`. Update fixtures with default nulls.
- [ ] **Step 4: Re-sign the feed.** Run `testDebugUnitTest --tests '*RuleFeedSigningTool*' -PsignRuleFeed=true` (needs `~/.finote/finote-rules.pem`; if the key is missing, stop and ask the user). Then the feed-matches-source test passes.
- [ ] **Step 5: Run** `testDebugUnitTest` → PASS.
- [ ] **Step 6: Commit** `feat(db): add bank key, post balance and rule account type`.

### Task 2: Account resolver

**Files:**
- Create: `domain/notification/AccountResolver.kt`, `domain/notification/model/AccountTarget.kt`
- Test: `test/.../domain/notification/AccountResolverTest.kt`

**Interfaces:**
- Consumes: `Account`, `ParserRule` (Task 1).
- Produces:
  ```kotlin
  sealed interface AccountTarget {
      data class Existing(val accountId: String) : AccountTarget
      data class New(val packageName: String, val mask: String?, val bankName: String, val type: AccountType) : AccountTarget
  }
  sealed interface AccountResolution {          // same file as resolveAccount, internal
      data class Resolved(val target: AccountTarget) : AccountResolution
      data class Rejected(val reason: String) : AccountResolution
  }
  internal fun resolveAccount(packageName: String, mask: String?, rule: ParserRule?, accounts: List<Account>): AccountResolution
  const val FAIL_AMBIGUOUS_ACCOUNT = "ambiguous_account"
  const val FAIL_ACCOUNT_ARCHIVED = "account_archived"
  const val FAIL_INVALID_AMOUNT = "invalid_amount"
  ```
  `accounts` may hold any account; the resolver ignores rows with `deletedAt != null` or a different `bankPackageName`.

- [ ] **Step 1: Write failing tests** (one per row):
  - `mask found`: account (`com.VCB`, `1234`) → `Existing(id)`.
  - `mask not found`: → `New("com.VCB", "1234", "Vietcombank", BANK)`.
  - `mask null, one account of the package` → `Existing`.
  - `mask null, no account` → `New(mask = null)`.
  - `mask null, two accounts` → `Rejected("ambiguous_account")`.
  - `archived match` → `Rejected("account_archived")`.
  - `deleted account is ignored` → `New`.
  - `wallet rule gives a wallet account`: rule `accountType = WALLET` → `New(type = WALLET)`.
  - `missing rule`: `rule = null` → `New(bankName = packageName, type = BANK)`.
- [ ] **Step 2: Run** `--tests '*AccountResolverTest*'` → FAIL.
- [ ] **Step 3: Implement** `resolveAccount`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(notif): resolve the account of a parsed notification`.

### Task 3: Settlement planner

**Files:**
- Create: `domain/notification/SettlementPlanner.kt`, `domain/notification/model/SettlementDecision.kt`
- Test: `test/.../domain/notification/SettlementPlannerTest.kt`

**Interfaces:**
- Consumes: `resolveAccount`, `AccountTarget`, fail constants (Task 2); `NotificationLog.linkedTransactionId` (Task 1).
- Produces:
  ```kotlin
  sealed interface SettlementDecision {
      data class Book(val bankSide: NotificationLog, val walletSide: NotificationLog?, val account: AccountTarget,
                      val merchant: String?, val note: String?) : SettlementDecision
      data class Fail(val log: NotificationLog, val partner: NotificationLog?, val reason: String) : SettlementDecision
      data class Repost(val log: NotificationLog, val original: NotificationLog) : SettlementDecision
  }
  const val PAIR_WINDOW_MILLIS = 30_000L
  const val REPOST_WINDOW_WITH_BALANCE_MILLIS = 600_000L
  const val REPOST_WINDOW_NO_BALANCE_MILLIS = 3_000L
  fun planSettlement(pending: List<NotificationLog>, settled: List<NotificationLog>, accounts: List<Account>,
                     rulesById: Map<String, ParserRule>, now: Long): List<SettlementDecision>
  ```
  Output order: every `Book` and `Fail` first (by `bankSide`/`log` `postTime`), then every `Repost`. A `Repost` is emitted only when its `original` is settled or is booked/failed in this plan; otherwise the repost log waits. `postBalance`, amount and type are read from `bankSide.parsed`.

Algorithm (spec "Planner"), the part the tests do not fully pin:
```
pool = pending sorted by postTime, id
1. repost pre-pass, oldest first, over pool (due or not): original = latest earlier log in settled ∪ pool
   with same packageName, title, content and |Δ| <= window(log.parsed.postBalance != null). Remove log from pool.
2. invalid: a due log with parsed == null or parsed.amount <= 0 → Fail(log, null, invalid_amount).
3. pairs: all (a, b) in pool, a.packageName != b.packageName, exactly one isWallet, same amount, same type,
   |Δ| <= 30 s, at least one due. Sort by |Δ|, then min(postTime). Take greedily, skipping taken logs.
   bankSide = the non-wallet log. Resolve its account: Rejected → Fail(bankSide, walletSide, reason).
4. singles: every remaining due log → resolve → Book or Fail(log, null, reason).
isWallet(log) = rulesById[log.matchedRuleId]?.accountType == WALLET
merchant/note of a pair: walletSide value when non-null, else bankSide value.
```

- [ ] **Step 1: Write failing tests** (logs built with a helper `log(pkg, ruleId, postTime, amount, type, mask?, balance?, merchant?, text?)`, `now` fixed):
  - `bank then wallet pair`, `wallet then bank pair`: one `Book`, `bankSide` is the VCB log, `walletSide` the MoMo log, merchant from MoMo.
  - `same package twice stays two`: two equal VCB logs 5 s apart → two `Book`.
  - `31 s apart no pair`, `bank and bank no pair`, `opposite direction no pair`.
  - `nearest pair wins`: VCB 0 s, VCB 20 s, MoMo 21 s (all 50_000 EXPENSE, `now` = 60 s) → the 20 s log pairs with MoMo; the 0 s log is booked alone.
  - `not due waits`: one log at `now - 10 s` → empty plan; a due log with a not-due partner → paired.
  - `repost with balance within 10 min`, `after 10 min booked`, `no balance within 3 s repost`, `no balance 4 s booked`.
  - `repost of a settled original` (original in `settled`), `repost of an original booked in this plan` (Repost after Book), `repost of a not-due original waits`.
  - `not-due repost cannot pair`: VCB 0 s, MoMo 10 s, VCB repost of the first at 20 s, `now` = 40 s → one `Book` (pair), the repost not paired.
  - `pair with unmappable bank fails both`: two VCB accounts with null mask, log mask null → `Fail(bankSide, walletSide, "ambiguous_account")`.
  - `invalid amount fails`.
  - `missing rule books as bank named after the package`.
  - `old rescanned logs pair at once`: VCB and MoMo at `now - 3 h` → paired.
- [ ] **Step 2: Run** `--tests '*SettlementPlannerTest*'` → FAIL.
- [ ] **Step 3: Implement** `planSettlement`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(notif): plan settlement of pending notifications`.

### Task 4: Settlement repository

**Files:**
- Create: `domain/notification/repository/NotificationSettlementRepository.kt`, `data/notification/NotificationSettlementRepositoryImpl.kt`
- Modify: `data/notification/NotificationLogDao.kt`, `data/account/AccountDao.kt`, `domain/account/BalanceCalculator.kt`, `di/RepositoryModule.kt`
- Test: `test/.../data/notification/NotificationSettlementRepositoryImplTest.kt` (Robolectric), `test/.../domain/account/BalanceCalculatorTest.kt`

**Interfaces:**
- Consumes: `SettlementDecision`, `AccountTarget` (Tasks 2–3); `AccountBalanceRebuilder` (existing, `internal` in `data.account`).
- Produces:
  ```kotlin
  interface NotificationSettlementRepository {
      suspend fun getPendingLogs(): List<NotificationLog>            // status PENDING, by postTime, id
      suspend fun getSettledLogsSince(since: Long): List<NotificationLog> // not PENDING, not IGNORED_SPAM, postTime >= since
      suspend fun getAccounts(): List<Account>                        // every row with bankPackageName != null
      suspend fun getRulesById(): Map<String, ParserRule>
      /** One write transaction. Throws IllegalStateException when a log is no longer PENDING (all rolled back). */
      suspend fun book(decision: SettlementDecision.Book, transactionId: String, now: Long)
      /** Marks log (and partner) FAILED with the reason. False when nothing was PENDING. */
      suspend fun fail(decision: SettlementDecision.Fail): Boolean
      /** Marks the log DEDUPLICATED with this link. False when it was not PENDING. */
      suspend fun markRepost(logId: String, linkedTransactionId: String?): Boolean
  }
  fun BalanceCalculator.initialBalanceFor(accountId: String, postBalance: Long, transactions: List<Transaction>): Long
  // = postBalance - balance(accountId, 0, transactions)
  ```
  DAO additions: `NotificationLogDao.getPending()`, `getSettledSince(since)`, `markSettled(ids: List<String>, status, linkedTransactionId: String?, failureReason: String?): Int` (`WHERE id IN (:ids) AND status = 'PENDING'`); `AccountDao.getWithBankKey()`, `findByBankKey(packageName: String, mask: String?): AccountEntity?` (`deletedAt IS NULL AND bankPackageName = :p AND accountNumberMask IS :m LIMIT 1`).

`book` order inside `withWriteTransaction`: resolve `New` via `findByBankKey` then insert if absent (constants in Global Constraints); insert the transaction (`source = NOTIFICATION_PARSER`, `timestamp = bankSide.postTime`, `postBalance = bankSide.parsed.postBalance`, `notificationLogId = bankSide.id`, `createdAt = updatedAt = now`); cold start when `!isInitialized`, type `!= CREDIT_CARD` and `postBalance != null` (transactions with `timestamp <= this one`); `rebuild(setOf(accountId))`; `markSettled` bank side `CONFIRMED` and wallet side `DEDUPLICATED`, then `check(updated == ids.size)`.

- [ ] **Step 1: Write failing tests**
  - `BalanceCalculatorTest.initial balance from post balance`: one EXPENSE 50_000, postBalance 950_000 → 1_000_000.
  - Repository (in-memory Room):
    - `book creates account and transaction`: `New` → account with name `"Vietcombank ••1234"`, `isInitialized = true`, `initialBalance = 1_000_000`, `currentBalance = 950_000`; log `CONFIRMED`, `linkedTransactionId` set.
    - `book pair dedups wallet log`: wallet log `DEDUPLICATED`, linked to the same transaction; merchant from the decision.
    - `two New for one key make one account`.
    - `no post balance leaves account uninitialized`; `later post balance initializes it`, counting the earlier transaction.
    - `credit card skips cold start`.
    - `user-initialized account keeps its balance`.
    - `pair update that hits one row rolls back`: wallet log already `CONFIRMED` → `book` throws, no transaction, no account, bank log still `PENDING`.
    - `deleted notification transaction is not booked again`: book, soft-delete the transaction, `getPendingLogs()` is empty.
    - `fail marks both logs`; `markRepost on a non-pending log returns false`.
- [ ] **Step 2: Run** `--tests '*NotificationSettlementRepositoryImplTest*' --tests '*BalanceCalculatorTest*'` → FAIL.
- [ ] **Step 3: Implement** DAO queries, `initialBalanceFor`, the repository, Hilt binding.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(notif): write settlement decisions atomically`.

### Task 5: Settle use case

**Files:**
- Create: `domain/notification/usecase/SettlePendingLogsUseCase.kt`
- Test: `test/.../domain/notification/usecase/SettlePendingLogsUseCaseTest.kt`, `FakeNotificationSettlementRepository.kt`

**Interfaces:**
- Consumes: `planSettlement` (Task 3), `NotificationSettlementRepository` (Task 4), `Clock`.
- Produces: `class SettlePendingLogsUseCase @Inject constructor(repository, clock) { suspend operator fun invoke(): Boolean }` — returns true when `PENDING` logs remain after the run (the worker retries). A process-wide `Mutex` (companion) wraps the run.

Behavior: read pending; when empty return false; read settled since `min(postTime) - REPOST_WINDOW_WITH_BALANCE_MILLIS`, accounts, rules; plan; run decisions in order with a local `Map<logId, transactionId>` (`UUID` per `Book`); for `Repost` the link is `original.linkedTransactionId ?: map[original.id]`. A decision that throws (not `CancellationException`) is skipped; the next ones still run. Finally return `repository.getPendingLogs().isNotEmpty()`.

- [ ] **Step 1: Write failing tests**
  - `settle books due logs with no scheduler involvement`.
  - `repost in the same run links to the new transaction`.
  - `a throwing book does not stop the next decision`; the failed log stays `PENDING`, result true.
  - `returns false when nothing is left`; `returns true when a log is not due`.
  - `cancellation propagates`.
- [ ] **Step 2: Run** `--tests '*SettlePendingLogsUseCaseTest*'` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(notif): settle pending notifications`.

### Task 6: Scheduling

**Files:**
- Create: `domain/notification/repository/SettleScheduler.kt`, `work/SettleWorker.kt`, `work/WorkSettleScheduler.kt`
- Modify: `domain/notification/usecase/CaptureNotificationUseCase.kt`, `domain/notification/usecase/ReparseFailedLogsUseCase.kt`, `MainActivity.kt`, `di/` module that binds schedulers
- Test: `test/.../work/SettleWorkerTest.kt`, `test/.../work/WorkSettleSchedulerTest.kt`, `CaptureNotificationUseCaseTest.kt`, `ReparseFailedLogsUseCaseTest.kt`, `FakeSettleScheduler.kt`

**Interfaces:**
- Consumes: `SettlePendingLogsUseCase` (Task 5).
- Produces:
  ```kotlin
  interface SettleScheduler {
      /** After a log became PENDING: run in 35 s, replacing any queued or running settle. */
      fun scheduleAfterCapture()
      /** On app open: run now, replacing any queued or running settle. */
      fun settleNow()
  }
  @HiltWorker class SettleWorker : CoroutineWorker   // retry() when invoke() is true, else success()
  ```
  Both scheduler calls enqueue one `OneTimeWorkRequest<SettleWorker>` under `settle-notifications`, `ExistingWorkPolicy.REPLACE`, `setBackoffCriteria(BackoffPolicy.LINEAR, 30, SECONDS)`; delay 35 s or 0.

- [ ] **Step 1: Write failing tests**
  - `CaptureNotificationUseCaseTest.stored log schedules settle`; `failed, ignored, duplicate and not tracked do not`.
  - `ReparseFailedLogsUseCaseTest.reparse that makes a PENDING log schedules settle`; `no change does not`.
  - `SettleWorkerTest.retries while logs wait`, `succeeds when none wait` (`TestListenableWorkerBuilder`, as `RuleUpdateWorkerTest`).
  - `WorkSettleSchedulerTest.scheduleAfterCapture enqueues with 35 s delay`, `settleNow enqueues with no delay`, `second call replaces the first` (`WorkManagerTestInitHelper`).
- [ ] **Step 2: Run** the tests above → FAIL.
- [ ] **Step 3: Implement**; `MainActivity.onCreate` calls `settleScheduler.settleNow()` (not `Application.onCreate`: WorkManager starts the process to run the worker, and a `REPLACE` there would loop with no delay).
- [ ] **Step 4: Run** `testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** `feat(work): schedule notification settlement`.

### Task 7: Balance drift banner

**Files:**
- Create: `domain/account/BalanceDrift.kt` (model + `detectBalanceDrift`), `domain/account/usecase/ObserveBalanceDriftUseCase.kt`, `ui/transactions/component/BalanceDriftBanner.kt`
- Modify: `ui/transactions/TransactionListContract.kt`, `TransactionListViewModel.kt`, `TransactionListScreen.kt`, 4 × `strings.xml`
- Test: `test/.../domain/account/BalanceDriftTest.kt`, `test/.../ui/transactions/TransactionListViewModelTest.kt`, screenshot test for the banner if the module has one for `ListenerOffBanner`

**Interfaces:**
- Consumes: `Transaction.postBalance` (Task 1), `BalanceCalculator.balance`, `AccountRepository.observeAccounts()`, `TransactionRepository.observeTransactions(null)`, `ReconcileBalanceUseCase(accountId, actualBalance)`.
- Produces:
  ```kotlin
  data class BalanceDrift(val accountId: String, val difference: Long)
  fun detectBalanceDrift(account: Account, transactions: List<Transaction>): BalanceDrift?
  class ObserveBalanceDriftUseCase { operator fun invoke(): Flow<List<BalanceDrift>> }
  TransactionListState.drifts: List<BalanceDrift> = emptyList()
  TransactionListIntent.AdjustDrift(val accountId: String)
  ```
  `detectBalanceDrift`: null for `CREDIT_CARD`. `P` = newest live transaction of the account with `postBalance != null` (by `timestamp`, then `createdAt`); null when none. `expected = balance(id, initialBalance, live transactions ordered at or before P)`; null when an `ADJUSTMENT` of the account is after `P` or `P.postBalance == expected`; else `BalanceDrift(id, P.postBalance - expected)`.
  `AdjustDrift` → `ReconcileBalanceUseCase(accountId, account.currentBalance + difference)`.
  Strings: `balance_drift_message` = "%1$s lệch %2$s so với ngân hàng" (vi), "%1$s differs from the bank by %2$s" (en), ja and zh-rCN translations; `balance_drift_adjust` = "Điều chỉnh" / "Adjust" / ja / zh. Amount via `MoneyFormat`.

- [ ] **Step 1: Write failing tests**
  - `BalanceDriftTest`: `match gives no drift`; `mismatch gives difference`; `stays after a later no-balance notification`; `gone after a later adjustment`; `later manual transaction is not a drift`; `credit card never drifts`; `no post balance no drift`.
  - `TransactionListViewModelTest`: `drifts reach the state`; `adjust reconciles to current plus difference`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** detection, use case, banner (design-system components as `ListenerOffBanner`), VM wiring.
- [ ] **Step 4: Run** `testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** `feat(ui): show bank balance drift with adjust`.

### Task 8: Gate and roadmap

**Files:**
- Modify: `docs/roadmap.md` (P2c status, debt #3 fixed, follow-ups)

- [ ] **Step 1: Run** `qualityGate` through `gradle-run` → `exit_status` 0.
- [ ] **Step 2: Update roadmap** P2c status line with the date and what is left (on-device check, `feature/account-merge-ignore`).
- [ ] **Step 3: Commit** `docs(roadmap): mark P2c done`.
- [ ] **Step 4: Run `reviewer`** on the whole branch, then squash into `dev` after user approval.
