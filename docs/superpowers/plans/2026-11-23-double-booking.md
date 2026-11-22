# Double Booking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One real payment books once: an updated notification (same `sbn.key`) is a repost, and a log already covered by a balance adjustment fails with `adjusted_after` instead of booking.

**Architecture:** The log stores `notificationKey`. `findReposts` in the pure planner gains a cluster-based key rule next to the text rule. A pure domain function `isCoveredByAdjustment` decides L4; `NotificationSettlementRepositoryImpl.book` calls it inside the write transaction and returns `SettlementError.AdjustedAfter`, which `SettlePendingLogsUseCase` turns into a final `Fail`.

**Tech Stack:** Kotlin, Room 3 (hand-written migration), JUnit4, Robolectric for Room tests, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-23-double-booking-design.md`

## Global Constraints

- Money is `Long` VND. No `Double`.
- Any balance change runs inside one write transaction (`db.storageWrite` / `TransactionRunner.write`).
- Never write notification text, balance, mask or `notificationKey` to Logcat or `toString`.
- `DATABASE_VERSION = 10`; migration 9 → 10 only adds a nullable column; schema JSON `app/schemas/.../10.json` committed.
- `FAIL_ADJUSTED_AFTER = "adjusted_after"`.
- Key window: `REPOST_WINDOW_WITH_BALANCE_MILLIS` (10 min), measured from the cluster root.
- Covered: `A.timestamp > t` strict, `t < time(R) <= A.timestamp`.
- Run Gradle only through the `gradle-run` skill, wrapper `.\gradlew.bat`.
- `coder` writes code and tests and never commits; the main session commits each task (scopes: `db`, `dedup`, `notif`, `balance`). No AI attribution in commits.
- Skills for `coder`: `ponytail:ponytail`, `testing-coroutines-with-runtest`, `using-robolectric-correctly`, `gradle-run`.

## Review Focus

1. A FAILED log with key `K` whose parsed update was booked earlier, then reparsed → must not book twice (Task 2 test `reparsed first version joins the later booked update`).
2. Three same-key logs bridged by a null balance → the third with a different balance books (Task 2 test `null balance does not bridge two balances`).
3. Tap Adjust while a fresh payment waits its 30 s → the payment still books (Task 3 test `adjustment with no balance between is not covered`).
4. `AdjustedAfter` after `accountFor` created an account or attached an identifier → both rolled back (Task 4 test `covered log rolls back new account and identifier`).
5. Pre-branch logs (key null) keep today's text rule exactly (Task 2 test `null keys use only the text rule`).

---

### Task 1: Store the notification key (capture, model, migration 9 → 10)

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/RawNotification.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/NotificationLog.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/local/entity/NotificationLogEntity.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/mapper/NotificationLogMapper.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/repository/NotificationCaptureRepositoryImpl.kt` (`toFailedLog`)
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/usecase/CaptureNotificationUseCase.kt` (`newLog`)
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/usecase/ReparseFailedLogsUseCase.kt` (build `RawNotification` with the key)
- Modify: `app/src/main/java/io/github/toanhp123/finote/automation/FinoteNotificationListener.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/data/database/Migration9To10.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/database/MigrationPolicy.kt`, `FinoteDatabase.kt`
- Create: `app/schemas/<db package>/10.json` (Room export from the build)
- Modify tests: `app/src/test/.../domain/notification/NotificationTestBuilders.kt`, `CaptureNotificationUseCaseTest.kt`, `ReparseFailedLogsUseCaseTest.kt`, `data/database/MigrationPolicyTest.kt`, toString tests (`NotificationLogEntityToStringTest.kt` and the domain ones)

**Interfaces:**
- Produces: `RawNotification.notificationKey: String? = null` (last parameter), `NotificationLog.notificationKey: String? = null` (last parameter), `NotificationLogEntity.notificationKey: String?` (last column), `val MIGRATION_9_10: Migration`.
- Produces: `testLog(..., notificationKey: String? = null)`, `testRaw(..., notificationKey: String? = null)`.

