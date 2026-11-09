# Roadmap chi tiết — Finote

Trạng thái: bản nháp v1. Nguồn: spec `docs/Tài liệu Đặc tả...(MVP).txt` mục 2.2, 3, 4, 5–9; `CLAUDE.md`; `docs/git-workflow.md`.

Hiện trạng (đã kiểm tra):
- Catalog đã có Compose BOM, Hilt, KSP, detekt, Spotless, Konsist, Navigation3. Room 3, Robolectric, Turbine đã có. Chưa có SQLCipher, WorkManager.
- `app/src/main/java/` chưa có source Kotlin production. Đã có luật Konsist trong `app/src/test/`.
- `minSdk = 26`, `targetSdk = 37` (spec ghi 35, đã chốt ở mục D3).
- Git: nhánh `dev` đã có commit khởi tạo, spec, plan và catalog. Xem lịch sử bằng `git log dev`.

## 0. Nguyên tắc làm việc

- Mỗi phase kết thúc bằng một mốc release nhỏ, có tiêu chí đo được (DoD).
- Mỗi tính năng là một nhánh `feature/<name>` từ `dev`, squash vào `dev`. `reviewer` chạy một lần cho mỗi nhánh, trước khi squash.
- Thay đổi logic (parser, dedup, pairing, balance) phải chạy `testDebugUnitTest` qua `gradle-run` và đọc bản tóm tắt.
- Tiền luôn là `Long` VND. Không dùng `Double` cho số tiền.
- Mọi thay đổi số dư nằm trong một `@Transaction`, gồm cả `AccountBalanceSnapshot`.
- Xóa mềm bằng `deletedAt`. Chỉ WorkManager dọn thùng rác mới được xóa cứng.
- Không log nội dung thông báo, số dư, số tài khoản. Log rule id hoặc trạng thái thay thế.
- Fixture test: thay tên, số tiền, số tài khoản bằng giá trị giả. Không commit dữ liệu thật.
- Instrumented test chỉ chạy khi user yêu cầu. Vì vậy test Room dùng Robolectric để chạy trong `testDebugUnitTest`.
- Thư viện native của SQLCipher không chạy trên JVM. Test Room dùng DB không mã hóa qua driver khác (dự kiến `BundledSQLiteDriver`). Mã hóa được kiểm riêng ở `feature/db-encryption`.

## 1. Mốc phát hành

| Mốc | Phase | Nội dung chính | Ghi chú |
|---|---|---|---|
| v0.1 | P0, P1 | Nhập tay, DB mã hóa, số dư đúng | Chưa tự động |
| v0.2 | P2 | Tự động bắt giao dịch từ thông báo | Phần khác biệt của sản phẩm |
| v0.3 | P3 | Dashboard, ngân sách, tìm kiếm, tài sản ròng | |
| v0.4 | P4 | Hoàn tiền, thẻ tín dụng, định kỳ, import/export, thùng rác | |
| v1.0 | P5 | Bảo mật UI, hiệu năng, onboarding quyền, đạt mục 5–9 | Phát hành production |
| v1.x | P6 | Could-Have còn lại | Sau v1.0 |

Mỗi mốc: `release/x.y.z` từ `dev` → `main` (`--no-ff`) → tag `vX.Y.Z` → `dev` (`--no-ff`). `versionCode` tăng mỗi lần.

## 2. Phase 0 — Nền tảng (không có tính năng)

Mục tiêu: build chạy, khung kiến trúc sẵn sàng cho mọi phase sau.

Việc (thứ tự theo `docs/superpowers/specs/2026-11-02-architecture-foundation-design.md` mục 6):
1. `chore/gradle-catalog`: **đã xong.** Gộp cả `chore/build-plugins`. Catalog có Compose BOM, Hilt, KSP, detekt, Spotless, Konsist. Kotlin dùng built-in của AGP, không thêm `kotlin-android`.
2. `chore/quality-gate`: **đã xong.** `./gradlew.bat qualityGate`, git hook, GitHub Actions, luật Konsist. Chi tiết: `docs/architecture.md`.
3. `feature/app-shell`: `MainActivity` (Single Activity, `AppCompatActivity` vì đa ngôn ngữ, xem D9), Compose theme Material 3, Dynamic Color, `NavDisplay` của Navigation3. Back stack bắt đầu bằng key `Home` (màn trống), vì `NavDisplay` không nhận back stack rỗng. Nav host nằm ở `ui/navigation`, không ở `ui/common`, vì nó import màn của các feature.
   - Đa ngôn ngữ (per-app language): giữ `appcompat`, theme `Theme.AppCompat.DayNight.NoActionBar`, `generateLocaleConfig = true`, `res/resources.properties`, `AppLocalesMetadataHolderService` với `autoStoreLocales` cho API 32 trở xuống. Mọi chuỗi UI nằm trong `strings.xml`.
   - ViewModel theo từng màn: khi có ViewModel đầu tiên, thêm `androidx.lifecycle:lifecycle-viewmodel-navigation3` và `androidx.hilt:hilt-lifecycle-viewmodel-compose` (cho `hiltViewModel()`).
4. `feature/mvi-base`: `ui/common/BaseViewModel<S, I, E>` (state là `StateFlow`, effect là `Channel(BUFFERED)`, một cửa vào `onIntent`) và `ui/common/ObserveEffects` (nhận effect khi STARTED, trên `Dispatchers.Main.immediate`). Không có interface đánh dấu `UiState`/`UiIntent`/`UiEffect`: generic đã đủ, và luật một public class mỗi file sẽ tạo 3 file rỗng.

