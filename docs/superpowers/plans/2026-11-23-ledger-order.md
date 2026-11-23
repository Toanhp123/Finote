# Ledger Order (N-03) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Notifications that arrive out of the bank's order no longer raise a false drift banner or a double-counted start. The app never books an adjustment amount it computed; the banner asks the user for the real balance.

**Architecture:**
- **Pure domain functions** in `domain/account/service`:
  - a balance-chain check (`chainsFrom`, `uniqueStart`) over edges `Q → P`;
  - `balanceDriftKey`, which decides whether there is a banner and returns `DriftKey(time, ledgerBalance)`;
  - `provisionalStart` and `isStartLocked` for young accounts.
- **Data layer:**
  - `LedgerWriter.coldStart` and the merge re-anchor call the young-account functions;
  - `reconcile` locks the start;
  - a SharedPreferences acknowledgement store hides the banner after a reconcile with zero difference.
- **UI:** the banner loses its amount, and its button opens a reconcile sheet that calls `ReconcileBalanceUseCase`.

**Tech Stack:** Kotlin, Room 3, Hilt, Compose Material 3, JUnit4, Robolectric (Room tests), kotlinx-coroutines-test, Turbine.

**Spec:** `docs/superpowers/specs/2026-11-23-ledger-order-design.md` (v3.1, approved).

## Global Constraints

- Money is `Long` VND. Use `Math.addExact` and `Math.subtractExact`. No `Double`.
- Any balance or `initialBalance` change runs inside one write transaction (`db.storageWrite` / `TransactionRunner.write`). The acknowledgement write happens after the commit, never inside it.
- Never put balances, `DriftKey` values or notification text in Logcat or `toString`. Every new data class that holds money overrides `toString` to hide it, like `BalanceAnchor`.
- Order for every rule in this branch: `effectiveTime(row, x)`, then `createdAt`, then `id`. Constant `BALANCE_ORDER_WINDOW_MILLIS = 30 * 60_000L`. It equals the D12 pairing window and serves both the checkpoint margin and the young-account lock.
- No schema change. `DATABASE_VERSION` stays 10.
- `isInitialized = false` now means "provisional start". Document it on `Account.isInitialized`.
- The app never computes an adjustment amount. Remove the amount path completely (spec section 3, "Removed").
- New strings go in all four locales: `values` (vi), `values-en`, `values-zh-rCN`, `values-ja`.
- Run Gradle only through the `gradle-run` skill, wrapper `.\gradlew.bat`.
- `coder` writes code and tests and never commits. The main session commits each task. Allowed scopes: `balance`, `ui`, `db`. No AI attribution in commits.
- Skills for `coder`: `ponytail:ponytail`, `kotlin-concurrency-and-flow`, `testing-coroutines-with-runtest`, `testing-flows-with-turbine`, `using-robolectric-correctly`, `compose-state-and-effects` (Task 5), `gradle-run`.

## Review Focus

1. **A long in-order stream** (every row linked, all agreeing) never shows a banner, including when rows share a timestamp. Task 2 test `in-order stream of twenty rows has no banner`.
2. **A future-dated manual row after the newest balance row** must not push the account into the fallback. Task 2 test `future manual row after T keeps the chain path`.
3. **Acknowledgement re-arms.** After a zero reconcile, editing an old row (a change to `L`) or a new notification (a change to `T`) shows the banner again. Task 5 test `acknowledged drift returns when the ledger changes`.
4. **Backdated manual row on a young account.** It must not lock the start, and it must not stop the recompute from the first balance row. Task 3 test `backdated manual row does not lock`.
5. **Credit card, ignored account, and overflow.** A credit card never shows a banner, and an overflow in one account does not hide the others. Task 5 test `overflow in one account keeps the others`.

---

### Step 0: Commit spec and plan

- [ ] `git add docs/superpowers/specs/2026-11-23-ledger-order-design.md docs/superpowers/plans/2026-11-23-ledger-order.md`
- [ ] `git commit -m "docs: plan ledger order fix for out-of-order notifications"`

---

