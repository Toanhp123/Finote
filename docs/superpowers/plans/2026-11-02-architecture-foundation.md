# Architecture Foundation and Quality Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dựng build Compose/Hilt trên AGP 9.5 và gate `qualityGate` để vi phạm kiến trúc, đặt tên, trách nhiệm file không vào được `dev`.

**Architecture:** Một module `:app`. Gate gồm compiler warning, Spotless + ktlint + compose-rules, detekt, Android Lint, Konsist và unit test, gom vào task `qualityGate`. Git hook và GitHub Actions gọi task đó. Plan dừng trước `feature/app-shell`.

**Tech Stack:** AGP 9.5.0, Gradle 9.6.0, Kotlin 2.4.21, KSP 2.3.13, Hilt 2.60.1, Compose BOM 2026.09.00, detekt 2.0.0-alpha.6, Spotless 8.10.4, ktlint 1.8.0, compose-rules 0.6.8, Konsist 0.18.1.

**Spec:** `docs/superpowers/specs/2026-11-02-architecture-foundation-design.md`

## Global Constraints

- Version chỉ khai báo trong `gradle/libs.versions.toml`, không hardcode trong `build.gradle.kts`.
- Không thêm plugin `org.jetbrains.kotlin.android`, không dùng kapt.
- Chỉ thêm phụ thuộc mà task cần (`CLAUDE.md`). Room, SQLCipher, WorkManager, Robolectric, Turbine để tới nhánh dùng chúng.
- Không baseline, không `@Suppress`, không `--no-verify`.
- Giới hạn: file 300 dòng, hàm 40 dòng, `@Composable` 80 dòng, tham số 6, cyclomatic 10, 1 class public mỗi file (Contract 3).
- Chạy lệnh Gradle từ PowerShell bằng `./gradlew.bat`. Trong `.githooks` và CI dùng `./gradlew`.
- `coder` không chạy `git commit`, `merge`, `tag`. Main session commit, và mỗi commit hoặc squash phải được user cho phép riêng. Không ghi `Co-Authored-By`.
- `reviewer` chạy một lần cho mỗi nhánh, trước squash vào `dev`.
- File và dữ liệu vi phạm mẫu không được commit.

## Review Focus

1. `import io.github.toanhp123.finote.R` và `BuildConfig` là code sinh ra ở gốc package, luật "không import ngược lên gốc" phải cho qua. Test: Task 4 probe P12.
2. Né luật bằng dạng viết khác: `@file:Suppress`, gọi `android.util.Log.d` không qua import, `originalAmount: Double?` nullable. Luật vẫn phải fail. Test: Task 4 probe P8, P9, P11.
3. Merge `release/*` vào `main` bằng `--no-ff` không chạy `pre-commit` của Git, phải có `pre-merge-commit`. Test: Task 5 bước 4.
4. Windows `autocrlf` làm Spotless đỏ trên file sạch hoặc hook mang CRLF không chạy được. Test: Task 3 bước 6, Task 5 bước 2.
5. Hàm `@Composable` đúng 80 dòng phải pass, 81 dòng phải fail, không lệch một dòng. Test: Task 4 probe P6.

---

### Task 0: Đưa spec và plan vào `dev`

**Files:**
- Commit: `docs/superpowers/specs/2026-11-02-architecture-foundation-design.md`, `docs/superpowers/plans/2026-11-02-architecture-foundation.md`

- [ ] **Step 1: Tạo nhánh docs**

```bash
git switch dev
git switch -c chore/architecture-docs
```

- [ ] **Step 2: Hỏi user, rồi commit**

```bash
git add docs/superpowers/specs/2026-11-02-architecture-foundation-design.md docs/superpowers/plans/2026-11-02-architecture-foundation.md
git commit -m "docs: add architecture foundation spec and plan"
```

- [ ] **Step 3: Hỏi user, rồi squash vào `dev`**

Chỉ có docs nên không chạy `reviewer`.

```bash
git switch dev
git merge --squash chore/architecture-docs
git commit -m "docs: add architecture foundation spec and plan"
```

