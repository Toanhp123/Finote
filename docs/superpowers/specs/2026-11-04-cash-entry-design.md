# Thiết kế `feature/cash-entry`

Ngày: 2026-11-04. Phase: P1 (roadmap mục 3, việc 8). Nguồn: spec mục 2.2 (dòng 53), Must-Have "CRUD Giao dịch & Nhập tiền mặt 1 chạm" (dòng 274–275), mục 9 (dòng 421); `docs/architecture.md`; `docs/design-system.md`.

## 1. Mục tiêu

Một bottom sheet nhập tiền mặt có bàn phím số và phép tính `+ − × ÷`, chọn tài khoản và danh mục. Quick Settings Tile và App Shortcut mở thẳng sheet này.

Đã chốt với user:
- Sheet nhập hai loại: Chi (`EXPENSE`) và Thu (`INCOME`), mặc định Chi. Chuyển (`TRANSFER`) để sau, xem roadmap.
- App tự seed một tài khoản "Tiền mặt" để sheet không bao giờ thiếu tài khoản. Cách làm giống Cashew (tự tạo tài khoản mặc định). Việc nhập số dư thật nằm ở `feature/reconciliation` và onboarding (P5).
- `Account` có thêm `nameKey`, cùng luật với `Category`, để tên "Tiền mặt" đổi theo ngôn ngữ app.
- Tài khoản mặc định: dùng lần trước → "Tiền mặt" seed → tài khoản đầu tiên theo `sortOrder`. Danh mục mặc định: dùng lần trước của loại đang chọn.
- Tile và Shortcut mở một Activity riêng, nền trong suốt. Sheet nổi trên màn hình đang mở, lưu xong thì đóng, app chính không mở ra.
- Máy tính: nhân chia trước, cộng trừ sau.

Ngoài phạm vi: Chuyển khoản; gắn sao tài khoản chính (`isPrimary`); nút "+" trong app (đi cùng màn danh sách giao dịch, việc 11); đo 0,5 giây (P5); tag; merchant; ảnh hóa đơn.

## 2. Thành phần

### 2.1 domain

- `domain/transaction/AmountExpression.kt`: logic thuần cho phần nhập và phần tính (mục 4). Không phụ thuộc Android.
- `domain/transaction/CashEntryDefaults.kt`: các hàm thuần chọn tài khoản và danh mục mặc định (mục 3.1).
- `TransactionRepository` thêm:
  - `suspend fun getLastManualAccountId(): String?`
  - `suspend fun getLastManualCategoryId(type: TransactionType): String?`
  - Cả hai lấy giao dịch `source = MANUAL`, chưa xóa mềm, có `createdAt` mới nhất.
  - Query danh mục lọc thêm `categoryId IS NOT NULL`. Một lần lưu không chọn danh mục không được xóa mất danh mục mặc định của lần sau.
- Lưu bằng `AddTransactionUseCase` có sẵn. Không có use case mới để lưu.
- `Account` thêm `nameKey: String?`. KDoc giống `Category`: key resource của tài khoản hệ thống, thành null khi user đổi tên.

### 2.2 data

- `AccountEntity`, `AccountMapper`: thêm `nameKey`.
- `AccountRepositoryImpl`:
  - `add` ép `nameKey = null`.
  - `update` giữ `nameKey` cũ khi `name` không đổi, xóa khi đổi. Chép luật của `CategoryRepositoryImpl.update`.
  - KDoc của `AccountRepository.update` ghi cùng hợp đồng về `name` như `CategoryRepository.update`: truyền lại `name` đã lưu, không truyền chuỗi đã dịch mà UI hiện ra.
- `FinoteDatabase`: `version = 3`, export `3.json`. `fallbackToDestructiveMigration` đã có, nên không viết migration (roadmap dòng 84).
- `TransactionDao`: hai query cho hai hàm repository ở trên.
- `data/account/SystemAccountSeeder.kt`: một `RoomDatabase.Callback`, theo mẫu của `SystemCategorySeeder`.
  - Seed một dòng: id `sys_cash`, `nameKey = "sys_cash"`, `name = "Cash"` (tên dự phòng), `type = CASH`, `initialBalance = 0`, `currentBalance = 0`, `currency = "VND"`, `isInitialized = false`, `sortOrder = 0`, `isArchived = false`, `isExcludedFromNetWorth = false`. Icon và màu chọn khi viết plan, theo `CategoryPalette` và bộ icon có sẵn.
  - Seed trong `onCreate`. Trong `onOpen`, chỉ seed khi bảng `accounts` không có dòng nào, **tính cả dòng đã xóa mềm**. Nhờ vậy user xóa "Tiền mặt" thì nó không tự quay lại, và đường destructive migration vẫn được seed (cùng lý do với seeder danh mục).
  - `createFinoteDatabase` gắn thêm callback này.
