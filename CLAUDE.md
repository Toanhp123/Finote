# Finote — Android PFM app (Kotlin, Compose)

Local-first personal finance app. It captures transactions automatically from bank and e-wallet notifications (NotificationListenerService).

Reply to the user in Vietnamese. Write code, identifiers, comments, and commit messages in English.

## Reply style (caveman mode, default on)
- Short sentences. Small words. No filler, no praise. Answer first.
- Max three bullets, each about five words.
- Code, identifiers, file names, error names, and tool calls stay normal.
- Off only when the user says "normal mode" or "stop caveman".

## Source of truth
- Spec (Vietnamese, ~420 lines): `docs/Tài liệu Đặc tả Dự án_ Ứng dụng Quản lý Tài chính Cá nhân Android (MVP).txt`
- Never read the whole spec. Use `offset` and `limit` on these lines: `2.2` notification engine L32–51, `3.1–3.10` Room schema L61–228, `4` MoSCoW scope L229–404, `5–9` acceptance criteria L405–422.

## Current state
- `app/src/main/java/` has no Kotlin source yet. Only `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt` exist.
- Git repository with no commits yet. Do not assume commit history exists.
- Package: `io.github.toanhp123.finote`. `minSdk` 26 (per spec). `targetSdk` 37 (intentionally above the spec's 35).
- Kotlin, Compose, Room, Hilt, and SQLCipher are not in `gradle/libs.versions.toml` yet. Add a dependency only when a task needs it, through the version catalog. Never hardcode versions in `build.gradle.kts`.

## Model routing (subagents in `.claude/agents/`)
- Find code: call `codegraph_explore` yourself. It is one call. Do not delegate a lookup.
- Broad plain-text sweep across many files: `finder` (haiku, effort low, read-only).
- Write code for a clear task: `coder` (sonnet, effort high).
- Review a finished branch against its task and find gaps: `reviewer` (opus, effort medium, read-only). Run it once per branch, before the squash into `dev`. Not on each commit.
- Hard bug, design trade-off: `architect` (opus, effort medium). Use only when the task is truly hard.
- Workflow: `coder` writes code and its test → `reviewer` checks the branch → squash into `dev`. Stop there unless review finds something hard.
- Escalate to `architect` only when `coder` is stuck or the cost of a wrong answer is high.
- `reviewer` never edits. Send its findings back to `coder` to fix, then review again.
- The main session model is the user's choice (`/model`). Do not switch it yourself.

## Commands
- Build: `./gradlew.bat assembleDebug`
- Unit tests: `./gradlew.bat testDebugUnitTest`
- Instrumented tests (`connectedDebugAndroidTest`) need a device or emulator. Run them only when the user asks.

## Verification
- IMPORTANT: after any logic change (parser, dedup, pairing, balance), run `./gradlew.bat testDebugUnitTest`.
- Do not report work as done without a real build or test run and its output.

## Git
- IMPORTANT: follow `docs/git-workflow.md` for every branch, commit, merge, and tag.
- Commit only when the user asks. Each approval covers one action.
- Never add `Co-Authored-By` or any AI attribution to a commit.
- `.claude/hooks/git_guard.py` blocks violations. Never work around a block. Tell the user.

## Architecture
- Clean Architecture with MVI: one-way flow of state, intent, and effect.
- Single Activity. UI is Compose with Material 3.

## Money and data rules
- IMPORTANT: store money as `Long` in VND. Never use `Double` for amounts.
- Soft delete through `deletedAt`. Never hard-delete rows, except when WorkManager purges the trash.
- Any change to a balance or to `AccountBalanceSnapshot` must run inside one `@Transaction`.

## Notification parsing
- Bank regexes use named groups: `(?<amount>…)`, `(?<sign>…)`, `(?<mask>…)`, `(?<balance>…)`, `(?<merchant>…)`, `(?<note>…)`, `(?<time>…)`.
- A remote JSON OTA rule must never overwrite a rule with `isUserCustom = true`.

## Privacy
- IMPORTANT: never write bank notification content, balances, or account numbers to Logcat.

## Working in this repo
- Small change: make it directly, no plan. Large feature (one module of spec `2.2`): write a short plan first.