- [ ] **Step 4: Tạo lại nhánh Gradle từ `dev` mới**

`chore/gradle-catalog` chưa có commit riêng nên `-d` xóa được.

```bash
git branch -d chore/gradle-catalog
git switch -c chore/gradle-catalog
```

Expected: `git log --oneline -1` trùng commit docs trên `dev`.

---

### Task 1: Catalog và plugin (`chore/gradle-catalog`)

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`

**Interfaces:**
- Produces: alias catalog `libs.plugins.kotlin.compose`, `libs.plugins.ksp`, `libs.plugins.hilt.android`; khối `[versions]` để Task 2–4 thêm vào.

- [ ] **Step 1: Thêm vào `gradle/libs.versions.toml`**

| Alias | Toạ độ | Version |
|---|---|---|
| plugin `kotlin-compose` | `org.jetbrains.kotlin.plugin.compose` | `kotlin = "2.4.21"` |
| plugin `ksp` | `com.google.devtools.ksp` | `ksp = "2.3.13"` |
| plugin `hilt-android` | `com.google.dagger.hilt.android` | `hilt = "2.60.1"` |
| `androidx-compose-bom` | `androidx.compose:compose-bom` | `composeBom = "2026.09.00"` |
| `androidx-compose-ui`, `-ui-tooling-preview`, `-material3` | nhóm `androidx.compose.ui`, `androidx.compose.material3` | theo BOM |
| `androidx-compose-ui-tooling` | `androidx.compose.ui:ui-tooling` | theo BOM |
| `androidx-activity-compose` | `androidx.activity:activity-compose` | `activityCompose = "1.13.0"` |
| `androidx-lifecycle-viewmodel-compose` | `androidx.lifecycle:lifecycle-viewmodel-compose` | `lifecycle = "2.11.0"` |
| `androidx-navigation-compose` | `androidx.navigation:navigation-compose` | `navigationCompose = "2.10.2"` |
| `hilt-android`, `hilt-compiler` | `com.google.dagger:hilt-android`, `hilt-android-compiler` | `hilt` |

- [ ] **Step 2: Áp plugin**

Root `build.gradle.kts`: ba plugin mới với `apply false`. `app/build.gradle.kts`: `alias` cả ba, `buildFeatures { compose = true }`, `implementation(platform(...))` cho BOM, `implementation` cho thư viện trên, `ksp(libs.hilt.compiler)`, `debugImplementation(libs.androidx.compose.ui.tooling)`.

- [ ] **Step 3: Xác nhận Kotlin thực dùng**

Run: `./gradlew.bat :app:buildEnvironment`
Expected: `kotlin-gradle-plugin` phân giải về `2.4.21`. AGP 9.5.0 chỉ khai báo 2.2.10, plugin Compose đẩy nó lên. Nếu không phải 2.4.21, dừng và báo user.

- [ ] **Step 4: Build và test**

Run: `./gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Nếu Hilt hoặc KSP không tương thích AGP 9.5, dừng và báo user, không hạ AGP.

