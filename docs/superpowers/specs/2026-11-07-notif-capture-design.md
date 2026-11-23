# Thiết kế `feature/notif-capture`

Ngày: 2026-11-07. Phase: P2a (roadmap mục 4). Nguồn: spec mục 2.2 Module 1–3 (dòng 33–40), mục 3.7 (dòng 166–183), mục 3.8 (dòng 184–195), mục 6 (dòng 408); `docs/architecture.md`; `docs/design-system.md`.

## 1. Mục tiêu

Bắt thông báo của app ngân hàng và ví, lọc rác sớm, lưu vào `NotificationLog` với `status = PENDING` để P2b bóc tách. Thêm màn setup xin quyền Notification Listener và banner nhắc khi thiếu quyền.

Đã chốt với user:
- Bảng `ParserRule` tạo ngay ở P2a, chỉ với các cột cần cho lọc. P2b thêm cột regex. Một nguồn duy nhất cho package và từ khóa chặn.
- Thông báo bị chặn vẫn ghi một dòng `IGNORED_SPAM`. Từ khóa chia hai loại. Loại bí mật (OTP, mã xác thực) là hằng số trong domain, áp cho mọi package: dòng chỉ có metadata và hash, không có `title`/`content`, `failureReason = secret:<từ>`. Loại spam/quảng cáo/cảnh báo là dữ liệu của rule: dòng GIỮ `title`/`content`, `failureReason = keyword:<từ>`, để một lần chặn nhầm vẫn cứu được. Chặn theo kênh cũng giữ nội dung (`channel:<id>`). Chỉ bí mật mới bị bỏ nội dung.
- P2a không bóc tách. Thông báo qua lọc chỉ lưu `PENDING`.
- Listener gọi use case trực tiếp, không qua WorkManager. Xử lý tuần tự. WorkManager để P2b (OTA) và P2d (hết hạn chờ ghép).
- `channelIdAllowlist` seed là `null` (không lọc kênh). `channelId` trong corpus D4 là giả. Điền giá trị thật khi dogfooding thấy trong `NotificationLog`.
- Màn setup đầu tiên một bước. P5 thêm bước Restricted Settings và tối ưu pin vào cùng màn này.
- Banner nhắc trên danh sách giao dịch khi thiếu quyền, làm luôn ở P2a.

Ngoài phạm vi:
- Parser, regex, Failed Inbox, OTA (P2b).
- Đối ánh tài khoản, dedup, cold-start (P2c). Ghép chuyển khoản (P2d).
- Hướng dẫn Restricted Settings đầy đủ, tắt tối ưu pin (P5). Trang chính sách quyền riêng tư (P5, trước khi lên Play).
- Dọn `NotificationLog` cũ (để P4 `trash-purge` quyết).

## 2. Dữ liệu (`data/notification/`)

### 2.1 Bảng `parser_rules`

Cột (tập con của spec 3.8):

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `String` PK | ví dụ `vcb_filter_v1` |
| `packageName` | `String` | index |
| `bankName` | `String` | |
| `channelIdAllowlist` | `String?` | JSON mảng chuỗi. `null` = không lọc kênh |
| `ignoreKeywords` | `String` | JSON mảng chuỗi |
| `priority` | `Int` | |
| `isUserCustom` | `Boolean` | seed = `false` |
| `version` | `Int` | seed = `2` |

P2b thêm `regexPattern`, `fallbackTransactionType`, `dateFormatPattern` qua tăng version DB.

JSON chỉ tồn tại ở tầng data. Mapper trong `data/notification/` giải mã bằng `kotlinx-serialization-json` (đã có trong `app/build.gradle.kts`) thành `List<String>`. Mảng rỗng `[]` của `channelIdAllowlist` chuẩn hóa thành `null`, để không vô tình chặn cả ngân hàng. Domain không thấy chuỗi JSON.

### 2.2 Seed (`ParserRuleSeeder`)

Theo mẫu `SystemCategorySeeder` về chỗ chạy: `onCreate`, và `onOpen` trong `BEGIN IMMEDIATE`. Khác ở điều kiện ghi: không chỉ seed khi bảng rỗng, mà so từng rule seed với DB:
- Chưa có `id` trong DB → insert.
- Có, `isUserCustom = false`, và `version` trong DB nhỏ hơn seed → ghi đè.
- Có, `isUserCustom = true`, hoặc `version` bằng hay lớn hơn → giữ nguyên.

