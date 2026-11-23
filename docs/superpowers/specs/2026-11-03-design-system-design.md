# Thiết kế design system

Trạng thái: chờ user duyệt. Ngày: 2026-11-03.

## 1. Mục tiêu

Chốt token, theme, bộ component và luật dùng trước khi làm màn hình P1. Màn nào, người hay agent viết, cũng trông cùng một app. Luật do máy ép, không dựa vào trí nhớ.

Tiêu chí thành công:
- Bộ component P1 (mục 5) có đủ trước màn feature đầu tiên. Mỗi component đủ trạng thái, đủ a11y, có preview sáng, tối, chữ lớn.
- Màn feature không viết được màu, cỡ chữ, `dp`, hay component M3 trực tiếp. Konsist chặn.
- Giao diện lệch so với ảnh mẫu thì `qualityGate` fail.

Ngoài phạm vi:
- Công tắc sáng/tối và công tắc Dynamic Color trong Cài đặt. Làm sau P1 nếu cần.
- Biểu đồ (P3, thư viện chọn lúc đó).
- Màn catalog/gallery component riêng. `@Preview` và screenshot test đã đủ.

## 2. Quyết định đã chốt

| Chủ đề | Quyết định | Lý do |
|---|---|---|
| Nguồn màu | Android 12+: Dynamic Color. API 26–30: scheme brand | Spec yêu cầu Material 3 và Dynamic Color. Máy cũ cần màu có chủ ý |
| Màu brand | Seed teal `#006A6A`, TonalSpot | Tránh trùng xanh lá (thu) và đỏ (chi) |
| Phong cách | Hướng C: đậm, thẻ số dư gradient, thanh điều hướng nổi kính mờ, thẻ tách rời, phẳng | User chọn từ ba mockup |
| Sáng/tối | Theo hệ thống, làm cả hai | Chuẩn Android, không chói khi máy để sáng |
| Màu thu/chi/chuyển | Cố định, không harmonize theo Dynamic | Màu mang nghĩa. Material 3 cho phép giữ tĩnh. Cashew, NiA cũng giữ |
| Font | Roboto hệ thống, số tiền bật `tnum` | Không tăng APK. Đủ chữ Việt, Trung, Nhật |
| Format tiền | Theo locale của app | Đúng cho 4 ngôn ngữ (D9) |
| Mức bọc | Bọc hết, kiểu Now in Android | Đồng nhất cao, máy kiểm được |
| Số `dp` | Feature và component không viết số, chỉ dùng token | Đồng nhất khoảng cách |
| Cách làm | Làm đủ bộ component P1 trước, mỗi cái chuẩn chỉnh | User yêu cầu, không làm lười từng cái |
| Thư viện | Haze, Compose Unstyled, Material 3 (phần máy móc) | Kính mờ và hành vi khó (sheet, dialog) không tự viết |
| Screenshot test | Có, chạy trong `qualityGate` | Bắt lệch giao diện mà mắt bỏ qua |

Dữ kiện tra ngày 2026-11-03:
- Haze `2.1.0` (ra 2026-11-02). Group `dev.chrisbanes.haze`, module `haze`, `haze-blur`, `haze-blur-materials`, `haze-glass`… Compose Multiplatform, chạy được trên Android.
- Compose Unstyled `com.composables:composeunstyled:2.10.2`, có module lẻ theo component (`composeunstyled-button`, `composeunstyled-text-field`…). Cần Java 17.
- Plugin `com.android.compose.screenshot` đã ngừng bảo trì. AGP 9.5 dùng AGP test suites: `testOptions.screenshotTests.create(...)`, engine `0.0.1-alpha16`, cờ `android.experimental.enableScreenshotTest=true` và `android.experimental.testSuiteSupport=true`. Còn experimental.
- Compose BOM `2026.09.00` đã có trong catalog.

## 3. Token

Tất cả nằm trong `ui/common/theme/`. Đây là nơi duy nhất được viết số `dp`, `sp`, `Color(...)`.

### 3.1 Màu

- `colorScheme`: Android 12+ dùng `dynamicLightColorScheme` / `dynamicDarkColorScheme`. Máy cũ dùng scheme sinh bằng Material Theme Builder từ seed `#006A6A`, TonalSpot, dán vào `Color.kt`.
- Màu mở rộng `FinoteColors`, sáng và tối riêng, mỗi màu có 4 role `color`, `onColor`, `container`, `onContainer`:
  - `income` (xanh lá), `expense` (đỏ), `transfer` (xanh dương).