- [ ] **Step 1: Write the failing tests**
  - `CaptureNotificationUseCaseTest`: `stores the notification key` — capture `testRaw(notificationKey = "0|pkg|1|null|10")`, stored log has that key.
  - `CaptureNotificationUseCaseTest`: `blank key is stored as null` — `notificationKey = "  "` → stored key `null`.
  - `CaptureNotificationUseCaseTest`: `same notification twice stays one row with a key` — second capture returns `CaptureResult.Duplicate`.
  - `ReparseFailedLogsUseCaseTest`: `reparse keeps the key` — a FAILED log with key `K` that now matches is `PENDING` and still has key `K` (read back from the repository).
  - toString tests: `toString` of `RawNotification`, `NotificationLog`, `NotificationLogEntity` built with key `"secret-key"` does not contain `"secret-key"`.
  - `MigrationPolicyTest`: `v9 logs survive migration to 10 with a null key` — insert one `notification_logs` row at v9, `helper.runMigrationsAndValidate(10, listOf(MIGRATION_8_9, MIGRATION_9_10))`, row exists and `notificationKey IS NULL`. Update the existing `runMigrationsAndValidate(9, …)` calls only where they assert the latest version.

- [ ] **Step 2: Run tests to verify they fail**

Run (gradle-run): `.\gradlew.bat testDebugUnitTest --tests "*CaptureNotificationUseCaseTest" --tests "*ReparseFailedLogsUseCaseTest" --tests "*MigrationPolicyTest" --tests "*ToStringTest"`
Expected: FAIL (compile errors on `notificationKey`).

- [ ] **Step 3: Implement**
  - Add the field to the three types and both mappers and `toFailedLog`. `toString` unchanged.
  - Blank → null in one place: `CaptureNotificationUseCase.newLog` uses `raw.notificationKey?.takeIf { it.isNotBlank() }`. The listener passes `sbn.key` as is.
  - `ReparseFailedLogsUseCase.reparse` passes `log.notificationKey` into `RawNotification`.
  - `Migration9To10.kt`: `ALTER TABLE notification_logs ADD COLUMN notificationKey TEXT` (no DEFAULT). Add to `FINOTE_MIGRATIONS`. `DATABASE_VERSION = 10`. Build once to export `10.json`.

- [ ] **Step 4: Run tests to verify they pass**

Same command. Expected: PASS. Then full `testDebugUnitTest`: PASS.

- [ ] **Step 5: Commit**

`feat(db): store the notification key on each log`

---

### Task 2: Key repost rule in the planner

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/service/SettlementPlanner.kt` (`findReposts`)
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/notification/service/SettlementPlannerTest.kt`

**Interfaces:**
- Consumes: `NotificationLog.notificationKey` (Task 1).
- Produces: no new public API; `planSettlement` keeps its signature.

- [ ] **Step 1: Write the failing tests** (all with `parsed`, same `packageName`, `matchedRuleId` of a bank rule; `K` = `"k"`; minutes as `60_000L`; `now` far enough that all are due)
  - `key repost with an extra line books once` — `a` (t=0, content "x", bal 1000) and `b` (t=2 s, content "x\nmore", bal 1000), both key K → decisions: one `Book(a)`, one `Repost(b, a)`.
  - `first without balance then with balance books once` — `a` bal null, `b` bal 1000 → `Repost(b, a)`.
  - `no-balance repost after 3 s books once` — `a`, `b` bal null, t=5 s → `Repost(b, a)`.
  - `different balances with the same key book twice` — bal 1000 and 950 → two `Book`.
  - `same key after 10 min books twice` — t=0 and t=10 min + 1 ms → two `Book`.
  - `different amount or mask with the same key books twice` — two cases.
  - `null keys use only the text rule` — different text, keys null → two `Book`; identical text within 3 s → `Repost`.
  - `blank keys never reach the planner` is covered by Task 1; skip here.
  - `unparsed first version is never a root` — `a` settled FAILED `parsed = null` key K, `b` pending parsed key K → `Book(b)`.
  - `reparsed first version joins the later booked update` — `b` settled CONFIRMED at t=2 s key K with `linkedTransactionId = "tx"`, `a` pending at t=0 key K → `Repost(a, b)`.
  - `settled same key one day later does not match` — `b` settled at t=+1 day → `Book(a)`.
  - `null balance does not bridge two balances` — `a` t=0 bal 1000, `b` t=2 min bal null, `c` t=4 min bal 950, all key K, all pending → `Book(a)`, `Repost(b, a)`, `Book(c)`.
  - `window is measured from the root` — `a` t=0, `b` t=8 min, `c` t=16 min, bal null, key K → `Book(a)`, `Repost(b, a)`, `Book(c)`.
  - `nearest root wins` — settled roots at t=−5 min and t=+1 min with key K, pending `a` at t=0 → `Repost(a, root at +1 min)`.

