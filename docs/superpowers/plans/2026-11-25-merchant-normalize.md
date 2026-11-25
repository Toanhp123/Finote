# Auto Category and Merchant Names Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Notification rows get a category and a short merchant name from keyword rules; the user makes rules from the edit sheet or a rule screen, and can apply a rule to past rows.

**Architecture:** A pure matcher in `domain/category/service` runs on the booking path before `LedgerWriter.insert` (settlement and inbox). Rules live in a new Room table seeded with system rules. UI: a second step in the cash entry sheet, and a list + edit screen under Settings.

**Tech Stack:** Kotlin, Room 3 (SQLCipher), Hilt, Compose M3, Navigation3, JUnit4 + Robolectric, Turbine, Konsist.

**Spec:** `docs/superpowers/specs/2026-11-25-merchant-normalize-design.md`

## Global Constraints

- Branch `feature/merchant-normalize` from `dev`. Commit scopes only: `db`, `notif`, `ui`, `build`, or none (`docs:`). No AI attribution in commits.
- Category never moves money: no task touches `AccountBalanceRebuilder`, snapshots or `currentBalance`.
- Rules only fill `categoryId IS NULL`; never overwrite a set category.
- Rules never apply to rows with `notificationLogId == null` (manual entries), at booking or in bulk apply.
- Keywords, merchant names and notes never reach Logcat: every new model, entity, state and draft that holds them overrides `toString()`.
- DB version 13; migration 12 → 13 is hand-written and in `FINOTE_MIGRATIONS`; export `app/schemas/.../13.json`.
- User rules are hard-deleted (matching keys, like `account_identifiers`).
- Seed: wallets and marketplaces (`momo`, `zalopay`, `shopee`, `lazada`, `tiki`) are never system rules.
- Copy in `vi`, `en`, `ja`, `zh-rCN`. UI follows `docs/design-system.md` (no FAB; add is `ic_add` on `FinoteTopBar`).
- Run Gradle through the `gradle-run` skill: `testDebugUnitTest` after each logic task, `qualityGate` at the end.

## Review Focus

1. A short keyword inside a longer word (`be` in `bep`, `an` in `banh`) must not match; system keywords are ≥ 3 chars brand names so they do not hit a person's name → tests in Tasks 1 and 2.
2. A system rule the user switched off stays off after a seed version bump → test in Task 2.
3. Inbox booking with a suggested category the user did not change must not offer a rule → test in Task 7.
4. Deleting a category with 0 transactions but some rules must not drop the rules silently → test in Task 6.
5. Bulk apply must skip manual rows, rows with a category and transfers → test in Task 5.

---

### Task 1: Domain model and matcher

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/category/model/KeywordRule.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/category/service/KeywordMatcher.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/common/TextMatch.kt`; modify `domain/notification/service/NotificationText.kt` and its callers' imports
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/category/service/KeywordMatcherTest.kt`

**Interfaces:**
- Consumes: `normalizeKeyword(text)` and the boundary rule of `firstMatchingKeyword` in `domain/notification/service/NotificationText.kt`; `Category`, `CategoryType`, `TransactionType`.
- Produces:
  - `enum class KeywordMatchType { CONTAINS, EXACT, REGEX }`
  - `data class KeywordRule(id: String, keyword: String, normalizedKeyword: String, matchType: KeywordMatchType, merchantName: String?, categoryId: String, priority: Int, isUserDefined: Boolean, isActive: Boolean, version: Int, createdAt: Long, updatedAt: Long)` with `override fun toString()` that shows `id` only (keyword and name hidden).
  - `data class RuleMatch(val rule: KeywordRule, val categoryId: String, val merchantName: String?)`
  - `fun classify(merchant: String?, note: String?, type: TransactionType, rules: List<KeywordRule>, categories: Map<String, Category>): RuleMatch?` — null for TRANSFER/ADJUSTMENT/REFUND.
  - `fun KeywordRule.matches(text: String): Boolean`
  - `fun guessKeyword(merchant: String?, note: String?): String?`
  - `fun containsPhrase(text: String, keyword: String): Boolean` in **`domain/common/TextMatch.kt`** (not in `domain/category`, so `domain/notification` does not depend on it). Both arguments raw; it normalizes both with `normalizeKeyword`. Move `normalizeForMatch` and `normalizeKeyword` there too; `firstMatchingKeyword` calls `containsPhrase`. No behaviour change: `NotificationTextTest` stays green.
  - Sort uses keyword length for every match type; system rules are `CONTAINS` only, so a long REGEX cannot jump ahead of a specific system rule.