### Task 1: Balance chain functions

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/account/service/BalanceChain.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/account/service/BalanceChainTest.kt`

**Interfaces:**
- Consumes:
  - `Transaction.anchorBalance(x)` and `effectiveTime(row, x)` from `BalanceAnchor.kt`;
  - `BalanceCalculator.balance`.
- Produces (all `internal`):
  - `const val BALANCE_ORDER_WINDOW_MILLIS: Long = 30 * 60_000L`
  - `data class BalanceEdge(val from: Long, val to: Long)`, with `toString()` = `"BalanceEdge"`
  - `fun Transaction.balanceEdge(x: String): BalanceEdge?`
    - Null when `anchorBalance(x)` is null.
    - Otherwise `to = anchorBalance(x)` and `from = to − BalanceCalculator.balance(x, 0, listOf(this))`, with exact math.
  - `fun effectiveOrder(x: String): Comparator<Transaction>` = `compareBy({ effectiveTime(it, x) }, { it.createdAt }, { it.id })`
  - `fun chainsFrom(start: Long, edges: List<BalanceEdge>): Boolean`
  - `fun uniqueStart(edges: List<BalanceEdge>): Long?`

- [ ] **Step 1: Write failing tests** in `BalanceChainTest`. Use `e(from, to)` = `BalanceEdge(from, to)`. Amounts are in thousands.

```kotlin
@Test fun `no edges chain from anything`() = assertTrue(chainsFrom(1000, emptyList()))
@Test fun `path in any list order chains`() = assertTrue(chainsFrom(1000, listOf(e(900, 1200), e(1000, 900))))
@Test fun `cycle back to start chains`() = assertTrue(chainsFrom(1000, listOf(e(900, 1000), e(1000, 900))))
@Test fun `cycle with an exit chains`() = assertTrue(chainsFrom(1000, listOf(e(1000, 800), e(1000, 1100), e(1100, 1000))))
@Test fun `gap does not chain`() = assertFalse(chainsFrom(1000, listOf(e(1000, 900), e(850, 1150))))
@Test fun `wrong start does not chain`() = assertFalse(chainsFrom(1300, listOf(e(1000, 900), e(900, 1200))))
@Test fun `disconnected cycle after a gap does not chain`() =
    assertFalse(chainsFrom(1000, listOf(e(1000, 1200), e(800, 900), e(900, 800))))
@Test fun `branch does not chain`() = assertFalse(chainsFrom(1000, listOf(e(1000, 900), e(1000, 800))))
@Test fun `identical edges chain`() = assertTrue(chainsFrom(1000, listOf(e(1000, 1100), e(1100, 1000), e(1000, 1100))))
@Test fun `unique start of a swapped path`() = assertEquals(1000L, uniqueStart(listOf(e(900, 1200), e(1000, 900))))
@Test fun `unique start of a cycle plus tail`() =
    assertEquals(1000L, uniqueStart(listOf(e(900, 1000), e(1000, 900), e(1000, 950))))
@Test fun `pure cycle has no unique start`() = assertNull(uniqueStart(listOf(e(900, 1000), e(1000, 900))))
@Test fun `gap has no unique start`() = assertNull(uniqueStart(listOf(e(1000, 900), e(850, 1150))))
@Test fun `single edge starts at its from`() = assertEquals(1000L, uniqueStart(listOf(e(1000, 900))))
```

Add two `balanceEdge` tests using the fixtures `tx` / `A` / `B`:
- An expense of 100 with `postBalance` 900 gives `e(1000, 900)`.
- A transfer `B → A` of 100 with `toPostBalance` 1100 and `toTimestamp` set gives `e(1000, 1100)` for `A`.
- A row without a balance gives null.

- [ ] **Step 2: Run, expect failure.** `testDebugUnitTest --tests '*BalanceChainTest*'`. Expected: compile failure, unresolved `chainsFrom`.
- [ ] **Step 3: Implement** in `BalanceChain.kt`.
  - `chainsFrom`:
    - Degrees: either `out − in = +1` at `start` and `in − out = +1` at exactly one other vertex, or every vertex is balanced and `start` is a vertex. All other vertices are balanced.
    - Connectivity: every edge is reached by a BFS over edges from `start`. A vertex map is `HashMap<Long, MutableList<Int>>`.
  - `uniqueStart`: exactly one `+1` vertex `H`, exactly one `−1` vertex, the rest balanced, and the BFS from `H` reaches every edge. Otherwise null.
- [ ] **Step 4: Run, expect PASS.** Same command.
- [ ] **Step 5: Commit.** `git commit -m "feat(balance): balance chain check over bank balances"`

---

### Task 2: Drift key detection

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/account/model/DriftKey.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/account/service/BalanceDriftDetector.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/account/service/BalanceDriftKeyTest.kt` (new; `BalanceDriftTest` stays until Task 5)