- [ ] **Step 2: Run tests to verify they fail**

Run (gradle-run): `.\gradlew.bat testDebugUnitTest --tests "*SettlementPlannerTest"`
Expected: new tests FAIL, old ones PASS.

- [ ] **Step 3: Implement the key rule in `findReposts`**

Keep the text pass as it is. Add a key pass for every log the text rule left unmatched, in `ordered` order:

```
roots      = settled logs with key != null, parsed != null, status != DEDUPLICATED
           + pending logs earlier in `ordered` that became roots (not in originals, parsed, keyed)
balances[root.id] = { root balance } ∪ { balance of settled DEDUPLICATED logs with the same key and
                     the same non-null linkedTransactionId as root } ∪ { balances of members added this run }
candidates for log (key != null, parsed != null):
  root.packageName == log.packageName && root.key == log.key
  && amount, type, accountMask equal
  && |log.postTime - root.postTime| <= REPOST_WINDOW_WITH_BALANCE_MILLIS
  && (log balance == null || balances[root.id].all { it == log balance })
  && (root is settled || root is earlier pending)
pick: min by |Δt|, then LOG_ORDER of the root
originals[log.id] = root; add log balance (non-null) to balances[root.id]
```

Add `// shortcut: one fixed key per app, equal amount/type/mask, no balances, within 10 min merges two payments; upgrade: per-rule opt-out of the key rule.` Keep `findReposts` readable; a private helper class for clusters is fine. Update the KDoc of `findReposts` and `planSettlement` ("earlier" no longer holds for the key rule against a settled root).

- [ ] **Step 4: Run tests to verify they pass**

Same command: PASS. Then full `testDebugUnitTest`: PASS (`NotificationSettlementRepositoryImplTest` still green).

- [ ] **Step 5: Commit**

`fix(dedup): treat an updated notification as a repost by its key`

---

### Task 3: `isCoveredByAdjustment` domain rule

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/account/service/AdjustmentCoverage.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/account/service/AdjustmentCoverageTest.kt`

**Interfaces:**
- Produces: `fun isCoveredByAdjustment(accountId: String, time: Long, transactions: List<Transaction>): Boolean`. `transactions` may hold rows of any account and deleted rows; the function filters itself (`deletedAt == null`, `touches(accountId)`).

- [ ] **Step 1: Write the failing tests** (use the fixtures of `BalanceAnchorTest`: `tx`, `adjustment`, account `A`, `B`; log time `t = 100`)
  - `balance between log and adjustment is covered` — row of A at 150 with `postBalance`, adjustment of A at 200 → true.
  - `adjustment with no balance between is not covered` — balance row at 50, adjustment at 200 → false (the 30 s race).
  - `adjustment at the log time is not covered` — adjustment at 100, balance row at 100 → false.
  - `balance at the adjustment time is covered` — balance row at 200, adjustment at 200 → true.
  - `deleted adjustment or deleted balance row is not covered` — two cases → false.
  - `adjustment of another account is not covered` — adjustment of B → false.
  - `received transfer balance counts` — transfer B → A with `toTimestamp = 150`, `toPostBalance` set, adjustment of A at 200 → true.
  - `sender balance on a transfer into the account does not count` — transfer B → A with `postBalance` only → false.

- [ ] **Step 2: Run tests to verify they fail**

Run (gradle-run): `.\gradlew.bat testDebugUnitTest --tests "*AdjustmentCoverageTest"`
Expected: FAIL (unresolved `isCoveredByAdjustment`).

- [ ] **Step 3: Implement** — balance time of a row for `x`: own row with `postBalance != null` → `timestamp`; transfer into `x` with `toPostBalance != null` → `toTimestamp`. Covered when some live adjustment `A` of `x` has `A.timestamp > time` and some live balance row has `time < balanceTime <= A.timestamp`. KDoc gives the 30 s race as the reason for the second condition. Reuse the receiver-time logic of `BalanceAnchor.kt` if it is already accessible (`internal`); do not duplicate it if it is.

- [ ] **Step 4: Run tests to verify they pass** — same command: PASS.

- [ ] **Step 5: Commit**

`feat(balance): tell when an adjustment already covers a late log`

---

### Task 4: Fail covered logs in the write path

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/error/SettlementError.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/service/SettlementPlanner.kt` (constant only)
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/repository/NotificationSettlementRepositoryImpl.kt` (`book`)
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/usecase/SettlePendingLogsUseCase.kt` (`execute`)
- Test: `app/src/test/.../data/notification/repository/NotificationSettlementRepositoryImplTest.kt`, `app/src/test/.../domain/notification/usecase/SettlePendingLogsUseCaseTest.kt`

