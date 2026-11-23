# Out-of-order notifications (debt N-03): design

Date: 2026-11-23. Status: v3.1 approved by the user 2026-11-23, after two external critiques and two `architect` reviews. Banner text and the 30 min lock are settled as written.
Branch (planned): `fix/ledger-order` from `dev`. Source: `docs/roadmap.md` section 6, N-03. Related: N-20 (bank time), D11/T2 (future-dated rows), D12 (30 min pairing window), step 11 (`isCoveredByAdjustment`).

## Why

The ledger orders rows by notification `postTime` (`LEDGER_ORDER`). When the push of an earlier bank event arrives later, the ledger order is wrong. Two money effects:

1. **False drift banner.** `detectBalanceDrift` reports a gap that is not real, or the wrong amount. Pressing Adjust (`adjustDrift`) books a wrong `ADJUSTMENT`.
2. **Silent wrong start.** `LedgerWriter.coldStart` fixes `initialBalance` once, from the first stored row. A row that arrives later but happened earlier is counted twice.

The user has not seen either in dogfood. The fix is a guard before v0.2. Reading the bank time from the text (N-20) is not possible: the real Techcombank alert has no time.

## Goals

- (a) The app never books an amount derived from a guessed order.
- (b) A real drift is not hidden for long. Healing on the next notification is fine.
- (c) Cold start must not double count, and must not swallow a real gap into the start.
- (d) The normal path stays cheap. The code stays small enough to test.

## Approaches considered

| Approach | Verdict |
|---|---|
| Sum-only check | Rejected. Wrong amount on a cold start and with gap plus swap; can hide a real drift |
| Swap the anchor with its neighbors | Rejected. Fails when more than one row moved |
| Greedy chain from a start (v1) | Rejected. Wrong amount after a gap, false drift on a cycle with an exit, wrong young head, a fixed window splits a swap |
| Euler path plus components ordered by time | Rejected. Ordering components by time is a guess with a confident amount |
| Store an order key at write time (migration) | Rejected. Same logic at insert, plus rewrites of placed rows and a backfill |
| Exact search for the last row, show the amount when unique (v2) | Rejected after critique 2. Window and checkpoint choices still let a wrong amount look certain (stale checkpoint after a linked row, a late row after an `ADJUSTMENT` plus a gap, a balance-less row inside the window) |
| **v3: the app decides only whether to show the banner; the user types the real balance** | **Chosen.** Every remaining algorithm error costs at most a needless banner, or a hidden drift by an exact coincidence. No path books money from a guess |

## Model

For a row `r` that carries a bank balance for account `x`:

- `δ(r)`: signed effect on `x`, `BalanceCalculator.balance(x, 0, listOf(r))` (fee included).
- `P(r)`: bank balance after `r`: `postBalance`, or `toPostBalance` for a paired transfer into `x` (the receiver edge, time `toTimestamp`). The sender edge uses `postBalance`.
- `Q(r) = P(r) − δ(r)`: bank balance before `r`.
- Each balance row is an edge `Q(r) → P(r)` in a directed multigraph. In the bank's real order with no missing event, the rows form one trail.

Every filter, sort and time below uses `effectiveTime(row, x)`, then `createdAt`, then `id`.

## Design

### 1. Chain check (pure, O(k))

`chainsFrom(start: Long, edges): Boolean` is true when the edges form one trail that starts at `start`:
- Degrees: either `start` has `out − in = +1` and exactly one other vertex has `in − out = +1`, or every vertex has `out = in` (the trail ends at `start`). All other vertices are balanced.
- Connectivity: every edge is reachable from `start` (BFS over edges). Degrees alone are not enough: a missing −400 followed by a separate +100/−100 cycle passes the degree test.
- No edges: true.

`uniqueStart(edges): Long?` is the start `H` when exactly one vertex has `out − in = +1`, exactly one has `in − out = +1`, all others are balanced, and every edge is reachable from `H`. Otherwise null (pure cycle, gap, branch).

### 2. Drift detection: `balanceDriftKey(account, rows): DriftKey?`

A null result means no banner. The key `DriftKey(T, L)` identifies the drift so the acknowledgement (section 4) can match it; `ObserveBalanceDriftUseCase` hides a drift whose key matches the stored acknowledgement.

1. Credit card: false.
2. `b` is the newest balance row and `T = effectiveTime(b)`. No balance row: false.
3. **Settled.** An `ADJUSTMENT` of `x` after `b` in the order above: null. `L` is the ledger balance through `b`.
4. **Checkpoint.** Walk the rows with a running ledger balance from `initialBalance`. A balance row agrees when `P == running`. `c` is the newest agreeing balance row with time `< T − 30 min`. If there is none, `c` is the account start with `S = initialBalance`; otherwise `S = P(c)`. The window is every row after `c` up to and including `b` in that order. Rows after `b` (a future-dated or later manual row, or a same-time row that sorts after `b`) are outside the window and outside `L`.
5. **Fallback.** If the window holds any row without a bank balance (a balance-less notification, a manual row, an `ADJUSTMENT`), the result is today's anchor verdict (`anchorDrift(...) != null`). Its amount is not used. Today's verdict is kept by design: its anchor check uses `effectiveTime`, while `LEDGER_ORDER` still sorts by `timestamp`.
6. **Chain.** Otherwise the result is `!chainsFrom(S, window balance edges)`. A trail ends at `S + Σδ = L`, so a passing chain means no drift.
7. **Isolation.** `ObserveBalanceDriftUseCase` keeps its per-account `ArithmeticException` catch, so an overflow in one account does not stop the others.