**Interfaces:**
- Consumes: Task 1, and `findBalanceAnchor`.
- Produces:
  - `data class DriftKey(val time: Long, val ledgerBalance: Long)`, with `toString()` = `"DriftKey"`.
  - `internal fun anchorDrift(account: Account, transactions: List<Transaction>): Long?`. This is today's `detectBalanceDrift` body, returning the difference or null. `detectBalanceDrift` keeps its public signature and becomes `anchorDrift(...)?.let { BalanceDrift(account.id, it) }`; Task 5 deletes it.
  - `fun balanceDriftKey(account: Account, transactions: List<Transaction>): DriftKey?`. Null means no banner.

- [ ] **Step 1: Write failing tests** in `BalanceDriftKeyTest`.
  - Use `account = testAccount(A, initialBalance = 1_000)` and `bank(id, amount signed, at, post)`, which builds an `INCOME` or `EXPENSE` row with `postBalance = post`, `createdAt = at`.
  - Times are in minutes: `m(n) = n * 60_000L`.
  - "Banner" means a non-null key: `assertEquals(DriftKey(T, L), …)` with the values shown. "None" means `assertNull`.

| Test name | Rows (arrival order, time) | Expected |
|---|---|---|
| `in-order stream of twenty rows has no banner` | 20 linked rows, several sharing a time | None |
| `swapped pair has no banner` (C1) | B(+300, 900→1200, m1), A(−100, 1000→900, m2) | None |
| `swap returning to the same balance has no banner` (C2) | B(+100, 900→1000, m1), A(−100, 1000→900, m2) | None |
| `missing notification shows a banner` (C5) | A(−100, 1000→900, m1), B(+300, 850→1150, m2) | `DriftKey(m(2), 1200)` |
| `missing plus swap shows a banner` (C6/C16) | B(+300, 850→1150, m1), A(−100, 950→850, m2) | Banner |
| `four rows interleaved have no banner` (C7) | −50 (1200→1150), +300 (1150→1450), −100 (1450→1350), +200 (1000→1200), minutes 1–4 | None |
| `cycle with an exit after an older row has no banner` (C17) | Account initial 900. R0(+100, 900→1000, m0; it agrees and is the checkpoint), then R3(−200, 1000→800, m40), R1(+100, 1000→1100, m41), R2(−100, 1100→1000, m42) | None |
| `swap split by the moving window has no banner` (C19) | B(+300, 900→1200, m0), A(−100, 1000→900, m20), D(+500, 1000→1500, m40), C(−200, 1200→1000, m41) | None |
| `hidden missing income shows a banner` (C21) | B(+300, 1200→1500, m1), A(−100, 1300→1200, m2) | Banner |
| `disconnected cycle shows a banner` (C22) | A(+200, 1000→1200, m1), B(+100, 800→900, m2), C(−100, 900→800, m3) | Banner |
| `adjustment after newest balance row settles` (C10) | A(−100, 1000→850, m1), adjustment OUT 50 at m2 | None |
| `balance-less row in window uses today's verdict` (C8) | A(−100, 1000→900, m1), income 50 no balance at m2, B(+100, 950→1050, m3) | Equals `anchorDrift != null` (None here) |
| `future manual row after T keeps the chain path` | B(+300, 900→1200, m1), A(−100, 1000→900, m2), manual expense 10 no balance at m(60·24·30) | None |
| `credit card has no banner` | Any mismatch on a `CREDIT_CARD` account | None |
| `paired transfer receiver edge chains` (C9) | Transfer into A with `toPostBalance`, `toTimestamp` ≠ `timestamp`, swapped with an expense | None |

- [ ] **Step 2: Run, expect failure.** `testDebugUnitTest --tests '*BalanceDriftKeyTest*'`.
- [ ] **Step 3: Implement** `balanceDriftKey` (spec section 2). These steps fix the order:

```
if account.type == CREDIT_CARD return null
mine = transactions.filter { it.touches(x) }.sortedWith(effectiveOrder(x))
bIdx = last index with balanceEdge(x) != null, or return null
T = effectiveTime(mine[bIdx], x)
if any row after bIdx is an ADJUSTMENT of x, return null
L = BalanceCalculator.balance(x, initialBalance, mine.take(bIdx + 1))
key = DriftKey(T, L)
walk running from initialBalance over mine[0..bIdx];
  c = last i < bIdx where balanceEdge != null, effectiveTime < T − BALANCE_ORDER_WINDOW_MILLIS, and anchorBalance == running after i
S = c?.let { anchorBalance(mine[c]) } ?: initialBalance
window = mine.subList(c?.plus(1) ?: 0, bIdx + 1)
if window.any { it.balanceEdge(x) == null } return key.takeIf { anchorDrift(account, transactions) != null }
return key.takeUnless { chainsFrom(S, window.map { it.balanceEdge(x)!! }) }
```

