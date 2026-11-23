# Account Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One account owns many identifiers `(packageName, kind, mask)`. Notifications resolve through them, merges move them, and the debts N-01, N-02, N-05, N-08 and N-21 close.

**Architecture:** A pure domain resolver reads live accounts and identifiers. The planner calls it once; the writer calls it again inside the write transaction and acts on the fresh answer. A new Room table `account_identifiers` replaces `accounts.accountNumberMask`, filled by the hand-written migration 8 → 9. Rules gain `maskKind`; when it is null, the kind comes from `accountType`.

**Tech Stack:** Kotlin, Room 3.0.3 (`androidx.room3`), `room3-testing`, Robolectric 4.17, JUnit4, kotlinx.serialization, Compose (one dialog line).

**Spec:** `docs/superpowers/specs/2026-11-22-account-identity-design.md` (approved 2026-11-22). Read it first. This plan does not repeat its reasons.

## Global Constraints

- Branch `fix/account-identity` from `dev`. Squash into `dev` after `reviewer` and a green `qualityGate` (`docs/git-workflow.md`).
- Commit format `<type>(<scope>): <subject>`. Scope is one of `parser`, `db`, `notif`, `ui`, `ota`, `balance`. No AI attribution trailer. Pre-commit runs spotless and detekt; `qualityGate` also runs detekt on test sources.
- Commits: the main session commits after each task. The `coder` subagent never commits.
- Money is `Long` VND. Any balance or snapshot write runs inside one write transaction.
- Privacy: masks, balances and notification text never reach Logcat or `toString`.
- No per-bank branch in Kotlin (`if (packageName == …)`).
- `IdentifierKind` values are exactly `ACCOUNT`, `CARD`, `WALLET`. No `FUNDING_SOURCE`.
- System rules keep `maskKind = null`. No rule version bump in this branch.
- `DATABASE_VERSION` becomes 9. Export `app/schemas/.../9.json`. `8.json` must not change.
- Gradle only through the `gradle-run` skill, with `'.\gradlew.bat'` from Bash.
- Fixtures use fake data only.
- Skills per task: `ponytail:ponytail`, `testing-coroutines-with-runtest`, `using-robolectric-correctly` (Room tests), `compose-state-and-effects` (Task 7), `gradle-run`, `superpowers:verification-before-completion`.

## Review Focus

