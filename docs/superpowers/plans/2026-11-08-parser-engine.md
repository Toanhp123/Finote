# Parser Engine (P2b-1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse captured bank and e-wallet notifications with named-group regex rules, so a passing notification is stored `PENDING` with `parsed*` fields, or `FAILED` with `no_rule_matched`.

**Architecture:** A pure `parseNotification` in `domain/notification` runs the package's active rules by priority. `CaptureNotificationUseCase` calls it right after the P2a filter and writes one row with the final status. `parser_rules` rows become one regex each (spec 3.8), seeded per bank; the corpus gate proves 100% on VCB, MB and MoMo.

**Tech Stack:** Kotlin, Room 3 (KSP), Hilt, kotlinx-serialization-json, JUnit4, Robolectric, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-08-parser-engine-design.md`

## Global Constraints

- Branch `feature/parser-engine` from `dev`. Commit scope `parser` (or `db`). English, imperative, lowercase subject ≤ 72 chars. No `Co-Authored-By`.
- Commit steps run only after the user approves each commit (`CLAUDE.md`). Stage files by name.
- `domain` imports no `android.*`/`androidx.*`. Layers per `docs/architecture.md`.
- No `android.util.Log`, `println`, `printStackTrace`. Test failure messages name corpus item ids and field names only, never notification text, amounts or masks.
- Money is `Long` VND. Never `Double`.
- `@Suppress` only for `LongMethod`, `CyclomaticComplexMethod`, `LongParameterList`, with `// why:`.
- No new dependencies.
- Run Gradle via the `gradle-run` skill, wrapper `'.\gradlew.bat'` from Bash. After every logic change run `testDebugUnitTest`.
- `FinoteDatabase` goes 4 → 5 through the existing destructive fallback; no migration code.
- A wrong parsed value is worse than `FAILED`: when unsure, reject the match.

## Review Focus

1. An expanded notification that just misses its full rule falls through to a looser rule and loses balance and note → Task 5 corpus gate checks expanded text field by field.
2. Amount written with NBSP / narrow NBSP, or `1,500,000.00 VND` → must be 1 500 000, never ×100 → Task 2 `parseMoney` tests.
3. User turns off every rule of a bank → spam must still be `IGNORED_SPAM`, not `FAILED` → Task 3 test.
4. One rule row with corrupt JSON or a lowercase `fallbackTransactionType` → that rule is dropped, the other rules of the bank still capture → Task 1 repository test.
5. A real payment whose note looks like spam ("Doi qua Shop -30,000 VND") must not become `IGNORED_SPAM`; after this branch it may be `PENDING` or `FAILED` → Task 3 updates `SystemParserRulesCorpusTest`.

---