**Interfaces:**
- Consumes: `isCoveredByAdjustment` (Task 3).
- Produces: `data object SettlementError.AdjustedAfter`, `const val FAIL_ADJUSTED_AFTER = "adjusted_after"`.

- [ ] **Step 1: Write the failing tests**
  - Repository (through `runSettlement`, the existing helper that wraps the runner): `covered log is not booked` — existing account with a booked row carrying a balance at t=150 and an adjustment at t=200; log at t=100 → result `AdjustedAfter`, no transaction with `notificationLogId = log.id`, account balance unchanged.
  - Repository: `covered log rolls back new account and identifier` — log whose mask is new for an existing single account with no identifier (the N-05 attach path) and that account covered → after the call, `account_identifiers` count unchanged, account count unchanged.
  - Repository: `covered log is never paired` — opposite-direction booked partner on another account within 30 min → no `TRANSFER` row, partner `deletedAt == null`.
  - Repository: `uncovered log books` — adjustment at t=200 but last balance row at t=50 → booked.
  - Use case: `adjusted after fails both sides for good` — fake repository returns `AdjustedAfter` for a bank+wallet `Book` → `fail` called once with reason `adjusted_after` and both logs; a second `invoke()` has no pending logs left.

- [ ] **Step 2: Run tests to verify they fail**

Run (gradle-run): `.\gradlew.bat testDebugUnitTest --tests "*NotificationSettlementRepositoryImplTest" --tests "*SettlePendingLogsUseCaseTest"`
Expected: new tests FAIL.

- [ ] **Step 3: Implement**
  - `SettlementError.AdjustedAfter` with KDoc: final, the log fails, not retried.
  - `book`: right after `accountFor(...)`, load `transactions.getActiveByAccount(account.id)` (same DAO `LedgerWriter.coldStart` uses), map `toDomain()`, `if (isCoveredByAdjustment(account.id, bank.postTime, rows)) return@storageWrite SettlementError.AdjustedAfter`. Before `TransactionRules.check` and `findPair`.
  - `execute`, `Book` branch: when `error == SettlementError.AdjustedAfter`, call `repository.fail(Fail(decision.bankSide, decision.walletSide, FAIL_ADJUSTED_AFTER))`, add both ids to `written`, return `null` (so `failIfStuck` does not run).
  - Update the KDoc of `SettlePendingLogsUseCase.invoke` and `NotificationSettlementRepository.book`.

- [ ] **Step 4: Run tests to verify they pass** — same command: PASS; then full `testDebugUnitTest`: PASS.

- [ ] **Step 5: Commit**

`fix(dedup): fail a late log that an adjustment already covers`

---

### Task 5: Roadmap and gate

**Files:**
- Modify: `docs/roadmap.md` (step 11 status; remove L3, L4 from section 6; T4 "Bước 11 dựng hàm" → done, name `isCoveredByAdjustment` and the key rule; add the step 13 note: show a bank+wallet `adjusted_after` pair as one item)

- [ ] **Step 1:** Edit the roadmap as above.
- [ ] **Step 2:** Run (gradle-run) `.\gradlew.bat qualityGate`. Expected: exit 0. Read the summary.
- [ ] **Step 3:** Commit `docs: mark step 11 done in the roadmap`.

Then `reviewer` checks the whole branch against the spec, before the squash into `dev` (squash needs the user's approval).