There is no row cap: the window is every row after an old agreeing checkpoint, and the check is linear.

### 3. Banner and reconcile (UI)

- The banner shows no amount: "Số dư {account} có thể lệch với ngân hàng". The button opens a reconcile sheet.
- The sheet shows the app's current balance, an input for the real balance, and the difference as the user types. Save calls the existing `ReconcileBalanceUseCase`, which books one `ADJUSTMENT` of `actual − currentBalance`, or nothing when the difference is 0.
- The sheet follows `docs/design-system.md` and the rule that sheets live in their own file under the feature `component/`.
- **Removed** (amount path), every place:
  - `AdjustBalanceDriftUseCase`, `TransactionRepository.adjustDrift` and its KDoc, `TransactionRepositoryImpl.adjustDrift`, `FakeTransactionRepository.adjustDrift`.
  - `BalanceDrift.difference`. `BalanceDrift` keeps only the account id.
  - `TransactionListContract.AdjustDrift`, which is replaced by an intent that opens the sheet. Also `TransactionListViewModel` (around lines 39, 138, 174–177) and `TransactionListScreen` (around 151–152).
  - `BalanceDriftBanner(difference, onAdjust)` and its KDoc.
  - Tests: `AdjustBalanceDriftUseCaseTest`, `TransactionRepositoryAdjustDriftTest`, the `adjustDrift` call in `AccountMergeTest` (around 285), `MoneyToStringTest` (around 36), `TransactionListScreenshots` (around 87–89), and the amount assertions in `BalanceDriftTest`.
  - Strings `balance_drift_message` (it takes an amount) and `balance_drift_adjust`, replaced in **every locale** (`vi`, `en`, `zh-rCN`, `ja`).

### 4. Acknowledgement when the difference is 0

When the user reconciles and the difference is 0, nothing is booked, so the banner would stay. The app then stores, per account, a SHA-256 digest of `accountId:T:L` (never the balance itself: the prefs file is not encrypted like the database). The banner is hidden while the digest of the current key equals the stored one. The key is read inside the reconcile write transaction, so a notification that lands right after the commit is not acknowledged by mistake. Any new balance row changes `T`, and any edit, delete or insert changes `L`, so a later real drift shows again.

- **Storage.** SharedPreferences, as in `SetupRepositoryImpl` (the project has no DataStore). Put it behind a domain repository, exposed as a `Flow` (a listener-backed `callbackFlow`) so `ObserveBalanceDriftUseCase` can combine it. Exclude the new prefs file from backup, like `app_setup.xml`.
- **Who writes.** `ReconcileBalanceUseCase` writes it after the Room write commits, only when the result is zero. The ViewModel does not.
- **Lock.** Reconcile locks a provisional account (`isInitialized = true`) inside `reconcile()`'s `storageWrite`, before the `delta == 0` early return in `writeAdjustment`. So a zero difference also locks.

### 5. Cold start and the young account (no schema change)

- `isInitialized = false` now means "provisional". Manual accounts are created with `true` (`CreateAccountUseCase`), so this never touches them. Document it on `Account`.
- The first insert sets `initialBalance = Q(first balance row) − Σδ(rows before it)`, the same value as today for one row.
- Each later `coldStart` insert while provisional: if the account has no balance-less row and no `ADJUSTMENT`, and `uniqueStart(balance edges) = H`, then `initialBalance = H`. Otherwise `initialBalance = Q(first balance row in effective order) − Σδ(rows before it)`: the first row by bank time, never a newest-anchor value. A gap is never folded into the start; it shows as a banner.
- **Lock** (`isInitialized = true`) when any of these is true:
  - the account's **balance rows** span at least 30 min (`maxEffectiveTime − minEffectiveTime`), checked after the recompute of that insert; a backdated manual row does not count;
  - an `ADJUSTMENT` is written (today `reconcile` is the only adjustment writer, and it locks);
  - the user reconciles (difference 0 included).
- `AccountMergeRepositoryImpl.withMergedStart`:
  - A provisional target uses the young rule on the merged rows. Merged balance rows that span at least 30 min lock the target.
  - An initialized target keeps today's re-anchor through `anchoredInitialBalance`. That anchor is order-sensitive (N-03), but in v3 a wrong derived start only shows a banner: no row agrees, so `chainsFrom(S)` fails. A test pins this.
  - `getMergeDroppedStart` keeps its `isInitialized` filter. A provisional account has an anchored start, so dropping it needs no warning.
  - The new meaning of `isInitialized` ("provisional" when false) is documented on `Account`. Its only users are `LedgerWriter`, `withMergedStart` and `getMergeDroppedStart`.