Cấu trúc package (spec mục 3, chi tiết ở `docs/architecture.md`): tầng là package, trong tầng chia theo feature.
- `domain/<feature>/`: model, repository interface, use case, logic thuần (parser, dedup, pairing, số dư).
- `data/<feature>/`: Room entity, DAO, mapper, repository impl.
- `ui/<feature>/`, `ui/common/`: Screen, Contract, ViewModel; theme và component dùng chung.
- `automation/` (listener, mỏng), `work/` (Worker, mỏng), `di/` (Hilt module).
- Feature ban đầu: `transaction`, `account`, `category`, `notification`.

Thêm vào catalog trên nhánh dùng chúng, không thêm trước:
- Room 3 (`androidx.room3:room3-runtime`, `room3-compiler` qua KSP), SQLCipher (`net.zetetic:sqlcipher-android`, từ 4.18 trở lên vì cần driver cho Room 3).
- WorkManager, Hilt-work.
- Robolectric, Turbine. (`junit` đã có trong catalog. `kotlinx-coroutines-test` thêm khi test đầu tiên cần.)
- Chỉ thêm version qua catalog. Không hardcode version trong `build.gradle.kts`.

DoD:
- `./gradlew.bat qualityGate` pass.
- `./gradlew.bat testDebugUnitTest` pass, có ít nhất một test.
- App mở được màn hình trống.

Quyết định đã chọn: Hilt (không dùng Koin). Lý do: `CLAUDE.md` ghi Hilt.

## 3. Phase 1 — Lõi dữ liệu và nhập tay (v0.1)

Mục tiêu: user nhập tay được, số dư luôn đúng, DB đã mã hóa.

Spec đã bổ sung ngày 2026-11-02 (các đoạn `[Bổ sung 2026-11-02]` trong mục 3, 4, 7): chiều của ADJUSTMENT (`adjustmentDirection`), phí trừ vào `accountId`, dấu số dư của thẻ tín dụng, giao dịch tách và xóa mềm không tính vào số dư, nội dung cột snapshot và ràng buộc liên tháng, `monthYear` theo múi giờ máy, luật xóa tài khoản và danh mục, `nameKey` cho danh mục hệ thống, làm tròn phép chia, D2.

Bảng Room tạo trong phase này (spec mục 3):
- `Account` (3.1), `Category` (3.2, phần cây 2 cấp), `Transaction` (3.3, bảng chính), `Tag` và `TransactionTagCrossRef` (3.4), `AccountBalanceSnapshot` (3.9).
- Lý do: mục 7 yêu cầu mọi thay đổi giao dịch cập nhật snapshot trong cùng transaction.
- Bảng còn lại tạo ở phase sau, qua migration.

Việc:
1. `feature/db-schema`: entity, DAO, `FinoteDatabase`, `exportSchema = true`.
   - DoD thêm: chạy luật `@Suppress` của Konsist với code KSP của Hilt và Room trong `build/generated/ksp/`, xác nhận không báo nhầm (spec mục 7).
   - Kiểm lại test DAO có cần Robolectric không. Room 3 dùng driver API, có thể chạy trên JVM thuần.
   - Trước v1.0 có thể tăng version và tạo lại DB. Sau v1.0 mọi thay đổi phải có migration và test migration.