- [ ] **Step 1: Write failing tests** in `KeywordMatcherTest`:
  - `contains matches a whole word without accents` — rule `grab` matches note `"GD THE TAI GRAB HCM"`; rule `cà phê` matches `"CA PHE MUOI"`.
  - `contains does not match inside a word` — `be` vs `"BEP NHA"` → false; `an` vs `"BANH MI"` → false. (A whole-word name hit such as `an` in `"NGUYEN VAN AN"` is prevented by the seed rule of Task 2: system keywords are at least 3 chars and brand names.)
  - `fields are matched one by one` — merchant `"ABC"`, note `"XYZ"`, keyword `"abc xyz"` → no match.
  - `exact equals one field`, `regex is case insensitive`, `invalid regex never matches`.
  - `only rules of the row type match` — EXPENSE row skips a rule whose category is INCOME.
  - `inactive rule, hidden or deleted category never match`.
  - `user rule beats system rule`, `longer keyword wins inside a group` (`grab food` over `grab`, even when `grab` was updated later), `system priority then updatedAt then id`.
  - `transfer and adjustment get no match`.
  - `rule name wins, else merchant stays` — `RuleMatch.merchantName == rule.merchantName ?: merchant`.
  - `guessKeyword` cases: merchant `"MOMO HO CHI MINH"` → `"MOMO HO CHI MINH"`; note `"NGUYEN VAN A chuyen tien"` → `"NGUYEN VAN A"`; note `"Nguyen Van A chuyển khoản"` → `"Nguyen Van A"`; a 50-char note → at most 30 chars, cut at a space; blank both → null.
- [ ] **Step 2: Run** `testDebugUnitTest --tests "*KeywordMatcherTest*"` via `gradle-run`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** the model and the functions above. Order: `compareBy({ !it.isUserDefined }, { -it.normalizedKeyword.length }, { -it.priority }, { -it.updatedAt }, { it.id })`.
- [ ] **Step 4: Run** `KeywordMatcherTest` and `NotificationTextTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(db): add keyword rule model and matcher`.

### Task 2: Table, migration, seed, repository