- [ ] **Step 4: Run** `BalanceDriftKeyTest` and `BalanceDriftTest`. Expect PASS, with old tests unchanged.
- [ ] **Step 5: Commit.** `git commit -m "feat(balance): decide drift by balance chain, not ledger order"`

---

### Task 3: Provisional start for young accounts

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/account/service/ProvisionalStart.kt`
- Modify:
  - `app/src/main/java/io/github/toanhp123/finote/data/transaction/repository/LedgerWriter.kt` (`coldStart`)
  - `app/src/main/java/io/github/toanhp123/finote/data/account/repository/AccountMergeRepositoryImpl.kt` (`withMergedStart`)
  - `app/src/main/java/io/github/toanhp123/finote/domain/account/model/Account.kt` (KDoc on `isInitialized`)
- Test:
  - `app/src/test/java/io/github/toanhp123/finote/domain/account/service/ProvisionalStartTest.kt`
  - `app/src/test/java/io/github/toanhp123/finote/data/transaction/repository/LedgerWriterColdStartTest.kt` (Robolectric with an in-memory Room DB, following `TransactionRepositoryAdjustDriftTest`'s setup)
  - Add to `app/src/test/java/io/github/toanhp123/finote/data/account/local/dao/AccountMergeTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `fun provisionalStart(account: Account, transactions: List<Transaction>): Long?`
    - Null for a credit card, or when there is no balance row.
    - Otherwise, when every touching row has a `balanceEdge` and `uniqueStart(edges) = H`, it returns `H`.
    - Otherwise it returns `Q(first balance row in effectiveOrder) − BalanceCalculator.balance(x, 0, rows before it)`.
  - `fun isStartLocked(accountId: String, transactions: List<Transaction>): Boolean`: true when the balance rows' `effectiveTime` span is at least `BALANCE_ORDER_WINDOW_MILLIS`.

- [ ] **Step 1: Write failing pure tests** in `ProvisionalStartTest`:
  - `single row gives its before balance`: expense 100 with post 900 gives 1000.
  - `swapped pair gives the real start` (C3): B(900→1200), then A(1000→900), gives 1000.
  - `pure cycle keeps the first arrival` (C4): B(900→1000), then A(1000→900), gives 900.
  - `cycle plus a third row gives the real start` (C18): adds C(1000→950) and gives 1000.
  - `gap keeps the first balance row` (C24): A(1000→900), then B(850→1150), gives 1000.
  - `manual row skips the trail` gives `Q(first balance row) − δ(rows before)`.
  - `credit card gives null`, and `no balance row gives null`.
  - `isStartLocked`: false at a 29 min span, true at a 30 min span. `backdated manual row does not lock`: a manual row a day earlier with balance rows 5 min apart gives false.
- [ ] **Step 2: Run, expect failure.** `--tests '*ProvisionalStartTest*'`.
- [ ] **Step 3: Implement** `ProvisionalStart.kt`.
- [ ] **Step 4: Write failing Room tests** in `LedgerWriterColdStartTest`. Book notification rows through `LedgerWriter.insert(row, coldStart = true)` on a provisional account.
  - C3 order gives `initialBalance` 1000 and `isInitialized` false.
  - A third row 30 min after the first gives `isInitialized` true.
  - After the lock, a late swapped row leaves `initialBalance` unchanged.
  - A manual account (`isInitialized = true`) is never changed.
- [ ] **Step 5: Change `LedgerWriter.coldStart`.**
  - Skip when `isInitialized`.
  - Otherwise `provisionalStart(...)?.let { accounts.update(account.copy(initialBalance = it, isInitialized = isStartLocked(account.id, rows))) }`. `rebuild` already runs after it in `insert` and `insertPaired`.
- [ ] **Step 6: Change `withMergedStart`.**
  - Provisional target: `provisionalStart(target, merged)`, then `isInitialized = isStartLocked(...)`.
  - If that is null: today's source-start branch.
  - Initialized target: today's `anchoredInitialBalance` branch, unchanged.
  - Add an `AccountMergeTest` test: `merge into initialized target with a swap shows a banner`. After the merge, `balanceDriftKey` is non-null, and `initialBalance` is today's anchored value.
  - Add a provisional-target merge test that gives the `uniqueStart` value.