- [ ] **Step 5: `reviewer` soát nhánh, hỏi user, commit và squash**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "build: add compose, hilt and ksp to the version catalog"
git switch dev
git merge --squash chore/gradle-catalog
git commit -m "build: add compose, hilt and ksp to the version catalog"
git switch -c chore/quality-gate
```

---

### Task 2: detekt (`chore/quality-gate`)

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
- Create: `config/detekt/detekt.yml`

**Interfaces:**
- Consumes: catalog từ Task 1.
- Produces: task Gradle `detekt`, `detektMain`.

Đây là bước thử của spec mục 6. Nếu thất bại, đi tới Step 6 thay vì sửa lan man.

- [ ] **Step 1: Khai báo plugin**

Catalog: `detekt = "2.0.0-alpha.6"`, plugin `detekt` id `dev.detekt`. Áp ở `app/build.gradle.kts`, `apply false` ở root.

- [ ] **Step 2: Xác nhận task tồn tại**

Run: `./gradlew.bat :app:tasks --all`
Expected: có `detekt` và `detektMain`.

- [ ] **Step 3: Viết `config/detekt/detekt.yml`**

Cấu hình `detekt { buildUponDefaultConfig = true; config.setFrom("$rootDir/config/detekt/detekt.yml") }`. Đặt các mức dưới đây, lấy tên khóa từ default config của `2.0.0-alpha.6` vì tên khóa 2.0 khác 1.x. Không dùng baseline. Mọi finding phải làm task fail, tương đương `maxIssues = 0`.

| Rule | Giá trị |
|---|---|
| `LongMethod` | 40 dòng, `ignoreAnnotated: ['Composable']` |
| `LongParameterList` | 6 tham số hàm, `ignoreAnnotated: ['Composable']` |
| `CyclomaticComplexMethod` | 10 |

- [ ] **Step 4: Chạy trên code sạch**

Run: `./gradlew.bat detekt detektMain`
Expected: `BUILD SUCCESSFUL`, kể cả lần chạy thứ hai với configuration cache.

- [ ] **Step 5: Chứng minh đỏ, rồi xóa probe**

Tạo `app/src/main/java/io/github/toanhp123/finote/RedProbeDetekt.kt` chứa:
- Hàm 41 dòng, hàm 7 tham số, hàm cyclomatic 11: phải fail ở `detekt`.
- Một vi phạm chỉ rule cần type resolution bắt được, ví dụ `UnnecessarySafeCall` trên kiểu non-null: phải fail ở `detektMain`, không fail ở `detekt`.
- Hàm `@Composable` 41 dòng với 7 tham số: không có finding.

Hàm `@Composable` cần Compose runtime có sẵn từ Task 1. Ghi output vào báo cáo, rồi xóa probe.

Run: `Remove-Item app/src/main/java/io/github/toanhp123/finote/RedProbeDetekt.kt`
Expected: `git status --short` không còn file probe.

- [ ] **Step 6: Phương án dự phòng (chỉ khi Step 2, 4 hoặc 5 thất bại)**

Dừng, báo lỗi và hỏi user có bỏ detekt không. Nếu user đồng ý: gỡ detekt, Task 3 bỏ `detektMain` khỏi `qualityGate`, Task 4 thêm luật đếm 40 dòng hàm và 6 tham số, Task 5 hook chỉ gọi `spotlessCheck`, Task 6 ghi rõ mất luật độ phức tạp và coroutine.

- [ ] **Step 7: Hỏi user, rồi commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts config/detekt/detekt.yml
git commit -m "build: add detekt with project limits"
```

---

### Task 3: Format, Lint, warning và `qualityGate`

**Files:**
- Create: `.editorconfig`, `.gitattributes`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, resource template nếu Lint báo

**Interfaces:**
- Consumes: `detektMain` từ Task 2.
- Produces: task `spotlessCheck`, `spotlessApply`, `qualityGate`.

- [ ] **Step 1: Viết `.gitattributes` và `.editorconfig`**

`.gitattributes`: `* text=auto eol=lf`, `*.bat text eol=crlf`, `*.jar binary`, `*.webp binary`.

`.editorconfig`, phần `[*.{kt,kts}]`: `end_of_line = lf`, `ktlint_code_style = ktlint_official`, `max_line_length = 120`, `ktlint_function_naming_ignore_when_annotated_with = Composable`.

- [ ] **Step 2: Chuẩn hóa line ending**

`git add --renormalize` từng file do `git ls-files --eol` báo lệch. Không dùng `.` vì `git_guard.py` chặn.

- [ ] **Step 3: Cấu hình Spotless trong `app/build.gradle.kts`**

Catalog: `spotless = "8.10.4"`, `ktlint = "1.8.0"`, `composeRules = "0.6.8"`.

```kotlin
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}"))
    }
    kotlinGradle {
        target("*.gradle.kts", "../*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
```

Chạy `./gradlew.bat spotlessApply` để format file hiện có.

