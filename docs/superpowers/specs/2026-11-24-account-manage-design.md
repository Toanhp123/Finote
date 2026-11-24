# Account management (roadmap step 14): design

Date: 2026-11-24. Status: draft after builder check and an external critique (Gemini, 2026-11-24), waiting for the user's approval.
Branch (planned): `feature/account-manage` from `dev`. Source: `docs/roadmap.md` section 5, step 14; debts N-10, N-22. Uses foundation N5 (account identifiers). Related: D13 (identifier table), D16 (no auto adjustment amount), step 17 (report rules skip `ADJUSTMENT`), step 23 (credit card).

## Why

The user wants every account action the roadmap lists for step 14. Today the app can create accounts only by itself (from notifications) and through the seeded cash wallet. The Accounts screen lists accounts and lets a bank account be merged or ignored. Nothing else is possible:

- no way to add a second cash wallet, a savings account, or a bank account by hand;
- no way to rename an account the app named;
- no way to correct a wrong balance on an account that has no bank balance in its notifications (cash never shows the drift banner, so the reconcile sheet is unreachable);
- no way to tell two Techcombank accounts apart or see the bank total;
- an archived account disappears with no way back;
- the list order cannot be changed.

Use cases for create, update, archive and delete already exist (`CreateAccountUseCase`, `UpdateAccountUseCase`, `ArchiveAccountUseCase`, `DeleteAccountUseCase`) but no screen calls them.

## Goals

1. Create an account: name, type, icon, color, start balance, "exclude from net worth"; optional bank app. Masked numbers are added from the edit screen after the account is created: create mode has no identifier section.
2. Edit an account: name, icon, color, exclude from net worth, star; balance (see Balance rules); masked numbers.
3. Star one primary account. It is the default account of the cash-entry sheet.
4. Group accounts of the same bank app. The group row shows the summed balance (computed, not stored) and expands to its accounts, each with its own balance.
5. Suggest a merge when two accounts look like one real account.
6. Reorder accounts by drag and drop.
7. Archive and unarchive. Archived accounts live in a collapsed section at the bottom of the list.
8. Delete an account without live transactions. With transactions, delete is refused and archive is offered (spec 3.1).
9. Fix N-10 (one snackbar for every error; the account sheet does not refresh when the list changes) and N-22 (unpair refused because an account is archived shows a generic save error).

## Non-goals

- Credit card type, `creditLimit`, `statementDay`, `dueDay`: step 23. The create form does not offer `CREDIT_CARD`.
- Per-account currency: Could-Have. Currency stays VND.
- Filtering the transaction list by account: step 20 (search and filter).
- Changing an account's type after creation.
- Picking any installed app as the bank app: only apps that have parser rules.

## Anti-goals

- Editing a balance must never silently rewrite past months once the start is fixed.
- Linking masked numbers must never steal an identifier owned by another account, and must never break notification matching for existing accounts.
- No balance, mask or account number in Logcat (CLAUDE.md privacy rule).

## Approaches

| Approach | Trade-off | Verdict |
|---|---|---|
| A. Separate edit screen: new route `EditAccount(id?)` with its own ViewModel; the list screen keeps grouping, archive section, reorder and merge suggestions | More files. Room for a long form; keyboard does not cover fields; list ViewModel stays small; same pattern as `EditTransaction`, `BookInboxItem` | **Picked by the user** |
| B. Form in a bottom sheet (like cash entry) | Fast to open. A ~7-field form plus an identifier list plus keyboard is cramped; step 3 already hit sheet height and focus issues | Rejected |
| C. Everything in the existing screen and `AccountsSettingsViewModel` | Fewest files. ViewModel (7.5K) grows past Konsist limits; hard to extend | Rejected |

