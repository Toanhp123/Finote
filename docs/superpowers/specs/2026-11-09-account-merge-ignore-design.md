# Account merge and ignore design (`feature/account-merge-ignore`)

Follow-up to P2c (`docs/superpowers/specs/2026-11-08-account-dedup-balance-design.md`). The spec has no section on
this; it fixes wrong guesses of the P2c account mapping. Roadmap P2c, "Ngay sau P2c".

## Scope

In this branch:

- Merge one auto-created account into another account of the same bank app. Its transactions move over, and later
  notifications with its key land on the target.
- Ignore an account: later notifications for it create no transaction and no failure. Optionally move its old
  transactions to the trash. Ignoring can be undone ("Follow again").
- A Settings → Accounts screen to do both.
- Lets the user repair two P2c known risks by hand: card mask is not account mask; a `New` with a mask and a `New`
  with a null mask in one run make two accounts (roadmap P2c debt). Settlement still creates the extra account; this
  branch does not prevent it, it lets the user merge it away once and keeps later notifications on the target.

Out of scope (future, recorded in the roadmap):

- Merge suggestion: a banner on the transaction list when a new auto-created account looks like an existing one of the
  same bank. Wait for real on-device data first; guesses can be wrong and the list already has drift banners.
- Merge into an account without a bank key (cross-key merge). Only useful once a manual "add account" screen exists.
  Ship it with that screen, together with an optional "link bank app + last 4 digits" field on the form.
- Rename, edit or delete accounts from the Accounts screen.
- Undo a merge.

## Decisions (brainstorm 2026-11-09)

| # | Question | Decision |
|---|---|---|
| 1 | What "ignore" does | Stop booking new transactions. Old ones stay unless the user ticks "move N old transactions to trash" in the confirm dialog (soft delete). |
| 2 | Where the user acts | New Settings → Accounts screen. No automatic suggestion (future). |
| 3 | Allowed merge target | Same `bankPackageName` only. Cross-key merge is future work; today the only key-less accounts are system ones like Cash, so allowing it would mostly enable mistakes. |
| 4 | How a merge is stored | Keep the source row as a soft-deleted pointer (`mergedIntoId`). No alias table. |
| 5 | Undo | Ignore: yes ("Follow again"). Merge: no; a confirm dialog says so. |

Why a pointer and not an alias table: the resolver already loads every account with a bank key, deleted ones too. A
pointer adds one column and one hop; an alias table adds a table, a DAO and a second lookup. Soft delete keeps the
trail, as the repo rules ask.

## Data (DB version 6 → 7)

Before v1.0 the DB is recreated (`fallbackToDestructiveMigration(dropAllTables = true)`), so no migration code.

### `accounts`

- `mergedIntoId: String?`: the account this one was merged into. Set only together with `deletedAt`. No foreign key
  (a self-reference that is rewritten on chained merges; the code keeps it valid).
- `isIgnored: Boolean` (default `false`).

Domain `Account` gets the same two fields.

### `notification_logs`

New status `IGNORED_ACCOUNT`: the log parsed fine but its account is ignored. No `linkedTransactionId`, no
`failureReason`. `getSettledLogsSince` keeps returning it (it only skips `IGNORED_SPAM`), so a repost of an ignored
notification is still found and marked `DEDUPLICATED` with no link.

## Account resolution (`domain/notification/AccountResolver.kt`)

Input is unchanged (package, mask, rule, every account with a bank key). New order:

1. **Mask present: exact key.** Candidates are rows of this package with this mask that are live, or soft-deleted
   with a `mergedIntoId`. A live row wins over a merged one. Among several merged rows, the one with the latest
   `deletedAt` wins (possible only after a target was deleted and the key was recreated and merged again). A merged
   row is followed through `mergedIntoId` to its live target (one hop: chains are flattened on merge, see below). A
   target that is missing or deleted is treated as no match.
2. **Mask absent.** Step 1 never runs: a merged null-mask row must not catch every maskless notification of the bank.
   Count the live accounts of the package (merged and deleted rows do not count; archived and ignored ones do).
   Exactly one → that account; more than one → `Rejected(FAIL_AMBIGUOUS_ACCOUNT)` as today. Ignored accounts count on
   purpose: a maskless notification may belong to the ignored account, so guessing the other one could book a stranger's
   money; ambiguous is the honest answer, and it is today's behaviour for two accounts.
