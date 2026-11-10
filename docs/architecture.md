# Architecture

Binding for every human and agent in this repo. Machines enforce most rules. A rule that only a human can check is listed at the end.

## Layers

One Gradle module, `:app`. Layers are packages. Inside a layer, packages split by feature. Root package: `io.github.toanhp123.finote`.

```text
domain/<feature>/    model, repository interface, use case, pure logic
domain/common/       shared kinds with no logic (qualifiers such as DefaultDispatcher)
data/<feature>/      Room entity, DAO, mapper, repository impl
ui/<feature>/        Screen, Contract, ViewModel
ui/common/           theme, shared components, BaseViewModel (tokens and components: docs/design-system.md)
automation/          NotificationListenerService, thin
work/                Worker, thin
di/                  Hilt module
```

- Starting features: `transaction`, `account`, `category`, `notification`.
- The root package holds only `MainActivity` and `FinoteApplication`. No layer imports it.
- Every production package is the root or sits under `domain`, `data`, `ui`, `automation`, `work`, or `di`. A stray or misspelled package fails.
- `TileService` only opens the cash entry screen, so it lives in `ui/<feature>`.
- Parser, dedup, pairing, and balance math live in `domain`. They test on the JVM without Robolectric.
- `automation/` receives a notification and calls a use case. Nothing more.

| Layer | May depend on | Must not depend on |
|---|---|---|
| `domain` | `kotlin`, `kotlinx.coroutines`, `java.time`, `java.util`, `java.text`, `java.security`, `java.math`, `javax.inject` (an import allowlist) | everything else, including `android.*`, `androidx.*`, Room, Hilt, `data`, `ui`, `automation`, `work`, `di` |
| `data` | `domain` | `ui`, `automation`, `work` |
| `ui` | `domain` | `data`, `automation`, `work` |
| `automation` | `domain` | `ui`, `data`, `work` |
| `work` | `domain` | `ui`, `data`, `automation` |
| `di` | every layer | logic |

`ui/<feature>` may use `ui/common`. `ui/common` must not use `ui/<feature>`.

`automation` and `work` import no `*Repository`: they call a use case. `di` holds only `@Module` types (each function `@Provides` or `@Binds`) and `@Qualifier` annotations; other types go in their layer.

### Folder rules

1. Split a package into subfolders only when it mixes two or more roles.
2. A subfolder should hold three or more files. Exceptions: infrastructure groups (`glass/`, `preview/`) and the `component/` folder of a feature, so every feature has the same shape.
3. Do not split when the file suffix already names the role (`Dao`, `Entity`, `Mapper`, `RepositoryImpl`). `data/<feature>/` stays flat.
4. At most one level of subfolder under `component/`, `theme/`, or a feature.

### Feature template

```text
ui/<feature>/
├── component/   # sub-composables used by this feature only
└── (root)       # *Contract, *ViewModel, *Screen, *Activity, *TileService, pure screen logic (*Defaults, AmountExpression)
```

A new feature follows this shape. Shared components are grouped by role in `ui/common/component/<group>/` (docs/design-system.md). Theme colors are in `ui/common/theme/color/`.

UI flow is MVI: Screen sends `Intent`, ViewModel calls use cases (reads included), ViewModel emits `State` and `Effect`, Screen draws.

## Naming and roles

| Role | Name | Where | May do |
|---|---|---|---|
| Model | `Transaction` | `domain/<f>/model` | Hold data. No I/O |
| Use case | `AddTransactionUseCase` | `domain/<f>/usecase` | One job. One public `operator fun invoke` |
| Pure logic | `NotificationParser` | `domain/<f>` | Pure business rules. No I/O |
| Screen logic | `AmountExpression` | `ui/<screen>` | Pure input or display logic that serves one screen. Not in `domain` |
| Repository | `TransactionRepository` | `domain/<f>/repository` | Interface only |
| Repository impl | `TransactionRepositoryImpl` | `data/<f>` | Drive DAOs, map |
| Entity, DAO | `TransactionEntity`, `TransactionDao` | `data/<f>` | Schema, queries |
| Mapper | `TransactionMapper.kt` | `data/<f>` | Only `toDomain()` and `toEntity()` extensions |
| Contract | `TransactionListContract.kt` | `ui/<f>` | Declare `State`, `Intent`, `Effect` |
| ViewModel | `TransactionListViewModel` | `ui/<f>` | Take intents, call use cases, emit state. Never a repository |
| Screen | `TransactionListScreen` | `ui/<f>` | Draw state. No use case calls |
| Worker | `TrashPurgeWorker` | `work` | Call a use case |
| DI | `DatabaseModule` | `di` | Bindings only |