Reorder: drag and drop (user's choice) over up/down buttons. Needs one new dependency (see Constraints).

## Decided with the user

### Balance rules

Chosen over "always edit the start balance" (approach A of the discussion) because it never rewrites history silently and matches Cashew, which the user knows.

- **On create:** the user types the start balance. `CreateAccountUseCase` stores it with `isInitialized = true` (as today). Exception: a bank or wallet account with a bank app and an empty start field is stored with `initialBalance = 0` and `isInitialized = false`, so the first notification balance derives the start (as for an account the app creates). Without a bank app the field is required (empty means 0).
- **Only an explicit start change fixes the start.** Saving the edit screen calls `setInitialBalance` only when the start field differs from the stored value. Renaming or recoloring a provisional account leaves `isInitialized = false`.
- **Start still provisional** (`isInitialized = false`: the seeded cash account before its first reconcile, or a bank account whose start `LedgerWriter` still derives from notification balances): the edit screen shows "Start balance" as an editable field. Saving sets `initialBalance`, sets `isInitialized = true`, and runs `AccountBalanceRebuilder.rebuild` (current balance and monthly snapshots), in one write transaction. This is the roadmap's `setInitialBalance`. The user's value wins over the derived one: with `isInitialized = true`, `LedgerWriter` stops re-deriving the start. A wrong value then shows as a drift banner, which is intended.
- **Start fixed** (`isInitialized = true`): the start balance is read-only. The edit screen offers "Correct balance": the user types the real current balance and the app books one `ADJUSTMENT` row dated now through the existing `ReconcileBalanceUseCase`. Past months stay as they were; the adjustment shows in the transaction list. A zero difference books nothing (existing behavior).
- Report rules (step 17) already exclude `ADJUSTMENT` from income and expense, as Money Lover and Cashew do.

### Primary account

- New column `accounts.isPrimary` (Boolean, default false). Migration 11 → 12 with a migration test (N4). The "at most one" rule lives in the write path, not in a partial unique index: Room entities cannot declare `WHERE` on an index, and an index made only in the migration fails Room's schema check on a fresh install.
- At most one live account is primary. Starring one clears the others in the same write transaction. Archiving, ignoring or deleting the primary account clears its star.
- Cash-entry default order becomes: primary → last used → seeded cash → lowest `sortOrder` (decided in the old roadmap, `roadmap-history.md`). Only `isUsable` accounts qualify.

### Grouping

- Group key: `bankPackageName`. Accounts without one (cash, manual savings without a bank) are not grouped.
- A bank with one live account shows that account directly, no group row.
- Group row: bank display name and the sum of `currentBalance` of its members that are not ignored, through `MoneyText`. Ignored members stay in the group, muted, and outside the sum. `isExcludedFromNetWorth` members are in the sum: the group shows what sits at that bank, not net worth. Tap expands or collapses.
- Groups start collapsed. A group whose members carry a merge suggestion shows a marker on the group row.

### Archive section

- Archived accounts are hidden from the main list and shown in a collapsed "Archived (n)" section at the bottom. The edit screen of an archived account offers "Unarchive".

### Delete

- Refused when live transactions touch the account (`AccountError.HasTransactions`). The count already covers source rows, transfer destinations and `ADJUSTMENT` rows (`getLiveTransactionCount`). Notification logs hold no account id, so they do not block. The message offers Archive instead.
- The seeded cash account can be archived but not deleted. (my call, see below)
- Identifiers of a deleted account are not removed: `resolveAccount` already treats a key whose owner is deleted as free, and settlement repoints it. Manual add (call 10) does the same.

### Edit screen: save vs immediate actions

- **Save** (one write transaction): name, icon, color, exclude from net worth, bank app (while null), start balance (only when changed). `update` plus `setInitialBalance` plus `setBankPackage` run in one `tx.write` in a new `SaveAccountUseCase`.
- **Immediate** (each its own transaction, no Save needed): star, correct balance, add or remove a masked number, ignore or follow again, archive or unarchive, delete, merge.
- Form state: the editable fields are a draft, filled once from the stored account and then owned by the screen. Live fields (balance, `isInitialized`, identifiers, flags, star) follow the database. If the account is deleted or merged elsewhere while the screen is open, the screen closes and says why.

### What each state allows

| Action | Active | Ignored | Archived | Seeded cash |
|---|---|---|---|---|
| Edit name, icon, color, exclude | yes | yes | yes | yes |
| Star | yes | hidden | hidden | yes |
| Correct balance | yes | no (follow again first) | no (unarchive first) | yes |
| Add or remove masked number | yes, with a bank app | yes | no | not shown (cash) |
| Archive | yes | yes | shows Unarchive | yes |
| Delete without transactions | yes | yes | yes | no |

- Bank app and masked numbers are offered for `BANK`, `WALLET`, `SAVINGS`; never for `CASH`.
- Archiving the last usable account is refused, so the cash-entry sheet always has a default.

### Debts

- N-10: each `AccountError` / `MergeRejection` gets its own message. The edit screen observes the account, so it updates when the account changes elsewhere (the sheet it replaces did not).
- N-22: the unpair path in the transaction edit sheet shows why it was refused when an account is archived.

## My calls (the user can override)

1. **Bank app on create** is chosen from the apps that have parser rules (today Techcombank, VCB, MB, MoMo), shown by display name. Optional.
2. **Bank app on edit** can be set only while it is null. Once set it is locked, like type, so notification matching cannot break.
3. **Masked numbers:** the edit screen lists the account's identifiers (kind + 4 digits) and can add one (kind defaults from type: `defaultKind()`). Adding requires a bank app. The mask is a 4-digit string (leading zeros kept). If the key `(packageName, kind, mask)` already belongs to another live account, the app does not write. It runs `checkMerge` first: when the merge is allowed it offers "Merge into <name>?" (Save any unsaved form first); when not (different type, transfer between them) or the holder is archived, it says why and names the holder. Removing the last number of a bank account asks for confirmation: a later notification may create a new account.
4. **Merge suggestion:** shown on an account row when it and another live account pass `AccountRules.checkMerge` (same bank app, same type, no transfer between them) and at least one of them has no identifier. With several candidates, one suggestion per row, preferring a candidate that has identifiers, then the oldest. The suggestion opens the existing merge dialog. "Not the same" dismisses the pair; the dismissal is stored in preferences as the ordered pair `(min id, max id)`, not in the database.
5. **Reorder mode:** a "Reorder" action shows the top-level items only: each bank group as one row, each ungrouped account as one row. Dragging a group moves its members as a block. Done renumbers `sortOrder` for all live accounts in one write transaction, keeping members' order inside each group. Member order inside a group is not editable in this step. (Changed after review: a flat list let cash drop between two Techcombank accounts and then jump back out of the group.)
6. **Ignored accounts** stay in the main list, muted, as today. Ignore and "follow again" move from the old sheet to the edit screen.
7. **Seeded cash account** cannot be deleted (it is the last-resort default). Archive allowed.
8. **Icon and color pickers:** a small fixed set of icons and the design-system palette. No free color input.
9. **Reconcile sheet** is reused, moved from `ui/transactions/component` to a shared place both screens can call; one ViewModel path per screen.
10. **Identifier add** checks the key and inserts in one write transaction. The key is the table's primary key, so the database also refuses a duplicate. Adding a number only affects notifications that arrive later; failed-inbox items and old rows are not re-matched.
11. **Merge suggestion edge cases:** the rule is kept as written in call 4. Two accounts the user keeps apart on purpose both carry identifiers once notifications arrive, so the suggestion stops by itself; "Not the same" covers the rest. A dismissed pair whose account is merged or deleted simply never matches again; stale pairs are not cleaned.
12. **Star rules:** only an `isUsable` account can be starred (the star is hidden otherwise). A merge whose source is primary moves the star to the target, in the merge transaction (`AccountMergeRepositoryImpl`).
13. **sortOrder:** a new account gets max + 1, both from the create screen and from `newAccount` in settlement (today it writes 0, which would put auto-created accounts above the user's order). Ties keep the existing `sortOrder, createdAt` order. Archive and unarchive keep the stored value.
14. **Bank app list and names** come from the active parser rules: distinct `packageName` with the rule's `bankName`. An OTA rule for a new bank adds it. No `PackageManager` lookup (it would need `<queries>` on Android 11+).
15. **Translations:** `zh-rCN` and `ja` strings may be machine-written, as today (debt N-16 covers review).

## Constraints

- Money is `Long` VND. Balance changes run in one `@Transaction` (CLAUDE.md).
- Soft delete only for accounts. Identifier rows have no `deletedAt` and are hard-deleted on removal (see Removing a masked number).
- New dependency for drag and drop: `sh.calvin.reorderable:reorderable`, through `gradle/libs.versions.toml`. Version picked at plan time.
- Layers and limits: `docs/architecture.md`, Konsist tests. UI tokens and components: `docs/design-system.md`. Dialogs and sheets in their own file under the feature's `component/`.
- `AccountRepository.update` keeps its contract (type, bank key, balances, flags are not touched). New writes (`setInitialBalance`, `setPrimary`, `reorder`, `setBankPackage`, `addIdentifier`, `removeIdentifier`) are separate repository methods; the use case that combines several runs them in one `tx.write`.
- No mask, balance or raw `SQLiteConstraintException` message in Logcat.
- Strings in `vi`, `en`, `zh-rCN`, `ja`.

### Removing a masked number (decided 2026-11-24)

- The edit screen can remove an identifier. This hard-deletes the `account_identifiers` row: it is a matching key, not money data, and old transactions do not change. Rejected: "edit only" (still a delete + insert, just hidden) and "no removal" (a typo would need a new account plus a merge).
- The branch adds this exception to the soft-delete rule in `CLAUDE.md` and `docs/architecture.md`.

## Left to the builder

- Exact layout of the edit screen and the order of fields.
- How to keep `EditAccountViewModel` inside Konsist limits (it calls about ten use cases); split dialog logic or group use cases at plan time.
- Reconcile sheet copy: it takes only an `Account`, so no bank-balance text needs a null case; check the wording fits an account opened from the edit screen.
- Test split: use-case tests for `setInitialBalance`, `setPrimary`, `reorder`, identifier add/conflict; migration test 11 → 12; ViewModel tests with Turbine; screenshot tests for list (grouped, archive section, reorder mode) and edit screen (provisional vs fixed balance).
