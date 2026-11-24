# Failed Inbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A release-build inbox that surfaces `FAILED` notification logs carrying the user's money, books them safely as manual transactions, and learns ad keywords from the user.

**Architecture:** Part A is the money-safe core (pure domain functions, one new table with migration 10→11, settlement and classifier changes, a booking write in the data layer). Part B is UI on top (inbox screen, from-log cash entry mode, keyword sheet and list, banner, red dots). The inbox is computed at read time from `notification_logs`; nothing about the inbox is stored except user keywords and two id sets in preferences.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 3 (SQLCipher), Hilt, Navigation3, coroutines/Flow, JUnit + Robolectric, Turbine, Konsist.

**Spec:** `docs/superpowers/specs/2026-11-23-failed-inbox-design.md` (v3). Read it before any task; section numbers below (§1…§7) refer to it.

## Global Constraints

- Money is `Long` VND. A booking and its log status change are one `TransactionRunner.write`. Every log status change is guarded by status and reason in its `WHERE`.
- No notification text, amounts, balances, masks or keywords in Logcat. New entities and models override `toString` (see `NotificationLogEntity.toString`).
- `DATABASE_VERSION = 11`, one hand-written `MIGRATION_10_11` in `FINOTE_MIGRATIONS`, schema `app/schemas/.../11.json` exported. Never destructive.
- Layers, names and limits per `docs/architecture.md`; Konsist tests in `app/src/test/java/io/github/toanhp123/finote/architecture/` must stay green.
- UI per `docs/design-system.md`; dialogs and sheets in their own files under the feature's `component/`.
- Every new string in `values` (vi), `values-en`, `values-zh-rCN`, `values-ja`.
- Fixtures use made-up values only. The two real samples in the spec are already masked; copy them as written.
- Reason codes (exact strings): `no_rule_matched`, `ambiguous_account`, `account_archived`, `invalid_amount`, `invalid_time`, `settle_error`, `adjusted_after`, `partner_failed`, `user_dismissed`, `stuck_repost`, `stuck_ignored`, `user_keyword:<normalizedKeyword>`.
- Inbox-shown reasons: `no_rule_matched` (shown or hidden by the filter), `ambiguous_account`, `account_archived`, `invalid_amount`, `invalid_time`, `settle_error`. Everything else is never an inbox item, except `IGNORED_SPAM` / `user_keyword:*`, which is listed under "Hidden".
- Gradle runs through the `gradle-run` skill: `testDebugUnitTest` after every logic task, `qualityGate` before the squash.
- Skills for implementers: `ponytail:ponytail` always; `kotlin-concurrency-and-flow`, `testing-coroutines-with-runtest`, `testing-flows-with-turbine`, `using-robolectric-correctly` for the Room tasks; `compose-state-and-effects` for Part B.

## Review Focus

1. A real transaction must never end up hidden with no way back: hidden items keep book/dismiss/restore; a keyword never matches parsed text. Owner: Task 4 (`user keyword never hides a parsed notification`), Task 8 (`keyword matching another money-looking item is refused`).
2. The same bank event must never be booked twice: same `notificationKey` already booked, identical copies, wallet partner. Owner: Task 5 (`same key booked later version hides the item`, `identical copies are one item`), Task 7 (`booking a copy group confirms every copy`).
3. Booking into the wrong account or at the wrong time: no lone-account fallback, exact `postTime`. Owner: Task 6 (`card mask with no card account gives no account`), Task 7 (`booked row keeps the exact post time`).
4. Concurrency: settlement, re-parse or a second tap changes the log while the sheet is open. Owner: Task 7 (`log settled meanwhile returns AlreadyHandled and writes nothing`).
5. Adjustment confirm bound to its inputs: account or date changed after the dialog. Owner: Task 7 (`token for another account asks again`).

---

# Part A: money-safe core

### Task 1: Money signals and amount extraction

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/service/MoneySignals.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/notification/service/MoneySignalsTest.kt`

**Interfaces:**
- Produces:
  - `fun looksLikeMoney(title: String, content: String): Boolean` (spec §2)
  - `fun extractVndAmount(title: String, content: String): Long?` (spec §4)
  - `fun extractMaskLast4(title: String, content: String): String?` (last 4 digits of the first signal-1 match)
  - `fun extractSignedType(title: String, content: String): TransactionType?` (`EXPENSE`/`INCOME` only when a `+`/`-` is attached to a currency amount, else null)
  - `fun extractMerchant(title: String, content: String): String?` (text after `payment at` / `tại` / ` at ` up to ` on ` or end of line)
  - Internal sets `BALANCE_LABELS`, `TRANSACTION_LABELS`, `FEE_WORDS` (normalized, as listed in §2 and §4).

- [ ] **Step 1: Write the failing tests**

```kotlin
private val abroadTitle = "- KRW 11,111/VND 1,111,111"
private val abroadContent = "1% Foreign Transaction Fee (Conditions Apply)\nCredit card: 1 .... .... 1111\n" +
    "Converted amount: VND 1,111,111\nAvailable limit: VND 111,111\nPayment at (JOO)GIMCHISEUKOOL on 11/11/1111 at 11:11:11"
