# Account identity (roadmap step 10): design

Date: 2026-11-22. Status: draft, waiting for approval.
Branch: `fix/account-identity`. Source: `docs/roadmap.md` section 5 step 10, foundation N5, decision D13, debts N-01, N-02, N-05, N-08, N-21. Uses N4 (real migrations).

## Why

The app picks the account of a notification by one key on `Account`: `(bankPackageName, accountNumberMask)`. In real life one account can show several numbers: the account number, a debit card number, a wallet number. With one key per account, every new number creates a new account, and the only fix is a merge whose key stays behind on a deleted row (`mergedIntoId` lookup in `AccountResolver.findByMask`).

The user wants the app to work for any bank, not only the four with system rules (more than 20 in Vietnam, thousands abroad). Rules are data: system seed, OTA feed, and later user-taught rules (step 13). So account matching must be generic code driven by one field on the rule, never per-bank code. This branch builds that model once.

The user has only Techcombank (one account, one credit card). No duplicate account has shown up in dogfood. The branch is groundwork for step 14 (account screen, link 4 digits) and step 23 (credit card), plus the small money debts listed below.

## Goals

1. An account owns any number of identifiers. An identifier is `(packageName, kind, mask)`; kind says whose digits they are: `ACCOUNT`, `CARD`, `WALLET`.
2. A rule says which kind its `mask` group is (`maskKind`). Null means derived from the rule's `accountType`: `WALLET` → `WALLET`, `CREDIT_CARD` → `CARD`, anything else → `ACCOUNT`. System, OTA and (later) user rules all carry it the same way.
3. A merge moves the source's identifiers to the target. Later notifications find the target directly. Example: a debit card that first created its own account is merged into the main account once; every later card notification lands on the main account.
4. N-05: a masked notification whose identifier is unknown attaches to the package's one live account when that account has no identifier at all and the same account type (it was created by a mask-less notification). No second account.
5. Existing data survives: migration 8 → 9 copies every current key into the new table, merged keys included.
6. Small debts closed: N-01, N-02, N-08, N-21 (below), plus a merge guard: two accounts merge only when their types match (a credit card never merges with a bank account).

## Non-goals

- L1 (VCB debit card), L2 (MoMo paid from a bank source), N-06 (VCB card limit read as balance). They need real samples. `vcb_card` keeps `maskKind = null` in this branch.
- A `FUNDING_SOURCE` kind (L2). An enum value stored as TEXT can be added later without a migration.
- UI to link digits to an account, or to suggest a merge. Step 14.
- Credit limit and "remaining limit" as an anchor. Step 23.
- Reading a non-VND amount. Such notifications fail; they are not converted.

## Anti-goals

- Any per-bank branch in Kotlin code (`if (packageName == …)`).
- Losing an existing account key in the migration, or changing which live account it leads to: every masked notification that matched an account before the upgrade must match the same account after it. (The mask-less lookup changes on purpose; see Resolver.)
- A credit card notification landing on a bank account, or the reverse.
- A merge or identifier write outside the one DB write transaction that rebuilds balances.

## Approaches

- **A. Identifier table (picked).** New table, rule field `maskKind`, merge moves rows. Generic for any bank; ready for steps 14, 23 and L1/L2. Cost: biggest branch of part A, a hand-written migration on real data.
- **B. Small fixes only, table at step 14.** No schema change now. Rejected by the user: matching must scale to many banks, and the model does not need VCB samples because "one account, many numbers" holds for every bank.
- **C. Extra columns on `Account` (`cardMask`, …).** Rejected: a fixed number of slots, and still per-shape logic in the resolver.

## Design

### Data

New entity `AccountIdentifierEntity`, table `account_identifiers`:

| Column | Type | Note |
|---|---|---|
| `packageName` | TEXT not null | app that posts the notification |
| `kind` | TEXT not null | `IdentifierKind` name |
| `mask` | TEXT not null | digits as the notification shows them |
| `accountId` | TEXT not null, FK → `accounts.id` (`RESTRICT`, like `transactions`), indexed | owner |

