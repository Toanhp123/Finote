# Thiết kế nền tảng kiến trúc và quality gate

Trạng thái: chờ user duyệt. Ngày: 2026-11-02.

## 1. Mục tiêu

Quy tắc kiến trúc, đặt tên và trách nhiệm file phải do máy ép, không dựa vào trí nhớ. Vi phạm không được vào `dev`, dù người hay agent viết.

Tiêu chí thành công:
- `./gradlew.bat qualityGate` pass trên code sạch.
- Mỗi lớp gate fail trên một vi phạm cố ý.
- Không có đường hợp lệ nào đưa code chưa qua gate vào `dev`.

Giả định: một người phát triển, repo chưa có remote.

Ngoài phạm vi: đo coverage (xét lại ở P1, khi có logic tiền), tách nhiều Gradle module.

## 2. Quyết định đã chốt

| Chủ đề | Quyết định | Lý do |
|---|---|---|
| Module | Một module `:app`, tầng là package | Hướng dẫn của Google: codebase nhỏ chưa cần tách. Tách sau vẫn dễ vì package đã khớp tầng |
| Phân tích tĩnh | detekt 2.0 alpha, ghim bản | Chỉ detekt có luật độ phức tạp và coroutine. Không vào APK |
| Nơi chạy gate | Task Gradle, git hook, GitHub Actions | Chặn cục bộ ngay, CI bắt máy chưa bật hook |

Dữ kiện tra ngày 2026-11-02:
- detekt mới nhất là `2.0.0-alpha.6` (2026-08-04). Plugin id `dev.detekt`. Hỗ trợ built-in Kotlin của AGP 9 nằm ở mốc 2.0.
- Konsist `0.18.1`, build bằng Kotlin 2.4.21.
- AGP 9 có sẵn Kotlin: bỏ plugin `org.jetbrains.kotlin.android`, dùng KSP thay kapt. Dự án dùng AGP 9.5.0, Gradle 9.6.0.

## 3. Kiến trúc

Tầng chia theo package, trong tầng chia theo feature. Gốc package: `io.github.toanhp123.finote`.

```text
domain/<feature>/    model, repository interface, use case, logic thuần
data/<feature>/      Room entity, DAO, mapper, repository impl
ui/<feature>/        Screen, Contract, ViewModel
ui/common/           theme, component dùng chung, BaseViewModel
automation/          NotificationListenerService, mỏng
work/                Worker, mỏng
di/                  Hilt module
```

Feature ban đầu: `transaction`, `account`, `category`, `notification`. Feature khác thêm khi tới phase tương ứng.

Gốc package chỉ chứa `MainActivity` và `FinoteApplication`, được phụ thuộc mọi tầng, không chứa logic. `TileService` chỉ mở màn nhập tiền mặt, nên nằm ở `ui/<feature>`.

| Tầng | Được phụ thuộc | Cấm |
|---|---|---|
| `domain` | Kotlin, coroutines, `javax.inject` | `android.*`, `androidx.*`, Room, Hilt |
| `data` | `domain` | `ui`, `automation`, `work` |
| `ui` | `domain` | `data`, `automation`, `work` |
| `automation` | `domain` | `ui`, `data`, `work` |
| `work` | `domain` | `ui`, `data`, `automation` |
| `di` | mọi tầng | Không chứa logic |

Trong `ui`: `ui/<feature>` được dùng `ui/common`, `ui/common` không được dùng `ui/<feature>`. Gốc package chỉ được chứa đúng hai file trên, và không tầng nào được import ngược lên gốc.

Parser, dedup, pairing và tính số dư nằm trong `domain`. `automation/` chỉ nhận thông báo và gọi use case. Lý do: logic thuần test được trên JVM, không cần Robolectric. Robolectric chỉ dùng cho DAO và `@Transaction`.

Luồng UI theo MVI: Screen gửi `Intent` → ViewModel gọi use case → ViewModel phát `State` và `Effect` → Screen vẽ.

## 4. Quy ước đặt tên và trách nhiệm

| Vai trò | Tên | Vị trí | Chỉ được làm |
|---|---|---|---|
| Model | `Transaction` | `domain/<f>/model` | Giữ dữ liệu, không I/O |
| Use case | `AddTransactionUseCase` | `domain/<f>/usecase` | Một việc, một `operator fun invoke` public |
| Logic thuần | `NotificationParser` | `domain/<f>` | Hàm thuần, không I/O |
| Repository | `TransactionRepository` | `domain/<f>/repository` | Chỉ interface |
| Repository impl | `TransactionRepositoryImpl` | `data/<f>` | Điều phối DAO, ánh xạ |
| Entity | `TransactionEntity` | `data/<f>` | Schema Room |
| DAO | `TransactionDao` | `data/<f>` | Truy vấn |
| Mapper | `TransactionMapper.kt` | `data/<f>` | Chỉ extension `toDomain()`, `toEntity()` |
| Contract | `TransactionListContract.kt` | `ui/<f>` | Khai báo `State`, `Intent`, `Effect` |
| ViewModel | `TransactionListViewModel` | `ui/<f>` | Nhận intent, gọi use case, phát state |
| Screen | `TransactionListScreen` | `ui/<f>` | Vẽ state, không gọi use case |
| Worker | `TrashPurgeWorker` | `work` | Gọi use case |
| DI | `DatabaseModule` | `di` | Chỉ khai báo binding |