### Known limits

- A coincidence where the remaining rows still form one trail hides a missing event (for example a lost +x and a lost −x in a row). The net is then zero, so the balance is right.
- A row delayed past the 30 min checkpoint margin can show a needless banner. The user's reconcile is still correct.
- A provisional start is recomputed only when a notification is inserted. Deleting or editing a row on a young account leaves the start as it was until the next notification; the cost is at most a needless banner.
- Fallback windows (with balance-less rows or an `ADJUSTMENT`) keep today's order sensitivity. Example: B arrives first, the user reconciles −100, then the late A makes `L == P(A)`, and the banner stays silent until the next notification.
- Future-dated rows (T2, D11) are in `currentBalance`, so the reconcile sheet compares against them too. This is unchanged and is settled at step 24.
- **A push in flight during a reconcile** (step 11 gap). The user reconciles at R, reading a bank balance that already includes A. A's log has `postTime < R` but settles after R. No balance row lies in `(postTime, R]`, so `isCoveredByAdjustment` is false and A is booked. A is now counted twice, and the account reads as settled (the `ADJUSTMENT` is after `b = A`) until the next balance row brings the banner back. Healing takes one notification. The risk exists today too.

## Cases

| # | Case | Result |
|---|---|---|
| C1 | Two rows swapped, distinct balances | No banner |
| C2 | Swap that returns to the same balance | No banner (trail ends at `S`) |
| C3 | C1 on a new account | `uniqueStart` gives the real start; no banner |
| C4 | C2 on a new account (pure cycle) | Start kept from the first arrival; the next row within 30 min heals it; after the lock a banner asks for the real balance |
| C5 | Missing notification, no swap | Banner |
| C6 | Missing notification plus swap | Banner |
| C7 | Four rows interleaved | No banner |
| C8 | Balance-less row in the window | Fallback to today's verdict |
| C9 | Paired transfer into `x` | Receiver edge; no banner when consistent |
| C10 | `ADJUSTMENT` after the newest balance row | Settled |
| C11 | Amount edited, `postBalance` kept | Correct edit chains; a wrong edit shows a banner |
| C12 | Push delayed for hours | Banner if past the 30 min margin; the reconcile is correct |
| C13 | Real drift equal to a neighbor's amount | Banner |
| C14 | Identical rows repeated | No banner |
| C15 | Two rows claim the same start (holds) | Banner |
| C16 | Gap, then a swapped pair (critique 1, E1) | Banner |
| C17 | Cycle with an exit, with an older row before it | Checkpoint before `T − 30 min`; trail exists; no banner |
| C18 | Young cycle, then a third row | `uniqueStart` = 1000; no banner |
| C19 | Swap split by a moving window | Checkpoint before `T − 30 min`; no banner |
| C20 | Late row after the user adjusted | Window holds the `ADJUSTMENT`: fallback; banner or a known-limit silence |
| C21 | Missing +300 hidden by today's anchor | Chain fails: banner |
| C22 | Disconnected cycle after a gap (critique 3) | Connectivity fails: banner |
| C23 | Reconcile with difference 0, then an old row is edited | `L` changes: banner returns |
| C24 | Young account with a gap | Start kept; banner |

## Non-goals

- Reading bank time from the text (N-20).
- Any amount computed by the app for an adjustment.
- Changing the displayed time or the list order.

## Tests

Pure tests:
- **`chainsFrom` and `uniqueStart`.** A path, a cycle back to start, a cycle with an exit, a gap, a disconnected cycle (C22), a branch, identical edges, no edges.
- **`balanceDriftKey`.** One test per case C1–C24 that is pure. Also the checkpoint margin (`T − 30 min` exactly), equal times, a transfer with `toTimestamp ≠ timestamp`, a credit card, the fallback with a balance-less row, and a future-dated manual row after `T` (the chain path is still used).

Room tests:
- **Cold start.** C3, C4 heal, C18, C24, a young account with a manual row (no recompute), the lock at 30 min of balance rows, a backdated manual row that does not lock, a lock on `ADJUSTMENT`, a lock on reconcile, and a manual account never recomputed.
- **Merge.** Merging into a provisional target. Merging into an initialized target with a swap gives a banner, not a silent wrong start.
- **Reconcile.** A non-zero difference books one `ADJUSTMENT`. A zero difference locks the account, stores the acknowledgement after the commit, and books nothing.

ViewModel tests:
- The banner hides on acknowledgement and returns on an edit.

Removed: `AdjustBalanceDriftUseCaseTest`, `TransactionRepositoryAdjustDriftTest`. Existing anchor tests are kept for the fallback path.

## Settled with the user

1. Banner text: "Số dư {account} có thể lệch với ngân hàng", with the button "Nhập số dư thật".
2. Lock window: 30 min (same as D12).