- `glass`: `fill` (nền trong mờ) và `stroke` (viền mảnh) cho phần nổi, sáng và tối riêng.
- `hero`: `start`, `end`, `onHero`. Gradient chỉ nối hai role cùng độ tối, để chữ không chìm ở giữa thẻ. Đổi theo Dynamic Color:
  - Sáng: `primary` → `tertiary`, chữ `onPrimary`.
  - Tối: `primaryContainer` → `tertiaryContainer`, chữ `onPrimaryContainer`. Giữ thẻ đậm như mockup C. Dùng `primary` ở tối sẽ ra thẻ pastel nhạt.
  - `brushes.hero` dựng từ `start` và `end`.
- Màu danh mục/tài khoản lưu trong DB là `Int`. `Color.kt` có `Int.toComposeColor()`. Chỉ component chung gọi hàm này.
- `categoryPalette`: bảng 12–16 màu chọn sẵn cho danh mục và tài khoản. User chỉ chọn được trong bảng này, không có màu tùy ý (user chốt 2026-11-03). Mỗi màu qua `ContrastTest` ở cả sáng và tối.
- Màu không bao giờ là tín hiệu duy nhất. Số tiền luôn có dấu `+`/`\u2212` (U+2212), giao dịch luôn có icon loại.

### 3.2 Chữ

- Thang chữ M3 mặc định, Roboto hệ thống.
- Thêm `amountLarge`, `amountMedium`, `amountSmall`, có `fontFeatureSettings = "tnum"`.
- Cỡ cụ thể của ba style tiền chốt lúc làm, theo mockup: số dư ~28–32sp đậm, dòng giao dịch ~14sp đậm vừa.

### 3.3 Khoảng cách, kích thước, bo góc

| Nhóm | Token |
|---|---|
| `spacing` | `xs 4`, `sm 8`, `md 12`, `lg 16`, `xl 24`, `xxl 32` |
| `sizes` | `iconSm 20`, `iconMd 24`, `avatar 36`, `touchTarget 48`, `border 1`, `navBarHeight 56` |
| `radii` | `sm 12`, `md 16`, `lg 20`, `xl 24`, `full` (giá trị `Dp`) |
| `shapes` | `sm`, `md`, `lg`, `xl`, `full` (`RoundedCornerShape` dựng sẵn từ `radii`) |

Thiếu kích thước thì thêm token vào file token tương ứng (`FinoteSizes.kt`…) trong cùng branch. Mỗi nhóm token là một class trong file riêng, vì luật "một class public mỗi file". Không viết số ở chỗ dùng.

### 3.4 Chuyển động và độ sâu

- `motion.standard` và `motion.emphasized`: hai `spring` dùng chung cho nhấn, mở sheet, chuyển màn.
- Nhấn: component co về ~0.96 bằng `motion.standard`.
- Phẳng, không đổ bóng. Phân lớp bằng màu nền: nền màn < thẻ < phần nổi kính mờ.

### 3.5 Cách truy cập

```kotlin
FinoteTheme.scheme.surface          // role M3 (ColorScheme), thay cho MaterialTheme.colorScheme
FinoteTheme.colors.income.color
FinoteTheme.typography.amountLarge
FinoteTheme.spacing.lg
FinoteTheme.radii.xl
FinoteTheme.shapes.xl
FinoteTheme.brushes.hero
```

`FinoteTheme` là một `@Composable fun` (bọc `MaterialTheme` và cấp token qua `CompositionLocal`) và một `object` cùng tên để đọc token. Code ngoài `ui/common/theme/` không gọi `MaterialTheme`.

## 4. Thư viện

| Thư viện | Dùng cho | Ghi chú |
|---|---|---|
| Material 3 (BOM) | `ColorScheme`, Dynamic Color, `TextField` gốc, `DatePicker` | Style lại toàn bộ, không để lộ kiểu M3 mặc định |
| Compose Unstyled | Sheet, dialog, menu, tab, switch | Có sẵn hành vi và a11y, tự style theo C. Chỉ lấy module lẻ cần dùng |
| Haze | Kính mờ cho `FinoteNavBar`, `FinoteBottomSheet` | API 31+ blur thật (`RenderEffect`). API 26–30 luôn tắt blur, dùng `glass.fill` đặc hơn. Thiết kế phải đẹp cả khi không blur |

- Phiên bản đi qua `gradle/libs.versions.toml`, chốt lúc làm.
- Nâng `compileOptions` từ Java 11 lên 17 (Compose Unstyled yêu cầu).