Nhờ vậy sửa từ khóa hay điền `channelIdAllowlist` thật khi dogfooding chỉ cần tăng `version` của rule seed, không phải xóa DB. Luật này khớp với luật OTA trong `CLAUDE.md`: không ghi đè rule `isUserCustom = true`.

Bốn rule, `channelIdAllowlist = null`:

| `id` | `packageName` | `bankName` |
|---|---|---|
| `vcb_filter_v1` | `com.VCB` | Vietcombank |
| `mb_filter_v1` | `com.mbmobile` | MB Bank |
| `momo_filter_v1` | `com.mservice.momotransfer` | MoMo |
| `tcb_filter_v1` | `vn.com.techcombank.bb.app` | Techcombank |

`ignoreKeywords` của rule seed (version 2) là cụm từ spam/quảng cáo/cảnh báo rút từ các mẫu `shouldIgnore = true` của `app/src/test/resources/fixtures/notification_corpus.json`. OTP, SmartOTP, mã xác thực không nằm ở đây mà là `SECRET_KEYWORDS` (mục 3.1). Không có từ rộng đứng một mình ("thiết bị", "cảnh báo", "voucher", "chúc mừng", "sinh lời", "phê duyệt", "đổi quà"), và không có "khuyến mãi" vì "hoàn tiền khuyến mãi" của MoMo là tiền vào thật. Vì dòng bị chặn giữ nội dung và người trả tiền gõ ghi chú tự do, mỗi cụm phải khó xuất hiện trong ghi chú chuyển khoản. Danh sách cuối cùng: ưu đãi đặc quyền, ưu đãi sinh nhật, cảnh báo bảo mật, đăng nhập trên thiết bị, liên kết thành công, chúc mừng sinh nhật quý khách, chúc mừng quý khách, momo tặng bạn, nhận gói voucher, nhắc nợ, nhắc hạn thanh toán, nhanh tay mở, mở ngay trên app, phê duyệt hạn mức, rinh quà, đổi quà ngay, đổi quà hấp dẫn, sinh lời mỗi ngày. Test corpus là trọng tài: chặn 100% mẫu rác, 0% mẫu giao dịch, cộng các ghi chú giao dịch chứa từ giống spam ("Thiet bi Y te", "Canh Bao Store", "chuc mung sinh nhat em", "phe duyet ho so") phải ở lại `PENDING`.

### 2.3 Bảng `notification_logs`

Đủ cột spec 3.7: `id` (UUID), `packageName` (index), `channelId?`, `title`, `content`, `contentHash` (unique index), `postTime`, `receivedAt`, `matchedRuleId?`, `parsedAmount?`, `parsedType?`, `parsedAccountMask?`, `parsedPostBalance?`, `parsedMerchant?`, `status`, `failureReason?`, `linkedTransactionId?`.

- `status`: enum `PENDING, CONFIRMED, DEDUPLICATED, PAIRED_TRANSFER, IGNORED_SPAM, FAILED`, lưu dạng chuỗi.
- Dòng `IGNORED_SPAM` do từ khóa bí mật: `title = ""`, `content = ""`. Dòng `IGNORED_SPAM` do từ khóa rule hay kênh giữ đủ `title`, `content`.
- Ở P2a, `matchedRuleId`, `parsed*`, `linkedTransactionId` luôn `null`.
- Không có `deletedAt`: đây là nhật ký hệ thống, không phải dữ liệu user sửa.
- DAO: `insert` với `OnConflictStrategy.IGNORE`, trả rowId để biết có trùng không (`-1` = trùng).

### 2.4 Database

`FinoteDatabase` version 3 → 4, thêm hai entity. Trước v1.0 dùng đường destructive hiện có, không viết migration.

### 2.5 Cờ setup

`SharedPreferences` tên `app_setup`, khóa `setup_done: Boolean`. Không thêm DataStore cho một cờ.

## 3. Domain (`domain/notification/`)

Không phụ thuộc `android.*`.