### Task 1: Schema, models and mappers

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/ParserRule.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/NotificationLog.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/ParsedFields.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/ParserRuleEntity.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/NotificationLogEntity.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/ParserRuleMapper.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/NotificationLogMapper.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/NotificationCaptureRepositoryImpl.kt:17-18`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/ParserRuleSeeder.kt` (UPSERT columns and `bindRule` only)
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/SystemParserRule.kt` (fill new columns only)
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/FinoteDatabase.kt:33` (version 5)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/notification/ParserRuleMapperTest.kt`, `NotificationCaptureRepositoryImplTest.kt`, `NotificationFixtures.kt`, and every test that builds a `ParserRule` or `NotificationLog`

**Interfaces:**
- Produces:
  - `ParserRule` gains `regexPattern: String`, `fallbackTransactionType: TransactionType?`, `dateFormatPattern: String?`, `isActive: Boolean`, `updatedAt: Long`.
  - `data class ParsedFields(val amount: Long, val type: TransactionType, val accountMask: String?, val postBalance: Long?, val merchant: String?, val note: String?)` in `domain/notification/model`.
  - `NotificationLog` gains `matchedRuleId: String? = null` and `parsed: ParsedFields? = null` (defaults, so P2a callers compile until Task 3).
  - `ParserRuleEntity` gains columns `regexPattern: String`, `fallbackTransactionType: String?`, `dateFormatPattern: String?`, `isActive: Boolean`, `updatedAt: Long`.
  - `NotificationLogEntity` gains `parsedNote: String?`. `parsedType` stores `TransactionType.name` (`INCOME` / `EXPENSE`).
  - `fun ParserRuleEntity.toDomain(): ParserRule?` — null on bad JSON or a `fallbackTransactionType` that is not exactly `INCOME` or `EXPENSE`.

- [ ] **Step 1: Write the failing tests**

`ParserRuleMapperTest`:
- `bad allowlist json gives null`: `parserRuleEntity(channelIdAllowlist = "[oops").toDomain()` → `null`.
- `bad keywords json gives null`: `parserRuleEntity(ignoreKeywords = "not json").toDomain()` → `null`.
- `unknown fallback type gives null`: `fallbackTransactionType = "expense"` → `null`; `"TRANSFER"` → `null`.
- `fallback type maps to enum`: `"EXPENSE"` → `TransactionType.EXPENSE`; `null` → `null`.
- Update the round-trip test to carry the new fields.

`NotificationCaptureRepositoryImplTest`:
- `a broken rule does not hide the other rules of the package`: insert `parserRuleEntity(id = "bad", ignoreKeywords = "x")` and `parserRuleEntity(id = "good")` → `getRules("com.test.bank").map { it.id } == listOf("good")`.
- `parsed fields round trip`: insert a `NotificationLog` with `matchedRuleId = "r"` and `ParsedFields(1_500_000, EXPENSE, "1234", 0, "Shop", "note")` → the stored entity has `parsedAmount = 1_500_000`, `parsedType = "EXPENSE"`, `parsedPostBalance = 0`, `parsedNote = "note"`.

`NotificationFixtures.parserRuleEntity` gains params `regexPattern = ""`, `fallbackTransactionType: String? = null`, `isActive = true`.

- [ ] **Step 2: Run** `testDebugUnitTest` via `gradle-run` → compile failure / the new tests fail.

- [ ] **Step 3: Implement**

- Mapper: wrap the decode in `runCatching { … }.getOrNull()`; fallback via `TransactionType.entries.firstOrNull { it.name == raw && it in setOf(INCOME, EXPENSE) }`, null when `raw != null` and no match.
- Repository: `mapNotNull { it.toDomain() }`.
- Log mapper writes `matchedRuleId` and the `parsed` fields; reverse mapping is not needed.
- `SystemParserRule.kt`: filter rules get `regexPattern = ""`, `fallbackTransactionType = null`, `dateFormatPattern = null`, `isActive = true`, `updatedAt = 0`. Task 5 replaces them. An empty pattern never yields an amount, so every passing notification becomes `FAILED` until then.
- Seeder: UPSERT and `bindRule` cover the 13 columns.
- Database version 5.

- [ ] **Step 4: Run** `testDebugUnitTest` via `gradle-run` → `exit_status` 0.

- [ ] **Step 5: Commit** `feat(db): add regex columns to parser rules and parsed note to logs`

---

### Task 2: Parser

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/NotificationParser.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/ParseResult.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/notification/NotificationParserTest.kt`

**Interfaces:**
- Consumes: `ParserRule`, `ParsedFields` (Task 1).
- Produces:
  - `sealed interface ParseResult { data class Parsed(val ruleId: String, val fields: ParsedFields); data object NoMatch }` in `domain/notification/model`.
  - `fun parseNotification(title: String, content: String, rules: List<ParserRule>): ParseResult` — uses only `isActive` rules; caller passes all rules.
  - `fun parseMoney(raw: String): Long?`

- [ ] **Step 1: Write the failing tests**

`parseMoney`:
- `"1.500.000"`, `"1,500,000"`, `"1 500 000 VND"`, `"1 500 000 VND"`, `"1,500,000.00 VND"`, `"1.500.000,00đ"` → `1_500_000L`.
- `"50.000đ"` → `50_000L`; `"0 VND"`, `"0đ"` → `0L`.
- `"abc"`, `""`, `"12345678901234567890"` → `null`.

`parseNotification` (build rules with a local `rule(id, priority, pattern, fallback = null, isActive = true)` helper):
- `credit with sign`: pattern `"(?<sign>[+-])(?<amount>[\\d,.]+) VND"` on `"TK 1234 +1,500,000 VND"` → `Parsed("r1", ParsedFields(1_500_000, INCOME, null, null, null, null))`.
- `zero balance is kept`: pattern with `SD (?<balance>[\\d,.]+ ?VND)` and balance `0 VND` → `postBalance == 0L`.
- `higher priority wins`: two matching rules priority 1 and 5 → ruleId of priority 5; equal priority → smaller id.
- `fallback type when no sign group`: pattern `"Thanh toan (?<amount>[\\d.]+)d"`, fallback `EXPENSE` → `type == EXPENSE`.
- `unknown sign rejects the rule even with a fallback`: sign group matches `"x"`, fallback `INCOME`, second rule matches → ruleId of the second rule; alone → `NoMatch`.
- `no sign and no fallback rejects`.
- `zero amount falls through to the next rule`.
- `bad pattern is skipped`: first rule `"(?<amount>[0-9"`, second valid → second rule's result.
- `inactive rule is not used` → `NoMatch`.
- `missing mask group gives null mask`.
- `blank merchant becomes null; merchant and note are trimmed`.
- `multi line content`: content `"So tien: +200,000 VND\nSo du: 1,000,000 VND\nND: an trua"`, pattern with `(?s)` → amount, balance and note all set.
- `title carries the sign`: title `"Nhận tiền thành công"`, content `"Số tiền 50.000đ"`, pattern `"(?s)Nhận tiền.*?Số tiền (?<amount>[\\d.]+)đ"`, fallback `INCOME` → `INCOME`, 50 000.