- `strings.xml` (4 ngôn ngữ): thêm `sys_cash`. `res/raw/keep.xml` đã giữ `@string/sys_*`, không cần sửa.

### 2.3 ui

`ui/cashentry/`:
- `CashEntryActivity`: `AppCompatActivity` có Hilt (cùng lý do với `MainActivity`: ngôn ngữ riêng của app chỉ áp dụng cho `AppCompatActivity` trên API 32 trở xuống, D9), theme trong suốt, `excludeFromRecents="true"`, `taskAffinity` riêng, `launchMode="singleTask"`. Chỉ vẽ sheet. Sheet đóng thì `finish()`.
- `CashEntryContract.kt`, `CashEntryViewModel`, `CashEntryScreen`: MVI trên `BaseViewModel` (mục 3). ViewModel nhận `java.time.Clock` qua inject (đã có trong DI), không gọi `LocalDate.now()` hay `System.currentTimeMillis()`, để test kiểm được `timestamp`.
- `CashKeypad`: bàn phím 4×4 cộng nút Lưu. Chỉ feature này dùng, nên nằm ở `ui/cashentry` (`design-system.md`).
- `CashEntryTileService`: `onClick` gọi `unlockAndRun { … }` rồi mở `CashEntryActivity`. API 34+ dùng `startActivityAndCollapse(PendingIntent)`, API 26–33 dùng bản nhận `Intent`.
- Tên hiển thị: một hàm trong `ui/common` đổi `nameKey` sang chuỗi resource, nếu null thì dùng `name`. Dùng chung cho danh mục và tài khoản. Dùng bảng `key → R.string` tường minh, không dùng `getIdentifier` (lint `DiscouragedApi` chặn). Icon cũng tra bằng bảng tương tự.
- `res/xml/shortcuts.xml`: một App Shortcut tĩnh mở `CashEntryActivity`, khai báo trong `<meta-data android:name="android.app.shortcuts">` của `MainActivity`. Không cần code `ShortcutManagerCompat`.
- `AndroidManifest.xml`: thêm Activity, TileService (`BIND_QUICK_SETTINGS_TILE`, icon, nhãn), meta-data shortcut.

Konsist: `TileService` và Activity nằm ở `ui/cashentry`, không import package gốc. Gói gốc vẫn chỉ có `MainActivity` và `FinoteApplication`.

## 3. Luồng và giao diện

### 3.1 Lúc mở

1. Tile: nếu máy khóa thì `unlockAndRun` bắt mở khóa trước. Shortcut: mở luôn.
2. ViewModel gom `observeAccounts()`, `observeCategories()`, `getLastManualAccountId()`, và `getLastManualCategoryId` cho **cả** `EXPENSE` lẫn `INCOME`. Mỗi giá trị chỉ đọc một lần, lúc mở.
3. `CashEntryDefaults`:
   - Tài khoản: id dùng lần trước nếu tài khoản đó còn sống và chưa lưu trữ → `sys_cash` nếu còn sống và chưa lưu trữ → tài khoản sống, chưa lưu trữ đầu tiên theo `sortOrder` → null.
   - Danh mục: id dùng lần trước của loại đó nếu danh mục còn sống, không ẩn và đúng loại → null.
4. State ban đầu: Chi, biểu thức rỗng, ngày hôm nay, ghi chú rỗng.

Mỗi lần `observeAccounts()` hoặc `observeCategories()` phát danh sách mới: nếu tài khoản đang chọn là null hoặc không còn hợp lệ (đã lưu trữ, đã xóa) thì chạy lại `CashEntryDefaults` cho tài khoản. Danh mục đang chọn của một loại không còn hợp lệ thì về null. Lựa chọn còn hợp lệ thì giữ nguyên.

Không còn tài khoản nào (user đã lưu trữ hết): ô tài khoản hiện "Chưa có tài khoản", nút Lưu tắt.

### 3.2 Contract