- `RawNotification(packageName: String, channelId: String?, title: String, content: String, postTime: Long)`.
- `ParserRule` (domain): `id`, `packageName`, `channelIdAllowlist: List<String>?`, `ignoreKeywords: List<String>`, `priority`, `isUserCustom`, `version`.
- `CaptureResult`: `Stored`, `Ignored(reason: String)`, `Duplicate`, `NotTracked`.
- `NotificationCaptureRepository` (interface): đọc rule theo package (sắp theo `priority`, rồi `id`), ghi log.

### 3.1 `CaptureNotificationUseCase(raw): CaptureResult`

Đầu tiên tính `contentHash` từ `raw` gốc (xem dưới). Hash luôn tính trước khi xóa trắng `title`/`content` của dòng bí mật.

1. Lấy mọi rule của `raw.packageName`. Không có → `NotTracked`, không ghi gì.
2. **Bí mật trước hết, cho mọi package có rule**: `SECRET_KEYWORDS = ["OTP", "SmartOTP", "mã xác thực"]` (hằng số domain, không phụ thuộc rule) khớp trong `title + "\n" + content` → `Ignored("secret:<từ khóa gốc>")`, ghi dòng `IGNORED_SPAM` đã xóa trắng `title`/`content`. Bước này chạy trước lọc kênh và từ khóa rule.
3. Lọc kênh chỉ bật khi **mọi** rule của package có `channelIdAllowlist != null`. Một rule `null` nghĩa là nhận mọi kênh, nên tắt lọc kênh cho cả package. Khi bật: `raw.channelId` không nằm trong hợp các allowlist → `Ignored("channel:<id>")`; `raw.channelId == null` → `Ignored("channel:null")`.
4. Gộp `ignoreKeywords` theo thứ tự rule (`priority`, rồi `id`), rồi thứ tự trong danh sách, bỏ trùng giữ lần đầu (`LinkedHashSet`). Từ khóa đầu tiên khớp → `Ignored("keyword:<từ khóa gốc>")`. Thứ tự cố định nên `failureReason` tất định.
5. Còn lại → ghi `PENDING` với đủ `title`, `content`.

Bước 2 ghi dòng `IGNORED_SPAM` không có nội dung. Bước 3 và 4 ghi dòng `IGNORED_SPAM` vẫn giữ `title`, `content`, và `failureReason`. Insert trả trùng → `Duplicate`.

**Chuẩn hóa văn bản** (hàm thuần `normalizeForMatch`, dùng cho cả văn bản và từ khóa):
`Normalizer.normalize(s, NFD)` → xóa `\p{InCombiningDiacriticalMarks}+` → `đ`/`Đ` → `d` → chữ thường (`Locale.ROOT`). Bước NFD bắt cả app gửi tiếng Việt dạng tổ hợp.

**Khớp từ khóa theo ranh giới từ**, không dùng `contains` trần. Văn bản là `title + "\n" + content` đã chuẩn hóa. Từ khóa (đã chuẩn hóa, có thể nhiều từ như `ma xac thuc`) khớp khi bên trái không phải chữ hay số và bên phải không phải chữ: `(?<![\p{L}\p{N}])<Pattern.quote(keyword)>(?!\p{L})`. Bên phải cho phép chữ số để bắt `OTP123456`; `SmartOTP` dính liền là từ khóa riêng vì bên trái vẫn chặn chữ. Lý do: `otp` nằm giữa `hotpot`, `uu dai` nằm giữa `luu dai`. Regex của từng từ khóa biên dịch một lần mỗi lần đọc rule.

**`contentHash`** = SHA-256 hex chữ thường của `packageName + "\u0000" + postTime + "\u0000" + title + "\u0000" + content`. Có `title` vì vài app để số tiền ở tiêu đề. Ký tự ngăn cách tránh trùng do nối chuỗi.

- `receivedAt` lấy từ `Clock` tiêm vào.

## 4. Automation (`automation/FinoteNotificationListener`)