- [ ] **Step 4: Bật warning là lỗi, Lint và `qualityGate`**

```kotlin
kotlin { compilerOptions { allWarningsAsErrors = true } }

android {
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Version-bump checks would turn the gate red whenever a library ships.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

tasks.register("qualityGate") {
    group = "verification"
    dependsOn("spotlessCheck", "detektMain", "lintDebug", "testDebugUnitTest", "assembleDebug")
}
```

`AndroidGradlePluginVersion` không có trong spec, thêm vì cùng lý do với hai check kia.

- [ ] **Step 5: Chạy gate**

Run: `./gradlew.bat qualityGate`
Expected: `BUILD SUCCESSFUL`. Lint báo resource template thì sửa hoặc xóa resource, không dùng baseline. Warning đến từ code KSP sinh ra thì dừng và báo user (spec mục 9).

- [ ] **Step 6: Kiểm line ending**

Run: `git ls-files --eol app/build.gradle.kts gradlew.bat .editorconfig`
Expected: `i/lf` với file text, `i/crlf` với `gradlew.bat`. Chạy lại `./gradlew.bat spotlessCheck`: pass.

- [ ] **Step 7: Chứng minh đỏ, rồi hoàn tác**

| Vi phạm tạm | Phải fail ở |
|---|---|
| Biến không dùng trong `app/src/test/.../ExampleUnitTest.kt` | `compileDebugUnitTestKotlin` |
| Thụt lề sai trong `ExampleUnitTest.kt` | `spotlessCheck` |
| Composable tên chữ thường trong `RedProbeCompose.kt` | `spotlessCheck` (compose-rules) |
| Chuỗi `<string>` chưa dùng trong `strings.xml` | `lintDebug` |

Hoàn tác bằng Edit từng chỗ và `Remove-Item` file probe, không dùng `git checkout --`.

- [ ] **Step 8: Hỏi user, rồi commit**

Stage đúng tên từng file đổi, gồm file được renormalize và format.

```bash
git commit -m "build: add spotless, lint and the quality gate task"
```

---

### Task 4: Luật Konsist

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create, trong `app/src/test/java/io/github/toanhp123/finote/architecture/`:
  - `KonsistScopes.kt`: scope và hằng package dùng chung
  - `LayerDependencyKonsistTest.kt`: luật tầng
  - `NamingKonsistTest.kt`: tên và vị trí
  - `ResponsibilityKonsistTest.kt`: trách nhiệm và giới hạn
  - `ProjectRulesKonsistTest.kt`: riêng tư, tiền, `@Suppress`

**Interfaces:**
- Consumes: `testDebugUnitTest`.
- Produces: `internal val productionFiles` trong `KonsistScopes.kt`, là `Konsist.scopeFromProduction()` đã lọc đường dẫn chứa `/build/`. Mọi test dùng biến này.

- [ ] **Step 1: Thêm phụ thuộc**

Catalog: `konsist = "0.18.1"`, `com.lemonappdev:konsist`. `testImplementation(libs.konsist)`.

- [ ] **Step 2: Xác nhận hai hành vi còn mở**

Trong một test tạm: `assertTrue` trên tập rỗng không có `strict` pass hay fail, và file trong `app/build/generated` có nằm trong `Konsist.scopeFromProduction()` không. Ghi kết quả vào báo cáo. Mọi `assertTrue` dùng `strict = false` tường minh. Xóa test tạm.

- [ ] **Step 3: Viết luật**

Kiểm tra bằng import theo package, không dùng `assertArchitecture` (spec mục 5). Mỗi dòng là một `@Test`.

`LayerDependencyKonsistTest`:

| Test | Tập file | Cấm import |
|---|---|---|
| `domain has no framework imports` | `..domain..` | `android..`, `androidx..`, `dagger..`, `..room..` |
| `data does not import outer layers` | `..data..` | `..ui..`, `..automation..`, `..work..` của dự án |
| `ui does not import data, automation or work` | `..ui..` | tương tự |
| `automation does not import ui, data or work` | `..automation..` | tương tự |
| `work does not import ui, data or automation` | `..work..` | tương tự |
| `ui common does not import ui features` | `..ui.common..` | `..ui..` của dự án, trừ `..ui.common..` |
| `root package holds only app entry points` | file ở gốc package | tên file phải thuộc `MainActivity.kt`, `FinoteApplication.kt` |
| `nothing imports the root package` | mọi file | import đúng gốc + tên class, trừ `R`, `BuildConfig` |