3. **Nothing found** → `Resolved(New(...))` as today.
4. On the account found: ignored → new `AccountResolution.Ignored`; else archived → `Rejected(FAIL_ACCOUNT_ARCHIVED)`
   as today; otherwise `Resolved(Existing(id))`. Ignored is checked first so an account that is both stays silent.

Step 1 replaces today's "with a mask, filter on the mask" branch; step 2 is today's "no mask" branch minus merged rows.

The P2c null-mask debt is fixed by the user merging the null-mask account into `••1234`: the package then has one live
account, so step 2 lands on it. If the user later opens a second account at the same bank, maskless notifications
become ambiguous again, which is correct.

Merging a masked source into a null-mask target is allowed. No key is lost: the source row keeps its mask and points at
the target, so step 1 still finds it. Only the target's display name lacks the digits (the user can rename it once
editing exists).

The index on `(bankPackageName, accountNumberMask)` is not unique (P2c), so a new account on the key of a merged row
inserts fine.

## Settlement (`domain/notification/SettlementPlanner.kt`)

- New decision `SettlementDecision.Ignore(log, partner)`. `decide()` maps `AccountResolution.Ignored` to it. A paired
  wallet log is ignored with its bank log.
- `SettlePendingLogsUseCase` sends it to a new `NotificationSettlementRepository.ignore(decision): Boolean`, which marks
  both logs `IGNORED_ACCOUNT` (same shape as `fail()`).
- `NotificationSettlementRepositoryImpl.resolveAccount()` re-checks at write time: it already throws when the account
  is deleted (this covers an account merged away after planning) or archived; it now also throws when it is ignored.
  The transaction rolls back and the log stays `PENDING` for the next run, which resolves again.
- Late partner: no new case. Pairing only happens between logs that are both still `PENDING`, and a log is not settled
  before it is 30 s old, so a wallet log inside the window is always planned together with its bank log. A wallet log
  outside the window is settled on its own, exactly as when the bank log was booked (P2c "late partner" risk).

## Merge (`AccountRepository.merge(sourceId, targetId, now)`)

Checks, inside the write transaction (the repository reads fresh rows there; `MergeAccountsUseCase` only adds the
clock). A failed check throws `AccountMergeRejectedException(reason)` and writes nothing:

- Source ≠ target. Both live (not deleted). Both have the same non-null `bankPackageName`.
- No transfer between the two, live or deleted (`accountId`/`toAccountId` pair in either direction). Moving it would
  make a transfer to itself, also after a later restore. Rejected with a typed error; the UI shows a snackbar.

Writes, all in one `withWriteTransaction`:

1. Move every transaction (deleted ones too, so a restore lands on the target) from source to target, in both
   `accountId` and `toAccountId`.
