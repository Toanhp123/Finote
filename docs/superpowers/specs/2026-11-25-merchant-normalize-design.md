# Auto category and merchant names (roadmap step 16): design

Date: 2026-11-25. Status: implemented on feature/merchant-normalize (2026-11-25). Revised the same day after an outside review (Gemini) checked it against the code.
Branch (planned): `feature/merchant-normalize` from `dev`. Source: `docs/roadmap.md` section 5, step 16; debt N-28 (rule part). Spec 3.8 (`CategoryKeywordRule`), spec 4 "Phân loại thông minh & Chuẩn hóa Merchant". Related: step 13b (text selection, later), step 17 (dashboard by category), step 18 (`Budget`, other half of N-28), step 20 (FTS on `merchantName`).

## Why

Rows booked from notifications have `categoryId = null` today (`NotificationSettlementRepositoryImpl.book`). The dashboard (step 17) draws spending by category, so without this step most of the chart is "uncategorized". The user also wants a short shop name ("Grab", "Highlands") in place of the raw text a rule captured.

Real data (Techcombank, the only bank the user has). `TCB_NOTE` puts the whole note line in `note` and, when it sees a known lead-in, the shop in a nested `merchant` group:

- `GD THE TREN INTERNET … TAI MOMO HO CHI MINH VN APPCODE 11111` → `merchant = "MOMO HO CHI MINH"` (the city stays in it);
- `Thanh toan QR tai <shop>` → `merchant = <shop>`;
- `NGUYEN VAN A chuyen tien` → no merchant, note only.

So matching runs on both `merchantName` and `note`.

Today no screen reads `Transaction.merchantName`: the list shows category and note only, the edit sheet does not show it, and inbox booking drops it. Showing it lets the user see during dogfood whether a rule named a row right.

## Goals