- State: `type`, `expression`, `amount: Long?`, `error`, `accounts`, `selectedAccountId`, `categories` (đã lọc theo loại, bỏ mục ẩn), `selectedCategoryIds: Map<TransactionType, String?>` (lựa chọn riêng của Chi và của Thu), `date`, `note`, `page` (`KEYPAD`, `ACCOUNT`, `CATEGORY`, `NOTE`), `isSaving`.
- Intent: `KeyPressed`, `TypeChanged`, `AccountPicked`, `CategoryPicked(id: String?)` (null là bỏ chọn), `DatePicked`, `NoteChanged`, `PageOpened`, `PageClosed`, `SaveClicked`, `Dismissed`.
- Effect: `Close`.

### 3.3 Bố cục sheet

Từ trên xuống: `FinoteSegmented` Chi/Thu; biểu thức (chữ nhỏ); số tiền qua `MoneyText` (Chi có `−`, Thu có `+`); dòng lỗi; ô tài khoản và ô danh mục; ô ngày và ô ghi chú; `CashKeypad`; nút Lưu.

Bấm ô tài khoản, ô danh mục hoặc ô ghi chú thì nội dung **trong cùng sheet** đổi sang trang tương ứng. Không mở sheet thứ hai.
- Trang tài khoản và trang danh mục: một danh sách. Chọn xong thì quay về bàn phím. Danh mục hiện cây 2 cấp, mục con thụt vào. Dòng đầu là "Không chọn danh mục" (gửi `CategoryPicked(null)`).
- Trang ghi chú: một `FinoteTextField` tự focus, bàn phím hệ thống mở, `CashKeypad` không hiện. Nút xong hoặc Back thì quay về bàn phím số. Nhờ vậy bàn phím hệ thống và `CashKeypad` không bao giờ cùng hiện.
- Ngày dùng `FinoteDatePicker`.

Back: khi `page != KEYPAD`, một `BackHandler(enabled = page != KEYPAD)` đặt trong nội dung sheet đưa về `KEYPAD`. Chỉ khi đang ở `KEYPAD` thì Back mới đóng sheet. `FinoteBottomSheet` dùng `composeunstyled` với hai trạng thái `FullyExpanded` và `Hidden`, nên không có trạng thái nửa màn hình. Plan phải có bước kiểm `BackHandler` trong nội dung có được ưu tiên hơn phần xử lý Back của sheet không; nếu không thì xử lý trong `onDismiss`.

Đổi Chi/Thu: danh mục hiện là `selectedCategoryIds[type mới]`, không đọc lại DB. Lựa chọn tay của mỗi loại được giữ suốt phiên mở sheet. Số tiền giữ nguyên.

### 3.4 Lưu

- Nút Lưu bật khi `amount > 0`, có tài khoản, không có lỗi và không đang lưu. Danh mục không bắt buộc.
- Tạo `Transaction` với `id` UUID mới, `type`, `amount`, `feeAmount = 0`, `accountId`, `categoryId`, `note`, `source = MANUAL`, `timestamp` (ngày đã chọn; nếu là hôm nay thì lấy giờ hiện tại, ngày khác thì lấy 12:00 giờ máy), rồi gọi `AddTransactionUseCase`.
- Thành công: phát `Close`. Activity đóng.
- Use case ném lỗi: giữ nguyên sheet và số đã nhập, hiện lỗi chung bằng chữ. Không ghi nội dung giao dịch vào Logcat.
- Vuốt xuống hoặc Back khi đang ở bàn phím: đóng luôn, không hỏi lại.

Mọi chữ nằm trong `strings.xml` đủ 4 ngôn ngữ (`vi`, `en`, `zh-rCN`, `ja`).

## 4. `AmountExpression`

### 4.1 Nhập

- Phím: `0–9`, `000`, `⌫`, `+`, `−`, `×`, `÷`.
- Không cho bắt đầu bằng phép tính, nên không có số âm.
- Bấm hai phép tính liền nhau: phép sau thay phép trước.
- Một số tối đa 13 chữ số. Phím số bấm thêm bị bỏ qua.
- `0` đứng đầu một số bị chữ số mới thay. `000` trên số rỗng hoặc bằng 0 cho ra `0`.
- `000` khi số đang có 11 hoặc 12 chữ số: chỉ thêm số chữ `0` vừa đủ chạm 13 (2 hoặc 1). Số đã có 13 chữ số thì bỏ qua.
- `⌫` xóa ký tự cuối.

### 4.2 Tính