private val promoTitle = "🥤 Mời bạn ly cà phê đầu tuần"
private val promoContent = "Miễn phí loạt mã Highlands mua 1+1, Katinat mua 1+1, giảm 11K Circle K...  dành riêng cho bạn, số lượng có hạn. Bấm vào nhận ngay!"

@Test fun `abroad card alert is money`() = assertTrue(looksLikeMoney(abroadTitle, abroadContent))
@Test fun `coffee promo is not money`() = assertFalse(looksLikeMoney(promoTitle, promoContent))
@Test fun `payment without mask or sign is money`() = assertTrue(looksLikeMoney("", "Bạn vừa thanh toán 150,000đ tại Highlands Coffee"))
@Test fun `balance change without sign is money`() = assertTrue(looksLikeMoney("", "Biến động số dư: 500,000 VND. Số dư hiện tại: 2,000,000 VND"))
@Test fun `percent after dots is not a mask`() = assertFalse(looksLikeMoney("", "Giảm đến ...50% cho đơn từ 100K"))
@Test fun `K after dots is not a mask`() = assertFalse(looksLikeMoney("", "Circle K... 50K"))
@Test fun `every non-ignored corpus sample is money`() { /* load fixtures/notification_corpus.json via NotificationCorpusFixtures; for each shouldIgnore == false: assertTrue(looksLikeMoney(title, content), id) */ }

@Test fun `abroad amount is the VND figure`() = assertEquals(1_111_111L, extractVndAmount(abroadTitle, abroadContent))
@Test fun `trailing dot zero zero is dropped`() = assertEquals(1_111_111L, extractVndAmount("", "Converted amount: VND 1,111,111.00"))
@Test fun `trailing comma zero zero is dropped`() = assertEquals(1_111_111L, extractVndAmount("", "1.111.111,00 VND"))
@Test fun `fee before amount is skipped`() = assertEquals(1_111_111L, extractVndAmount("", "Phí GD: VND 11,111\nConverted amount: VND 1,111,111"))
@Test fun `balance only gives null`() = assertNull(extractVndAmount("", "Số dư: 2,000,000 VND"))
@Test fun `abroad mask is 1111`() = assertEquals("1111", extractMaskLast4(abroadTitle, abroadContent))
@Test fun `abroad sign is expense`() = assertEquals(TransactionType.EXPENSE, extractSignedType(abroadTitle, abroadContent))
@Test fun `dash in a date is no sign`() = assertNull(extractSignedType("", "GD ngay 11-11-2026 so tien 50,000 VND"))
@Test fun `abroad merchant`() = assertEquals("(JOO)GIMCHISEUKOOL", extractMerchant(abroadTitle, abroadContent))
```

- [ ] **Step 2: Run** `testDebugUnitTest --tests '*MoneySignalsTest*'` through `gradle-run`. Expected: FAIL, unresolved references.
- [ ] **Step 3: Implement** the five functions in `MoneySignals.kt`. Work on `normalizeForMatch(text)` with whitespace runs (including NBSP) collapsed. Rules and priorities exactly as spec §2 and §4. Overflow (more than 15 digits) or zero gives null.
- [ ] **Step 4: Run** the same test filter. Expected: PASS. Also print (not assert) how many corpus `shouldIgnore == true` samples read as money, in the test output, for the reviewer.
- [ ] **Step 5: Commit** `feat(notif): add bank-agnostic money signals for the failed inbox`

### Task 2: Settlement reasons for pairs and stuck fallbacks

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/service/SettlementPlanner.kt` (constants next to `FAIL_SETTLE_ERROR`)
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/notification/repository/NotificationSettlementRepositoryImpl.kt:169-177` (`fail`)
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/usecase/SettlePendingLogsUseCase.kt:73-94` (`failIfStuck`)
- Modify tests: `SettlePendingLogsUseCaseTest.kt` (L204–217, L250–268), `FakeNotificationSettlementRepository.fail` (L77–83), `NotificationSettlementRepositoryImplTest.kt` (L482–487)

**Interfaces:**
- Produces: `const val FAIL_PARTNER_FAILED = "partner_failed"`, `FAIL_STUCK_REPOST = "stuck_repost"`, `FAIL_STUCK_IGNORED = "stuck_ignored"`, `FAIL_USER_DISMISSED = "user_dismissed"`, `FAIL_NO_RULE_MATCHED = "no_rule_matched"` (replace the literal in `classifyNotification` and the DAO query comment), and `val INBOX_REASONS: Set<String>` = the six inbox-shown reasons.

- [ ] **Step 1: Write the failing tests**
  - `NotificationSettlementRepositoryImplTest`: `fail of a pair gives the bank the reason and the wallet partner_failed` — bank log reason == decision reason, wallet log reason == `partner_failed`, both `FAILED`.
  - Same file: `fail of a pair writes both or neither` — a fake DAO or a trigger that throws on the second update leaves both logs `PENDING`.
  - `SettlePendingLogsUseCaseTest`: `stuck repost fails with stuck_repost`, `stuck ignore fails with stuck_ignored`, `stuck book keeps settle_error`; update the two existing tests to expect `partner_failed` on the wallet side.