**Files:**
- Create: `data/category/local/entity/CategoryKeywordRuleEntity.kt`, `data/category/local/dao/CategoryKeywordRuleDao.kt`, `data/category/mapper/KeywordRuleMapper.kt`
- Create: `data/database/Migration12To13.kt`
- Create: `data/category/seed/SystemKeywordRules.kt`, `data/category/seed/KeywordRuleSeeder.kt`
- Create: `domain/category/repository/KeywordRuleRepository.kt`, `data/category/repository/KeywordRuleRepositoryImpl.kt`
- Modify: `data/database/FinoteDatabase.kt` (entity, `keywordRuleDao()`, `DATABASE_VERSION = 13`), `data/database/MigrationPolicy.kt`, `data/database/FinoteDatabaseFactory.kt` (register seeder **after** `SystemCategorySeeder`), `di/DatabaseModule.kt` (provide `KeywordRuleDao`), `di/RepositoryModule.kt` (bind `KeywordRuleRepository`).
- Modify test: `data/database/InMemoryDatabaseRule.kt` (`seedKeywordRules: Boolean = false`, added after categories).
- Test: `data/database/MigrationPolicyTest.kt`, `data/category/seed/KeywordRuleSeederTest.kt`, `data/category/repository/KeywordRuleRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `KeywordRule`, `KeywordMatchType` (Task 1).
- Produces:
  - Table `category_keyword_rules`: columns per spec "Data"; FK `categoryId → categories.id ON DELETE RESTRICT`; indices `categoryId`, `normalizedKeyword`.
  - `interface KeywordRuleRepository { fun observeRules(): Flow<List<KeywordRule>>; suspend fun getRules(): List<KeywordRule>; suspend fun getRule(id: String): KeywordRule?; suspend fun upsertUserRule(rule: KeywordRule); suspend fun deleteUserRule(id: String); suspend fun setActive(id: String, active: Boolean, now: Long) }`
  - `KeywordRuleDao` queries used later: `getAll()`, `observeAll()`, `getById(id)`, `upsert(entity)`, `deleteUser(id)`, `setActive(id, active, now)`, `moveToCategory(ids: Collection<String>, to: String)`, `deleteByCategories(ids: Collection<String>)`, `countByCategories(ids: Collection<String>): Int`.
  - `val SystemKeywordRules: List<CategoryKeywordRuleEntity>` (15–20 single-purpose brands → `sys_*` ids, keywords ≥ 3 normalized chars, version 1).

- [ ] **Step 1: Write failing tests**:
  - `MigrationPolicyTest`: `v12 opens at 13 with an empty rule table and rows intact`, and the existing v8 chain still reaches the current version.
  - `KeywordRuleSeederTest`: `new database gets every system rule`; `onOpen inserts missing rules`; `a higher version updates the rule but keeps isActive false`; `a user rule is never touched`; `every system rule points at an existing sys category and none is a wallet or marketplace keyword`.
  - `KeywordRuleRepositoryImplTest`: `upsert and observe a user rule`; `deleteUserRule removes a user rule but not a system rule`; `setActive flips only isActive and updatedAt`; `blank merchant name is stored as null`.
- [ ] **Step 2: Run** these tests. Expected: FAIL.
- [ ] **Step 3: Implement.** Seeder: same two-path scheme as `ParserRuleSeeder` (`onCreate` writes all; `onOpen` cheap read, then `immediateTransaction` re-read and write). Write = `INSERT` for missing ids; `UPDATE … WHERE id = ? AND isUserDefined = 0 AND version < ?` that sets keyword, normalizedKeyword, matchType, normalizedMerchantName, categoryId, priority, version, updatedAt — never `isActive` or `createdAt`. Migration creates the table and indices only. Entity `toString()` hides keyword and name.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS; `13.json` exported.
- [ ] **Step 5: Commit** `feat(db): add category keyword rules table and seed`.

### Task 3: Auto category at notification booking

**Files:**
- Modify: `data/notification/repository/NotificationSettlementRepositoryImpl.kt:69-131`
- Test: `data/notification/repository/NotificationSettlementRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `classify` (Task 1), `keywordRuleDao().getAll()` and `categoryDao()` reads (Task 2).
- Produces: booked rows carry `categoryId` and `merchantName` from the first match.

- [ ] **Step 1: Write failing tests**: `book sets category and rule merchant name`; `book keeps the parsed merchant when the rule has no name`; `book uses the wallet merchant of a pair` (decision with wallet merchant); `book leaves category null when no rule matches`; `a paired transfer has no category`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** inside the existing write, before building `Transaction`: load rules and live categories, `classify(decision.merchant, decision.note, parsed.type, …)`, set `categoryId = match?.categoryId`, `merchantName = match?.merchantName ?: decision.merchant`, pass the category's type to `TransactionRules.check`.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(notif): categorize notification rows by keyword rules`.

### Task 4: Inbox suggestion and merchant on inbox booking

**Files:**
- Modify: `domain/notification/model/InboxPrefill.kt` (add `categoryId: String?`, `merchantName: String?`), `domain/notification/service/InboxPrefill.kt` (`prefillFor` gains `rules`, `categories`), `domain/notification/usecase/GetInboxPrefillUseCase.kt`, `domain/notification/model/InboxBooking.kt` (add `merchantName: String?`), `data/notification/repository/FailedInboxRepositoryImpl.kt:137-190`, `ui/cashentry/logic/CashEntryPrefill.kt` (seed the category choice from the prefill; carry `merchantName` into `toInboxBooking`)
- Test: `domain/notification/service/InboxPrefillTest.kt`, `data/notification/repository/FailedInboxBookingTest.kt`, `ui/cashentry/CashEntryFromLogViewModelTest.kt`, `ui/cashentry/CashEntryTestSupport.kt` (new use case deps)