`NamingKonsistTest`:

| Test | Luật |
|---|---|
| `use cases live in usecase` | Hậu tố `UseCase` ⇔ `..domain.*.usecase..` |
| `repository package holds only interfaces` | Khai báo trong `..domain.*.repository..` là interface, hậu tố `Repository` |
| `repository impls live in data` | Hậu tố `RepositoryImpl` ⇒ `..data..` |
| `entities and daos live in data` | Hậu tố `Entity`, `Dao` ⇒ `..data..` |
| `view models live in ui` | Hậu tố `ViewModel` ⇒ `..ui..` |
| `workers live in work` | Hậu tố `Worker` ⇒ `..work..` |
| `hilt modules live in di` | Hậu tố `Module` ⇔ `..di..`, có `@Module` |
| `flow functions start with observe` | Hàm khai báo trả `Flow` trong `..domain..`, `..data..` |
| `boolean properties use a prefix` | Kiểu `Boolean`, tên bắt đầu `is`, `has`, `should` |

`ResponsibilityKonsistTest`:

| Test | Luật |
|---|---|
| `use cases expose only invoke` | Một hàm public duy nhất, `operator fun invoke` |
| `mapper files hold only mapping extensions` | File `*Mapper.kt` chỉ có hàm `toDomain`, `toEntity` |
| `contract files declare state intent effect` | File `*Contract.kt` có đúng ba khai báo top-level với hậu tố `State`, `Intent`, `Effect` |
| `screens do not touch use cases` | File `*Screen.kt` không import `..usecase..` |
| `view models do not import compose ui` | File `*ViewModel.kt` không import `androidx.compose..` |
| `files stay under 300 lines` | `file.text.lines().size <= 300` |
| `composables stay under 80 lines` | Hàm `@Composable`: `text.lines().size <= 80` |
| `one public class per file` | Tối đa 1 class, interface, object top-level public, Contract được 3 |

`ProjectRulesKonsistTest`:

| Test | Luật |
|---|---|
| `production code never logs` | Không import `android.util.Log`, text file không chứa `android.util.Log` hay `println(` |
| `money is never floating point` | Thuộc tính và tham số có tên chứa `amount`, `balance`, `limit`, `fee` (không phân biệt hoa thường) không có kiểu `Double`, `Float`, kể cả `?` |
| `suppress is forbidden` | Không có `@Suppress` ở khai báo lẫn `@file:Suppress` |

- [ ] **Step 4: Chạy trên code sạch**