- `NotificationListenerService`, `@AndroidEntryPoint`. Khai báo trong manifest với `BIND_NOTIFICATION_LISTENER_SERVICE`.
- Scope: `CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler)`, hủy ở `onDestroy`. Mỗi lời gọi use case nằm trong một `Mutex` công bằng (FIFO), nên mọi thông báo đi qua một hàng tuần tự. Dùng `Main` vì các hàm `suspend` của Room tự rời luồng chính, và detekt `InjectDispatcher` chặn `Dispatchers.IO`.
- `onNotificationPosted(sbn)` → dựng `RawNotification` → use case.
- `onListenerConnected()` → `activeNotifications` → lần lượt qua cùng use case. Hash trùng thì bị bỏ. Kết quả `null` coi như rỗng. Bọc `try/catch` quanh cả lời gọi `activeNotifications` (một số ROM ném `SecurityException` hay `RuntimeException` khi service vừa ngắt), không chỉ quanh từng thông báo.
- Bỏ qua trước khi dựng `RawNotification`: thông báo của chính Finote; `FLAG_GROUP_SUMMARY`; `sbn.isOngoing`; `FLAG_FOREGROUND_SERVICE`. Thông báo đang chạy (cuộc gọi, tải, trạng thái) cập nhật liên tục và không phải biến động số dư.
- Đọc extras bằng `getCharSequence` và `getCharSequenceArray`, không dùng `getString`: app ngân hàng hay gửi `SpannableString` (in đậm số tiền), `getString` trả `null`.
- Ghép nội dung bằng hàm thuần `notificationContent(bigText: CharSequence?, textLines: Array<CharSequence>?, text: CharSequence?): String?`. "Có nội dung" nghĩa là `isNotBlank()`. Thứ tự: `bigText` → `textLines` (bỏ dòng trống, nối bằng `\n`) → `text`. Cả ba trống → `null` và bỏ qua thông báo. Kết quả `trim()`.
- `title` từ `EXTRA_TITLE` (`getCharSequence`), thiếu thì `""`.
- Lỗi: mỗi thông báo chạy trong coroutine riêng, exception đi vào `CoroutineExceptionHandler` rỗng nên không làm sập service. Dự án cấm `Log`, nên thông báo lỗi bị bỏ im lặng; lần quét `onListenerConnected` kế tiếp xử lý lại nó (hash chống trùng). Không ghi ra đâu nội dung, package của thông báo cụ thể, hay số tiền.

## 5. UI

### 5.1 Màn setup (`ui/setup/`)

`SetupScreen`, `SetupContract`, `SetupViewModel` theo `BaseViewModel<S, I, E>`.

Nội dung (theo hướng dẫn special permission của Android và yêu cầu prominent disclosure của Google Play):
1. Giải thích: Finote đọc thông báo của app ngân hàng để tự ghi giao dịch. Ba cam kết:
   - Chỉ đọc app ngân hàng và ví đã hỗ trợ (liệt kê tên từ rule seed).
   - Bỏ qua OTP và mã xác thực, không lưu.
   - Dữ liệu nằm trên máy, đã mã hóa, không gửi đi đâu.