| Loại | Quy ước | Ví dụ |
|---|---|---|
| Hàm trả `Flow` | `observeX` | `observeAccounts()` |
| Hàm `suspend` đọc một lần | `getX` | `getAccount(id)` |
| Boolean | `is`, `has`, `should` | `isArchived` |
| Intent | Hành động của user | `AmountChanged` |
| Effect | Lệnh một lần | `NavigateBack` |
| Hằng số | `UPPER_SNAKE_CASE` | `DEDUP_WINDOW_MS` |

| Chỉ số | Mức | Công cụ |
|---|---|---|
| Dòng mỗi file | 300 | Konsist, detekt không có rule theo file |
| Dòng mỗi hàm | 40 | detekt |
| Dòng mỗi hàm `@Composable` | 80 | Konsist |
| Tham số mỗi hàm | 6, không tính `@Composable` | detekt |
| Độ phức tạp cyclomatic | 10 | detekt |
| Class public mỗi file | 1, Contract được 3 | Konsist |

detekt không có hai ngưỡng trong một rule. Vì vậy `LongMethod` và `LongParameterList` đặt `ignoreAnnotated: ['Composable']`, Konsist đếm mức 80.

## 5. Quality gate

`./gradlew.bat qualityGate` phụ thuộc `spotlessCheck`, `detektMain`, `lintDebug`, `testDebugUnitTest`, `assembleDebug`.

Gate dùng `detektMain` vì task `detekt` không có type resolution, và các rule cần type resolution không chạy trong task đó. Theo tài liệu detekt, trên Android `detektMain` phủ mọi source set production.

| Lớp | Công cụ | Ép gì |
|---|---|---|
| Biên dịch | `allWarningsAsErrors` | Warning Kotlin thành lỗi |
| Format | Spotless + ktlint + compose-rules | Style, lỗi Compose |
| Phân tích | detekt | Độ phức tạp, độ dài, coroutine, bug |
| Android | Android Lint, `warningsAsErrors` | Lỗi nền tảng, tài nguyên |
| Kiến trúc | Konsist trong `testDebugUnitTest` | Mục 3, 4 và luật dự án |
| Hành vi | Unit test | Logic |

compose-rules dùng bản ktlint, không dùng bản detekt, để không phụ thuộc vào tương thích với detekt alpha.

Luật tầng trong Konsist viết bằng kiểm tra import theo package, không dùng `assertArchitecture`, vì hai lý do trong tài liệu Konsist:
- `assertArchitecture` fail khi một tầng khai báo không có file. Nhánh `chore/quality-gate` chưa có code, `automation` và `work` còn rỗng tới P2, P4.
- Nó bỏ qua import không thuộc tầng khai báo, nên không chặn được `domain` import `android.*`.

Luật dự án trong Konsist, quét bằng `scopeFromProduction()`:
- Cấm `android.util.Log` và `println` trong toàn bộ code production, vì `CLAUDE.md` cấm ghi dữ liệu ngân hàng ra Logcat.
- Cấm `Double`, `Float` cho thuộc tính và tham số có tên chứa `amount`, `balance`, `limit`, `fee`, không phân biệt hoa thường. `exchangeRate` (`Double`) và `alertThreshold` (`Int`) của spec không dính luật này.
- Cấm `@Suppress`, lọc thêm đường dẫn chứa `/build/` để không quét code KSP sinh ra. detekt không dùng baseline, `maxIssues = 0`.

Rule Konsist chạy trên tập rỗng được xem là chưa chứng minh gì. Tham số assert có `testName`, `strict`, `additionalMessage`. Tài liệu chỉ nói `strict = true` làm fail khi tập rỗng, không nói mặc định. Mặc định phải xác nhận khi cài. Rule dùng `strict = false` để pass khi feature chưa tồn tại, và mục 7 chứng minh rule đỏ được.

Nếu một luật sai cho một trường hợp hợp lệ, sửa cấu hình luật trong một commit riêng, không chú thích bỏ qua tại chỗ.