Run: `./gradlew.bat testDebugUnitTest --tests "*KonsistTest"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Chạy ma trận đỏ**

Mỗi probe là file tạm, tạo xong chạy lệnh ở Step 4, ghi tên test fail, xóa bằng `Remove-Item` rồi sang probe sau. Probe phải biên dịch được. Với P13, gom vào một lần chạy cũng được nếu mỗi test fail đúng vi phạm của nó.

| Probe | File tạm dưới `finote/` | Vi phạm | Test phải fail |
|---|---|---|---|
| P1 | `domain/probe/Bad.kt` | `import android.content.Context` | `domain has no framework imports` |
| P2 | `data/probe/Bad.kt` + `ui/probe/Thing.kt` | `data` import `ui` | `data does not import outer layers` |
| P3 | `ui/common/Bad.kt` + `ui/probe/Thing.kt` | `ui/common` import `ui/probe` | `ui common does not import ui features` |
| P4 | `Stray.kt` ở gốc | File lạ ở gốc | `root package holds only app entry points` |
| P5 | `domain/probe/LoadUseCase.kt` | Sai package, thêm hàm public thứ hai | `use cases live in usecase`, `use cases expose only invoke` |
| P6 | `ui/probe/Long.kt` | `@Composable` 81 dòng, sau đó thử 80 dòng | Fail ở 81, pass ở 80 (`composables stay under 80 lines`) |
| P7 | `ui/probe/Huge.kt` | 301 dòng, sau đó thử 300 dòng | Fail ở 301, pass ở 300 (`files stay under 300 lines`) |
| P8 | `ui/probe/LogBad.kt` | `android.util.Log.d("x", "y")` không import | `production code never logs` |
| P9 | `domain/probe/Money.kt` | `val originalAmount: Double?`, `val feeAmount: Float` | `money is never floating point` |
| P10 | `domain/probe/Rate.kt` | `val exchangeRate: Double?`, `val alertThreshold: Int` | Không test nào fail |
| P11 | `domain/probe/Quiet.kt` | `@file:Suppress("unused")` | `suppress is forbidden` |
| P12 | `ui/probe/UsesR.kt` | `import io.github.toanhp123.finote.R` | Không test nào fail |
| P13 | Một file sai mỗi luật còn lại ở Step 3 | Theo tên test | Đúng test đó |

Expected cuối cùng: `git status --short` không còn file probe.

- [ ] **Step 6: Hỏi user, rồi commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/test/java/io/github/toanhp123/finote/architecture/KonsistScopes.kt app/src/test/java/io/github/toanhp123/finote/architecture/LayerDependencyKonsistTest.kt app/src/test/java/io/github/toanhp123/finote/architecture/NamingKonsistTest.kt app/src/test/java/io/github/toanhp123/finote/architecture/ResponsibilityKonsistTest.kt app/src/test/java/io/github/toanhp123/finote/architecture/ProjectRulesKonsistTest.kt
git commit -m "test: add konsist architecture rules"
```

---

### Task 5: Git hook và CI

**Files:**
- Create: `.githooks/pre-commit`, `.githooks/pre-merge-commit`, `.github/workflows/quality-gate.yml`

**Interfaces:**
- Consumes: `qualityGate`, `spotlessCheck`, `detekt`.

- [ ] **Step 1: Viết hook**

`.githooks/pre-commit`:

```sh
#!/bin/sh
case "$(git symbolic-ref --short HEAD 2>/dev/null)" in
  dev|main) exec ./gradlew qualityGate ;;
  *) exec ./gradlew spotlessCheck detekt ;;
esac
```

`.githooks/pre-merge-commit`: `#!/bin/sh` và `exec .githooks/pre-commit`. Git không gọi `pre-commit` khi tạo merge commit, nên thiếu file này thì `release/*` vào `main` không qua gate. Spec không nêu, thêm để đạt mục tiêu của spec.

Đánh dấu thực thi cho cả hai hook và `gradlew` (đang là mode `100644`, CI Linux sẽ không chạy được):

```bash
git add .githooks/pre-commit .githooks/pre-merge-commit
git update-index --chmod=+x .githooks/pre-commit .githooks/pre-merge-commit gradlew
```

- [ ] **Step 2: Kiểm hook**

Run: `git ls-files --eol .githooks/pre-commit`
Expected: `i/lf`, `w/lf`.

Run: `git config core.hooksPath .githooks`, rồi `git config --get core.hooksPath`
Expected: `.githooks`.

- [ ] **Step 3: Chứng minh hook chặn trên nhánh làm việc**

Thêm lỗi thụt lề vào `ExampleUnitTest.kt`, stage, chạy `git commit -m "test: probe the hook"`.
Expected: commit bị chặn bởi `spotlessCheck`, không có commit mới. Hoàn tác bằng `git restore --staged` rồi Edit lại. Đo thời gian một lần hook chạy pass và ghi vào báo cáo.

- [ ] **Step 4: Kiểm nhánh rẽ của hook mà không tạo commit**