1. Table `category_keyword_rules` (spec 3.8, plus `normalizedKeyword`, `isActive`, `version`).
2. A pure domain matcher. When a notification row is booked, the first matching active rule sets `categoryId` and, if the rule has one, `merchantName`.
3. The failed inbox prefill uses the same matcher to suggest a category, and inbox booking keeps the merchant.
4. "Apply to similar?" step: when the user changes the category of a row that came from a notification, offer to create a rule. The keyword is prefilled by a guess the user can edit (option A, chosen by the user).
5. A rule screen under Settings → Categories group: list, create, edit, delete user rules; turn system rules on and off.
6. A new rule, or a change of keyword or category, can be applied to **past** notification rows that have no category, after the user confirms a count ("Will categorize 12 past transactions").
7. A small seed of system rules (about 15–20 single-purpose Vietnamese brands mapped to `sys_*` categories); each one can be turned off.
8. N-28 (rule part): deleting a category moves its rules with its transactions.
9. Show `merchantName` on the transaction list now (user's choice): in `rowSubtitle` it **replaces** the note when not blank, for EXPENSE, INCOME and REFUND only (`Techcombank · Highlands`); an auto-paired transfer keeps its note, since `TransferPairing` copies `merchantName` onto it; without it the note shows as today. The full note stays in the edit sheet. Editing the merchant name in the sheet is not in this step; the user fixes the rule.

## Non-goals

- No rule for manual entries, at booking or in bulk apply. A row counts as "from a notification" when `notificationLogId != null` (inbox bookings are stored as `MANUAL` with the log id).
- Never overwrite a category that is set, by the user or by an earlier rule. A rule only fills `categoryId IS NULL`.
- No learning beyond explicit rules: no fuzzy match, no statistics, no ML.
- No text-selection picker for the keyword. Step 13b builds it; then the step gets a "Pick from note" button (new debt line).
- No change to parser rules (`ParserRule`). That is step 13b.
- Transfers and adjustments are never categorized (`TransactionRules` refuses a category on them).
- A rule that only renames a merchant without a category: `categoryId` is required (spec 3.8).

## Anti-goals

- A wrong guess must stay cheap. A category does not move money: balances and snapshots never change, so this step touches no balance code. A wrong rule only mislabels reports, and the user can fix the row and the rule.
- Never silently recategorize many past rows. Bulk apply always shows the count first.
- Never force a multi-purpose name into one category. Wallets and marketplaces (`momo`, `zalopay`, `shopee`, `lazada`, `tiki`) are not in the seed: a payment "TAI MOMO" may be food, a bill or shopping. The user can still make their own rule for them.

## Approaches

1. **Domain matcher, called at booking time (chosen).** `classify(merchant, note, type, rules)` in `domain/category/service`. Settlement `book` calls it inside the same write, before it builds the `Transaction`; inbox prefill calls it for the suggestion; bulk apply uses it over uncategorized notification rows. Logic stays in domain and is easy to test; manual entries are untouched. Cost: two call sites.
2. Auto-assign inside `LedgerWriter.insert`. One call site, but a data-layer writer would hold a business rule (against `docs/architecture.md`) and would see transfers, paired rows and adjustments.
3. A background worker that categorizes later. Decoupled, but rows show up uncategorized first, extra writes, hard to test.

Chosen by the user: 1. Roadmap says "on the write path (N1)": the category is set before `LedgerWriter.insert`, so this holds.

## Design

### Data

`category_keyword_rules` (migration 12 → 13, hand-written in `FINOTE_MIGRATIONS`, with a migration test like `MigrationPolicyTest`):

| Column | Type | Note |
|---|---|---|
| `id` | String PK | system: `kw_<slug>`; user: UUID |
| `keyword` | String | as typed, for display |
| `normalizedKeyword` | String, indexed | `normalizeKeyword(keyword)`; dedup and lookup, like `UserIgnoreKeywordEntity` |
| `matchType` | `CONTAINS` / `EXACT` / `REGEX` | UI creates `CONTAINS` only; the others exist per spec |
| `normalizedMerchantName` | String? | blank is stored as null |
| `categoryId` | String, FK → categories, `ON DELETE RESTRICT` | |
| `priority` | Int | system rules only; user rules 0 |
| `isUserDefined` | Boolean | |
| `isActive` | Boolean | system rules can be turned off, not deleted |
| `version` | Int | system rules; user rules 0 |
| `createdAt`, `updatedAt` | Long | |

User rules are hard-deleted: like `account_identifiers`, a rule is a matching key, not a record. Add this table to that exception in `CLAUDE.md`, `docs/architecture.md` and `docs/roadmap.md` section 2.

Model, entity and any UI draft override `toString()` so `keyword` and `normalizedMerchantName` never reach a log.

### Seed

- New `CategoryKeywordRuleSeeder` (`RoomDatabase.Callback`), registered **after** `SystemCategorySeeder` in `createFinoteDatabase`: the FK needs the `sys_*` rows, and on the destructive path (v1–v7) categories are seeded in `onOpen` only.
- Migration 12 → 13 only creates the table and its index. Existing installs get the system rules from `onOpen`.
- Missing system rule: `INSERT`. Stored system rule with a lower `version`: `UPDATE` keyword, match type, name, category, priority and version, **never `isActive`**. Not `INSERT OR REPLACE`, which would turn a rule the user switched off back on. User rules are never touched.
- System rules point only at `sys_*` categories, which `CategoryRules.checkDelete` never lets the user delete (only hide), so the FK always resolves.

### Matching

- Inputs: `merchant` and `note`, each matched **on its own** (never joined, so the end of one cannot run into the start of the other).
- `CONTAINS`: whole word or phrase, reusing `normalizeKeyword` and the boundary rule of `firstMatchingKeyword` (`domain/notification/service/NotificationText.kt`). A bare substring would find `be` in `bep` or a short keyword inside a person's name.
- `EXACT`: `normalizeKeyword(field) == normalizedKeyword`. `REGEX`: `Regex(keyword, IGNORE_CASE)` on the raw field; an invalid pattern never matches.
- Only active rules whose category is live, not hidden, and of the row's type (EXPENSE row → EXPENSE category, INCOME → INCOME).
- Order: user rules first; inside each group, longer `normalizedKeyword` first (a specific rule beats a general one: `grab food` before `grab`); then system `priority` desc; then newest `updatedAt`; then `id`. First match wins.
- Rules are loaded once per booking (the table is small) inside the write.

### Where it runs

- `NotificationSettlementRepositoryImpl.book`: classify `decision.merchant` and `decision.note` (the planner already prefers the wallet side, so a MoMo + bank pair keeps the MoMo shop), set `categoryId` and `merchantName` (rule name, else `decision.merchant`), then pass the real category type to `TransactionRules.check`.
- When pairing turns the row into a `TRANSFER`, the original's category does not matter (the transfer has none).
- Inbox prefill: suggested category when the item has none. Input is the parsed merchant and note when the item has them; a `no_rule_matched` item has none, so its notification title and text go in as the note (`EXACT` then never matches, which is fine).
- Inbox booking: `InboxBooking` gains `merchantName` (prefill: rule name, else parsed merchant); `FailedInboxRepositoryImpl.insertBooking` writes it. Today `InboxPrefill` folds the merchant into `note`; keep that note as is.
- A rule name replaces the parsed merchant at booking and in bulk apply alike.

### "Apply to similar?" step

- Lives **inside the cash entry sheet**: after a successful Save the sheet shows a second step instead of closing, so the ViewModel is still alive. Skip or Create closes it. This applies to the edit mode and the inbox mode.
- Shown when: the row has `notificationLogId != null`, the user picked a category that is not null and differs from the one the sheet opened with (row value, or the inbox suggestion), and the row has a merchant or a note.
- Prefilled keyword, in order: `merchantName` (a TCB card line already gives `MOMO HO CHI MINH`); else the words before `chuyen tien` / `chuyen khoan` (normalized compare); else the note cut to 30 characters at a word boundary. Prefilled short name: the keyword in title case.
- Duplicate check on `(normalizedKeyword, category type)`: a user rule with both → "Update rule" (category and short name). Only a system rule has it → Create makes a user rule that wins (user rules match first); system rules are never edited. A rule of the other type is a different rule.
- Create, or Update with a new category, shows the bulk-apply confirm when the count is above zero.

### Rule screen

- Settings → Categories group → new row "Auto category rules". Two features per `FeatureTemplateKonsistTest`: the list and the edit screen.
- List: user rules, then system rules. Row: keyword → category icon and name · short name. System row: a switch only, no edit. User row: opens the edit screen. Add is an `ic_add` action on `FinoteTopBar` (the design system has no FAB). Empty state for no user rules.
- Edit: keyword, short name, category picker, delete (`FinoteDialog` confirm). Keyword at least 2 characters after normalizing; `(normalizedKeyword, type)` unique among user rules.
- The category picker comes from `ui/cashentry/component/CategoryList.kt`, moved to `ui/common/component/` (shown categories only, plus the current one if it is hidden). Reuse the category page pieces of N-30 where they fit; do not copy again.
- Saving a new rule, or a new keyword or category, offers bulk apply with the count. A change of short name alone does not.

### Bulk apply

- One write: live EXPENSE / INCOME rows with `categoryId IS NULL` and `notificationLogId IS NOT NULL`; run the matcher with **only the saved rule**; set `categoryId`, and `merchantName` when the rule has one. `updatedAt` stays (same as `deleteAndMove`; pairing reads `updatedAt = createdAt`).
- No balance rebuild: category does not move money.
- The count in the dialog comes from the same function as the write.

### N-28

- `deleteAndMove(categoryIds, targetId)`: in the same write, before `softDeleteAll`, rules of those categories point at `targetId`, except one whose keyword a rule of `targetId` already has (that one is dropped, the target's stays); null target hard-deletes them. `DeleteCategoryUseCase` already checks the target (`CategoryRules.checkMoveTarget`), and only user categories can be deleted, so only user rules are affected.
- The delete flow counts rules as well as transactions: a category with 0 transactions and some rules opens the move sheet too, with the rule count in its text. Remove the rule part from N-28 (Budget part stays for step 18).

## Constraints

- Notes, merchants and keywords never reach Logcat.
- Domain code stays pure Kotlin; Konsist rules in `architecture/` must stay green.
- DB version 13; from v8 a missing migration crashes and keeps the file.

## Left to the builder

- Exact seed list and which `sys_*` category each maps to (single-purpose brands only).
- UI copy in 4 languages (`vi`, `en`, `ja`, `zh-rCN`).
- Use case and DAO split, file names, following existing patterns.

## New debt

- Step 13b: add "Pick from note" (text selection) to the "Apply to similar?" step.