- [ ] **Step 2: Run** `testDebugUnitTest --tests '*Settle*' --tests '*NotificationSettlementRepositoryImpl*'`. Expected: FAIL.
- [ ] **Step 3: Implement**: `fail()` runs `db.storageWrite { markSettled(bank) ; markSettled(wallet, reason = FAIL_PARTNER_FAILED) }` (same guards as today); `failIfStuck` maps `Repost` → `FAIL_STUCK_REPOST`, `Ignore` → `FAIL_STUCK_IGNORED`. Update the fake the same way.
- [ ] **Step 4: Run** the filter, then the full `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `fix(dedup): mark failed wallet partners and stuck fallbacks apart`

### Task 3: User ignore keyword table and migration 10→11

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/data/notification/local/entity/UserIgnoreKeywordEntity.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/data/notification/local/dao/UserIgnoreKeywordDao.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/data/database/Migration10To11.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/UserIgnoreKeyword.kt`
- Modify: `FinoteDatabase.kt` (entity, `userIgnoreKeywordDao()`, `DATABASE_VERSION = 11`), `MigrationPolicy.kt` (`FINOTE_MIGRATIONS += MIGRATION_10_11`), `di/DatabaseModule.kt` (provider), `NotificationText.kt` (`normalizeKeyword`, whitespace collapse in `firstMatchingKeyword`)
- Create: `app/schemas/io.github.toanhp123.finote.data.database.FinoteDatabase/11.json` (generated by the build)
- Test: `MigrationPolicyTest.kt` (new case), `app/src/test/java/io/github/toanhp123/finote/data/notification/local/dao/UserIgnoreKeywordDaoTest.kt`, `NotificationTextTest` (existing file for `firstMatchingKeyword`, or create)

**Interfaces:**
- Produces:
  - Table `user_ignore_keywords(id TEXT PK, packageName TEXT NOT NULL, keyword TEXT NOT NULL, normalizedKeyword TEXT NOT NULL, createdAt INTEGER NOT NULL)`, unique index on `(packageName, normalizedKeyword)`, index on `packageName`.
  - `data class UserIgnoreKeyword(id: String, packageName: String, keyword: String, normalizedKeyword: String, createdAt: Long)` with `toString` hiding `keyword` and `normalizedKeyword`.
  - DAO: `suspend fun getByPackage(packageName: String): List<UserIgnoreKeywordEntity>`, `fun observeAll(): Flow<List<UserIgnoreKeywordEntity>>`, `@Insert(onConflict = ABORT) suspend fun insert(e)`, `suspend fun delete(id: String): Int`, `suspend fun getById(id: String): UserIgnoreKeywordEntity?`.
  - `fun normalizeKeyword(text: String): String` = `normalizeForMatch(text)` with whitespace runs (incl. ` `) collapsed to one space, trimmed. `firstMatchingKeyword` collapses whitespace in both text and keyword the same way.