- Tính lại sau mỗi phím. Phép tính thừa ở cuối được bỏ qua.
- `×` và `÷` trước, `+` và `−` sau, cùng cấp thì từ trái qua phải.
- Mỗi phép `÷` làm tròn nửa lên về đồng ngay tại chỗ: `(a * 2 + b) / (b * 2)`, tính bằng `Math.*Exact`. Toán hạng của `÷` luôn `≥ 0` vì không có số âm, nên "nửa lên" chỉ có một nghĩa.
- Hệ quả đã chấp nhận: `100.000 ÷ 3 × 3 = 99.999`.
- Kết quả: `Ok(amount: Long)`, `Empty`, hoặc `Error(reason)` với `reason` là `DIVIDE_BY_ZERO`, `OVERFLOW` hoặc `NOT_POSITIVE` (kết quả `≤ 0`). `NOT_POSITIVE` mang theo giá trị đã tính.

Hiển thị lỗi:
- `NOT_POSITIVE`: không hiện chữ lỗi, vì user thường đang gõ dở (`10 − 20` rồi `+ 50`, `1 ÷ 3` rồi `× 10000`). Số tiền hiện giá trị đã tính qua `MoneyText` (số âm có dấu `−`), nút Lưu tắt.
- `DIVIDE_BY_ZERO`, `OVERFLOW`: hiện chữ lỗi dưới số tiền, nút Lưu tắt.

## 5. Test

Mọi test dưới đây chạy trong `testDebugUnitTest`.

- `AmountExpressionTest`: thứ tự tính; làm tròn (`5 ÷ 2 = 3`, `7 ÷ 2 = 4`, `1 ÷ 3` báo `NOT_POSITIVE`); chia 0; tràn số; phép tính thừa ở cuối; thay phép tính; không bắt đầu bằng phép tính; giới hạn 13 chữ số; `000` ở 11, 12 và 13 chữ số; `⌫`; số 0 đứng đầu; `NOT_POSITIVE` mang đúng giá trị âm.
- `CashEntryDefaultsTest`: đủ chuỗi chọn tài khoản; bỏ qua tài khoản đã lưu trữ hoặc xóa; danh mục bị ẩn, đã xóa hoặc sai loại thì trả null.
- `TransactionDaoTest` (Robolectric, `InMemoryDatabaseRule`): hai query mới bỏ qua giao dịch không phải `MANUAL` và giao dịch đã xóa mềm, lấy `createdAt` mới nhất. Query danh mục bỏ qua giao dịch có `categoryId` null.
- `SystemAccountSeederTest`: seed trên DB mới; không seed lại khi `sys_cash` đã bị xóa mềm; seed qua đường destructive migration.
- `AccountMapperTest`, `AccountRepositoryImplTest`: `nameKey` đi qua mapper; `add` ép null; đổi tên thì xóa key, giữ tên thì giữ key.
- `CashEntryViewModelTest` (Turbine, `MainDispatcherRule`, `Clock` cố định): mặc định lúc mở; lưu thành công phát `Close` và gọi use case đúng dữ liệu, kể cả `timestamp` cho hôm nay và cho ngày khác; use case lỗi thì giữ số đã nhập; chọn tay danh mục Chi, đổi sang Thu rồi về Chi vẫn giữ lựa chọn; `CategoryPicked(null)` bỏ chọn; tài khoản đang chọn bị lưu trữ thì chọn lại theo `CashEntryDefaults`; danh sách tài khoản rỗng rồi có tài khoản thì tự chọn.
- Screenshot test của nội dung sheet: sáng, tối, cỡ chữ 200%; trang bàn phím, trang danh mục, trang ghi chú.

Kiểm tay trên máy thật, ghi kết quả khi review nhánh:
- Tile khi máy mở khóa và khi máy khóa.
- App Shortcut từ icon trên màn hình chính.
- Sheet nổi trên màn hình chính, lưu xong không còn trong Recent Apps.
- Giao dịch vừa lưu làm đổi `currentBalance` của tài khoản.
- Back ở trang danh mục và trang ghi chú quay về bàn phím; Back ở bàn phím đóng sheet.

## 6. Rủi ro

- `startActivityAndCollapse(Intent)` bị khóa từ API 34. Phải rẽ nhánh theo API như mục 2.3.
- Sheet trong Activity trong suốt: phải `finish()` khi sheet đóng xong, không phải lúc bắt đầu animation, để không nháy màn đen. Kiểm tay.
- Đổi version DB sang 3 sẽ xóa dữ liệu trên máy dev. Trước v1.0 thì chấp nhận được.