**Interfaces:**
- Consumes: `classify`, `KeywordRuleRepository.getRules()`, categories via `CategoryRepository.observeCategories().first()`.
- Produces: `InboxPrefill.categoryId`, `InboxPrefill.merchantName`; `InboxBooking.merchantName`.

- [ ] **Step 1: Write failing tests**: `prefill suggests the category of a matching rule`; `no_rule_matched item matches on title and text`; `prefill keeps the note as today`; `booking writes merchantName`; VM: `from-log sheet preselects the suggested category`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** For a `no_rule_matched` item call `classify(null, "$title\n$content", …)`; else `classify(parsed.merchant, parsed.note, …)`. `merchantName = match?.merchantName ?: parsed?.merchant`. The merchant names the shop, so a type change on the form keeps it; the suggested category follows the normal per-type choice (`selectedCategoryIds`).
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(notif): suggest a category for inbox items`.

### Task 5: Rule use cases and bulk apply

**Files:**
- Create in `domain/category/usecase/`: `SaveKeywordRuleUseCase.kt`, `FindUserKeywordRuleUseCase.kt`, `DeleteKeywordRuleUseCase.kt`, `SetSystemRuleActiveUseCase.kt`, `ObserveKeywordRulesUseCase.kt`, `CountRuleMatchesUseCase.kt`, `ApplyRuleToPastUseCase.kt`
- Create: `domain/category/error/KeywordRuleError.kt`
- Modify: `KeywordRuleRepository` + impl (add `getUncategorizedNotificationRows(): List<Transaction>` and `setCategory(rows: List<Pair<String, RuleMatch>>)`), `TransactionDao` (query + update that keep `updatedAt`)
- Test: one test file per use case under `domain/category/usecase/`, plus repository test cases.

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces:
  - `sealed interface KeywordRuleError { data object TooShort; data object Duplicate; data object CategoryGone }`
  - `SaveKeywordRuleUseCase(id: String?, keyword: String, merchantName: String?, categoryId: String): Outcome<KeywordRule, KeywordRuleError>` — `id == null` creates; a user rule with the same `(normalizedKeyword, category type)` and a different id → `Duplicate`; keyword < 2 normalized chars → `TooShort`.
  - `FindUserKeywordRuleUseCase(keyword: String, type: CategoryType): KeywordRule?` — its own use case (a ViewModel cannot read a repository; a use case exposes `invoke` only). Used by Task 7 for "Update rule".
  - `CountRuleMatchesUseCase(rule: KeywordRule): Int` and `ApplyRuleToPastUseCase(rule: KeywordRule): Int` — same selection: live EXPENSE/INCOME, `categoryId IS NULL`, `notificationLogId IS NOT NULL`, matched with only that rule; writes `categoryId` and `merchantName = COALESCE(:ruleName, merchantName)` (a rule without a name never clears one); `updatedAt` unchanged. Apply reads rows, re-checks that the rule's category is live and not hidden, matches and writes inside one `TransactionRunner.write`. Both use cases also take `CategoryRepository` for the `categories` map of `classify`.

- [ ] **Step 1: Write failing tests**: save create/update/duplicate/too-short/category-gone; `same keyword of the other type is not a duplicate`; delete of a system rule does nothing; count equals rows written; `bulk apply skips manual rows, rows with a category, transfers and deleted rows`; `bulk apply keeps updatedAt`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** with `TransactionRunner.write` around read-check-write, like `DeleteCategoryUseCase`.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(db): add keyword rule use cases and bulk apply`.

### Task 6: Category delete moves rules (N-28)