2. `feature/db-encryption`: SQLCipher.
   - Sinh khóa 32 byte ngẫu nhiên.
   - Bọc khóa bằng Android Keystore (AES-GCM). Lưu bản đã bọc trong SharedPreferences.
   - Mở DB bằng driver SQLCipher cho Room 3 (`RoomDatabase.Builder.setDriver(...)`). Room 3 không còn `SupportOpenHelperFactory`.
   - D2 và D8 đã chốt (xem mục 9). Loại DB và file chứa khóa bọc khỏi Auto Backup (`backup_rules.xml`, `data_extraction_rules.xml`).
   - Máy dev đã có file DB không mã hóa từ `db-schema`. `fallbackToDestructiveMigration` không cứu được: nó chỉ chạy khi lệch version, còn file thường mở bằng khóa sẽ báo `file is not a database`. Cách xử lý: trước khi build DB, nếu file tồn tại và 16 byte đầu là `SQLite format 3\0` (header file không mã hóa) thì gọi `context.deleteDatabase(name)` (xóa cả `-wal`, `-shm`). Chỉ an toàn trước v1.0 vì chưa có dữ liệu người dùng thật. Có test cho nhánh này.
   - Nghiên cứu (2026-11-03), nguồn: [README](https://github.com/sqlcipher/sqlcipher-android), [`SQLCipherDriver.java`](https://github.com/sqlcipher/sqlcipher-android/blob/master/sqlcipher/src/main/java/net/zetetic/database/sqlcipher/driver/SQLCipherDriver.java):
     - Dependency: `net.zetetic:sqlcipher-android:4.20.0@aar`, cần `androidx.sqlite` 2.7.0 trở lên (catalog đang 2.7.1). Tối thiểu 4.19.1: bản 4.18–4.19.0 trả sai kiểu cột NULL ([#93](https://github.com/sqlcipher/sqlcipher-android/issues/93)).
     - Driver: `net.zetetic.database.sqlcipher.driver.SQLCipherDriver(passphrase: ByteArray, hook, errorHandler)`. Gọi `System.loadLibrary("sqlcipher")` một lần trước khi mở DB.
     - Khóa 32 byte ngẫu nhiên: truyền dạng raw key `"x'<64 hex>'"` để bỏ bước PBKDF2 (256k vòng) mỗi lần mở kết nối. Pool mở nhiều kết nối nên tiết kiệm đáng kể. Kiểm bằng test trên máy thật.
     - Log: thư viện mặc định ghi Logcat. Gọi `Logger.setTarget(NoopTarget())` ở mọi bản build, kể cả debug (luật Privacy).
     - Khóa sai không xóa DB: `DefaultDatabaseErrorHandler` bỏ qua xóa khi build có codec. Truyền `errorHandler = null` là được.
     - Lỗi đã biết, chưa sửa: ở WAL, đọc và transaction chỉ-đọc chiếm kết nối chính, chặn ghi ([#99](https://github.com/sqlcipher/sqlcipher-android/issues/99)). Ảnh hưởng nhỏ với Finote nếu truy vấn ngắn. Truy vấn danh sách lớn dùng phân trang.
     - Test: Robolectric không chạy được native SQLCipher, cũng không có provider `AndroidKeyStore`. Unit test (JVM): kiểm header file thường, logic sinh/bọc/mở khóa qua `Cipher` giả. Instrumented test: header file trên đĩa khác `SQLite format 3`, mở lại đúng khóa đọc được dữ liệu, khóa sai báo lỗi.
     - Backup: loại `finote.db`, `finote.db-wal`, `finote.db-shm` và file prefs chứa khóa bọc ở cả `<cloud-backup>` và `<device-transfer>`, và trong `backup_rules.xml` (API < 31). Khóa Keystore không đi theo backup, nên DB khôi phục sẽ không mở được.
     - D10 đã chốt: mất khóa Keystore thì xóa DB và khóa bọc cũ, sinh khóa mới, tạo DB rỗng. Có test cho các nhánh này.
       - Xóa ngay: không có alias Keystore, `AEADBadTagException`, `KeyPermanentlyInvalidatedException`, Base64 hỏng, khóa giải ra sai kích thước, blob ngắn hơn IV + tag, hoặc có file DB mà không có khóa bọc.
       - `UnrecoverableKeyException`: xóa sau 3 lần khởi động liên tiếp đều lỗi. Bộ đếm `unwrap_failures` nằm trong prefs `db_key`. Một lần mở khóa thành công đặt lại về 0.
       - Lỗi khác (Keystore bận hoặc đang khóa): app dừng, lần khởi động sau thử lại. Không xóa, không đếm.
3. `feature/balance-engine`: hàm thuần trong domain tính lại số dư:
   `currentBalance = initialBalance + thu − chi − phí + hoàn ± chuyển nội bộ + điều chỉnh`.
   - Dùng để kiểm tra chéo khi ghi DB.
   - Test: mọi loại giao dịch, ADJUSTMENT IN/OUT, phí của TRANSFER, thẻ tín dụng âm, bỏ qua `deletedAt` và `isSplitParent`, số dư đầu bằng 0.
   - Thêm hàm thuần dựng lại snapshot theo tháng. Kiểm ràng buộc `closing = opening + income − expense + transferIn − transferOut` và nối tháng.
4. `feature/transaction-crud`: thêm/sửa/xóa mềm giao dịch. Mỗi thao tác là một `@Transaction` cập nhật `currentBalance` và `AccountBalanceSnapshot`.
   - Sửa/xóa giao dịch ở tháng cũ, kể cả đổi timestamp sang tháng khác, phải tính lại snapshot của tháng đó và mọi tháng sau trong cùng `@Transaction`.
   - Test với Robolectric: thêm, sửa đổi số tiền, đổi tháng, xóa mềm, rollback khi lỗi giữa chừng.
   - Giới hạn trên cho vòng lặp tháng của `BalanceCalculator.monthlySnapshots`: nó chạy từ tháng giao dịch đầu tới `max(untilMonth, tháng cuối)`, nên một `timestamp` hỏng (ví dụ năm 1970 hay 9999) sinh hàng chục nghìn snapshot. Chặn `timestamp` ngoài khoảng hợp lệ khi ghi giao dịch, và đặt trần số tháng trong vòng lặp. Có test.
5. `feature/account-crud`: tạo, sửa, lưu trữ (`isArchived`). Đặt `initialBalance` và `isInitialized`.
   - Xóa mềm tài khoản có giao dịch sẽ chặn bằng validation, chỉ cho lưu trữ. Chỉ VND.
6. `feature/category-seed`: seed cây danh mục chuẩn quốc tế (`isSystemDefault = true`). `parentId` `onDelete = RESTRICT`. Danh mục hệ thống lưu `nameKey` để hiện theo 4 ngôn ngữ (D9). Tối đa 2 cấp, con cùng type với cha, chặn xóa mềm cha còn con, danh mục hệ thống chỉ ẩn.
   - UI quản lý cây 2 cấp: nhánh riêng `feature/category-ui` (Must-Have, mục 4).
7. `feature/design-system` (xong): token, `FinoteTheme`, `MoneyFormat`, bộ component dùng chung, luật Konsist, screenshot test trong `qualityGate`. Chi tiết ở `docs/design-system.md`.
8. `feature/cash-entry`: màn nhập tiền mặt có bàn phím số, phép tính `+ − × ÷`, chọn tài khoản và danh mục. Phép chia làm tròn nửa lên về đồng; số tiền cuối > 0.
   - `TileService` và `ShortcutManagerCompat` mở thẳng màn này (Must-Have).
   - Chốt 2026-11-04: sheet chỉ có Chi và Thu. Chuyển (`TRANSFER`) để sau.
   - Việc sau: thêm Chuyển vào sheet (tài khoản đích, phí). Lý do: rút ATM là cách tiền mặt vào ví chủ yếu. Ngân hàng chỉ báo trừ, ví tiền mặt không có thông báo, nên P2d không ghép được. Không có Chuyển thì user phải ghi Thu, làm lệch báo cáo thu chi. Nên làm cùng hoặc ngay trước P2d.
   - Việc sau: gắn sao tài khoản chính (`isPrimary`, kiểu Cashew). Làm cùng nhánh màn quản lý tài khoản. Thêm vào đầu chuỗi chọn tài khoản mặc định của sheet: gắn sao → dùng lần trước → "Tiền mặt" seed → đầu tiên theo `sortOrder`.
9. `feature/reconciliation` (xong): điều chỉnh số dư thực tế. Tạo giao dịch `ADJUSTMENT` với `adjustmentDirection` IN hoặc OUT theo chênh lệch.
10. `feature/reimbursement-flag` (xong): cờ `excludeFromAnalytics` và `reimbursementStatus` (Must-Have, mục 4). Chỉ lưu và lọc. Logic hoàn tiền (REFUND) ở P4.
11. `feature/transaction-list` (xong): danh sách giao dịch cơ bản, lọc theo tài khoản, sắp xếp theo `timestamp DESC`. Kèm thanh 4 tab. Thiết kế: `docs/superpowers/specs/2026-11-04-transaction-list-design.md`.

DoD:
- Test công thức số dư pass cho mọi loại giao dịch.
- Test rollback chứng minh `@Transaction` nguyên tử.
- Giao dịch có `deletedAt` không ảnh hưởng số dư và báo cáo.
- Nhập tiền mặt mở dưới 0,5 giây trên máy thật (kiểm ở P5, theo dõi từ đây).

Trạng thái (2026-11-04): P1 xong. Ba mục DoD đầu có test và pass trên `dev`. Mục đo 0,5 giây chưa đo, chờ P5. Còn mở: hai "việc sau" của mục 8 (Chuyển trong sheet, gắn sao tài khoản chính); `isSplitParent` chưa có trong model (P4).

## 4. Phase 2 — Automation Core (v0.2)

Mục tiêu: bắt giao dịch từ thông báo ngân hàng và ví mà không cần user nhập tay.

Đây là phần rủi ro lớn nhất. Chia thành 4 nhánh theo module của spec mục 2.2:

### P2a — `feature/notif-capture` (Module 1, 2, 3)
- `NotificationListenerService`.
- Lọc sớm theo `channelIdAllowlist` và `ignoreKeywords` (OTP, SmartOTP, khuyến mãi, cảnh báo đăng nhập, chúc mừng).
- Lấy `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES`.
- Bảng `NotificationLog` (3.7). `contentHash` SHA-256 của `packageName + postTime + content`, unique index.
- `onListenerConnected()` gọi `getActiveNotifications()`, bỏ qua hash đã có.
- Màn setup đầu tiên, một bước: xin quyền Notification Listener (đầy đủ ở P5). Banner nhắc trên danh sách giao dịch khi thiếu quyền.
- Tạo bảng `ParserRule` (3.8) ngay ở đây, chỉ cột lọc (`packageName`, `channelIdAllowlist`, `ignoreKeywords`). P2b thêm cột regex.
- Chỉ lưu `PENDING`, chưa bóc tách. Thông báo bị chặn lưu `IGNORED_SPAM`. Chỉ dòng chứa bí mật (`SECRET_KEYWORDS`: OTP, SmartOTP, mã xác thực) bị xóa nội dung; dòng quảng cáo và cảnh báo giữ nội dung để chặn nhầm còn lấy lại được.
- Test: lọc OTP và quảng cáo, không tạo bản ghi lỗi giả. Test quét lại không tạo trùng.
- Thiết kế: `docs/superpowers/specs/2026-11-07-notif-capture-design.md`.

Trạng thái (2026-11-08): code xong, squash vào `dev` (`1f03be2`). Chưa thử trên máy thật: để sau khi đủ chức năng P2, cần thêm màn debug (chỉ bản debug) xem `notification_logs`.

Nợ kỹ thuật từ review P2a (Minor, chưa sửa):

| # | Vấn đề | Chỗ | Sửa ở |
|---|---|---|---|
| 1 | Seeder đọc rule đang lưu trước `BEGIN IMMEDIATE`. Một ghi xen giữa có thể làm ghi đè rule vừa thành `isUserCustom`. Sửa: mở transaction trước rồi mới đọc | `data/notification/ParserRuleSeeder.kt` | P2b-1 (đã sửa) |
| 2 | JSON rule hỏng làm mapper ném lỗi, listener nuốt lỗi, mọi thông báo của package đó mất mà không báo. Sửa: `runCatching` trong mapper, rơi về `null` hoặc rỗng | `data/notification/ParserRuleMapper.kt` | P2b-1 (đã sửa) |
| 3 | Thông báo được đăng lại (cùng `sbn.key`, `postTime` mới) đổi hash, tạo thêm một dòng `PENDING` | `automation/FinoteNotificationListener.kt` | P2c (dedup phải tính trường hợp này) |
| 4 | Hash của dòng bí mật có thể dò ngược ra OTP 6 số (khoảng 10^6 lần thử). DB đã mã hóa và OTP hết hạn nên rủi ro thấp. Sửa: HMAC với khóa riêng mỗi máy | `domain/notification/NotificationText.kt` (`contentHash`) | P5 (bảo mật) |
| 5 | `SetupEffect.Finished` là tên sự kiện, không phải lệnh (`docs/architecture.md`). Đổi thành kiểu `CloseSetup` | `ui/setup/SetupContract.kt` | Khi sửa màn setup ở P5 |
| 6 | `MainActivity` inject thẳng `SetupRepository`, không qua use case, và đọc SharedPreferences trên main thread lúc khởi động. Sửa: `IsSetupDoneUseCase` | `MainActivity.kt` | P5 (`feature/perf`, đo cold start) |
| 7 | Tên ngân hàng viết cứng trong `setup_point_banks` ở 4 file strings, dễ lệch khi thêm rule. Spec 5.1 muốn lấy từ rule seed | `res/values*/strings.xml` | P6 (thêm ngân hàng mới) |
| 8 | `vcb_card` đọc "Số dư khả dụng" thành `postBalance`; nếu thẻ tín dụng VCB dùng cụm này cho hạn mức còn lại thì lưu nhầm hạn mức. Chưa kiểm được vì corpus chỉ có thẻ ghi nợ | `data/notification/SystemParserRule.kt` | Dogfooding P2b (cần mẫu thật) |
| 9 | Parse regex chạy trên `Dispatchers.Main` trong listener. Rule OTA hoặc rule tự sửa có backtracking thảm họa sẽ gây ANR | `automation/FinoteNotificationListener.kt` | P2b-3 (đã sửa: parse chạy trên dispatcher nền; regex ICU trên máy vẫn không ngắt được, ghi `shortcut:`, chặn bằng test an toàn trước khi ký feed) |

Thêm một điểm yếu đã biết: cả 4 ngân hàng dùng chung danh sách `IGNORE_KEYWORDS` trong `data/notification/SystemParserRule.kt`. Bảng đã hỗ trợ từ khóa riêng từng ngân hàng; tách khi viết regex từng ngân hàng ở P2b. Đã sửa ở P2b-1.

### P2b — `feature/parser-engine` (Module 4, Failed Inbox)
- Parser nằm ở `domain/notification`, không phụ thuộc `android.*`.
- Bảng `ParserRule` (3.8) và seed rule cho Vietcombank, MB Bank, MoMo.
- Named groups: `sign`, `amount`, `mask`, `balance`, `merchant`, `note`, `time`.
- Chuẩn hóa số tiền: bỏ dấu `.` hoặc `,` hàng nghìn.
- Chạy rule theo `priority`. Không khớp rule nào thì `status = FAILED`, lưu `failureReason`.
- Failed Inbox: màn hình liệt kê `FAILED`, user bấm chuyển thành giao dịch thủ công.
- OTA: WorkManager tải JSON rule từ GitHub/CDN. Không ghi đè rule có `isUserCustom = true`.
- Corpus test: ~60+ mẫu tổng hợp (mục D4), có cả dạng thu gọn (`EXTRA_TEXT`) và mở rộng (`EXTRA_BIG_TEXT`). Đạt 98% bóc tách đúng (mục 6).
- Sửa nợ P2a #1, #2, và tách `IGNORE_KEYWORDS` theo ngân hàng (xem bảng ở P2a).

Chốt 2026-11-08: chia P2b thành 3 nhánh, làm theo thứ tự (nhánh 3 làm sau P2c, xem ghi chú dưới):
1. `feature/parser-engine`: rule regex (VCB, MB, MoMo, Techcombank), parser, cổng corpus, nợ #1 #2. Bóc được thì điền `parsed*` và giữ `PENDING` (P2c tạo giao dịch); không khớp thì `FAILED`. Thiết kế: `docs/superpowers/specs/2026-11-08-parser-engine-design.md`.
2. `feature/rule-ota` (đổi thứ tự 2026-11-08, Must-Have và sửa nợ #9): OTA rule bằng WorkManager, bóc lại các dòng `FAILED` sau khi có rule mới. Mặc định tắt, user tự bật; file JSON có chữ ký ECDSA. Thiết kế: `docs/superpowers/specs/2026-11-08-rule-ota-design.md`.
3. `feature/failed-inbox`: màn Failed Inbox, kèm bộ dạy mẫu cục bộ (Regex Sandbox cho người phổ thông): user mở một dòng `FAILED`, bôi đen số tiền, số dư, 4 số cuối, nội dung; app sinh regex `isUserCustom = true` rồi gọi `ReparseFailedLogsUseCase`. Cho user không muốn bật mạng. Cần bàn thêm: ngân hàng chưa có rule bị `NotTracked` nên không có dòng `FAILED` để dạy.

Trạng thái (2026-11-08): `parser-engine` xong, đã ở `dev` (`29a94e2`). 12 rule regex. Corpus: VCB, MB, MoMo đúng 100%; Techcombank bóc 8/8.

Trạng thái (2026-11-08): `rule-ota` xong, đã ở `dev` (`2956fae`).

Đổi thứ tự 2026-11-08: làm P2c trước `failed-inbox`. Lý do: bóc lại sau khi dạy mẫu chỉ ra dòng `PENDING`; không có P2c (tạo giao dịch, đối ánh tài khoản) thì user không thấy kết quả, và `failed-inbox` phải tự viết lại phần đó.
- Feed `rules/v1/feed.json` sinh từ `SystemParserRules`, ký ECDSA P-256. Khóa riêng ở `~/.finote/finote-rules.pem` (ngoài repo, user tự sao lưu). Ký lại: `testDebugUnitTest --tests '*RuleFeedSigningTool*' -PsignRuleFeed=true`.
- Feed phải lên `main` cùng hoặc trước bản phát hành đầu có OTA; trước đó "Kiểm tra ngay" báo "không có mạng" (404).
- Nợ mới (follow-up):
  - Rule `isActive = false` vẫn lọc và vẫn tính để quyết định `NotTracked` (hành vi có từ P2b-1; OTA giờ chạm tới được).
  - `HttpParserRuleFeed` đọc chặn trên dispatcher của worker; nên dùng IO dispatcher.
  - Kiểm `UnstyledSwitch` với `onCheckedChange = null` không đọc trạng thái hai lần trên TalkBack (thử trên máy).

### P2c — `feature/account-dedup-balance` (Module 5, 6, 8)
- Dedup nằm ở `domain/notification`, không phụ thuộc `android.*`.
- Đối ánh tài khoản theo `bankPackageName` + `accountNumberMask` (4 số cuối).
- Khử trùng: cùng số tiền, cùng chiều, cùng merchant rút gọn, trong 30 giây. Dùng cho ví có liên kết ngân hàng.
- Cold-start: tài khoản `isInitialized = false` và có `postBalance` thì tính ngược `initialBalance` và gợi ý user xác nhận.
- Test: ví và ngân hàng cùng gửi một biến động, chỉ tạo một giao dịch.
- Xử lý nợ P2a #3: thông báo đăng lại tạo dòng `PENDING` trùng.
- Thiết kế: `docs/superpowers/specs/2026-11-08-account-dedup-balance-design.md`. Chốt 2026-11-08: tự tạo tài khoản khi không khớp, chờ cửa sổ 30 giây rồi mới ghi, tự đặt số dư đầu, banner lệch số dư kèm nút Điều chỉnh.
- Ngay sau P2c: `feature/account-merge-ignore` (gộp tài khoản, bỏ qua tài khoản), để user sửa khi app tự gán sai. Thiết kế: `docs/superpowers/specs/2026-11-09-account-merge-ignore-design.md`. Chốt 2026-11-09: chỉ gộp cùng app ngân hàng, gộp không hoàn tác, bỏ qua thì bật lại được.
- Tương lai (sau `account-merge-ignore`):
  - Gợi ý gộp: banner trên màn giao dịch khi tài khoản tự tạo trông giống tài khoản cũ cùng ngân hàng. Chờ dữ liệu thật trên máy.
  - Màn thêm tài khoản tay (kiểu Cashew): có ô tuỳ chọn "liên kết app ngân hàng + 4 số cuối", và cho gộp vào tài khoản tay chưa gắn ngân hàng. Không có thì thông báo sẽ tạo tài khoản trùng.

Trạng thái (2026-11-08): code xong trên `feature/account-dedup-balance`, review từng task và review cả nhánh đã chạy. Nợ P2a #3 đã sửa (ngưỡng đăng lại 10 phút khi có số dư, 3 giây khi không). Chưa thử trên máy thật.

Nợ còn lại (Minor, chưa sửa):
- `MainActivity` gọi `settleNow()` mỗi lần `onCreate`, kể cả xoay màn hình. Vô hại vì REPLACE và settle chạy lại an toàn.
- Luồng drift trong `TransactionListViewModel` dừng hẳn nếu một tài khoản ném lỗi (`.catch` một lần); `adjust()` chưa bắt `ArithmeticException`.
- Nhiều banner lệch số dư đẩy danh sách xuống trên màn nhỏ.
- Chuỗi ja và zh-rCN do máy viết, cần người bản ngữ xem trước khi phát hành.
- Rule hệ thống vẫn `version = 1`: khi có migration thật (sau v1.0), tăng version để rule cũ nhận `accountType`.
- Một `New` có mask và một `New` mask null cùng gói trong một lần chạy tạo hai tài khoản. Hiếm; từ `account-merge-ignore` user gộp được bằng tay (Cài đặt → Tài khoản).

Trạng thái (2026-11-09): `account-merge-ignore` code xong, review từng task và review cả nhánh đã chạy, `qualityGate` xanh. Chưa thử trên máy thật (sheet và dialog mới chỉ thấy trong screenshot).
- DAO chia theo nghiệp vụ: gắn nhãn chuyển sang `TagDao`, query gộp ở `AccountMergeDao`.
- Nợ cho P2d: khi tính số dư đầu (gộp và cold start), chuyển khoản mà tài khoản chỉ là bên nhận có thể bị lấy làm mốc `postBalance` (số dư của bên kia). Chưa có dòng nào như vậy trước P2d. Sửa cả hai chỗ: chỉ lấy dòng có `accountId` = tài khoản.
- Nợ nhỏ: màn Tài khoản bắt mọi lỗi thành snackbar "không thành công" mà không ghi nguyên nhân; sheet không tự cập nhật nếu danh sách đổi khi đang mở.

### P2d — `feature/transfer-pairing` (Module 7)
- Pairing nằm ở `domain/notification`, không phụ thuộc `android.*`.
- Hai biến động cùng số tiền, một trừ (tài khoản A) và một cộng (tài khoản B), trong 1–2 phút → gộp thành `TRANSFER`, `status = PAIRED_TRANSFER`.
- Cần thiết kế: biến động thứ hai đến muộn. Lưu trạng thái chờ ghép, không tự tạo hai giao dịch lệch.
- Test: ghép đúng, không ghép khi số tiền khác, timeout thì giữ hai giao dịch riêng.

DoD (spec mục 6):
- Bóc tách đúng trên 98% mẫu của Vietcombank, MB và MoMo.
- Chặn 100% OTP, cảnh báo đăng nhập, quảng cáo trong bộ test.
- Quét `getActiveNotifications()` khi service kết nối lại.
- Đối ánh 4 số cuối và dedup 30 giây đạt 100% trong bộ test.
- Ghép chuyển khoản trong 1–2 phút.

## 5. Phase 3 — Báo cáo và tìm kiếm (v0.3)

- `feature/dashboard`: thu, chi, số dư ròng theo tháng. Biểu đồ phân bổ theo danh mục và merchant (Vico + Compose Canvas). Should-Have.
- `feature/merchant-normalize`: bảng `CategoryKeywordRule` (3.8). Tự gán danh mục và chuẩn hóa tên cửa hàng (Grab, Shopee, Highlands). Should-Have.
- `feature/fts-search`: bảng `TransactionFts` (3.3) với bộ tách unicode61 trên `merchantName` và `note`. Lọc kết hợp: khoảng thời gian, tài khoản, danh mục, nhãn. Should-Have.
- `feature/net-worth`: biểu đồ tài sản ròng từ `AccountBalanceSnapshot`. Loại trừ `excludeFromNetWorth`. Should-Have.
- `feature/budget`: bảng `Budget` (3.5). Hạn mức theo danh mục hoặc tổng. Kết chuyển `carryOverAmount` khi `isRollover`. Cảnh báo tại `alertThreshold` và 100%, dùng `lastAlertSentThreshold` để không lặp. Should-Have.
- `feature/tags`: UI gắn nhãn và lọc theo nhãn. Should-Have.

DoD (spec mục 8):
- Tìm kiếm dưới 50 ms với 50.000 giao dịch.
- Biểu đồ tài sản ròng dưới 16 ms với 50.000 giao dịch.
- Lưu ý: hai chỉ số này cần đo trên máy thật, nên cần instrumented test hoặc benchmark do user chạy (mục D7).

## 6. Phase 4 — Nghiệp vụ nâng cao và dữ liệu (v0.4)

- `feature/refund`: giao dịch `REFUND`, `refundForTransactionId`. Giảm trừ vào tổng chi của danh mục gốc, không cộng vào thu nhập (mục 7). Should-Have.
- `feature/credit-card`: `creditLimit`, `statementDay`, `dueDay`, luồng chuyển khoản tất toán dư nợ. Should-Have.
- `feature/recurring-plan`: bảng `RecurringPlan` (3.6). WorkManager sinh giao dịch khi `autoCreate`, hoặc gửi nhắc khi không. Should-Have.
- `feature/import-csv`: bảng `ImportProfile` (3.10). Import CSV trước. Các cột: ngày, số tiền, loại, danh mục, ghi chú, số tài khoản. Should-Have.
- `feature/import-xlsx`: thêm XLSX sau khi CSV ổn định (mục D5).
- `feature/export`: xuất `.json` mã hóa và `.xlsx`. Should-Have.
- `feature/trash-purge`: WorkManager xóa cứng mục có `deletedAt` cũ hơn 30 ngày. Đây là nơi duy nhất được xóa cứng. Should-Have.
- `feature/regex-sandbox`: màn thử regex trên máy để user tự sửa rule (`isUserCustom = true`). Should-Have.

## 7. Phase 5 — Hoàn thiện production (v1.0)

- `feature/app-lock`: `BiometricPrompt`, nút ẩn số dư (`***`), `FLAG_SECURE`, làm mờ khi vào Recent Apps (mục 9). Must-Have.
- `feature/perf`: đo cold start dưới 1,2 giây bằng Macrobenchmark. Đo mở nhập tiền mặt dưới 0,5 giây từ Tile và Shortcut.
- `feature/onboarding-permission`: hướng dẫn Android 13+ (Allow Restricted Settings) và tắt tối ưu pin trên ROM tùy biến. Could-Have, nhưng cần để P2 dùng được trên máy thật. Thêm bước vào màn setup có sẵn từ P2a, không làm màn mới.
- Trang chính sách quyền riêng tư và link trong màn setup. Bắt buộc trước khi lên Google Play (prominent disclosure).
- Sửa nợ P2a #4 (HMAC cho hash), #5 (tên effect của setup), #6 (`IsSetupDoneUseCase`, đọc prefs ngoài main thread). Xem bảng ở P2a.
- `chore/audit`: test đối soát đầy đủ. Công thức số dư khớp mọi tài khoản, mọi thời điểm (mục 7).
- `release/1.0.0`: theo release flow.

DoD: đạt toàn bộ mục 5–9 của spec.

## 8. Phase 6 — Sau v1.0 (Could-Have)

- Sao lưu Google Drive cá nhân, file mã hóa trong thư mục ẩn AppData (Cao). Cần thiết kế riêng vì liên quan quyền truy cập Drive.
- Thêm mẫu regex cho Techcombank, ACB, VPBank, TPBank, BIDV, VietinBank, ZaloPay, ShopeePay.
- Tách giao dịch (`isSplitParent`) và ngoại tệ (`originalAmount`, `exchangeRate`).
- Đính kèm ảnh hóa đơn, nén và lưu cục bộ.

Won't-Have (không làm trong MVP): OCR hóa đơn bằng ML Kit (v1.2), sổ nợ và chia tiền nhóm, đồng bộ đa thiết bị, quản lý chứng khoán, vàng, crypto.

## 9. Quyết định cần user chốt

| Mã | Câu hỏi | Đề xuất | Cần chốt trước |
|---|---|---|---|
| D1 | FTS4 hay FTS5? Room annotation chỉ hỗ trợ FTS4. FTS5 cần SQL thô trong migration. Chưa kiểm Room 3 còn `@Fts4` không. | FTS4 (đơn giản, đủ cho mục tiêu 50 ms) | P3 |
| D2 | Khóa SQLCipher lưu ở đâu? | **Đã chốt (2026-11-02):** 32 byte ngẫu nhiên, bọc bằng Android Keystore (AES-GCM), không bắt sinh trắc học (listener ghi DB khi máy khóa). DB và khóa bọc bị loại khỏi Auto Backup | P1 |
| D3 | `targetSdk`: spec ghi 35, build đang 37. `CLAUDE.md` ghi 37 là có chủ đích. | **Đã chốt:** giữ 37. Ghi chú này là lý do, spec không sửa | P0 |
| D4 | Mẫu thông báo thật để đạt 98%? Không có mẫu thì không đo được. | **Đã chốt (2026-11-06):** Dùng Synthetic Corpus (~60 mẫu tổng hợp của VCB, MB, MoMo, Techcombank + OTP/Spam) làm baseline. Người dùng bổ sung mẫu thực tế qua Failed Inbox khi dogfooding. 98% trên corpus tổng hợp chỉ là baseline; chỉ tiêu thật đo trên mẫu dogfooding. Techcombank là phần thêm, chỉ tiêu 98% chỉ tính VCB, MB, MoMo (spec L407) | P2b |
| D5 | Import XLSX có cần ngay ở P4? | CSV trước, XLSX sau | P4 |
| D6 | DI: Hilt hay Koin? | **Đã chốt:** Hilt (theo `CLAUDE.md`) | P0 |
| D7 | Ai chạy benchmark và instrumented test trên máy thật? | User chạy khi tới P3 và P5, theo yêu cầu | P3 |
| D8 | Room 2.x hay Room 3? `navigation-compose` hay Navigation3? | **Đã chốt (2026-11-02):** Room 3 (stable 3.0.x từ 07/2026, chỉ KSP, driver API) và Navigation3 (stable 1.2.0). Chưa có code dùng nên không tốn chi phí chuyển. Rủi ro: driver SQLCipher cho Room 3 mới có từ 08/2026 | P0 |
| D9 | Đa ngôn ngữ: spec gốc không ghi, user bổ sung 2026-11-02. Hỗ trợ ngôn ngữ nào? | **Đã chốt (2026-11-02):** `vi` (mặc định, `values/`), `en` (`values-en`), tiếng Trung giản thể (`values-zh-rCN`), tiếng Nhật (`values-ja`). Màn chọn ngôn ngữ trong app: chưa làm | Trước màn đầu tiên có chữ |
| D10 | Mất khóa Keystore (xóa dữ liệu bảo mật, khôi phục backup sang máy khác) thì DB SQLCipher không giải mã được. Xóa và tạo DB mới, hay báo lỗi và giữ file? | **Đã chốt (2026-11-03):** xóa DB (cả `-wal`, `-shm`) và khóa bọc cũ, sinh khóa mới, tạo DB rỗng. Mất rõ ràng (không có alias, `AEADBadTagException`, `KeyPermanentlyInvalidatedException`, Base64 hỏng, sai kích thước, blob ngắn) thì xóa ngay. `UnrecoverableKeyException` thì xóa sau 3 lần khởi động liên tiếp lỗi. Lỗi khác thì app dừng, thử lại lần sau, không xóa. Không ghi nội dung DB hay khóa vào Logcat | Trước `db-encryption` |

## 10. Thứ tự làm việc ngay

1. D3, D6, D8 đã chốt.
2. P0: `chore/gradle-catalog`, `chore/quality-gate`, `feature/app-shell`, `feature/mvi-base` đã xong.
3. D2 đã chốt. Spec P1 đã bổ sung ngày 2026-11-02.
4. P1 đã xong (2026-11-04): `db-schema` → `db-encryption` → `balance-engine` → `transaction-crud` → `account-crud` → `category-seed` → `design-system` → `cash-entry` → `reconciliation` → `transaction-list` → `reimbursement-flag`.
5. D4 đã chốt (2026-11-06). P1 đã xong. Bắt đầu P2: `notif-capture` (P2a) → `parser-engine`, `rule-ota` (P2b-1, P2b-2) → `account-dedup-balance` (P2c) → `failed-inbox` (P2b-3) → `transfer-pairing` (P2d).