- [ ] **Step 1: Write the failing tests**: `migrating 10 to 11 keeps every row and adds an empty keyword table` (pattern of the existing 9→10 case); `same normalized keyword twice in one package is refused` (insert throws); `same keyword in two packages is fine`; `double space and NBSP still match` for `firstMatchingKeyword("Ưu  đãi lớn", listOf("ưu đãi lớn"))`.
- [ ] **Step 2: Run** `testDebugUnitTest --tests '*MigrationPolicyTest*' --tests '*UserIgnoreKeywordDao*' --tests '*NotificationText*'`. Expected: FAIL.
- [ ] **Step 3: Implement** entity, DAO, migration SQL (CREATE TABLE + two CREATE INDEX matching Room's generated names in `11.json`), wiring. Build once with `assembleDebug` to export `11.json`.
- [ ] **Step 4: Run** the filter. Expected: PASS.
- [ ] **Step 5: Commit** `feat(db): add user ignore keywords table (migration 10 to 11)`

### Task 4: Classifier order, capture and re-parse with user keywords

**Files:**
- Modify: `domain/notification/service/NotificationClassifier.kt` (`classifyNotification`)
- Modify: `domain/notification/usecase/CaptureNotificationUseCase.kt`, `domain/notification/usecase/ReparseFailedLogsUseCase.kt`
- Modify: `domain/notification/repository/NotificationCaptureRepository.kt` + `data/.../NotificationCaptureRepositoryImpl.kt` + test fake (`getUserKeywords`, `getReparseLogs`)
- Modify: `data/notification/local/dao/NotificationLogDao.kt` (`getFailed` → `getReparseCandidates`, `updateFailedOutcome` guard)
- Modify: `domain/notification/model/Classification.kt`, `CaptureResult.kt` (`toString`)
- Test: `NotificationClassifierTest`, `CaptureNotificationUseCaseTest`, `ReparseFailedLogsUseCaseTest`, `NotificationCaptureRepositoryImplTest`

**Interfaces:**
- Consumes: `normalizeKeyword`, `firstMatchingKeyword` (Task 3), `FAIL_NO_RULE_MATCHED` (Task 2).
- Produces:
  - `fun classifyNotification(raw: RawNotification, rules: List<ParserRule>, userKeywords: List<String> = emptyList()): Classification` — order: secret, rule channel+keyword filter, parse, then user keywords only when the parse found nothing → `IGNORED_SPAM`, reason `user_keyword:<kw>` (kw = normalized form).
  - `NotificationCaptureRepository.getUserKeywords(packageName: String): List<String>` (normalized forms).
  - `NotificationLogDao.getReparseCandidates(packages: Set<String>)`: `FAILED`/`no_rule_matched` **or** `IGNORED_SPAM` with `failureReason LIKE 'user_keyword:%'`, oldest first.
  - `updateFailedOutcome` guard becomes `WHERE id = :id AND ((status = 'FAILED' AND failureReason = 'no_rule_matched') OR (status = 'IGNORED_SPAM' AND failureReason LIKE 'user_keyword:%'))`.
  - `ReparseFailedLogsUseCase` changes a log when the new outcome differs from its stored status+reason (a keyword-hidden log whose keyword is gone becomes `FAILED`/`no_rule_matched` or `PENDING`).
  - `Classification.toString()` and `CaptureResult.Ignored.toString()` print no reason text.
- [ ] **Step 1: Write the failing tests**
  - `user keyword never hides a parsed notification` — a rule parses the text and the user keyword matches it: status `PENDING`.
  - `user keyword hides unparsed text as user_keyword` — status `IGNORED_SPAM`, reason `user_keyword:mời bạn` normalized (`moi ban`).
  - `rule keyword still wins before parse` (existing behaviour kept).
  - `capture passes the package's user keywords`.
  - `reparse restores a keyword-hidden log once the keyword is gone` → `FAILED`/`no_rule_matched`.
  - `reparse books a keyword-hidden log a new rule parses` → `PENDING` and a settle is scheduled.
  - `classification toString hides the reason`.
- [ ] **Step 2: Run** `testDebugUnitTest --tests '*Classifier*' --tests '*Capture*' --tests '*Reparse*'`. Expected: FAIL.
- [ ] **Step 3: Implement** as in Interfaces. Capture reads user keywords after `getRules` (skip the read when `rules.isEmpty()`, still `NotTracked`). Re-parse reads them once per package.
- [ ] **Step 4: Run** the filter, then full `testDebugUnitTest`. Expected: PASS. The existing `inactive rules still filter` test stays as is (N-07 is not done at capture).
- [ ] **Step 5: Commit** `feat(notif): apply user ad keywords only to unparsed notifications`

### Task 5: Inbox read model

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/FailedInboxItem.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/service/FailedInbox.kt`
- Create: `domain/notification/repository/FailedInboxRepository.kt`, `data/notification/repository/FailedInboxRepositoryImpl.kt`
- Modify: `NotificationLogDao.kt` (two queries), `di/RepositoryModule.kt`
- Create: `domain/notification/usecase/ObserveFailedInboxUseCase.kt`
- Test: `FailedInboxTest.kt` (pure), `FailedInboxRepositoryImplTest.kt` (Robolectric, in-memory DB)

**Interfaces:**
- Consumes: `looksLikeMoney` (Task 1), `INBOX_REASONS` (Task 2).
- Produces:
  - `data class FailedInboxItem(val id: String /* representative log id: newest copy */, val logIds: List<String>, val packageName: String, val title: String, val content: String, val postTime: Long, val receivedAt: Long, val reason: String, val parsed: ParsedFields?, val isHidden: Boolean, val isKeywordHidden: Boolean)`; `toString` prints `id`, `reason`, `isHidden` only.
  - `fun buildFailedInbox(logs: List<NotificationLog>, handledKeys: Set<Pair<String, String>> /* (packageName, notificationKey) of handled logs */, activePackages: Set<String>): List<FailedInboxItem>` — rules of spec §1: reason filter, active-package filter, same-key suppression (handled log within `REPOST_WINDOW_WITH_BALANCE_MILLIS`; the DAO pre-filters by window, so the function only checks membership), copy grouping by `(packageName, title, content)` for `no_rule_matched`, `isHidden = reason == no_rule_matched && !looksLikeMoney`, keyword-hidden logs as hidden items with `isKeywordHidden = true`; order `postTime` desc, `id` desc.
  - DAO: `fun observeInboxLogs(): Flow<List<NotificationLogEntity>>` (`FAILED` or `IGNORED_SPAM`/`user_keyword:%`), `suspend fun getHandledKeys(keys: List<String>): List<KeyRow>` (`packageName`, `notificationKey`, `postTime` of `CONFIRMED`/`PAIRED_TRANSFER`/`DEDUPLICATED` logs with those keys).
  - `FailedInboxRepository.observeInbox(): Flow<List<FailedInboxItem>>` — combines `observeInboxLogs`, parser rules (`isActive` packages) and `getHandledKeys` with the window check against each candidate's `postTime`.
  - `class ObserveFailedInboxUseCase { operator fun invoke(): Flow<List<FailedInboxItem>> }`
- [ ] **Step 1: Write the failing tests** (pure, `FailedInboxTest`)
  - `adjusted_after partner_failed user_dismissed stuck reasons are not items`
  - `settlement failures are always shown`
  - `promo no_rule_matched is hidden, abroad alert is shown`
  - `package with no active rule gives no items`
  - `same key booked later version hides the item`
  - `identical copies are one item with every log id`
  - `keyword-hidden log is a hidden item flagged isKeywordHidden`
  - `order is newest first`
  - Repository test: `observeInbox re-emits when a log turns FAILED`.
- [ ] **Step 2: Run** `testDebugUnitTest --tests '*FailedInbox*'`. Expected: FAIL.
- [ ] **Step 3: Implement** as in Interfaces.
- [ ] **Step 4: Run** the filter. Expected: PASS.
- [ ] **Step 5: Commit** `feat(notif): read the failed inbox from notification logs`

### Task 6: Prefill for an inbox item

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/model/InboxPrefill.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/notification/service/InboxPrefill.kt` (pure function)
- Create: `domain/notification/usecase/GetInboxPrefillUseCase.kt`
- Modify: `FailedInboxRepository` (+ impl): `suspend fun getItem(id: String): FailedInboxItem?`, `suspend fun getIdentifiers(packageName: String): List<AccountIdentifier>`
- Test: `InboxPrefillTest.kt`

**Interfaces:**
- Consumes: Task 1 extractors, Task 5 `FailedInboxItem`.
- Produces:
  - `data class InboxPrefill(val type: TransactionType, val isTypeKnown: Boolean, val amount: Long?, val accountId: String?, val archivedAccountName: String?, val note: String, val timestamp: Long?)`
  - `fun prefillFor(item: FailedInboxItem, identifiers: List<AccountIdentifier>, accounts: List<Account>): InboxPrefill` — spec §4 table. Account: identifiers of `item.packageName` whose `mask` equals the parsed mask, or `extractMaskLast4` for `no_rule_matched`, any `IdentifierKind`; exactly one owner that is live and usable (not deleted, archived or ignored; follow `mergedIntoId` the way settlement does) → its id; else null. `archivedAccountName` set when that single owner is archived. `timestamp = item.postTime`, null for `invalid_time`. Never `resolveAccount`, never a lone-account fallback.
- [ ] **Step 1: Write the failing tests**
  - `abroad alert prefills expense 1111111 and merchant`
  - `card mask with no card account gives no account` — package has one `ACCOUNT` identifier `9999` only: `accountId == null`.
  - `card mask with a card identifier gives that account`
  - `two owners give no account`
  - `archived owner gives no account and its name`
  - `invalid_amount with null parsed amount falls back to text`
  - `invalid_time gives no timestamp`
  - `settlement failure uses parsed fields`
- [ ] **Step 2: Run** `--tests '*InboxPrefill*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(notif): prefill an inbox item without guessing the account`

### Task 7: Book and dismiss an inbox item

**Files:**
- Create: `domain/notification/model/BookInboxResult.kt`, `domain/notification/model/AdjustmentToken.kt`, `domain/notification/model/InboxBooking.kt`
- Create: `domain/notification/usecase/BookInboxItemUseCase.kt`, `domain/notification/usecase/DismissInboxItemUseCase.kt`
- Modify: `FailedInboxRepository` + `FailedInboxRepositoryImpl` (`book`, `dismiss`)
- Modify: `NotificationLogDao.kt` (guarded `confirmFromInbox`, `dismissFromInbox`)
- Test: `FailedInboxBookingTest.kt` (Robolectric, real DB, `RoomTransactionRunner`)

**Interfaces:**
- Consumes: Task 5 item and same-key rule, Task 6 account lookup, `isCoveredByAdjustment`, `LedgerWriter` (data-internal), `TransactionRules.check`.
- Produces:
  - `data class InboxBooking(val itemId: String, val amount: Long, val type: TransactionType /* EXPENSE or INCOME */, val accountId: String, val categoryId: String?, val note: String, val timestamp: Long, val resolvedAccountId: String? /* from prefill, for the proven check */)`
  - `data class AdjustmentToken(val accountId: String, val since: Long, val newestAdjustmentAt: Long)`
  - `sealed interface BookInboxResult { data object Booked; data object AlreadyHandled; data object CoveredByAdjustment; data class NeedsAdjustmentConfirmation(val token: AdjustmentToken); data class Refused(val error: TransactionError) }`
  - `FailedInboxRepository.book(booking: InboxBooking, confirmation: AdjustmentToken?, transactionId: String, now: Long): BookInboxResult` — one `db.storageWrite`, steps exactly as spec §4 "Save, two phases": re-read the item's logs (all still `FAILED` with an inbox reason, else `AlreadyHandled`); same-key check; proven check only when `accountId == resolvedAccountId` and the log time is valid, with `isCoveredByAdjustment(accountId, logPostTime, rows)`; possibly-covered check with `since = min(logPostTime, timestamp)` (`timestamp` alone for `invalid_time`) → token; a non-null `confirmation` passes only if its `accountId` and `since` equal the current ones and no live `ADJUSTMENT` of the account is newer than `newestAdjustmentAt`; `TransactionRules.check` with the category type; insert `Transaction(source = MANUAL, notificationLogId = representative log id, postBalance = null, timestamp = booking.timestamp)` via `ledger.insert(transaction)` (no cold start); `confirmFromInbox(logIds, transactionId)` must change exactly `logIds.size` rows, else throw to roll back and return `AlreadyHandled`.
  - DAO: `confirmFromInbox(ids: List<String>, linkedTransactionId: String): Int` — `SET status='CONFIRMED', linkedTransactionId=:id WHERE id IN (:ids) AND status='FAILED' AND failureReason IN (<inbox reasons>)`; `dismissFromInbox(ids): Int` — `SET failureReason='user_dismissed'` with the same guard (status stays `FAILED`).
  - `BookInboxItemUseCase(booking, confirmation: AdjustmentToken? = null): BookInboxResult` runs `tx.write` and generates the id; `DismissInboxItemUseCase(itemId): Boolean` (false = already handled).
- [ ] **Step 1: Write the failing tests**
  - `booking writes one manual row and confirms the log`
  - `booked row keeps the exact post time` (08:00 post time on another day: stored timestamp == post time, not noon)
  - `booked row has null post balance and manual source`
  - `booking a copy group confirms every copy`
  - `log settled meanwhile returns AlreadyHandled and writes nothing` (mark the log `CONFIRMED` before the call; no transaction row, balance unchanged)
  - `proven covered returns CoveredByAdjustment and writes nothing` (resolved account with a bank-balance row and a later adjustment)
  - `proven check is skipped on another account` (same data, user picks a different account → `NeedsAdjustmentConfirmation` if it has a later adjustment, else `Booked`)
  - `later adjustment asks for confirmation then books with the token`
  - `token for another account asks again`
  - `adjustment newer than the token asks again`
  - `dismiss keeps FAILED with user_dismissed and the item leaves the inbox`
  - `dismiss of a handled log returns false`
- [ ] **Step 2: Run** `--tests '*FailedInboxBooking*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the filter, then full `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(notif): book or dismiss a failed inbox item in one write`

### Task 8: Learn, preview and delete ad keywords

**Files:**
- Create: `domain/notification/model/AdKeywordRefusal.kt`
- Create: `domain/notification/service/AdKeywordCheck.kt` (pure)
- Create: `domain/notification/usecase/PreviewAdKeywordUseCase.kt`, `LearnAdKeywordUseCase.kt`, `DeleteAdKeywordUseCase.kt`, `ObserveAdKeywordsUseCase.kt`
- Create: `domain/notification/repository/UserKeywordRepository.kt`, `data/notification/repository/UserKeywordRepositoryImpl.kt`
- Modify: `NotificationLogDao.kt` (`getByPackage`, `hideWithKeyword`, `restoreKeyword`), `di/RepositoryModule.kt`
- Test: `AdKeywordCheckTest.kt` (pure), `UserKeywordRepositoryImplTest.kt` (Robolectric)

**Interfaces:**
- Consumes: Task 1 `looksLikeMoney`, Task 3 table and `normalizeKeyword`, Task 4 re-parse.
- Produces:
  - `enum class AdKeywordRefusal { TOO_SHORT, NOT_IN_SELECTED, BANK_NAME, IN_REAL_TRANSACTION, IN_OTHER_MONEY_ITEM, DUPLICATE }`
  - `fun checkAdKeyword(keyword: String, selected: NotificationLog, packageLogs: List<NotificationLog>, bankName: String): AdKeywordRefusal?` — spec §3 step 2, in that order; real money = status `PENDING`/`CONFIRMED`/`DEDUPLICATED`/`PAIRED_TRANSFER`/`IGNORED_ACCOUNT`, or `FAILED` with a reason other than `no_rule_matched`; other money item = another `FAILED`/`no_rule_matched` log with `looksLikeMoney`.
  - `fun suggestAdKeyword(title: String): String` — title with emoji and leading/trailing punctuation stripped, trimmed.
  - `PreviewAdKeywordUseCase(selectedLogId, keyword): AdKeywordPreview(refusal: AdKeywordRefusal?, otherHiddenCount: Int)`
  - `LearnAdKeywordUseCase(selectedLogId, keyword): Outcome<String /* keyword id */, AdKeywordRefusal>` — re-checks, then one write: insert keyword, `hideWithKeyword(ids, reason)` on the matching `FAILED`/`no_rule_matched` log ids computed before the write (guarded on that status+reason).
  - `DeleteAdKeywordUseCase(keywordId)` — one write: delete row, `restoreKeyword(packageName, "user_keyword:<normalized>")` (`IGNORED_SPAM` → `FAILED`/`no_rule_matched`); then `ReparseFailedLogsUseCase(setOf(packageName))`.
  - `ObserveAdKeywordsUseCase(): Flow<Map<String /* packageName */, List<UserIgnoreKeyword>>>`
- [ ] **Step 1: Write the failing tests**
  - `two letters is TOO_SHORT`
  - `keyword not in the selected log is NOT_IN_SELECTED`
  - `bank name is BANK_NAME`
  - `phrase from a confirmed log is IN_REAL_TRANSACTION`
  - `keyword matching another money-looking item is refused` (`IN_OTHER_MONEY_ITEM`)
  - `promo phrase passes and counts other promos`
  - `suggestion strips emoji` (`"🥤 Mời bạn ly cà phê đầu tuần"` → `"Mời bạn ly cà phê đầu tuần"`)
  - Repository: `learn hides the selected and matching promos`, `delete restores them to the inbox`, `learn after the log changed hides nothing else`.
- [ ] **Step 2: Run** `--tests '*AdKeyword*' --tests '*UserKeywordRepository*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the filter, then full `testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(notif): learn and forget ad keywords from the inbox`

# Part B: UI

### Task 9: Inbox markers and nav bar badge

**Files:**
- Create: `domain/notification/repository/InboxMarkerRepository.kt`, `data/notification/repository/InboxMarkerRepositoryImpl.kt` (SharedPreferences `inbox_markers`, pattern of `DriftAcknowledgementRepositoryImpl`)
- Create: `domain/notification/usecase/ObserveInboxAlertsUseCase.kt`, `MarkInboxSeenUseCase.kt`, `DismissInboxBannerUseCase.kt`
- Modify: `ui/common/component/layout/FinoteNavBar.kt` (`FinoteNavItem.hasBadge: Boolean = false`, red dot on the icon; content description gets a "new" suffix), `docs/design-system.md` (badge rule)
- Test: `InboxMarkerRepositoryImplTest.kt`, `ObserveInboxAlertsUseCaseTest.kt` (Turbine), screenshot test for the badge in `FrameScreenshots.kt`

**Interfaces:**
- Produces:
  - `InboxMarkerRepository { fun observe(): Flow<InboxMarkers>; suspend fun markSeen(ids: Set<String>); suspend fun dismissBanner(ids: Set<String>) }`, `data class InboxMarkers(val seenIds: Set<String>, val bannerDismissedIds: Set<String>)`. Writes always store a new `HashSet` copy.
  - `data class InboxAlerts(val shownCount: Int, val hasUnseen: Boolean, val isBannerVisible: Boolean)`; `ObserveInboxAlertsUseCase(): Flow<InboxAlerts>` — shown = items with `!isHidden`; `hasUnseen` = a shown id not in `seenIds`; banner visible = shown > 0 and a shown id not in `bannerDismissedIds`.
  - `MarkInboxSeenUseCase(currentShownIds)` / `DismissInboxBannerUseCase(currentShownIds)` store only the current ids (prunes old ones).
- [ ] **Step 1: Tests**: `new item after seen shows the dot`, `opening clears the dot`, `banner hidden until a new item`, `marker file holds ids only`.
- [ ] **Step 2–4:** run `--tests '*InboxMarker*' --tests '*InboxAlerts*'`, implement, run. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): track seen inbox items and add a nav bar badge`

### Task 10: Inbox screen, Settings row and Settings tab dot

**Files:**
- Create: `ui/settings/inbox/FailedInboxContract.kt`, `FailedInboxViewModel.kt`, `FailedInboxScreen.kt`, `ui/settings/inbox/component/InboxItemRow.kt`, `ui/settings/inbox/component/HiddenSection.kt`
- Modify: `ui/navigation/NavKeys.kt` (`data object FailedInbox : NavKey`, `data class BookInboxItem(val itemId: String) : NavKey`), `NavBackStack.kt` (`openFailedInbox`), `FinoteNavHost.kt` (entry; `TabBar` passes `hasBadge` for Settings from `InboxAlerts.hasUnseen`), `ui/settings/SettingsContract.kt` / `SettingsViewModel.kt` / `SettingsScreen.kt` (row "Failed notifications" in every build, subtitle with count, dot when `hasUnseen`)
- Strings: 4 locales
- Test: `FailedInboxViewModelTest.kt`, `SettingsViewModelTest.kt` (row state), screenshot tests for the screen (shown + hidden section, empty)

**Interfaces:**
- Consumes: Tasks 5, 7 (dismiss), 8 (restore = delete keyword), 9.
- Actions per item: shown or filter-hidden `no_rule_matched` → book, dismiss, ad; settlement failures → book, dismiss; keyword-hidden (`isKeywordHidden`) → restore only (it is `IGNORED_SPAM`, so booking's guard would refuse it; restoring brings it back as a normal item).
- Produces: intents `ItemClicked(id)`, `DismissClicked(id)`, `AdClicked(id)`, `RestoreClicked(id)`, `HiddenToggled`, `BackClicked`; effects `OpenBooking(itemId)`, `OpenAdSheet(itemId)`, `ShowAlreadyHandled`, `Close`. Opening the screen calls `MarkInboxSeenUseCase`. Row shows bank name, amount (`MoneyText`) when known, title, time.
- [ ] **Step 1: Tests**: `opening marks items seen`, `dismiss of a handled item shows the snackbar`, `hidden section starts collapsed`, `restore deletes the keyword`.
- [ ] **Step 2–4:** run, implement, run. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): add the failed notifications inbox`