- [ ] **Step 7: Add the KDoc** on `Account.isInitialized`: "false: provisional start, recomputed by `provisionalStart` on each notification until `isStartLocked` or a reconcile; true: fixed".
- [ ] **Step 8: Run** `testDebugUnitTest`. Expect PASS. Existing settlement and merge tests that assumed an immediate `isInitialized = true` are re-derived. Each change is listed in the task report.
- [ ] **Step 9: Commit.** `git commit -m "fix(balance): keep a young account's start provisional for 30 minutes"`

---

### Task 4: Reconcile locks the start and acknowledges a zero difference

**Files:**
- Create:
  - `app/src/main/java/io/github/toanhp123/finote/domain/account/repository/DriftAcknowledgementRepository.kt`
  - `app/src/main/java/io/github/toanhp123/finote/data/account/repository/DriftAcknowledgementRepositoryImpl.kt`
- Modify:
  - the Hilt module that binds `SetupRepository` (same pattern)
  - `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` (exclude `drift_ack.xml` next to `app_setup.xml`)
  - `TransactionRepository.kt`, `TransactionRepositoryImpl.kt` and `FakeTransactionRepository.kt`
  - `ReconcileBalanceUseCase.kt`
- Test:
  - `ReconcileBalanceUseCaseTest.kt`
  - `app/src/test/java/io/github/toanhp123/finote/data/transaction/repository/TransactionRepositoryReconcileTest.kt`
  - `app/src/test/java/io/github/toanhp123/finote/data/account/repository/DriftAcknowledgementRepositoryImplTest.kt` (Robolectric)

**Interfaces:**
- Consumes: Task 2 `balanceDriftKey`, `DriftKey`.
- Produces:
  - `interface DriftAcknowledgementRepository { fun observe(): Flow<Map<String, DriftKey>>; suspend fun acknowledge(accountId: String, key: DriftKey) }`
  - `TransactionRepository.getDriftKey(accountId: String): DriftKey?`: reads the account and its active rows, then calls `balanceDriftKey`, inside `storage { }`.
  - `FakeDriftAcknowledgementRepository` in test sources, backed by a `MutableStateFlow`.

- [ ] **Step 1: Write failing tests.**
  - `DriftAcknowledgementRepositoryImplTest`: `acknowledge then observe emits the key`; `second acknowledge replaces the first`.
    - The stored format is one string per account id, `"$time:$ledgerBalance"`, in prefs file `drift_ack`.
    - `observe()` is a `callbackFlow` over `OnSharedPreferenceChangeListener`. It emits the current map first.
  - `TransactionRepositoryReconcileTest`:
    - `zero difference locks a provisional account and books nothing`;
    - `non-zero difference books one adjustment and locks`.
  - `ReconcileBalanceUseCaseTest`:
    - `zero difference acknowledges the current drift key` (the fake repository returns `DriftKey(5, 1000)`, and the ack repository then holds it for that account);
    - `non-zero difference does not acknowledge`;
    - `unusable account acknowledges nothing`.
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement.**
  - In `TransactionRepositoryImpl.reconcile`, inside `storageWrite`, before `writeAdjustment`: `if (!account.isInitialized) db.accountDao().update(account.copy(isInitialized = true))`.
  - Add `getDriftKey`.
  - `ReconcileBalanceUseCase` injects `DriftAcknowledgementRepository`. After `tx.write { … }` returns `Outcome.Ok(null)`, call `repository.getDriftKey(accountId)?.let { acks.acknowledge(accountId, it) }`, outside the write.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expect PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(balance): reconcile locks the start and remembers a zero check"`

---

### Task 5: Banner without amount, reconcile sheet, remove the amount path

**Files:**
- Modify:
  - `domain/account/model/BalanceDrift.kt` (drop `difference`)
  - `domain/account/usecase/ObserveBalanceDriftUseCase.kt`
  - `domain/account/service/BalanceDriftDetector.kt` (delete public `detectBalanceDrift`; keep `anchorDrift`)
  - `ui/transactions/TransactionListContract.kt`, `TransactionListViewModel.kt`, `TransactionListScreen.kt`
  - `ui/transactions/component/BalanceDriftBanner.kt`
  - `res/values*/strings.xml` (4 locales)
  - `docs/roadmap.md` (remove N-03 from section 6; add the step to section 5 Đoạn A as `fix/ledger-order`)