Nối Haze, không để lộ ra feature:
- `FinoteScaffold` tự tạo `HazeState` và gắn `Modifier.hazeSource(state)` vào vùng `content`.
- `HazeState` truyền xuống `FinoteNavBar` và `FinoteBottomSheet` qua một `CompositionLocal` `internal` trong `ui/common/`. Hai component này gắn `Modifier.hazeEffect(state)`.
- API 26–30 không gắn `hazeEffect`, chỉ vẽ `glass.fill`.

## 5. Component

Chỗ đặt: `ui/common/component/`, một component một file. Component chỉ một feature dùng (bàn phím số của cash-entry) nằm ở `ui/<feature>`, vẫn chỉ dùng token. Feature thứ hai cần thì chuyển lên `ui/common/component/`.

| Nhóm | Component | Ghi chú |
|---|---|---|
| Khung | `FinoteScaffold` | Edge-to-edge, chừa chỗ cho thanh nổi, giữ `HazeState`, nhận `FinoteSnackbarState` |
| | `FinoteTopBar` | Tiêu đề, nút back, hành động phải |
| | `FinoteNavBar` | Nổi, kính mờ, mục đang chọn là khối màu `primary` |
| | `FinoteBottomSheet` | Compose Unstyled + Haze |
| | `FinoteDialog` | Compose Unstyled |
| Hành động | `FinoteButton` | `style`: `Primary`, `Secondary`, `Ghost`, `Danger`. Có `isLoading` |
| | `FinoteIconButton` | Vùng chạm 48 |
| Nhập | `FinoteTextField` | Nhãn, gợi ý, lỗi, đếm ký tự |
| | `FinoteSwitch`, `FinoteChip` | |
| | `FinoteSegmented` | Chi / Thu / Chuyển |
| | `FinoteDatePicker` | Bọc `DatePicker` M3 trong `FinoteBottomSheet`. Nhận và trả `LocalDate`, không để lộ `DatePickerState` |
| Hiển thị | `BalanceHeroCard` | Gradient `brushes.hero`, tổng thu/chi dạng chip |
| | `FinoteCard`, `FinoteListRow` | |
| | `TransactionRow` | `CategoryIcon` + tên + phụ đề + `MoneyText` |
| | `CategoryIcon` | Nhận `colorArgb: Int`. Nền ô = màu đó, alpha 0.16 (sáng) / 0.24 (tối). Icon tint = màu đó (sáng), hoặc màu đó pha 40% về trắng (tối) |
| | `MoneyText` | Mọi số tiền trên UI đi qua đây |
| | `FinoteSnackbar` | Kèm `FinoteSnackbarState` và `rememberFinoteSnackbarState()`, bọc `SnackbarHostState`. Feature gọi `show(message, actionLabel)` |
| | `FinoteEmptyState`, `FinoteSkeleton` | |

Wrapper tự giữ mọi state của M3, Haze và Compose Unstyled. Feature không bao giờ cầm kiểu dữ liệu của ba thư viện này.

Mỗi component chung phải có:
- Đủ trạng thái liên quan: thường, nhấn, tắt, lỗi, đang tải.
- A11y: vùng chạm ≥ 48dp, `Role` đúng, `contentDescription` từ `strings.xml`, không vỡ khi cỡ chữ 200%.
- `modifier: Modifier = Modifier` là tham số tùy chọn đầu tiên.
- `@Preview` sáng, tối, `fontScale = 2f`, bọc trong `FinoteTheme`.
- Icon là vector drawable Material Symbols Rounded, tên `ic_<name>.xml`. Không dùng `material-icons-extended`.

Format tiền:
- `ui/common/format/MoneyFormat.kt`: hàm thuần `format(amount: Long, kind: MoneyKind, locale: Locale): String`.
  - Phần số: `NumberFormat` tiền tệ VND của locale, 0 chữ số thập phân, chạy trên giá trị tuyệt đối. Vị trí `₫` và khoảng trắng (`\u00A0` hay không) để locale quyết.
  - Dấu do app ghép ở đầu chuỗi, không để `NumberFormat` tự xử lý số âm: `Income` → `+`, `Expense` → `\u2212` (U+2212). `Transfer`, `Neutral` và số 0 không có dấu.
  - `MoneyKind.Neutral` với số âm (vd số dư thẻ tín dụng) → `\u2212` (U+2212) + giá trị tuyệt đối.
  - `amount == Long.MIN_VALUE` thì ném `IllegalArgumentException`, vì `abs` bị tràn.
  - Không dùng `Double`.