2. Initial balance. Both rows stand for one real bank account, so the bank balance already includes the source's
   transactions; keeping the target's old start would count them twice. Rule: take the live transaction with a
   `postBalance` and the latest `timestamp` among the merged set (target's own plus moved-in). If there is one and the
   target is not a `CREDIT_CARD`, set `initialBalance = BalanceCalculator.initialBalanceFor(target, postBalance,
   live merged transactions with timestamp <= that one)` and `isInitialized = true`, whatever `isInitialized` was
   (same math as P2c cold start). Else, if only the source `isInitialized`, take the source's `initialBalance` and set
   `isInitialized = true`. Else keep the target's.
3. Rewrite every row with `mergedIntoId = source` to `mergedIntoId = target` (flattens chains).
4. Soft-delete the source: `deletedAt = now`, `mergedIntoId = target`, `updatedAt = now`. Delete its balance snapshots.
5. `AccountBalanceRebuilder.rebuild(setOf(target))`.

Archived or ignored accounts may be merged either way; the target's flags decide what later notifications do.

## Ignore (`AccountRepository.setIgnored(id, ignored, trashOldTransactions, now)`)

- `ignored = true`: set `isIgnored`. If `trashOldTransactions`, soft-delete every live non-transfer transaction with
  `accountId` = this account, then rebuild it. One write transaction. Transfers are left alone: trashing one would
  change the other account's balance and could raise a drift banner there. Notification transactions are never
  transfers before P2d, so in practice everything the bank booked goes.
- `ignored = false`: clear `isIgnored`. Trashed transactions stay in the trash.
- Through `domain/account/usecase/SetAccountIgnoredUseCase.kt`.

Ignored accounts are hidden from the cash entry account picker and get no balance drift banner
(`ObserveBalanceDriftUseCase`). Their old transactions still show in the transaction list until trashed.

## UI (`ui/settings/accounts/`)

MVI like `ui/settings/capture/` (contract, screen, view model). Components from `docs/design-system.md` only:
`FinoteListRow`, `FinoteBottomSheet`, `FinoteDialog`, `MoneyText`, snackbar.

- Settings gets an "Accounts" row that opens the screen (new nav key).
- The list shows live accounts: name, bank, balance. Ignored ones go last, dimmed, with an "Ignored" label. Accounts
  without a bank key show but have no actions.
- Tapping a bank account opens a bottom sheet:
  - **Merge into…**: a list of other live accounts with the same package, each with its name (which carries the mask)
    and balance. Pick one → dialog "Merge VCB ••5678 into VCB ••1234? N transactions move over. This cannot be undone."
    Disabled with a short hint when no account qualifies.
  - **Ignore account**: dialog "No new transactions will be recorded for this account", with a checkbox "Move N old
    transactions to trash", unticked by default and hidden when N = 0. On an ignored account the action is **Follow
    again**, with no dialog.
  - N counts live transactions only, once each (a transfer between both sides is counted once). For merge: live rows
    with the source as `accountId` or `toAccountId`. For ignore: the rows the trash step would delete.
- After an action: snackbar ("Merged", "Ignored", "Following again"). The list updates through its `Flow`.
- Strings in every locale the app has (vi, en, ja, zh-rCN). ja and zh-rCN are machine-written, as before.

## Testing

Unit tests on the JVM; Room tests with Robolectric as in earlier branches.

- `AccountResolverTest`: a mask matches a merged row and lands on its target; a live row beats a merged row with the
  same key; the latest merged row wins among two; a merged row whose target is deleted acts as no match; an ignored
  target gives `Ignored`; ignored and archived gives `Ignored`; with no mask, a merged null-mask row is not used and
  merged rows do not count toward "exactly one"; with no mask, an ignored account still counts (two live → ambiguous);
  archived behaviour unchanged.
- `SettlementPlannerTest`: an ignored account gives `Ignore`; a wallet + bank pair on an ignored account ignores both.
- `NotificationLog` status: the app has no exhaustive `when` on `NotificationStatus` today (checked 2026-11-09); the
  capture screen does not list statuses, so `IGNORED_ACCOUNT` needs no UI. The future Failed Inbox lists `FAILED` only.
- `SettlePendingLogsUseCase` test: `Ignore` calls `ignore()`.
- `NotificationSettlementRepositoryImplTest`: `ignore()` marks both logs `IGNORED_ACCOUNT`; booking into an account that
  became ignored after planning rolls back and leaves the log `PENDING`.
- `AccountRepositoryImplTest` (merge): transactions move in `accountId` and `toAccountId`, deleted ones too; target
  balance and snapshots are right; source snapshots are gone; initial-balance rule (an initialized target whose anchor
  predates source transactions ends at the latest bank `postBalance`, no double count; uninitialized target set from
  the latest `postBalance` of either side; credit card keeps its start; neither side has a `postBalance`); chained
  merge rewrites pointers; a live or a deleted transfer between the two is rejected; different package, same id or
  deleted account rejected; a failure mid-way rolls back.
- `AccountRepositoryImplTest` (ignore): with and without trashing, balances rebuilt; transfers are not trashed and the
  other account's balance is unchanged; follow again.
- `MergeAccountsUseCase` and `SetAccountIgnoredUseCase` tests for the pure checks.
- `AccountsSettingsViewModel` test: merge targets list only same-package live accounts; confirm runs the use case and
  emits the snackbar effect; a rejected merge emits the error effect.
- Existing Konsist architecture tests must pass.

## Known risks

- **Merge is final.** Undo would need the moved transaction ids. The dialog warns; accepted.
- **Wrong merge target within one bank.** Two real accounts of one bank can be merged by mistake. Same warning; the user
  can still edit single transactions.
- **Trashed transactions after "Follow again".** They stay in the trash; the user restores them by hand once a trash
  screen exists.
