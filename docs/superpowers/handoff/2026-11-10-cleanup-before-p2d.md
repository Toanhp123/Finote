# Handoff: cleanup before P2d (2026-11-10)

Read this first in the next session. Reply to the user in Vietnamese (CLAUDE.md).

## Where things stand

- `dev` HEAD `0e68c87` = squash of `feature/account-merge-ignore` (merge accounts, ignore accounts, Settings → Accounts). Clean tree except this file.
- Old local branches waiting for the user to delete (`git branch -D` is blocked for agents): `feature/account-merge-ignore`, `feature/account-dedup-balance`.
- Not yet tried on a real device: P2c settle flow, Settings → Accounts (sheet and dialogs only seen in screenshots).
- Roadmap (`docs/roadmap.md`, P2c section) already records: P2d debt (anchor `postBalance` only from rows with `accountId` = the account, in merge and cold start), small UI debts.
- The user asked for a whole-project cleanup before starting P2d (`feature/transfer-pairing`). A full audit ran on 2026-11-10; results below. Nothing is fixed yet.

## Agreed next step

Branch 1 `fix/balance-adjust`, starting with a short design in chat (bounded path), then implement. Also add a "Dọn dẹp trước P2d" section to `docs/roadmap.md` in that branch, listing the 8 branches below.

## Cleanup branches (user-approved order)

| # | Branch | Findings | Process |
|---|---|---|---|
| 1 | `fix/balance-adjust` | A1, A3 | short design in chat |
| 2 | `fix/settle-stuck-logs` | A2, A7 | short design in chat |
| 3 | `chore/domain-account-rules` | A8, A9, A11, A20 — must finish before P2d | brainstorming → spec → plan → subagents |
| 4 | `chore/ui-overlay-api` | A10, A6, A16 | brainstorming → spec → plan |
| 5 | `chore/ui-components` | A12, A13, A14, A15 | brainstorming → spec → plan |
| 6 | `chore/test-hygiene` | A18, A19 + small items | brainstorming → spec → plan |
| 7 | `chore/release-readiness` | A4, A17 + small items | short design; independent, may run in a parallel session |
| 8 | `fix/notification-text-lines` | A5 | wait for a real-device sample from the user |

## Audit findings (2026-11-10)

Verified against code by the controller: A1, A2, A3, A4, A6, A7, A17. Others come from audit agents with file:line, not re-read line by line.

**Must fix**
- **A1 Adjust race, hidden.** `ui/transactions/TransactionListViewModel.kt:170-183` sends absolute `currentBalance + drift` from UI state; `data/transaction/TransactionRepositoryImpl.kt:62` computes delta vs DB balance. A settle landing between makes the balance wrong; `domain/account/BalanceDriftDetector.kt:28` then hides it. Fix: reconcile by delta inside the write transaction; test with a booking in between.
- **A2 Stuck PENDING log, infinite retry.** `domain/notification/usecase/SettlePendingLogsUseCase.kt:50` swallows per-decision errors; `work/SettleWorker.kt:23` retries while PENDING. A 19-digit amount (`NotificationParser.kt:77-85` accepts it; `SettlementPlanner.kt:111` checks only `> 0`) overflows `addExact` forever; `since` window (`SettlePendingLogsUseCase.kt:32`) drags back. Fix: amount cap (e.g. ≤ 10^15 → `invalid_amount`), FAILED after N throwing runs, test.
- **A3 Adjustment stored as MANUAL.** `TransactionRepositoryImpl.kt:64-77` builds ADJUSTMENT with default `source = MANUAL` (`domain/transaction/model/Transaction.kt:19`); `TransactionDao.getLastManualAccountId` (`source = 'MANUAL'`) then makes the adjusted bank account the cash-entry default. Fix: add `ADJUSTMENT` to `TransactionSource` (spec L110), set it in `reconcile`.