| Kind | Rule | Example |
|---|---|---|
| Function returning `Flow` | `observeX` | `observeAccounts()` |
| One-shot `suspend` read | `getX` | `getAccount(id)` |
| Boolean | `is`, `has`, `should` | `isArchived` |
| Constant | `UPPER_SNAKE_CASE` | `DEDUP_WINDOW_MS` |

| Limit | Value | Tool |
|---|---|---|
| Lines per file | 400 | Konsist |
| Lines per function | 60 | detekt |
| Lines per `@Composable` | 80, `*Screen` 120 | Konsist |
| Parameters per function | 6, `@Composable` exempt | detekt |
| Cyclomatic complexity | 15, simple `when` entries ignored | detekt |
| Public types per file | 1 in `*UseCase`, `*RepositoryImpl`, `*ViewModel`, `*Screen`, `*Dao`, `*Entity`. Other files may co-locate closely related types | Konsist |

detekt cannot hold two limits in one rule. `LongMethod` and `LongParameterList` set `ignoreAnnotated: ['Composable']`. Konsist checks the 80-line limit, 120 for `*Screen` composables.

Private helpers of a class stay inside the class; detekt `TooManyFunctions` ignores private functions so they need not leave it.

When a file co-locates types, the declaration the file is named after comes first and the serving types (style enum, item class, state holder) go below it. detekt `MatchingDeclarationName` (`mustBeFirst`) relies on this order: a file that starts with its only class must be named after that class.

Any class or interface named `*Repository` is an interface in `domain/<feature>/repository`. That package holds interfaces only: repositories and the other ports to the outside world (a feed, a scheduler). `*Mapper.kt` lives in `data`, `*Contract.kt` and `*Screen.kt` in `ui`.

Project rules, checked over all production code:
- No `android.util.Log`, no `println`, no `printStackTrace`, no `::println`, no `System.out`, no `System.err`, no `java.util.logging`.
- No `Double` or `Float` in a property or parameter whose name has a money word: `amount`, `balance`, `limit`, `fee`, `price`, `cost`, `total`, `debt`, `income`, `expense`, `spent`, `budget`, `difference`, `drift`, `delta`. Names are split into words before matching (`totalCost`, `MAX_FEE`, `VNDAmount`), so `feedback` is fine. This covers type arguments (`List<Double>`) and an undeclared type whose initializer is a floating literal or ends in `.toDouble()` or `.toFloat()`. No `typealias` to `Double` or `Float`.
- `@Suppress` only for `LongMethod`, `CyclomaticComplexMethod`, `LongParameterList`, on one declaration, with `// why: <reason>` on the same line. No `@file:Suppress`, no `SuppressLint`, no `SuppressWarnings`, no `konsist.*`. Fix the cause first. If a rule is wrong for a valid case, change the rule in its own commit.

## Quality gate

```bash
./gradlew.bat qualityGate      # the full gate
./gradlew.bat spotlessApply    # auto-fix format
git config core.hooksPath .githooks   # once per clone
```

`qualityGate` depends on `spotlessCheck`, `detekt`, `detektMain`, `detektTest`, `lintDebug`, `testDebugUnitTest`, `testDebugScreenshotTestDefaultTestSuite`, `assembleDebug`.

| Layer | Tool | Enforces |
|---|---|---|
| Compile | Kotlin `allWarningsAsErrors` | Warnings fail the build |
| Format | Spotless, ktlint, compose-rules | Style, Compose mistakes |
| Analysis | detekt, any finding fails, no baseline | Complexity, length, coroutines, bugs |
| Android | Lint, `warningsAsErrors` | Platform and resource errors |
| Architecture | Konsist, inside `testDebugUnitTest` | Layers, naming, project rules |
| Behavior | Unit tests | Logic |