### Task 11: "This is an ad" sheet, Undo, keyword list

**Files:**
- Create: `ui/settings/inbox/component/AdKeywordSheet.kt`; `ui/settings/capture/component/AdKeywordList.kt`
- Modify: `FailedInboxViewModel` (sheet state, live preview debounced 150 ms, save, Undo snackbar → `DeleteAdKeywordUseCase`), `NotificationCaptureSettingsViewModel` / `Screen` / `Contract` (list per bank, delete)
- Strings: one message per `AdKeywordRefusal`, preview count plural, Undo
- Test: `FailedInboxViewModelTest` (sheet), `NotificationCaptureSettingsViewModelTest` (list, delete)

**Interfaces:**
- Consumes: Task 8 use cases.
- [ ] **Step 1: Tests**: `sheet opens with the suggestion`, `refusal shows its message and keeps the sheet`, `save closes and offers undo`, `undo deletes the keyword`, `capture settings lists and deletes keywords`.
- [ ] **Step 2–4:** run, implement, run. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): mark inbox items as ads and manage ad keywords`

### Task 12: Cash entry from-log mode

**Files:**
- Modify: `ui/cashentry/CashEntryViewModel.kt` (`Factory.create(transactionId: String?, inboxItemId: String? = null)`), `CashEntryContract.kt` (state: `sourceText: SourceText?`, `pendingConfirmation: AdjustmentToken?`, `isCoveredNotice: Boolean`, `archivedAccountName: String?`; intents `AdjustmentConfirmed`, `AdjustmentDismissed`, `DismissFromCovered`), `CashEntryScreen.kt`
- Create: `ui/cashentry/component/SourceNotificationBlock.kt` (collapsible title + content), `ui/cashentry/component/AdjustmentConfirmDialog.kt`, `ui/cashentry/component/CoveredNotice.kt`
- Modify: `FinoteNavHost.kt` (entry for `BookInboxItem` as a sheet, like `editorEntry`)
- Strings: dialog text exactly "A balance adjustment after this time may already include this. Saving will change the account's current balance by this amount." (vi/zh/ja translated), covered notice, already handled
- Test: `CashEntryFromLogViewModelTest.kt`

**Interfaces:**
- Consumes: Task 6 `GetInboxPrefillUseCase(itemId): InboxPrefill?`, Task 7 `BookInboxItemUseCase`, `DismissInboxItemUseCase`.
- Behaviour: prefill from `InboxPrefill`; account empty → Save disabled until picked; type selector visible; the stored timestamp is `prefill.timestamp` until the user picks a date, then that date at the `postTime` time of day (noon when `timestamp` is null). Never call `AddManualTransactionUseCase` in this mode. Map results: `Booked` → close; `AlreadyHandled` → snackbar + close; `CoveredByAdjustment` → `CoveredNotice` with Dismiss; `NeedsAdjustmentConfirmation` → dialog, confirm re-calls with the token; `Refused` → existing save error.
- [ ] **Step 1: Tests**: `from-log mode fills amount note and empty account`, `save is disabled without an account`, `untouched date keeps the post time`, `picked date keeps the time of day`, `confirmation dialog then booked closes`, `changing account after the dialog asks again` (VM passes the old token; use case answers `NeedsAdjustmentConfirmation`), `covered shows the notice and dismiss`, `already handled closes with a message`.
- [ ] **Step 2–4:** run `--tests '*CashEntryFromLog*' --tests '*CashEntry*'`, implement, run. Expected: PASS (existing cash entry tests unchanged).
- [ ] **Step 5: Commit** `feat(ui): book a failed notification from the cash entry sheet`

### Task 13: Transaction list banner, roadmap, final gate

**Files:**
- Create: `ui/transactions/component/FailedInboxBanner.kt` (style of `BalanceDriftBanner.kt`)
- Modify: `TransactionListContract.kt`, `TransactionListViewModel.kt` (collect `ObserveInboxAlertsUseCase`; intents `InboxBannerClicked`, `InboxBannerClosed`), `TransactionListScreen.kt`, `FinoteNavHost.kt` (`onOpenInbox`)
- Modify: `docs/roadmap.md` (step 13 scope; new step "13b `feature/regex-sandbox` (beta)" after 13; N-07 removed; new small debt "wallet pairs failed before `partner_failed` show as two inbox items"; section 1 status)
- Strings: banner plural "N transactions could not be recorded · Review"
- Test: `TransactionListViewModelTest` (banner state, close, click)

- [ ] **Step 1: Tests**: `banner shows the shown count`, `close hides it until a new item`, `click opens the inbox`.
- [ ] **Step 2–4:** run, implement, run. Expected: PASS.
- [ ] **Step 5: Run** `qualityGate` through `gradle-run`; read the summary. Expected: `exit_status` 0.
- [ ] **Step 6: Commit** `feat(ui): show a failed inbox banner on the transaction list` and `docs: plan the regex sandbox and close N-07`