**Should fix**
- **A4 Release/R8 never built.** `qualityGate` runs debug only; `app/build.gradle.kts:64` enables optimization; keep rules file is the template. Add `assembleRelease` to the gate, keep rules for Room / serialization / SQLCipher JNI as needed.
- **A5 Notification updated with extra lines booked twice.** `domain/notification/NotificationText.kt:51-60`, `NotificationParser.kt:24`, repost check needs identical text (`SettlementPlanner.kt:85`). Needs a device sample.
- **A6 Cash entry sheet closes on a guessed 250 ms delay with `remember` flags.** `ui/cashentry/CashEntryScreen.kt:67,81-100`; rotation inside the window can reopen the sheet after save. Move closing into state; `FinoteBottomSheet` `onDismissed`.
- **A7 Settle starves in bursts.** `work/WorkSettleScheduler.kt:30` uses `ExistingWorkPolicy.REPLACE` per capture (35 s delay). Use `KEEP` after capture.
- **A8 Anchor rule written 3 times.** `NotificationSettlementRepositoryImpl.kt:164-177` (coldStart), `AccountRepositoryImpl.kt:135-159` (withMergedStart), `BalanceDriftDetector.kt:20-31`. One domain function `BalanceCalculator.anchoredInitialBalance(...)`; fixes the P2d debt once.
- **A9 "Usable account" / "touches account" predicates scattered and disagreeing.** `TransactionRepositoryImpl.kt:28-31` (allows ignored), `NotificationSettlementRepositoryImpl.kt:126`, `AccountRepositoryImpl.kt:161`, `ObserveBalanceDriftUseCase.kt:25`, `CashEntryDefaults.kt:15`, `UpdateTransactionUseCase.kt:20`, `TransactionListViewModel.kt:76`; touches: `BalanceCalculator.kt:66`, `BalanceDriftDetector.kt:34`, 3 SQL copies. Add `Account.isUsable`, `Transaction.touches(id)`.
- **A10 Dialog/sheet visibility hard-coded.** `ui/settings/accounts/AccountsSettingsDialogs.kt:33,78` pass `isVisible = true`, screen uses `if` (`AccountsSettingsScreen.kt:87-88`); sheet content vanishes on close (`:126`); `ReimbursementSheet.kt:34-37` patches with `lastShown`. Each feature hand-builds Cancel/confirm buttons; ignore toggle in `remember` with a guard duplicated in the VM. Fix: `FinoteDialog` API `confirmText/onConfirm/isDestructive/dismissText`, overlays keep last shown content, dialog state sealed in VM (`AccountsDialog?`), `TrashToggled` intent. Only 2 callers of `FinoteDialog` today.
- **A11 ViewModels do domain work.** `CashEntryViewModel.kt:39-45,143-178` builds `Transaction` + noon rule, `init` has no `.catch`; `CashEntryViewModel` and `TransactionListViewModel.kt:3,177-195` import `android.database.SQLException`. Add `AddManualTransactionUseCase`, map storage errors to a domain exception. Also: VMs read repositories directly though `architecture.md` says VMs call use cases — decide: amend the rule for `observe*` reads or add use cases.
- **A12 Copied UI pieces.** Switch row (`AccountsSettingsDialogs.kt:106-111`, `NotificationCaptureSettingsScreen.kt:141-151`), section header + card (`SettingsScreen.kt:67-96`, `NotificationCaptureSettingsScreen.kt:235-249`), listener-access wiring (`SettingsScreen.kt:42-45`, `NotificationCaptureSettingsScreen.kt:58-67`, `SetupScreen.kt:44-52`). Add `FinoteSwitchRow`, `FinoteSection`, `ReportListenerAccess` / `openListenerSettings`.
- **A13 Raw numbers outside tokens.** `CashEntryScreen.kt:67-72,134` (motion ms, `Dp(30f)` dodges the Konsist `.dp` rule), `AccountsSettingsScreen.kt:35,101` (`IGNORED_ALPHA = 0.6f` vs disabled 0.38).
- **A14 Date format hard-coded `"EEE, dd/MM"` for all locales.** `ui/transactions/component/TransactionRowText.kt:27-28`; two other ad-hoc date formatters. One `formatDate` in `ui/common/format/`; extend Konsist rule 3 to `DateTimeFormatter`.
- **A15 Accessibility.** `CashEntryPickers.kt:235-250` selectors read value only; `:133` literal `"✓"`; `SetupScreen.kt:138-155` "Granted" row needs `liveRegion`.
- **A16 Screenshot support copied.** `PHONE` 8×, scrim 4×, `ScreenPreviews` 6×, `DialogFrame`/`SheetFrame` hand-copy real UI and call internal `DialogCard`. One `ScreenshotSupport.kt`, render real bodies.
- **A17 Setup flag backed up, DB not.** `res/xml/data_extraction_rules.xml`, `backup_rules.xml` exclude DB and keys but not `app_setup.xml`. Exclude it (3 places) or `allowBackup=false`.
- **A18 Fakes drifted + builders copied.** `FakeTransactionRepository.kt:48` (no deleted filter, no sort), `FakeAccountRepository.kt:26` (add/update/softDelete/setArchived contracts); `rule(` builder in ~10 test files, `log(`/`raw(` in several. Shared `testRule()` / `testLog()`.
- **A19 Konsist gaps.** `LayerDependencyKonsistTest.kt:8` bans only android/androidx/dagger/room in domain (use an allowlist); no check for "automation/work call use cases" or "`di` bindings only"; money and log word lists too narrow.
- **A20 `AccountRepository.update()` overwrites newer values (latent).** `data/account/AccountRepositoryImpl.kt:38-54` + `UpdateAccountUseCase` forcing `isInitialized = true`; bites when an account edit screen ships. Keep bank key from the stored row; change `initialBalance` only explicitly.