Android Lint tắt `GradleDependency` và `NewerVersionAvailable`, để thư viện ra bản mới không làm gate đỏ.

| Khi nào | Chạy gì | Cơ chế |
|---|---|---|
| Commit trên nhánh làm việc | `spotlessCheck`, `detekt` | `.githooks/pre-commit` |
| Commit trên `dev` hoặc `main` | `qualityGate` | Cùng hook, xét theo tên nhánh |
| PR vào `dev`, `main` | `qualityGate` | `.github/workflows/quality-gate.yml` |

Hook do `sh` của Git chạy, nên gọi `./gradlew spotlessCheck detekt`. Không thêm `--daemon` (đã là mặc định) hay `--parallel` (dự án một module). Configuration cache đã bật trong `gradle.properties`.

Hook bật một lần cho mỗi clone:

```bash
git config core.hooksPath .githooks
```

`git_guard.py` đã chặn `--no-verify`, nên agent không né được hook. Workflow CI chỉ chạy được khi repo có remote. Trước đó file đó chưa được kiểm chứng.

## 6. Thứ tự thực hiện

Thay P0 trong roadmap:

1. `chore/gradle-catalog`: gộp cả `chore/build-plugins`. Thêm phụ thuộc và plugin. Kotlin dùng built-in của AGP, không thêm `kotlin-android`.
2. `chore/quality-gate`: thử detekt, cài gate, hook, CI, `.editorconfig`, `.gitattributes`, luật Konsist.
3. `feature/app-shell`.
4. `feature/mvi-base`.

Đầu nhánh `chore/quality-gate`, thử detekt `2.0.0-alpha.6` trên AGP 9.5.0, Gradle 9.6.0, có bật configuration cache. Nếu hỏng và không sửa nhanh được: bỏ detekt, Konsist đếm dòng file, dòng hàm và tham số, ghi rõ mất luật độ phức tạp và coroutine.

`.gitattributes` và `.editorconfig` cố định LF, riêng `*.bat` giữ CRLF. Repo chưa có hai file này.

## 7. Tiêu chí nghiệm thu `chore/quality-gate`

- `./gradlew.bat qualityGate` pass.
- Mỗi lớp fail trên một vi phạm cố ý, ghi lại output. Vi phạm không được commit. Yêu cầu này tồn tại vì luật chạy trên code rỗng có thể pass mà không chứng minh gì.
- Mỗi luật ở mục 3 và 4 có ít nhất một lần kiểm tra đỏ.
- Commit vi phạm format trên nhánh làm việc bị hook chặn.
- Hook xét đúng nhánh: `qualityGate` chạy trên `dev`, không chạy trên `chore/*`.
- Rule cần type resolution bắt được vi phạm qua `detektMain`.
- Hàm `@Composable` 81 dòng làm Konsist fail, hàm 80 dòng thì pass.
- Sau khi có Hilt và Room, luật `@Suppress` không báo nhầm code trong `build/generated/ksp/`. Kiểm ở nhánh đầu tiên thêm hai thư viện này.
- Đo và ghi lại thời gian hook trên nhánh làm việc.

## 8. Cập nhật tài liệu

- Tạo `docs/architecture.md`: bản rút gọn mục 3, 4, 5, là nguồn chuẩn lâu dài.
- `CLAUDE.md`: trỏ tới `docs/architecture.md`, thêm lệnh `qualityGate`, sửa "Current state" và mục kiểm chứng.
- `docs/git-workflow.md`: thêm bước gate trước squash, bước bật hook. Sửa cùng `git_guard.py` nếu luật hook đổi.
- `docs/roadmap.md`: sửa cấu trúc package ở P0, thứ tự P0, vị trí parser ở P2.
- `.claude/settings.json`: cho phép lệnh `gradlew.bat qualityGate`.
- `.claude/agents/reviewer.md`: yêu cầu xem output `qualityGate`.

## 9. Rủi ro

| Rủi ro | Xử lý |
|---|---|
| detekt alpha hỏng trên AGP 9.5 | Thử trước, có phương án bỏ detekt ở mục 6 |
| Spotless, Konsist chưa tương thích AGP 9.5 | Cùng lần thử. Chưa kiểm chứng ở bước thiết kế |
| `allWarningsAsErrors` đỏ vì warning từ KSP sinh ra | Kiểm khi cài Hilt, Room. Phương án: chỉ áp cho source của dự án |
| Pre-commit chậm khi code lớn | Hook chạy cả dự án. Đo ở mục 7, giới hạn theo file đổi nếu gây khó |
| Pattern package sai làm rule pass mãi trên tập rỗng | Mỗi rule có một lần đỏ ở mục 7. Rule mới về sau phải làm tương tự |
| CI chưa từng chạy | Kiểm khi tạo remote |