- `MoneyText(amount: Long, kind: MoneyKind, style)`: gọi `MoneyFormat` với locale của app, tô màu theo `kind`.

## 6. Luật máy kiểm

File mới `app/src/test/java/io/github/toanhp123/finote/architecture/DesignSystemKonsistTest.kt`. Cách đọc text giống các rule hiện có.

| # | Luật | Phạm vi |
|---|---|---|
| 1 | Không import `androidx.compose.material3.*` trừ `Text` và `Icon`. Không import Haze, Compose Unstyled | `ui/` trừ `ui/common/` |
| 2 | Không `MaterialTheme`, `Color(`, `Color.<Tên>` (trừ `Color.Transparent`, `Color.Unspecified`), `.dp`, `.sp`, `TextStyle(`, `RoundedCornerShape(`, `Brush.` | `ui/` trừ `ui/common/theme/` |
| 3 | Không `NumberFormat`, `DecimalFormat`, `String.format`, `"...".format(` | Mọi code production trừ `ui/common/format/` |
| 4 | File có `@Composable` public phải có ít nhất một `@Preview` | `ui/common/component/` |

- Mỗi luật có red proof theo `docs/architecture.md`.
- Luật 2 quét cả comment và chuỗi, như các rule text hiện có. Chấp nhận.

## 7. Test

- `MoneyFormatTest`: `vi`, `en`, `zh-CN`, `ja` × (0, `Income`, `Expense`, `Transfer`, `Neutral` âm, `Long.MAX_VALUE`, `Long.MIN_VALUE` ném lỗi).
  - So chuỗi chính xác, viết `\u00A0` và `\u2212` (U+2212) dạng escape trong test.
  - Rủi ro: unit test dùng dữ liệu locale của JDK, máy thật dùng ICU của Android. Hai bên có thể lệch khoảng trắng hoặc vị trí `₫`. Luật dấu (do app ghép) không bị ảnh hưởng. Kiểm bằng mắt trên máy thật ở 4 ngôn ngữ một lần khi xong.
- `ContrastTest`: hàm thuần tính tỉ lệ tương phản WCAG, chạy trên JVM, chỉ trên scheme brand (Dynamic không kiểm tĩnh được). Phải ≥ 4.5:1, ở cả sáng và tối:
  - `income`, `expense`, `transfer` trên nền thẻ.
  - `onHero` trên `hero.start` và trên `hero.end`.
  - Icon tint của mỗi màu `categoryPalette` trên nền ô của chính nó, ngưỡng 3:1 (WCAG 1.4.11 cho đồ họa, icon không phải chữ).
- Screenshot test qua AGP test suites:
  - Suite `screenshotTest`, nguồn ở `app/src/screenshotTest/kotlin/`, target `debug`.
  - Mỗi component chung có một hàm `@PreviewTest` dùng multi-preview sáng, tối, chữ lớn.
  - Ảnh mẫu commit vào repo.
  - `qualityGate` thêm task test của suite. Tên task chính xác xác nhận lúc làm.
  - Rủi ro: tính năng còn experimental, DSL có thể đổi khi nâng AGP.

## 8. Tài liệu

- `docs/design-system.md` mới, bắt buộc như `docs/architecture.md`: token, bảng component, luật dùng, cách cập nhật ảnh mẫu.
- `CLAUDE.md` và `docs/architecture.md` thêm một dòng trỏ tới nó.
- Luật chỉ `reviewer` kiểm, ghi vào `docs/architecture.md` mục "Rules only reviewer can check":
  - Gradient chỉ cho `BalanceHeroCard`, tối đa một chỗ mỗi màn.
  - Kính mờ chỉ cho phần nổi (thanh điều hướng, sheet).
  - Màu không là tín hiệu duy nhất.
- `.gitignore` thêm `.superpowers/`.

## 9. Thứ tự làm

Một branch `feature/design-system`, trước `feature/cash-entry` và mọi màn P1:
1. Nâng Java 17. Thêm Haze, Compose Unstyled vào catalog.
2. Token và `FinoteTheme`, `MoneyFormat`, `ContrastTest`, `MoneyFormatTest`.
3. `DesignSystemKonsistTest` với red proof từng luật.
4. Component theo bảng mục 5, mỗi cái kèm preview.
5. Screenshot test suite, ảnh mẫu, nối vào `qualityGate`.
6. `docs/design-system.md`, cập nhật `CLAUDE.md`, `docs/architecture.md`, roadmap P1.

Gate: `qualityGate` pass, `reviewer` chạy một lần trước khi squash vào `dev`.