2. Hướng dẫn: tìm Finote trong danh sách và bật. Một dòng phụ: nếu thấy "Restricted setting" thì vào Thông tin ứng dụng → ⋮ → Cho phép cài đặt hạn chế.
3. Nút chính "Bật tự động ghi" → `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. Nút phụ "Để sau, tôi nhập tay".

Trạng thái:
- `Intro`: như trên.
- `Retry`: user đã đi Settings, quay về mà chưa bật. Nút chính đổi thành "Thử lại".
- `Granted`: hiện "Đã bật" ngắn rồi phát effect hoàn tất.

Màn hình kiểm quyền mỗi `ON_RESUME` bằng `NotificationManagerCompat.getEnabledListenerPackages` và gửi intent `Resumed(isGranted)` cho ViewModel, nên ViewModel thuần và test được, không cần interface kiểm quyền. `ON_RESUME` cũng bắn ngay khi màn vừa hiện, nên ViewModel giữ cờ `hasLaunchedSettings` (chỉ thành `true` khi bấm nút mở Settings):
- Có quyền → `Granted`, kể cả ở lần resume đầu (user đã bật sẵn trước đó).
- Không có quyền và `hasLaunchedSettings = false` → giữ `Intro`.
- Không có quyền và `hasLaunchedSettings = true` → `Retry`.

Cả "Để sau" và `Granted` đều đặt `setup_done = true` rồi phát effect hoàn tất.

### 5.2 Điều hướng

- Thêm key `Setup(isFromOnboarding: Boolean)` vào `ui/navigation`.
- `setup_done = false` → back stack bắt đầu bằng `Setup(isFromOnboarding = true)`. Ngược lại bắt đầu bằng `Overview` như hiện tại.
- `isFromOnboarding = true`: hoàn tất thì thay cả back stack bằng `Overview`. Back hệ thống thoát app như mọi màn gốc.
- `isFromOnboarding = false` (mở từ banner): đẩy lên trên màn hiện tại. Back hệ thống, "Để sau" và hoàn tất đều pop về màn trước.
- `SetupViewModel` không biết luồng nào. Nó chỉ phát effect hoàn tất. Nav host quyết thay hay pop theo `isFromOnboarding`.

### 5.3 Banner

- Trên màn danh sách giao dịch, chỉ hiện khi thiếu quyền. Kiểm lại mỗi `ON_RESUME`.
- Bấm → mở `Setup`. Không có nút đóng.
- Dùng component có sẵn trong `docs/design-system.md`. Không có kiểu banner thì dùng card sẵn có, không tạo component mới.

### 5.4 Chuỗi

Mọi chữ trong `strings.xml`, đủ `values/` (vi), `values-en`, `values-zh-rCN`, `values-ja`.

## 6. Test

JVM, chạy trong `testDebugUnitTest`.

- `CaptureNotificationUseCaseTest` (repo giả):
  - Package không có rule → `NotTracked`, không ghi.
  - Từ khóa bí mật có dấu, không dấu, hoa thường, dạng NFD, dính liền (`OTP123456`, `SmartOTP:`) → `IGNORED_SPAM`, `title`/`content` rỗng, `failureReason = secret:<từ>`; bí mật thắng lọc kênh.
  - Từ khóa rule và lọc kênh → `IGNORED_SPAM` giữ `title`/`content`, `failureReason = keyword:<từ>` hoặc `channel:<id>`.
  - Ranh giới từ: "Kichi Hotpot" không bị chặn bởi `OTP`; "Luu Dai Trang" không bị chặn bởi `ưu đãi`; "Mã OTP: 123456" bị chặn.
  - Nhiều từ khóa cùng khớp → `failureReason` luôn là từ khóa đầu theo thứ tự rule.
  - Lọc kênh: mọi rule `null` → không lọc; mọi rule có danh sách → kênh ngoài bị chặn `channel:<id>`, `channelId` null bị chặn `channel:null`; một rule có danh sách và một rule `null` → không lọc.
  - Hash của dòng `IGNORED_SPAM` tính từ nội dung gốc: hai thông báo spam cùng `postTime`, khác nội dung, ra hai dòng.
  - Thông báo giao dịch → `PENDING`, đủ nội dung.
  - Cùng thông báo hai lần → lần hai `Duplicate`.
- Mapper rule: JSON → `List<String>`; `channelIdAllowlist = "[]"` → `null`.
- Corpus D4 (`notification_corpus.json`) với rule seed thật:
  - Mọi mẫu `shouldIgnore = true` → `Ignored`. 100%.
  - Mọi mẫu `shouldIgnore = false` → `Stored`. 0% chặn nhầm.
  - Nội dung đưa vào: `bigText` nếu có, ngược lại `content`.
- `notificationContent`: thứ tự ưu tiên; `bigText` chỉ có khoảng trắng thì rơi xuống `text`; `textLines` bỏ dòng trống.
- Hash: ổn định; đổi `postTime`, `title` hoặc `content` thì đổi hash.
- DAO (Robolectric, DB không mã hóa như test DAO hiện có): `contentHash` trùng bị bỏ qua.
- `ParserRuleSeeder`: DB mới → đủ 4 rule; chạy lại không nhân đôi; rule seed `version` cao hơn ghi đè rule hệ thống cũ; rule `isUserCustom = true` không bị ghi đè.
- `SetupViewModelTest` (Turbine): resume đầu chưa có quyền → giữ `Intro`; resume đầu đã có quyền → `Granted`; sau khi mở Settings, resume chưa có quyền → `Retry`; "Để sau" → đặt cờ và effect hoàn tất.

Không test tự động: service thật, mở Settings. Kiểm tay trên máy khi dogfooding.

## 7. DoD

- `testDebugUnitTest` và `qualityGate` pass.
- Corpus: chặn 100% mẫu rác, 0% chặn nhầm mẫu giao dịch.
- Trên máy thật: cấp quyền qua màn setup; thông báo VCB/MB/MoMo tạo dòng `PENDING`; khởi động lại máy, thông báo còn treo được quét mà không tạo trùng.