**Files:**
- Modify: `data/category/repository/CategoryRepositoryImpl.kt:70-79` (`deleteAndMove`), `domain/category/repository/CategoryRepository.kt` (kdoc; add `countRules(categoryIds: Set<String>): Int`), `domain/category/usecase/CountCategoryTransactionsUseCase.kt` → returns `DeleteImpact(transactions: Int, rules: Int)`, `ui/settings/categoryedit/CategoryEditViewModel.kt:192-205`, delete sheet text in `ui/settings/categoryedit/component/`
- Modify also: `ui/settings/categoryedit/logic/DeleteChoice.kt` (`DeleteSheet` holds `DeleteImpact`), `CategoryEditContract.kt`, `CategoryEditScreen.kt`, test fake `FakeCategoryRepository`
- Test: `CategoryRepositoryImplTest.kt`, `CategoryEditViewModelTest.kt`, `CountCategoryTransactionsUseCaseTest.kt`, `DeleteCategoryUseCaseTest.kt`

**Interfaces:**
- Consumes: `KeywordRuleDao.moveToCategory`, `deleteByCategories`, `countByCategories` (Task 2).
- Produces: `data class DeleteImpact(val transactions: Int, val rules: Int)`.

- [ ] **Step 1: Write failing tests**: `deleteAndMove moves rules to the target`; `a moved rule that duplicates a target rule of the same keyword and type is dropped, the target's rule stays`; `deleteAndMove to null deletes the rules`; VM: `delete with 0 transactions and rules opens the move sheet with the rule count`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** In `deleteAndMove`, before `softDeleteAll`: delete the deleted branch's user rules whose `normalizedKeyword` already exists on a rule of `targetId`, then move the rest. `DeleteChoice.Uncategorized` only when both counts are 0.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): move keyword rules when a category is deleted`.

### Task 7: "Apply to similar?" step in the cash entry sheet

**Files:**
- Create: `ui/cashentry/logic/RuleOffer.kt` (pure: when to offer, prefill), `ui/cashentry/logic/RuleOfferActions.kt` (plain `@Inject` class holding the four rule use cases; the VM calls it), `ui/cashentry/component/RuleOfferPage.kt` (built from `FinoteTextField`, not `TextPage`, which owns its focus and its Done button)
- Create: `ui/common/component/overlay/BulkApplyDialog.kt` with `@FinotePreviews` (shared with Task 8)
- Modify: `ui/cashentry/CashEntryContract.kt` (state `ruleOffer: RuleOfferState?`, `bulkApplyCount: Int?`; intents `RuleKeywordChanged(text)`, `RuleNameChanged(text)`, `RuleSaveClicked`, `RuleSkipped`, `BulkApplyConfirmed`, `BulkApplyDismissed`), `ui/cashentry/CashEntryViewModel.kt`, `ui/cashentry/CashEntryScreen.kt`, strings ×4
- Test: `ui/cashentry/logic/RuleOfferTest.kt`, `ui/cashentry/CashEntryEditViewModelTest.kt`, `ui/cashentry/CashEntryFromLogViewModelTest.kt`, `ui/cashentry/CashEntryTestSupport.kt`

**Interfaces:**
- Consumes: `guessKeyword` (Task 1); `SaveKeywordRuleUseCase`, `FindUserKeywordRuleUseCase`, `CountRuleMatchesUseCase`, `ApplyRuleToPastUseCase` (Task 5).
- Produces:
  - `fun ruleOfferFor(notificationLogId: String?, type: TransactionType, merchant: String?, note: String, startCategoryId: String?, pickedCategoryId: String?): RuleOfferState?` — fed with the **form values at Save** (current type and note), not the row read at open.
  - `data class RuleOfferState(val keyword: String, val merchantName: String, val categoryId: String, val type: CategoryType, val existingRuleId: String?, val isSaving: Boolean, val error: KeywordRuleError?)` with `override fun toString()` that hides keyword and name.