Tạo repo tạm trong scratchpad, thay `./gradlew` bằng stub in tên task, chép hai hook vào, chạy trên `dev`, `main`, `chore/x` và một `git merge --no-ff`.
Expected: `dev`, `main`, merge in `qualityGate`; `chore/x` in `spotlessCheck detekt`.

- [ ] **Step 5: Viết `.github/workflows/quality-gate.yml`**

`on: pull_request` với `branches: [dev, main]`. Job `ubuntu-latest`: `actions/checkout@v4`, `actions/setup-java@v4` (`temurin`, `21`), `gradle/actions/setup-gradle@v4`, `./gradlew qualityGate`.

Workflow chưa chạy được vì chưa có remote. Ghi rõ điều này trong báo cáo.

- [ ] **Step 6: Hỏi user, rồi commit**

```bash
git add .github/workflows/quality-gate.yml
git commit -m "ci: run the quality gate from git hooks and actions"
```

---

### Task 6: Tài liệu và đóng nhánh

**Files:**
- Create: `docs/architecture.md`
- Modify: `CLAUDE.md`, `docs/git-workflow.md`, `docs/roadmap.md`, `.claude/settings.json`, `.claude/agents/reviewer.md`

- [ ] **Step 1: Viết `docs/architecture.md`**

Tiếng Anh, như `docs/git-workflow.md`. Rút gọn spec mục 3, 4, 5, kèm ba lệnh `qualityGate`, `spotlessApply`, `git config core.hooksPath .githooks`. Ghi quy tắc: rule Konsist mới phải có một lần chứng minh đỏ. Ghi riêng các luật máy không ép được: tên `Intent`, `Effect`, và `getX` cho hàm đọc một lần, do `reviewer` kiểm.

- [ ] **Step 2: Cập nhật tài liệu dự án**

| File | Sửa |
|---|---|
| `CLAUDE.md` | "Current state": đã có commit, đã có Kotlin, Compose, Hilt. "Architecture": trỏ `docs/architecture.md`. "Commands": thêm `./gradlew.bat qualityGate`. "Verification": chạy `qualityGate` trước squash |
| `docs/git-workflow.md` | "Daily flow": thêm `qualityGate` trước `reviewer`. "Setup": thêm `git config core.hooksPath .githooks` |
| `docs/roadmap.md` | P0: cấu trúc package theo spec mục 3, thứ tự theo spec mục 6, ghi `chore/gradle-catalog`, `chore/quality-gate` đã xong. P2b, P2c, P2d: parser, dedup, pairing ở `domain/notification`. `feature/db-schema`: thêm vào DoD là chạy luật `@Suppress` với code KSP của Hilt và Room (spec mục 7) |
| `.claude/settings.json` | Thêm `Bash(./gradlew.bat qualityGate*)`, `PowerShell(./gradlew.bat qualityGate*)`, `PowerShell(.\\gradlew.bat qualityGate*)` |
| `.claude/agents/reviewer.md` | Yêu cầu output `qualityGate` thay vì chỉ `testDebugUnitTest`, kiểm các luật ở Step 1 |

`git_guard.py` không đổi nên không cần `test_git_guard.py`.

- [ ] **Step 3: Chạy gate lần cuối**

Run: `./gradlew.bat qualityGate`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Hỏi user, rồi commit**

```bash
git add docs/architecture.md CLAUDE.md docs/git-workflow.md docs/roadmap.md .claude/settings.json .claude/agents/reviewer.md
git commit -m "docs: document the architecture rules and quality gate"
```

- [ ] **Step 5: `reviewer` soát toàn nhánh**

Đưa `reviewer` spec, plan, và output Task 2 Step 5, Task 3 Step 7, Task 4 Step 5, Task 5 Step 3–4.

- [ ] **Step 6: Hỏi user, rồi squash vào `dev`**

Hook đã bật nên commit này chạy `qualityGate` thật.

```bash
git switch dev
git merge --squash chore/quality-gate
git commit -m "build: add the architecture quality gate"
```

Expected: output có `> Task :app:qualityGate`, commit được tạo. Dừng tại đây. `feature/app-shell` là việc của phiên sau.