**Small (~10, not numbered):** unused UI (`FinoteDatePicker`, `DatePickerContent`, `BalanceHeroCard`); `ExampleUnitTest`, `ExampleInstrumentedTest`; `RawNotification`/`NotificationLog` data-class `toString()` prints content; `sqlite-framework` only used by tests (→ `testImplementation`), unused `espresso-core` in androidTest; OTA single pinned key, no rotation; tests using wall clock (`SettleWorkerTest`, `RuleUpdateWorkerTest`); unread Boolean returns of `fail/ignore/markRepost`; wrong KDoc `NotificationLogEntity.kt:17-20`; destructive migration and key-loss wipe must be blocked before v1.0.

Not checked by the audit: no build or test run; regex bodies in `SystemParserRule.kt`, `category` packages, `KeystoreKeyCipher`, theme/color files; contrast not measured.

## Working rules learned this session

- Commits: the user approves **each commit** ("stop and ask per task"). Implementer subagents (`coder`) never commit; the main session commits.
- Commit scopes must be in the git_guard list: `parser, dedup, pairing, balance, db, notif, ui, ota, build` or no scope (`docs: ...`). `account` is rejected.
- The pre-commit hook runs spotless + detekt; `qualityGate` also runs `detektDebugUnitTest` (detekt on test sources) — tell implementers to run `spotlessKotlinCheck detekt` and, at the end, `qualityGate`.
- The `coder` agent's rules forbid writing report files; ask for the report in its reply.
- Gradle only through the `gradle-run` skill (`'.\gradlew.bat'` single-quoted from Bash). If a Kotlin daemon locks `classes.jar`, ask the user to kill it (agents are not allowed to stop processes).
- Design preferences from the user: split DAOs by business (not by function count; `AccountMergeDao`, tag links in `TagDao`); hide ignored accounts like archived ones; no hard-coded UI that bypasses component APIs.
- `TransactionDao` is at the detekt `TooManyFunctions` limit (11); `@Suppress` is not allowed for it.