- Create: `ui/transactions/component/ReconcileSheet.kt`
- Delete:
  - `domain/transaction/usecase/AdjustBalanceDriftUseCase.kt`
  - `TransactionRepository.adjustDrift` and its KDoc, the impl, and `FakeTransactionRepository.adjustDrift`
  - `AdjustBalanceDriftUseCaseTest.kt`, `TransactionRepositoryAdjustDriftTest.kt`
  - `BalanceDriftTest` amount tests (port the cases that still apply to `BalanceDriftKeyTest` as None/Banner)
- Fix callers: `AccountMergeTest` (around line 285), `MoneyToStringTest` (around line 36), `TransactionListScreenshots.kt` (around lines 87–89), `ObserveBalanceDriftUseCaseTest`, `TransactionListViewModelTest`, `TransferPairRepositoryImplTest`, `NotificationSettlementRepositoryImplTest` (replace `detectBalanceDrift(...)` with `balanceDriftKey(...)` null checks).

**Interfaces:**
- Consumes: Tasks 2 and 4.
- Produces:
  - `data class BalanceDrift(val accountId: String)`
  - Intents: `OpenReconcile(accountId: String)`, `ReconcileSubmitted(actualBalance: Long)`, `ReconcileDismissed`.
  - State: `reconcilingAccountId: String?`.
  - Effect: `ReconcileFailed`.
  - `BalanceDriftBanner(accountName: String, onReconcile: () -> Unit, modifier: Modifier = Modifier, otherCount: Int = 0)`.

- [ ] **Step 1: Write failing tests.**
  - `ObserveBalanceDriftUseCaseTest` (Turbine, fakes):
    - `drift shows when key is not acknowledged`;
    - `acknowledged drift is hidden`;
    - `acknowledged drift returns when the ledger changes` (edit a row amount, so `L` changes);
    - `overflow in one account keeps the others`.
  - `TransactionListViewModelTest`:
    - `OpenReconcile sets reconcilingAccountId`;
    - `ReconcileSubmitted calls reconcile and closes the sheet`;
    - `failed reconcile sends ReconcileFailed and keeps the sheet`.
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Implement the domain change.**
  - `ObserveBalanceDriftUseCase` = `combine(observeUsableAccounts(), observeTransactions(null), acks.observe())`. It maps each account to `BalanceDrift(it.id)` when `balanceDriftKey(...)` is non-null and differs from `acks[it.id]`. Keep the per-account `ArithmeticException` catch.
- [ ] **Step 4: Implement the UI.**
  - **Banner:** text `balance_drift_message` = "Số dư %1$s có thể lệch với ngân hàng"; button `balance_drift_adjust` = "Nhập số dư thật"; content description `balance_drift_adjust_cd` = "Nhập số dư thật của %1$s".
  - **Sheet:** a `FinoteBottomSheet` in its own file, holding:
    - the account name;
    - the app balance through `MoneyText`;
    - an amount field (reuse the money input that the cash entry sheet uses);
    - a live line "Chênh lệch: …" shown with `MoneyText`;
    - Cancel and Save, with Save disabled while the field is empty.
  - Follow `docs/design-system.md`.
  - **English strings:** "%1$s balance may differ from the bank", "Enter real balance", "Enter the real balance of %1$s", sheet title "Real balance", field "Balance in your bank app", "In app", "Difference", error "Could not save the balance".
  - Write `zh-rCN` and `ja` to match (machine-written, under debt N-16).
- [ ] **Step 5: Delete the amount path** (files listed above) and fix every caller. Update the screenshot test's banner preview.
- [ ] **Step 6: Run** `testDebugUnitTest`. Expect PASS. Then `assembleDebug`. Expect `exit_status` 0.
- [ ] **Step 7: Update** `docs/roadmap.md`: delete the N-03 row, add a step-11b row `fix/ledger-order` to Đoạn A, and note D16 "the app never computes an adjustment amount; the user types the real balance".
- [ ] **Step 8: Commit.** `git commit -m "feat(ui): ask for the real balance instead of booking a computed drift"`

---

### Finish

- [ ] Run `qualityGate` through `gradle-run` and read its summary.
- [ ] `reviewer` reviews the whole branch against the spec once. Its findings go back to `coder`.
- [ ] Ask the user before `git merge --squash` into `dev`.
- [ ] Then redo `release/0.2.0` from the new `dev`. The old local `release/0.2.0` branch (commit `dfef3c2`) is deleted only with the user's OK.