Facts about the real setup:
- `detektMain` has type resolution but skips test sources. `detekt` covers `src/main` and `src/test` without type resolution, and not `src/androidTest`. `detektTest` covers `src/test` and `src/androidTest` with type resolution (`detektDebugUnitTest`, `detektReleaseUnitTest`, `detektDebugAndroidTest`). The gate runs all three.
- Konsist reads source text, not classes. `app/build.gradle.kts` adds `src/main` as an input of every `Test` task, so a source-only change re-runs `testDebugUnitTest`.
- `LongParameterList` fires only in `detektMain`. The working-branch hook runs `detekt`, so it misses this rule. The `dev` gate catches it.
- Kotlin sets `extraWarnings = true` on top of `allWarningsAsErrors`. Kotlin 2.4 reports unused variables only then.
- Lint disables `GradleDependency`, `NewerVersionAvailable`, and `AndroidGradlePluginVersion`. A new library release must not turn the gate red.
- compose-rules use the ktlint build, not the detekt build.
- Layer rules check imports by package. They also fail a forbidden project package written fully qualified in code (`io.github.toanhp123.finote.data.Foo()` in `domain`). Lines that start with `package ` or `import ` are skipped by prefix; comments and strings are not. They do not use `assertArchitecture`, which fails on an empty layer and ignores imports outside declared layers.
- The root rule (`nothing imports the root package`) covers imports and fully qualified names. `R` and `BuildConfig` are allowed. That allowance has a unit test (`RootClassNameTest`). `buildConfig` stays off.
- The Gradle daemon is pinned to Java 21, vendor Eclipse Temurin (`ADOPTIUM`), in `gradle/gradle-daemon-jvm.properties`. A Java 25 JRE without `jlink` breaks `compileSdk 37`. Change it only with `./gradlew.bat updateDaemonJvm --jvm-version=21 --jvm-vendor=adoptium`.

## Hooks and CI

| When | Runs | How |
|---|---|---|
| Commit on a work branch | `spotlessCheck`, `detekt` | `.githooks/pre-commit` |
| Commit on `dev` or `main` | `qualityGate` | same hook, by branch name |
| Merge commit | same as above | `.githooks/pre-merge-commit` |
| Pull request into `dev`, `main` | `qualityGate` | `.github/workflows/quality-gate.yml` |

The workflow installs Temurin 21, then the Android SDK with `android-actions/setup-android` (`platform-tools`, `platforms;android-37.0`; licences accepted), then runs Gradle. `platforms;android-37.0` is the folder name AGP created locally for `compileSdk 37`. Bump `compileSdk` and this package together.

- Git skips `pre-commit` on a merge commit. That is why `pre-merge-commit` exists.
- `git_guard.py` blocks `--no-verify`, `-c core.hooksPath=`, and `git config` changes to `core.hooksPath` other than `.githooks`. It binds Claude Code only.
- The hooks do not cover everything:
  - A user can disable them in their own terminal.
  - A fast-forward merge creates no commit, so no hook runs.
  - `git cherry-pick` and `git revert` skip `pre-commit`.
  - The hook checks the working tree, not the index.
- CI is the backstop. The GitHub Actions workflow runs `qualityGate` on every pull request into `dev` or `main`. It only blocks a merge once branch protection requires the `quality-gate` check.

## Rules for new Konsist rules

Rules live in `app/src/test/java/io/github/toanhp123/finote/architecture/`.

- Konsist `assertTrue` passes on an empty set. A wrong package pattern passes silently.
- `app/build/generated` is outside `scopeFromProduction`. A rule never sees KSP output.
- Every new rule needs one red proof. Write a deliberate violation, run the rule, see it fail, record the output, then remove the violation. Never commit the violation.
- A rule with `strict = false` passes while its feature does not exist. The red proof is what shows the rule works.
- After Hilt and Room land, confirm the `@Suppress` rule does not flag code under `build/generated/ksp/`.

## Rules only `reviewer` can check

Machines cannot enforce these. `reviewer` checks them on every branch.

- `Intent` names describe a user action: `AmountChanged`.
- `Effect` names describe a one-time command: `NavigateBack`.
- A one-shot `suspend` read is named `getX`.
- Test fixtures hold no real bank notification, balance, or account number.
- The hero gradient (`brushes.hero`) is used only by `BalanceHeroCard`, at most once per screen.
- Glass (`finoteGlass`) is only for floating chrome: nav bar, snackbar, and the sheet surface. It blurs only in the scaffold; a modal sheet always takes the opaque `solidFill`. Never for content cards.
- Color is never the only signal. An amount has a sign, a transaction has a type icon, an error has text.