1. **Two real sub-accounts in one app** (the user's Techcombank case). Account `1111` already has its identifier, and a notification with `2222` arrives. N-05 must not attach `2222` to `1111`; the app creates a second account. Test: Task 2 `second sub-account gets its own account`.
2. **Upgrade of a real v8 file.** After migration, the new resolver run on the migrated rows sends every old key to the same live account the v8 resolver chose. Test: Task 3 `every v8 key resolves to the same account after migration`.
3. **Notification right after a merge.** A notification with the source's old mask lands on the target. Test: Task 6 `source mask lands on target after merge`.
4. **Old feed or old server without `maskKind`.** A rule without the field loads with `maskKind = null`, and its kind comes from `accountType`. Test: Task 5 `feed rule without maskKind loads with derived kind`.
5. **Account archived between plan and write.** The writer returns `AccountUnusable` and the log stays `PENDING`. On the next run the log is rejected, not booked. Test: Task 4 `account archived after planning stays pending`.

---

### Task 1: `parseMoney` reads VND safely (N-01)

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/domain/notification/service/NotificationParser.kt` (`parseMoney`, `DECIMAL_TAIL`)
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/notification/service/NotificationParserTest.kt`

**Interfaces:**
- Produces: `fun parseMoney(raw: String): Long?`. Same signature, new rules from the spec, "Small debts / N-01".

- [ ] **Step 1: Write the failing tests** (one `@Test` each, `assertEquals(expected, parseMoney(input))`):
  `"100.500"`→100500, `"100,500"`→100500, `"1.000.000"`→1000000, `"1,000.00"`→1000, `"1.000,00"`→1000, `"100.0"`→100, `"1,000.50"`→null, `"100,50"`→null, `"100.5"`→null, `"-500,000"`→-500000, `"- 500,000"`→-500000, `"−500,000"` (U+2212)→-500000, `"-0"`→0, `"-0.00"`→0, `"abc"`→null.
  Also a parse test: a rule with `amount` text `100.5` gives no `Parsed`, so the rule is rejected and is not booked as 1005.
- [ ] **Step 2: Run** `testDebugUnitTest --tests "*NotificationParserTest"`. Expected: FAIL on `100.5`, the negatives and the non-zero decimals.
- [ ] **Step 3: Implement.** A leading `-` or `−`, optionally followed by spaces, sets the sign. After the last separator: 3 digits is a thousands group; 1–2 digits is a decimal part, dropped when all zero, otherwise return null. Overflow still returns null. Update the KDoc.
- [ ] **Step 4: Run** the same tests, then `NotificationParserCorpusTest` and `SystemParserRulesCorpusTest`. Expected: all PASS. The corpus must stay green.
- [ ] **Step 5: Commit** `fix(parser): read VND decimals and negative balances safely`.

### Task 2: Pure resolver on identifiers

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/account/model/IdentifierKind.kt`, `.../domain/account/model/AccountIdentifier.kt`
- Modify: `.../domain/notification/model/ParserRule.kt` (add `maskKind`), `.../domain/notification/model/AccountTarget.kt`, `.../domain/notification/service/AccountResolver.kt`, `.../domain/notification/service/SettlementPlanner.kt` (`planSettlement`, `decide`), `.../domain/notification/model/SettlementDecision.kt` (`Book`), `.../domain/notification/repository/NotificationSettlementRepository.kt`, `.../domain/notification/usecase/SettlePendingLogsUseCase.kt`, `.../data/notification/repository/NotificationSettlementRepositoryImpl.kt` (`getIdentifiers`, interim), test fakes (`FakeNotificationSettlementRepository`, `testRule`)
- Test: `AccountResolverTest.kt` (rewrite), `SettlementPlannerTest.kt` (new parameter)

**Interfaces:**
- Produces:
  - `enum class IdentifierKind { ACCOUNT, CARD, WALLET }`
  - `data class AccountIdentifier(val packageName: String, val kind: IdentifierKind, val mask: String, val accountId: String)`. Its `toString()` leaves out the mask.
  - `ParserRule.maskKind: IdentifierKind? = null`
  - `internal fun ParserRule?.identifierKind(): IdentifierKind`. Returns `maskKind` when set. Otherwise `WALLET` for `AccountType.WALLET`, `CARD` for `CREDIT_CARD`, `ACCOUNT` for anything else or a null rule.
  - `AccountTarget.Existing(accountId: String, attach: AccountIdentifier? = null)`. `toString` hides the mask.
  - `AccountTarget.New(packageName, mask, bankName, type, kind: IdentifierKind)`
  - `internal fun resolveAccount(packageName: String, mask: String?, rule: ParserRule?, accounts: List<Account>, identifiers: List<AccountIdentifier>): AccountResolution`. `accounts` may hold deleted rows; only live ones count. An identifier whose owner is not live is free.
  - `planSettlement(pending, settled, accounts, identifiers: List<AccountIdentifier>, rulesById, now)`
  - `SettlementDecision.Book` gains `rule: ParserRule?`, the rule of the bank side, for the writer's re-resolve.
  - `NotificationSettlementRepository.getIdentifiers(): List<AccountIdentifier>`

- [ ] **Step 1: Rewrite `AccountResolverTest`.** Use identifiers in place of `accountNumberMask`, `mergedIntoId` and deleted rows. Cases, each asserting the exact `AccountResolution`:
  - `mask found on live owner` → `Resolved(Existing(id))`
  - `mask found on ignored owner` → `Ignored`
  - `mask found on archived owner` → `Rejected(FAIL_ACCOUNT_ARCHIVED)`
  - `identifier of a deleted owner is free` → `New` with `kind`
  - `N-05 attaches to the one identifier-less account of the type` → `Existing(id, attach = AccountIdentifier(pkg, ACCOUNT, "1234", id))`
  - `N-05 does not attach across types`: the only account is BANK and the rule type is CREDIT_CARD → `New(type = CREDIT_CARD, kind = CARD)`
  - `second sub-account gets its own account` (Review Focus 1)
  - `N-05 on an ignored account ignores without attaching` → `Ignored`
  - `mask-less picks the one account of the rule's type`: BANK + CREDIT_CARD in one package, BANK rule → `Existing(bankId)`
  - `mask-less with two of the type is ambiguous`
  - `mask-less with none creates`
  - `maskKind overrides accountType`
  - `kind is part of the key`: an ACCOUNT `1234` exists, and a CARD rule with `1234` → `New`
- [ ] **Step 2: Run** `--tests "*AccountResolverTest"`. Expected: FAIL to compile, or FAIL.
- [ ] **Step 3: Implement** the types and `resolveAccount` per spec "Resolver". `newTarget` passes `rule.identifierKind()`. `SettlementPlanner.decide` passes `identifiers` and puts `rule` on `Book`.
- [ ] **Step 4: Plumb.** `SettlePendingLogsUseCase` passes `repository.getIdentifiers()`. The interim `NotificationSettlementRepositoryImpl.getIdentifiers()` builds identifiers from `accountNumberMask`: a live row owns its own key, a merged row's key goes to `mergedIntoId`, and the kind comes from `Account.type` as in the spec's migration step 2. Mark it `// shortcut: reads the old mask column until Task 3 adds the table`. Fix the fakes and `SettlementPlannerTest` call sites.
- [ ] **Step 5: Run** `testDebugUnitTest`. Expected: all PASS.
- [ ] **Step 6: Commit** `refactor(notif): resolve accounts through identifiers`.

### Task 3: Table, migration 8 → 9, drop the mask column

**Files:**
- Create: `.../data/account/local/entity/AccountIdentifierEntity.kt`, `.../data/account/local/dao/AccountIdentifierDao.kt`, `.../data/account/mapper/AccountIdentifierMapper.kt`, `.../data/database/Migration8To9.kt`, `app/schemas/.../9.json` (generated)
- Modify: `FinoteDatabase.kt` (entity, DAO, `DATABASE_VERSION = 9`), `MigrationPolicy.kt` (`FINOTE_MIGRATIONS = listOf(MIGRATION_8_9)`), `DatabaseModule.kt`, `AccountEntity.kt` and `Account.kt` (remove `accountNumberMask`; index on `bankPackageName` only), `AccountMapper.kt`, `AccountDao.kt` (remove `getByBankKey`), `ParserRuleEntity.kt` (add `maskKind: String?`, unmapped until Task 5), `NotificationSettlementRepositoryImpl.kt` (`getIdentifiers` reads the table; `newAccount` inserts the identifier when the mask is non-null; the `New` branch no longer calls `getByBankKey`), test `Fixtures`, `AccountMapperTest`, `EncryptedDatabaseTest`, every test that set `accountNumberMask`
- Test: `MigrationPolicyTest.kt`, new `AccountIdentifierDaoTest.kt` (Robolectric, in-memory)

**Interfaces:**
- Consumes: `AccountIdentifier`, `IdentifierKind` (Task 2).
- Produces:
  - `@Entity(tableName = "account_identifiers", primaryKeys = ["packageName", "kind", "mask"], foreignKeys = [FK accountId → accounts.id RESTRICT], indices = [Index("accountId")]) data class AccountIdentifierEntity(packageName: String, kind: IdentifierKind, mask: String, accountId: String)`. Its `toString` leaves out the mask.
  - `AccountIdentifierDao`: `getAll(): List<AccountIdentifierEntity>`, `getByPackage(packageName: String)`, `get(packageName: String, kind: IdentifierKind, mask: String): AccountIdentifierEntity?`, `insert(e)` (ABORT on conflict), `repoint(packageName, kind, mask, accountId: String): Int`, `moveAll(from: String, to: String): Int`
  - `FinoteDatabase.accountIdentifierDao()`
  - `val MIGRATION_8_9: Migration`

- [ ] **Step 1: Write the failing migration tests** in `MigrationPolicyTest`. Each uses a v8 file built with raw SQL. Fixed tests:
  - `every v8 key resolves to the same account after migration` (Review Focus 2). Rows: a live keyed account, a merged pair, a deleted unmerged account, two live accounts on one key (one archived), a live account and a merged row on one key, two merged rows on one key with different `deletedAt`, a merge target that was deleted later, a mask-less account, a cash account, a row with `accountNumberMask = ''`. After migration, for each v8 key, run `resolveAccount` on the migrated data and assert the same account id the v8 rules give, as in the spec.
  - `migration keeps transactions and snapshots`: the v8 transaction and snapshot rows are still there.
  - `empty mask gets no identifier`
  - `accounts has no accountNumberMask column`: check `PRAGMA table_info`.
  - `migrated schema validates`: `runMigrationsAndValidate(9, MIGRATION_8_9)`.
- [ ] **Step 2: Write `AccountIdentifierDaoTest`.**
  - `primary key rejects a second owner`
  - `repoint moves one key`
  - `moveAll moves every key of an account`
  - `toString hides the mask`
- [ ] **Step 3: Run** both test classes. Expected: FAIL.
- [ ] **Step 4: Implement** entity, DAO, mapper and `MIGRATION_8_9`, following spec "Migration 8 → 9" steps 1–4. Use three `INSERT OR IGNORE … SELECT` passes (live, then merged, then plain deleted), ordered as the spec says. Then `DROP INDEX index_accounts_bankPackageName_accountNumberMask`, `ALTER TABLE accounts DROP COLUMN accountNumberMask`, `CREATE INDEX index_accounts_bankPackageName`, and `ALTER TABLE parser_rules ADD COLUMN maskKind TEXT`. Never copy the table. If Robolectric's SQLite rejects `DROP COLUMN`, switch the test to native SQLite mode; do not change the schema.
- [ ] **Step 5: Replace the interim `getIdentifiers`** with `accountIdentifierDao().getAll()`, mapped. Make `newAccount` insert the identifier. Fix every compile break the column removal causes.
- [ ] **Step 6: Run** `testDebugUnitTest`. Expected: all PASS, and `9.json` is generated.
- [ ] **Step 7: Commit** `feat(db): store account identifiers in their own table`.

### Task 4: Writer re-resolves in the write transaction

**Files:**
- Modify: `NotificationSettlementRepositoryImpl.kt` (`book`, `resolveAccount`, new private `attach`)
- Test: `NotificationSettlementRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `resolveAccount` and `Book.rule` (Task 2); `AccountIdentifierDao` (Task 3).
- Produces: none new. `book` keeps its signature and its `SettlementError` meanings.

- [ ] **Step 1: Write the failing tests.** Each one plans a batch of logs against one snapshot, then books every decision in order, as `SettlePendingLogsUseCase` does:
  - `two masks planned onto one N-05 account attach only the first`: the second log gets a new account.
  - `two mask-less logs planned New make one account`
  - `free identifier is re-pointed through New`
  - `free identifier is re-pointed through N-05`: no primary-key crash.
  - `account archived after planning stays pending` (Review Focus 5): `book` returns `AccountUnusable`, and the log is still `PENDING`.
  - `account ignored after planning stays pending`
- [ ] **Step 2: Run** `--tests "*NotificationSettlementRepositoryImplTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.** Inside `storageWrite`, call `resolveAccount(bank.packageName, parsed.accountMask, decision.rule, accounts.getWithBankKey().map { it.toDomain() }, identifiers.getByPackage(bank.packageName).map { it.toDomain() })`. If the answer is `Ignored` or `Rejected`, return `AccountUnusable`. For `New`, create the account. Then `attach(identifier)`: insert when the key is absent; re-point when its owner is not live; do nothing when the owner is live. The planner's `decision.account` is no longer read here.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: all PASS, including `TransferPairRepositoryImplTest` and `SettlementTransactionTest`.
- [ ] **Step 5: Commit** `fix(notif): resolve the account again inside the booking write`.

### Task 5: `maskKind` through mapper, OTA codec and seeder (N-08)

**Files:**
- Modify: `ParserRuleMapper.kt`, `RuleFeedCodec.kt` (`RuleDto.maskKind: String? = null`, `decodeRuleFeed`, `toRule`), `ParserRuleSeeder.kt` (`UPSERT_SQL` and its binds), `SystemParserRule.kt` (`Bank.rule` passes `maskKind = null`)
- Test: `ParserRuleMapperTest.kt`, `RuleFeedCodecTest.kt`, `ParserRuleSeederTest.kt`, `MigrationPolicyTest.kt`

**Interfaces:**
- Consumes: `ParserRule.maskKind` (Task 2); `ParserRuleEntity.maskKind` (Task 3).
- Produces: `decodeRuleFeed` returns the valid rules and skips any rule whose `maskKind` is not an exact `IdentifierKind` name. Every other bad rule still refuses the whole feed, as before.

- [ ] **Step 1: Write the failing tests.**
  - Mapper: `maskKind round trips`; `unknown stored maskKind makes the rule unusable` (`toDomain()` returns null).
  - Codec: `feed rule without maskKind loads with derived kind` (Review Focus 4). Skipped rules: `unknown maskKind skips only that rule` with `"FUNDING_SOURCE"`, `"card"` and `""`. Also `bad regex still refuses the feed`.
  - Seeder: `seeder writes maskKind when a rule version rises`.
  - N-08: `after migration and seeding every system rule has its accountType and maskKind`. Open a v8 file, migrate, run `ParserRuleSeeder`, then compare each stored row to `SystemParserRules` by id.
- [ ] **Step 2: Run** the four test classes. Expected: FAIL.
- [ ] **Step 3: Implement.** In `decodeRuleFeed`, use `mapNotNull`: `toRule()` returns null only for an unknown `maskKind` and keeps throwing for everything else. Duplicate-id detection must still see the skipped rule's id.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: all PASS.
- [ ] **Step 5: Commit** `feat(ota): carry maskKind through rules, feed and seeder`.

### Task 6: Merge moves identifiers, matches types, keeps rows pairable (N-21)

**Files:**
- Modify: `AccountMergeDao.kt` (`moveSource`, `moveDestination`: drop `updatedAt` and the `now` parameter), `AccountRepositoryImpl.merge` (call `accountIdentifierDao().moveAll(sourceId, targetId)` in the same `storageWrite`), `MergeRejection.kt` (add `DIFFERENT_TYPE`), `AccountRules.checkMerge` (after `DIFFERENT_BANK`: `source.type != target.type` → `DIFFERENT_TYPE`), `AccountsSettingsViewModel.openSheet` (targets also need `it.type == account.type`)
- Test: `AccountMergeTest.kt`, `AccountRulesTest.kt`, `MergeAccountsUseCaseTest.kt`, `AccountsSettingsViewModelTest.kt`, `TransferPairRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `AccountIdentifierDao.moveAll` (Task 3).
- Produces: `MergeRejection.DIFFERENT_TYPE`. The rejection order is now same, gone, bank, type, transfer.

- [ ] **Step 1: Write the failing tests.**
  - `merge moves identifiers to target`
  - `source mask lands on target after merge` (Review Focus 3). Merge, then book a log with the source's mask; it lands on the target.
  - `merge keeps moved rows' updatedAt`
  - `moved notification rows still auto-pair after merge` (N-21, in `TransferPairRepositoryImplTest`)
  - `different types are DIFFERENT_TYPE`. Update the order test to same, gone, bank, type, transfer.
  - `merge targets exclude other types` (ViewModel)
- [ ] **Step 2: Run** these classes. Expected: FAIL.
- [ ] **Step 3: Implement** as listed under Files. `DIFFERENT_TYPE` maps to `ActionFailed` in `blockOrFail`, which is the existing default.
- [ ] **Step 4: Run** `testDebugUnitTest`. Expected: all PASS.
- [ ] **Step 5: Commit** `fix(db): move identifiers on merge and keep merged rows pairable`.

### Task 7: Merge dialog warns when a start balance is dropped (N-02)

**Files:**
- Modify: `AccountRepository.kt` and `AccountRepositoryImpl.kt` (new read), `AccountsSettingsContract.kt` (`AccountsDialog.Merge.droppedStart: Long?`), `AccountsSettingsViewModel.askMerge`, `component/MergeDialog.kt`, `values/strings.xml`, `values-en/strings.xml`, `values-ja/strings.xml`, `values-zh-rCN/strings.xml`
- Create: `.../domain/account/usecase/GetMergeDroppedStartUseCase.kt`
- Test: `AccountRepositoryImplTest.kt`, `AccountsSettingsViewModelTest.kt`, `AccountsSettingsScreenshots.kt` (one new preview)

**Interfaces:**
- Produces:
  - `AccountRepository.getMergeDroppedStart(sourceId: String, targetId: String): Long?`. Returns the source's `initialBalance` when both accounts are `isInitialized` and `BalanceCalculator.anchoredInitialBalance` gives null over the live rows of both accounts as they will be after the move (rows re-homed to the target). Returns null otherwise.
  - `GetMergeDroppedStartUseCase(sourceId, targetId): Long?`
  - `AccountsDialog.Merge(source, target, count, droppedStart: Long? = null)`
  - String `accounts_merge_drops_start`. `vi`: `Số dư đầu %1$s của %2$s sẽ bị bỏ. Có thể chỉnh lại bằng Điều chỉnh.` (`%1$s` is the amount, formatted with the existing money formatter in `ui/common/format`; `%2$s` is the source name). Add `en`, `ja`, `zh-rCN` translations in the same shape.

- [ ] **Step 1: Write the failing tests.**
  - Repository: `dropped start when both initialized without anchor` → the source's `initialBalance`. Null for `null when the merged rows carry a bank balance`, `null when only the source is initialized`, and `null when only the target is initialized`.
  - ViewModel: `merge dialog carries dropped start`.
- [ ] **Step 2: Run** the tests. Expected: FAIL.
- [ ] **Step 3: Implement.** `askMerge` loads the count and the dropped start in the same `launchCatching`. `MergeDialog` shows the line only when `droppedStart != null`. The amount is formatted by the existing formatter in `ui/common/format`, never by hand.
- [ ] **Step 4: Run** `testDebugUnitTest`, then the screenshot record and verify task the repo uses for `screenshotTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): warn when a merge drops a start balance`.

### Task 8: Ledger and gate

**Files:**
- Modify: `docs/roadmap.md`. Mark step 10 ✅. Close D13 into "Đã chốt" with a pointer to the spec. Delete the debt rows N-02, N-05, N-08 and N-21. Change N-01 to the remainder: system rules cannot read a negative balance until a real overdraft sample exists; fix at the next rule change. Keep L1, L2 and N-06 open.

- [ ] **Step 1: Edit the roadmap** as listed.
- [ ] **Step 2: Run** `qualityGate` through `gradle-run`. Expected: `exit_status` 0. Read the summary.
- [ ] **Step 3: Commit** `docs: close account identity debts`.
- [ ] **Step 4: Hand off.** `reviewer` reviews the branch against the spec. The user runs `EncryptedDatabaseTest` on a device for the SQLCipher upgrade, which no JVM test can open.