- [ ] **Step 2: Run** `testDebugUnitTest --tests "*NotificationParserTest"` via `gradle-run` → fails (unresolved).

- [ ] **Step 3: Implement** per spec "Parser" steps 1–7 and `parseMoney`.

```kotlin
// parseMoney core: after keeping only [0-9.,]
val cleaned = kept.replace(Regex("[.,]\\d{2}$"), "")   // decimal tail; VND groups are 3 digits
return cleaned.filter(Char::isDigit).ifEmpty { null }?.toLongOrNull()
```

Group names: `Regex("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>").findAll(pattern)`; read a group only when its name is in that set (`match.groups[name]?.value`).

- [ ] **Step 4: Run** the same test → pass. Then full `testDebugUnitTest` → `exit_status` 0.

- [ ] **Step 5: Commit** `feat(parser): add named-group notification parser`

---

### Task 3: Parse inside capture

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/usecase/CaptureNotificationUseCase.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/CaptureResult.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/notification/usecase/CaptureNotificationUseCaseTest.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/notification/SystemParserRulesCorpusTest.kt`

**Interfaces:**
- Consumes: `parseNotification`, `ParseResult` (Task 2).
- Produces: `CaptureResult.Failed` (`data object`, doc: stored as `FAILED`, no rule matched). `CaptureResult.Stored` now means `PENDING` with parsed fields.

- [ ] **Step 1: Write the failing tests** in `CaptureNotificationUseCaseTest` (the local `rule()` helper gains `pattern`, `fallback`, `isActive`):
- `passing notification that matches is pending with parsed fields`: rule pattern `"(?<sign>[+-])(?<amount>[\\d,]+)"`, `raw(content = "SD TK -50,000 tai quan")` → `Stored`; stored log has `matchedRuleId == "r1"`, `parsed.amount == 50_000`, `parsed.type == EXPENSE`, `failureReason == null`.
- `passing notification without a match is failed`: rule pattern `"nothing(?<amount>\\d+)"` → `CaptureResult.Failed`; log `status == FAILED`, `failureReason == "no_rule_matched"`, `parsed == null`, title and content kept.
- `blocked notification is not parsed`: keyword rule blocks a text that the pattern would match → `Ignored`, `parsed == null`, `matchedRuleId == null`.
- `inactive rules still filter`: every rule `isActive = false`, keyword `"ưu đãi đặc quyền"` → spam text gives `Ignored`; a transaction text gives `Failed`.

`SystemParserRulesCorpusTest`: `no transaction sample is blocked` and `payments that merely contain a spam-like word stay pending` assert `result !is CaptureResult.Ignored` (rename the second to `… are not blocked`).

- [ ] **Step 2: Run** `testDebugUnitTest` via `gradle-run` → new tests fail.

- [ ] **Step 3: Implement**: after `reason == null`, call `parseNotification(raw.title, raw.content, rules)`; status `PENDING` + `matchedRuleId`/`parsed`, or `FAILED` + `"no_rule_matched"`. The filter keeps using all rules. Return `Failed` for a stored `FAILED` row.

- [ ] **Step 4: Run** `testDebugUnitTest` → `exit_status` 0.

- [ ] **Step 5: Commit** `feat(parser): parse notifications during capture`

---

### Task 4: Seeder lock order (debt #1)

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/ParserRuleSeeder.kt:20-39`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/notification/ParserRuleSeederTest.kt`

**Interfaces:** none new.

- [ ] **Step 1: Write the failing test** `outdatedRules keeps user rules and same versions`. The race is not reachable through Room's callback, so extract `internal fun outdatedRules(stored: Map<String, StoredRule>, seeds: List<ParserRuleEntity>): List<ParserRuleEntity>` (top-level in the seeder file; `StoredRule(version: Int, isUserCustom: Boolean)` becomes `internal`) and test it directly: user-custom stored rule with a lower version → not in the result; system rule with a lower version → in it; same version → not in it; missing → in it.

- [ ] **Step 2: Run** `testDebugUnitTest --tests "*ParserRuleSeederTest"` → fails (unresolved).

- [ ] **Step 3: Implement** `onOpen`: `outdatedRules(storedRules(connection), SystemParserRules)`; empty → return. Else `BEGIN IMMEDIATE TRANSACTION`, compute `outdatedRules` again from a fresh `storedRules`, write those, `COMMIT`; keep the existing rollback guard. The race itself is not unit-testable; the reviewer checks the order by reading.

- [ ] **Step 4: Run** `testDebugUnitTest` → `exit_status` 0.

- [ ] **Step 5: Commit** `fix(parser): read seeded rules inside the write transaction`

---

### Task 5: Seed regex rules and corpus gate

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/SystemParserRule.kt`
- Create: `app/src/test/java/io/github/toanhp123/finote/data/notification/NotificationParserCorpusTest.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/data/notification/ParserRuleSeederTest.kt` (expected rule set)
- Read only: `app/src/test/resources/fixtures/notification_corpus.json`