- [ ] **Step 1: Write failing tests** (`RuleOfferTest`): `no offer without a log id`; `no offer when the category did not change` (edit: row value; inbox: the suggestion); `no offer when the new category is null`; `no offer without merchant and note`; `uses the note typed on the form`; `prefill uses the merchant, then the guess`. VM: `edit save with a new category shows the rule step instead of leaving`; `skip leaves`; `back and swipe on the rule step act as skip`; `editing the keyword to one of a user rule switches to update`; `save with matches shows the count, confirm applies and leaves`; `double tap saves once`; `error keeps the step open with the error`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** The step shows while `ruleOffer != null` (not a `CashEntryPage`): Back and sheet dismiss send `RuleSkipped`, which hides the sheet (the row is already saved). Each keyword change re-runs `FindUserKeywordRuleUseCase` and sets `existingRuleId` (button "Tạo luật" / "Cập nhật luật"). `CashEntryViewModel.kt` is at 399 of 400 lines (`ResponsibilityKonsistTest`): first move `saveInbox`, `onInbox` and `dismissInbox` into `ui/cashentry/logic/InboxSaving.kt` (same pattern as `CashEntryReducers.kt`), then wire the rule step through `RuleOfferActions`; the VM must end ≤ 400 lines.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: PASS (Konsist included).
- [ ] **Step 5: Commit** `feat(ui): offer a keyword rule after a category change`.

### Task 8: Rule screens and list row merchant

**Files:**
- Move: `ui/cashentry/component/PickerRow.kt` → `ui/common/component/control/PickerRow.kt`, and `ui/cashentry/component/CategoryList.kt` → `ui/common/component/finance/CategoryList.kt`; both get `@FinotePreviews`; `CategoryList` gains `isNoneShown: Boolean = true` (the rule editor passes false). Update cash entry imports.
- Create: `ui/settings/keywordrules/` (`KeywordRulesScreen.kt`, `KeywordRulesViewModel.kt`, `KeywordRulesContract.kt`, `component/…`), `ui/settings/keywordruleedit/` (`KeywordRuleEditScreen.kt`, `KeywordRuleEditViewModel.kt`, `KeywordRuleEditContract.kt`, `component/DeleteRuleDialog.kt`); bulk apply uses the shared `BulkApplyDialog` (Task 7)
- Modify: `ui/navigation/NavKeys.kt` (`KeywordRulesSettings`, `EditKeywordRule(id: String?)`), `ui/navigation/FinoteNavHost.kt`, `ui/settings/SettingsContract.kt` + `SettingsViewModel.kt` + `SettingsScreen.kt` (row in the Categories group), `ui/transactions/logic/TransactionRowText.kt:55-83`, strings ×4
- Test: `KeywordRulesViewModelTest.kt`, `KeywordRuleEditViewModelTest.kt`, `ui/transactions/logic/TransactionRowTextTest.kt`; screenshot tests under `app/src/screenshotTest` (Settings, new components)

**Interfaces:**
- Consumes: Task 5 use cases.
- Produces: screens reachable from Settings.

- [ ] **Step 1: Write failing tests**: `rowSubtitle shows merchant instead of note for expense and income`; `rowSubtitle keeps the note without a merchant`; `an auto-paired transfer keeps its note` (`TransferPairing` copies `merchantName` onto the transfer); list VM: `user rules come before system rules`, `switch toggles a system rule`; edit VM: `save new rule with matches asks bulk apply`, `changing only the name does not ask`, `duplicate shows error`, `delete asks then removes`, `an unknown id or a system rule id closes the screen`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** following `ui/settings/categories` and `categoryedit` (feature template). Merchant replaces the note only for EXPENSE, INCOME and REFUND. Empty state when there is no user rule. Flow-returning domain/data functions start with `observe`; Boolean properties start with `is`/`has`/`should` (`NamingKonsistTest`).
- [ ] **Step 4: Run** `testDebugUnitTest`, then update the screenshot goldens for the changed and new composables if the gate validates them (check `qualityGate` in `app/build.gradle.kts`). Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): add keyword rule screens and show merchant names`.

### Task 9: Docs and gate

**Files:**
- Modify: `CLAUDE.md` (hard-delete exception adds `category_keyword_rules`), `docs/architecture.md` (same exception), `docs/roadmap.md` (section 1 status, step 16 ✅ with spec link, N-28 keeps only the Budget part, new debt: step 13b adds "Pick from note" to the rule step; DB version 13)
- Modify: spec status line.

- [ ] **Step 1:** Edit the docs above.
- [ ] **Step 2: Run** `qualityGate` via `gradle-run`. Expected: `exit_status` 0.
- [ ] **Step 3: Commit** `docs: record step 16`.
