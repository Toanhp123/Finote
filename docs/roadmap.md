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
9. `feature/reconciliation`: điều chỉnh số dư thực tế. Tạo giao dịch `ADJUSTMENT` với `adjustmentDirection` IN hoặc OUT theo chênh lệch.
10. `feature/reimbursement-flag`: cờ `excludeFromAnalytics` và `reimbursementStatus` (Must-Have, mục 4). Chỉ lưu và lọc. Logic hoàn tiền (REFUND) ở P4.
11. Danh sách giao dịch cơ bản, lọc theo tài khoản, sắp xếp theo `timestamp DESC`.

DoD:
- Test công thức số dư pass cho mọi loại giao dịch.
- Test rollback chứng minh `@Transaction` nguyên tử.
- Giao dịch có `deletedAt` không ảnh hưởng số dư và báo cáo.
- Nhập tiền mặt mở dưới 0,5 giây trên máy thật (kiểm ở P5, theo dõi từ đây).

## 4. Phase 2 — Automation Core (v0.2)

Mục tiêu: bắt giao dịch từ thông báo ngân hàng và ví mà không cần user nhập tay.

Đây là phần rủi ro lớn nhất. Chia thành 4 nhánh theo module của spec mục 2.2:

### P2a — `feature/notif-capture` (Module 1, 2, 3)
- `NotificationListenerService`.
- Lọc sớm theo `channelIdAllowlist` và `ignoreKeywords` (OTP, SmartOTP, khuyến mãi, cảnh báo đăng nhập, chúc mừng).
- Lấy `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES`.
- Bảng `NotificationLog` (3.7). `contentHash` SHA-256 của `packageName + postTime + content`, unique index.
- `onListenerConnected()` gọi `getActiveNotifications()`, bỏ qua hash đã có.
- Màn hướng dẫn cấp quyền Notification Listener (mức tối thiểu; đầy đủ ở P5).
- Test: lọc OTP và quảng cáo, không tạo bản ghi lỗi giả. Test quét lại không tạo trùng.

### P2b — `feature/parser-engine` (Module 4, Failed Inbox)
- Parser nằm ở `domain/notification`, không phụ thuộc `android.*`.
- Bảng `ParserRule` (3.8) và seed rule cho Vietcombank, MB Bank, MoMo.
- Named groups: `sign`, `amount`, `mask`, `balance`, `merchant`, `note`, `time`.
- Chuẩn hóa số tiền: bỏ dấu `.` hoặc `,` hàng nghìn.
- Chạy rule theo `priority`. Không khớp rule nào thì `status = FAILED`, lưu `failureReason`.
- Failed Inbox: màn hình liệt kê `FAILED`, user bấm chuyển thành giao dịch thủ công.
- OTA: WorkManager tải JSON rule từ GitHub/CDN. Không ghi đè rule có `isUserCustom = true`.
- Corpus test: 30–50 mẫu thông báo đã ẩn số (mục D4). Đạt 98% bóc tách đúng (mục 6).

### P2c — `feature/account-dedup-balance` (Module 5, 6, 8)
- Dedup nằm ở `domain/notification`, không phụ thuộc `android.*`.
- Đối ánh tài khoản theo `bankPackageName` + `accountNumberMask` (4 số cuối).
- Khử trùng: cùng số tiền, cùng chiều, cùng merchant rút gọn, trong 30 giây. Dùng cho ví có liên kết ngân hàng.
- Cold-start: tài khoản `isInitialized = false` và có `postBalance` thì tính ngược `initialBalance` và gợi ý user xác nhận.
- Test: ví và ngân hàng cùng gửi một biến động, chỉ tạo một giao dịch.

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
- `feature/onboarding-permission`: hướng dẫn Android 13+ (Allow Restricted Settings) và tắt tối ưu pin trên ROM tùy biến. Could-Have, nhưng cần để P2 dùng được trên máy thật.
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
| D4 | Mẫu thông báo thật để đạt 98%? Không có mẫu thì không đo được. | User cung cấp mẫu đã ẩn số. Nếu không có, mình tự tạo mẫu tổng hợp và ghi rõ là tổng hợp | P2b |
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
4. P1 theo thứ tự: `db-schema` → `db-encryption` → `balance-engine` → `transaction-crud` → `account-crud` → `category-seed` → `design-system` → `cash-entry` → còn lại.
5. P2 chỉ bắt đầu khi P1 xong và D4 đã chốt.