**Interfaces:**
- Consumes: `CaptureNotificationUseCase`, `FakeNotificationCaptureRepository`, `NotificationCorpusFixtures`.
- Produces: `SystemParserRules` = regex rules only (no `*_filter_v1`), ids `<bank>_<shape>_v1` (`vcb`, `mb`, `momo`, `tcb`), `version = 1`, `updatedAt = 0`. Per bank: `private val VCB_KEYWORDS = SHARED_KEYWORDS + listOf(…)` etc.

- [ ] **Step 1: Write the gate** `NotificationParserCorpusTest`. For each financial item, run capture on `item.content` (collapsed) and, when `bigText != null`, on `item.bigText` (expanded), each with a fresh `FakeNotificationCaptureRepository(SystemParserRules.mapNotNull { it.toDomain() })`, then read the stored log.

Field check `mismatches(item, log, collapsed): List<String>` returns field names:
- `sign`: label `"+"` ↔ `INCOME`, `"-"` ↔ `EXPENSE`.
- `amount`, `accountMask`, `postBalance`, `merchant`, `note`: when the label is non-null, the parsed value must equal it. On collapsed text (a `bigText` item run on `content`) a null parsed value is allowed for all but `sign` and `amount`. When the label is null, the parsed value must be null too, except `postBalance` on an item with `availableCredit` (must be null).
- Not parsed (`FAILED`) → `["unparsed"]`.

Tests:
- `vcb mb and momo parse every sample correctly`: failures map `"${item.id}/${variant}: ${fields}"` must be empty.
- `techcombank parses nothing wrong`: same check, but `unparsed` is allowed; assert no other mismatch. No printing (Global Constraints): the implementer counts the parsed share once and records it in the commit body and the roadmap.
- `ignored samples are never parsed`: every `ignoredOrSpam()` item → `CaptureResult.Ignored`, log `parsed == null`.

- [ ] **Step 2: Run** `testDebugUnitTest --tests "*NotificationParserCorpusTest"` → fails (every item `unparsed`).

- [ ] **Step 3: Split keywords per bank.** Move each phrase of `IGNORE_KEYWORDS` to the bank whose ignored samples need it; a phrase needed by two or more banks stays in `SHARED_KEYWORDS`. `SystemParserRulesCorpusTest` must stay green.

- [ ] **Step 4: Write the regex rules**, bank by bank, until the gate is green. One rule per message shape, optional groups for expanded-only parts (spec "Seed rule style"). Inline flags in the pattern. `fallbackTransactionType` only for shapes whose direction is words, not a sign.

- [ ] **Step 5: Update `ParserRuleSeederTest`** expected ids to the new rule set; the user-rule and version-upgrade tests keep their meaning with one of the new ids.

- [ ] **Step 6: Run** `testDebugUnitTest` → `exit_status` 0.

- [ ] **Step 7: Commit** `feat(parser): seed regex rules for vcb, mb, momo and techcombank` (body: Techcombank parse rate).

---

### Task 6: Gate and docs

**Files:**
- Modify: `docs/roadmap.md` (P2b-1 status line)
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/NotificationLog.kt` doc comment (no longer "P2b adds the parsed fields"; text is dropped only for secrets)

- [ ] **Step 1: Run** `qualityGate` via `gradle-run` → `exit_status` 0. Read its summary; fix and re-run on failure.
- [ ] **Step 2:** Add under P2b in `docs/roadmap.md`: `Trạng thái (<date>): parser-engine xong, chờ review.` with the Techcombank rate.
- [ ] **Step 3: Commit** `docs: record parser engine status`