Primary key `(packageName, kind, mask)`: one identifier belongs to one account, and no surrogate id is needed. Kind is in the key so an account number and a card number with the same 4 digits stay apart. Accounts are never hard-deleted, so `RESTRICT` never fires in practice.

`accounts.accountNumberMask` is dropped (data moves to the new table). `bankPackageName` stays: merge same-bank check and mask-less lookup use it. `mergedIntoId` stays as history; the resolver no longer reads it.

`Account.accountNumberMask` goes too. Nothing in the UI reads it: an auto-created account carries its digits in its name (`Techcombank ••1234`), which is what the account list and the merge dialog show.

`parser_rules` gets a nullable TEXT column `maskKind`; `ParserRuleSeeder`'s `INSERT OR REPLACE` writes it, so a later version bump (L1 turning `vcb_card` to `CARD`) reaches old installs. In the OTA codec (`RuleFeedCodec`) today any bad rule throws and the whole feed is refused, while an unknown `accountType` silently falls back to BANK. Neither fits `maskKind`: a fallback would send a newer kind (`FUNDING_SOURCE`) to the wrong account, and refusing the feed would cut old clients off every update. So a rule whose `maskKind` is not an exact `IdentifierKind` name (blank and lower case included) is skipped alone; the rest of the feed loads. `ParserRuleMapper` treats a stored unknown value as unusable, like `accountType`.

Domain: `enum class IdentifierKind { ACCOUNT, CARD, WALLET }`, `data class AccountIdentifier(accountId, packageName, kind, mask)`. `AccountIdentifier`, `AccountIdentifierEntity` and any new resolution or target type that holds a mask override `toString` without it (privacy rule; `AccountTarget.New` already does).

### Migration 8 → 9 (hand-written, in `FINOTE_MIGRATIONS`)

1. Create `account_identifiers` and its indexes.
2. For each account row with `bankPackageName` and `accountNumberMask` not null and not empty, insert one identifier. Kind from the account's `type`: `WALLET` → `WALLET`, `CREDIT_CARD` → `CARD`, else `ACCOUNT` (matches the derived rule kind, so lookups keep matching). Owner:
   - live row (`deletedAt IS NULL`) → itself;
   - merged row (`mergedIntoId` not null) → `mergedIntoId`; when several merged rows share a key, the latest `deletedAt` wins, then `id` (today's resolver rule, made deterministic). No recursive walk is needed: `AccountMergeDao.repointMerged` keeps every chain one hop (A → B → C becomes A → C at the second merge). If the target was later deleted, the identifier lands on a deleted owner, which the resolver treats as free, the same as today ("a deleted target is no match");
   - other deleted row → itself.
   On a key conflict, a live owner beats a merged one, a merged one beats a plain deleted one. Three `INSERT OR IGNORE` passes in that order. Two live accounts on one key (possible today): the non-archived one first, then `createdAt`, then `id`, which keeps today's "first non-archived wins". The loser keeps its rows; the user can merge it.
   A merged pair of different types (a credit card merged into a bank account before the new guard) keeps matching as it does today: anti-goal 2 wins over the new guard for old data. No such merge exists in the dogfood DB as far as known (call made at review).
3. Drop `accountNumberMask`: drop `index_accounts_bankPackageName_accountNumberMask` (a plain `CREATE INDEX` in `8.json`, no inline `UNIQUE`), `ALTER TABLE accounts DROP COLUMN accountNumberMask`, create `index_accounts_bankPackageName`. New-table index names follow Room's pattern (`index_account_identifiers_accountId`) so `validateMigration` passes. Never rebuild `accounts` by table copy: the migration runs inside a transaction where foreign keys cannot be switched off, and dropping the old table would cascade-delete `account_balance_snapshots` or fail on the `RESTRICT` keys of `transactions`. SQLCipher 4.20 ships SQLite ≥ 3.35, which has `DROP COLUMN`. The production schema does not bend to the test machine: if the JVM test's SQLite (Robolectric 4.17) cannot run `DROP COLUMN`, fix the test setup (native SQLite mode), not the schema.
4. Add `parser_rules.maskKind`.

Test with `MigrationTestHelper` (extend `MigrationPolicyTest`) on a v8 file holding: a live keyed account, a merged pair, a deleted unmerged account, two live accounts on one key, a mask-less account, a cash account, plus a transaction and a snapshot. Assert every old key resolves to the same live account after migration, and the transaction and snapshot rows survive. `EncryptedDatabaseTest` (device) runs the same upgrade on SQLCipher; the user runs it.

### Resolver (`AccountResolver`, pure domain)

Input: package, mask, rule (for kind and type), the package's live accounts, the package's identifiers. Deleted accounts are not an input: merges always move identifiers away, so an identifier whose owner is not in the live list is free. `mergedIntoId` is gone from the resolver.

- **Mask present:** look up `(package, kind, mask)`.
  - Found, owner live → same outcome as today (ignored → `Ignored`, archived → rejected, else `Existing`).
  - Found, owner not live → treat as not found (the identifier is free; whoever gets it takes it over).
  - Not found → N-05 check: among the package's live accounts of the target type (archived and ignored count, as in the mask-less case) there is exactly one, and it has no identifier at all → that account, plus "attach this identifier". If that account is ignored or archived the outcome is `Ignored` / rejected and nothing is attached (those decisions never reach the writer). Known limit: once a second account of that type appears, such notifications create a new account instead of being ignored; accepted, the case needs a mask-less ignored account first. Otherwise `New`.
- **Mask absent:** count only the package's live accounts whose type equals the target type (from the rule). Exactly one → use it; more than one → `ambiguous_account`; none → `New` without identifier. So a mask-less card notification never lands on the bank account of the same app (call made at review). Two intended behaviour changes: a mask-less notification that was `ambiguous_account` because the app also holds an account of another type now resolves; one that used to land on the only account of another type (a bank notification on the MB credit card account) now creates its own account.

`AccountResolution.Resolved` / `AccountTarget` gain what the writer needs to attach or create the identifier (builder's call on the exact shape).

### Writer (`NotificationSettlementRepositoryImpl.resolveAccount`)

One settle run plans every log against one snapshot of accounts, then books the decisions one by one, each in its own write transaction (`SettlePendingLogsUseCase`, process-wide mutex). So the plan goes stale inside a run: two masked logs with different masks can both pick the same account for N-05, and two mask-less logs can both plan `New`. Re-checking one identifier does not catch that.

So the writer runs the same pure `resolveAccount` again, on accounts and identifiers read inside the write transaction, and acts on that fresh answer; the planner's target only decides Fail and Ignore. A fresh `Ignored` or rejected answer returns `SettlementError.AccountUnusable`, which rolls back and leaves the log `PENDING` for the next run, as today. Then: create the account if `New`; attach the identifier if the answer carries one. Attaching is an upsert that may only take a key from a deleted owner: insert when the key is absent, re-point when its owner is deleted, and never touch a key whose owner is live (the fresh resolve already found it). This covers both `New` and N-05 `Existing`, so a free key reused through N-05 does not hit the primary key.

### Merge (`AccountRepositoryImpl.merge`, `AccountRules.checkMerge`)

- Move identifiers: `UPDATE account_identifiers SET accountId = target WHERE accountId = source`, in the same transaction as the row moves.
- New rejection `MergeRejection.DIFFERENT_TYPE` when `source.type != target.type`. Both rows are one real account, so their types must match; this is stricter than "credit card only" and simpler. The merge sheet's target list drops those accounts too.

### Small debts

- **N-21:** `moveSource` and `moveDestination` stop setting `updatedAt`. A merge is not a user edit, so moved notification rows stay pairable (`isPairable` checks `updatedAt == createdAt`).
- **N-01:** `parseMoney` rules, VND has no minor unit:
  - A separator followed by exactly 3 digits groups thousands: `100.500`, `100,500`, `1.000.000` → 100500 / 1000000.
  - The last separator followed by 1 or 2 digits is a decimal part. All zeros → dropped (`1,000.00`, `1.000,00`, `100.0` → 1000 / 100). Anything else → null (`100.50`, `100,50`, `100.5`, `1,000.50`). Today `100.5` reads as 1005 đ; this fixes it.
  - A leading `-` or `−` (U+2212), optionally followed by spaces, makes the value negative (`- 500,000` → −500000). `-0` and `-0.00` → 0.
  - The `amount` group must still be > 0, so a negative or zero amount rejects the rule as today. No system rule puts a `-` inside `amount` (`MONEY` is `[\d.,]+`, the sign is its own group).
  System regexes stay as they are: `MONEY` (`[\d.,]+`) never captures a `-`, and there is no real sample of a negative balance. Rules from OTA or the user get the fix now; system rules get a `-?` in their balance group (with a version bump) when a real overdraft sample exists. The roadmap keeps that remainder as a debt (call made at review).
- **N-02 (user decision):** when both accounts have a start balance (`isInitialized`) and no row carries a bank balance, the target's start is kept, as today. The merge dialog now says so: it names the source's start balance that will be dropped, so nothing is lost silently. Summing is wrong (both rows are one real account, the money would count twice); blocking would leave the duplicate stuck. The user fixes a wrong balance later with Adjust. The dialog needs the case flag (both initialized, no anchor) from the merge preview; the builder picks where it is computed. "No anchor" means `BalanceCalculator.anchoredInitialBalance` returns null over the union of both accounts' live transactions, the same set `withMergedStart` sees after the move; snapshots are derived data and play no part. The other asymmetric case already works and stays: only the source initialized → its start is copied to the target (`withMergedStart`).
- **N-08:** close with a test: a v8 DB after migration and `ParserRuleSeeder` holds every system rule with its current `accountType` and `maskKind`. No version bump needed, since every system rule keeps `maskKind = null`.

## Not a merge: several accounts in one bank app

One bank app can hold several real accounts, each with its own balance (the user has this at Techcombank). Each is its own Finote account with its own `ACCOUNT` identifier; a notification updates only the account it names. They never merge. A per-bank total is a sum shown on the account screen (step 14), never stored.

## Left to the builder

- Exact shapes of `AccountResolution` / `AccountTarget` changes, DAO names, file split.
- Whether the merge-target filter lives in the ViewModel or a use case.
- Konsist and architecture rules as in `docs/architecture.md`.
- How the planner gets identifiers (a new repository read next to `getAccounts`, or identifiers inside the account list).

Reach list the builder must cover: `AccountMapper`, `ParserRuleMapper`, `RuleFeedCodec` (feed JSON already has `ignoreUnknownKeys`, so old clients ignore the new field), `NotificationSettlementRepositoryImpl.newAccount` (stops writing the mask column), `ParserRuleSeeder` insert SQL, `AccountDao.getByBankKey` (replaced), `AccountsSettingsViewModel` merge targets, test `Fixtures`, `AccountMapperTest`, `AccountResolverTest`, `MigrationPolicyTest` (v8 rows), `EncryptedDatabaseTest`. The new merge-dialog sentence needs `vi`, `en`, `zh-rCN`, `ja` strings.

## Tests (minimum)

- Resolver: each branch above, including credit card and bank account under one package with N-05 not attaching across types; an identifier whose owner is not live is free.
- Writer: new identifier; free identifier re-pointed through `New` and through N-05 (no primary-key crash); in one run, two masked logs with different masks planned for the same N-05 account (the second must not attach); two mask-less logs planned `New` with no account yet (one account, not two); account archived or ignored between plan and write (`AccountUnusable`, log stays `PENDING`).
- Merge: identifiers move; different types rejected; moved rows still pair (N-21).
- `parseMoney`: `100.500`, `1,000.00`, `1.000,00`, `100.0`, `1,000.50` → null, `100,50` → null, `100.5` → null, `-500,000`, `- 500,000`, `−500,000`, `-0`.
- Migration test above, plus: live and merged on one key (live wins); two merged rows on one key with different `deletedAt` (newer wins); a merge target that was later deleted; a row with `accountNumberMask = ''` (no identifier).
- OTA: a feed with one rule of unknown `maskKind` among valid rules loads the valid ones; `ParserRuleSeeder` writes `maskKind` when a rule's version rises.
- Privacy: `toString` of `AccountIdentifier` and `AccountIdentifierEntity` holds no mask.
- N-08 seeder test.
- Corpus test stays green.
