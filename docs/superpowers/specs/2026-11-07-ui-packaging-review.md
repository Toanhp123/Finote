# Đánh giá kiến trúc: Đóng gói và tổ chức thư mục (Packaging Review)

Trạng thái: Đã chốt (xem mục 3). Ngày: 2026-11-07.  
Nguồn: Ý kiến người dùng về tình trạng các file đang quá flat ở các thư mục như `ui`; `docs/architecture.md`; `docs/design-system.md`.

---

## 1. Bối cảnh & Vấn đề

Codebase Finote đang trong quá trình phát triển các màn hình và tính năng P1/P2. Cấu trúc hiện tại ưu tiên quy tắc mỗi thư mục theo tầng/feature, nhưng dẫn đến tình trạng các file nằm **quá phẳng (flat)** ở nhiều nơi, đặc biệt là trong tầng `ui/`.

Cụ thể:
1. **`ui/common/component/`**: Đang chứa **30 files** nằm cùng một cấp thư mục (`io.github.toanhp123.finote.ui.common.component`).
   - Khi mở trong IDE, một danh sách dài gần 20 file bắt đầu bằng `Finote...` gây quá tải nhận thức (*cognitive overload*).
   - Trộn lẫn giữa các thành phần rất khác nhau:
     - *Core UI Primitives:* `FinoteButton`, `FinoteTextField`, `FinoteSwitch`, `FinoteChip`, `FinoteIconButton`, `FinoteSegmented`.
     - *Overlays / Modals:* `FinoteBottomSheet`, `FinoteDialog`, `FinoteDatePicker`, `FinoteSnackbar`, `DatePickerDates`.
     - *Layout & Scaffolding:* `FinoteScaffold`, `FinoteTopBar`, `FinoteNavBar`, `FinoteNavItem`.
     - *Domain / Finance-specific UI:* `BalanceHeroCard`, `TransactionRow`, `CategoryIcon`, `MoneyText`.
     - *Helpers & Previews:* `HazeLocals`, `BlurSupport`, `ListRowLayout`, `FinotePreviews`, `FinotePreviewSurface`.
2. **`ui/common/theme/`**: Đang có **17 files** nằm flat.
   - Các bảng màu bị xé nhỏ thành 8 files độc lập (`BrandColors`, `CategoryIconColors`, `CategoryPalette`, `FinoteColors`, `FixedColors`, `GlassColors`, `HeroColors`, `MoneyColorRole`), nhiều file chỉ có vài chục dòng code.
3. **`ui/<feature>/` (như `ui/cashentry` 9 files, `ui/transactions` 7 files)**:
   - Nằm flat toàn bộ: Android components (`CashEntryActivity`, `CashEntryTileService`), MVI Trio (`*Contract`, `*ViewModel`, `*Screen`), logic xử lý biểu thức (`AmountExpression`), và các composable con (`CashKeypad`, `CashEntryPickers`).
4. **`data/<feature>/`**:
   - Flat 6–8 files mỗi feature: Entity, Dao, CrossRef, Mapper, RepositoryImpl, Seeder, Rebuilder.

---

## 2. Phân tích Đánh đổi (Trade-offs)

| Tiêu chí | Cấu trúc Flat hiện tại | Cấu trúc Phân nhóm (Grouped / Modular) |
|---|---|---|
| **Duyệt file trong IDE** | Khó tìm kiếm bằng mắt thường, danh sách file dài dằng dặc | Rõ ràng theo vai trò chức năng (input, overlay, layout, display) |
| **Độ dài câu lệnh Import** | Ngắn gọn (`ui.common.component.*`) | Dài hơn một phân cấp (`ui.common.component.control.*`) |
| **Phân định trách nhiệm** | Mập mờ giữa generic component và finance component | Rạch ròi ranh giới giữa primitives dùng chung và widget nghiệp vụ |
| **Rủi ro Over-engineering** | Thấp (không bao giờ có thư mục trống hoặc 1 file) | Cần kiểm soát để không lồng quá 2 cấp thư mục con |

---

## 3. Quyết định

Đã chốt phân nhóm theo trách nhiệm. Phương án "giữ flat, chỉ ghi tài liệu" bị loại vì không giúp nhìn thư mục là biết trách nhiệm.

### 3.1. Luật tách thư mục

Áp dụng cho mọi package:
1. Chỉ tách khi thư mục trộn **≥ 2 vai trò** khác nhau.
2. Mỗi nhóm con nên có **≥ 3 file**. Ngoại lệ: nhóm hạ tầng (`glass/`, `preview/`) và thư mục `component/` của feature (để mọi feature cùng một mẫu).
3. Không tách khi **hậu tố tên file đã nói vai trò** (`Dao`, `Entity`, `Mapper`, `RepositoryImpl`...).
4. Tối đa **1 cấp** thư mục con dưới `component/`, `theme/` hoặc một feature.

### 3.2. `ui/common/component/`

```text
ui/common/component/
├── control/   # Nhập liệu & hành động: FinoteButton (+ FinoteButtonStyle), FinoteIconButton, FinoteTextField, FinoteSwitch, FinoteChip, FinoteSegmented
├── overlay/   # Lớp phủ & thông báo: FinoteBottomSheet, FinoteDialog, FinoteDatePicker, DatePickerDates, FinoteSnackbar (+ FinoteSnackbarState)
├── layout/    # Khung màn hình: FinoteScaffold, FinoteTopBar, FinoteNavBar (+ FinoteNavItem)
├── display/   # Hiển thị chung, không nghiệp vụ: FinoteCard, FinoteListRow, ListRowLayout, FinoteEmptyState, FinoteSkeleton
├── finance/   # Widget nghiệp vụ tài chính: BalanceHeroCard, TransactionRow, MoneyText, CategoryIcon
├── glass/     # Hạ tầng hiệu ứng kính: HazeLocals (+ LocalBlurEnabled)
└── preview/   # Hạ tầng preview: FinotePreviews (+ FinotePreviewSurface)
```

Điểm mấu chốt: `finance/` tách khỏi `display/` để phân biệt rõ primitive dùng chung và widget nghiệp vụ.

### 3.3. `ui/common/theme/`

Không gộp nội dung file. Chuyển các file màu vào một thư mục con (kiểu nhỏ đi kèm chủ của nó đã được gộp):

```text
ui/common/theme/
├── color/     # BrandColors (+ màu tiền cố định), CategoryColors, FinoteColors (+ MoneyColorRole, HeroColors, GlassColors)
└── (root)     # FinoteTheme, FinoteTypography, FinoteShapes, FinoteSizes, FinoteSpacing, FinoteRadii, FinoteMotion, FinoteBrushes (+ shimmerBrush)
```

Lý do: các file màu giữ nguyên nội dung, thay đổi chỉ là package và import; gốc `theme/` chỉ còn các token chính.

### 3.4. Mẫu chung cho `ui/<feature>/`

- **Gốc feature:** bộ MVI (`*Contract`, `*ViewModel`, `*Screen`), điểm vào Android (`*Activity`, `*TileService`), logic thuần (`AmountExpression`, `*Defaults`).
- **`component/`:** composable con riêng của feature.

```text
ui/cashentry/
├── component/   # CashKeypad, CashEntryPickers
└── (root)       # AmountExpression, CashEntryActivity, CashEntryContract, CashEntryDefaults, CashEntryScreen, CashEntryTileService, CashEntryViewModel

ui/transactions/
├── component/   # ReimbursementChoice, ReimbursementSheet, TransactionFilterChips, TransactionRowText
└── (root)       # TransactionListContract, TransactionListScreen, TransactionListViewModel
```

Feature mới làm theo cùng mẫu này.

### 3.5. `data/<feature>/`

Giữ flat. Hậu tố tên file đã nói vai trò (luật 3.1.3); tách sẽ tạo thư mục 1–2 file.

---

## 4. Tương thích với Konsist & Quality Gate

Đã kiểm tra kỹ các bài test kiến trúc trong `app/src/test/java/io/github/toanhp123/finote/architecture/`:
1. **`LayerDependencyKonsistTest`**:
   - Sử dụng `it.packageName().inPackage(pkg)` để kiểm tra ranh giới giữa các tầng (`DOMAIN`, `DATA`, `UI`, `AUTOMATION`, `WORK`, `DI`).
   - Vì `inPackage` khớp cả tiền tố (`pkg == name || name.startsWith("$pkg.")`), nên mọi sub-package con như `ui.common.component.control` hay `ui.cashentry.component` đều **hoàn toàn hợp lệ và pass 100%**.
2. **`DesignSystemKonsistTest`**:
   - `filesIn(UI_COMPONENT)` và `filesIn(UI_THEME)` cũng dựa trên `inPackage`. Các sub-package vẫn được áp dụng đầy đủ luật của design system (bắt buộc `@FinotePreviews`, cấm hardcode raw `dp`/`Color`).
3. **`ResponsibilityKonsistTest`**:
   - Các luật về số dòng (tối đa 400 dòng/file, 80 dòng/composable, 120 dòng/screen) và quy tắc 1 public type cho các role cốt lõi không bị ảnh hưởng.

---

## 5. Kết luận & Hướng giải quyết tiếp theo

- **Nhận định**: Cấu trúc hiện tại không sai về mặt nguyên tắc kiến trúc Clean Architecture, nhưng đang gây bất tiện về mặt trải nghiệm đọc/quản lý code (đặc biệt tại `ui/common/component` với 30 files và `ui/common/theme` với 17 files).
- **Các bước triển khai** (mỗi bước một commit, chỉ di chuyển file và sửa package/import, không đổi logic):
  1. `ui/common/theme/color/` (mục 3.3).
  2. Phân nhóm `ui/common/component/` (mục 3.2).
  3. Thư mục `component/` cho `cashentry` và `transactions` (mục 3.4).
  4. Cập nhật `docs/architecture.md` (luật 3.1, mẫu feature) và `docs/design-system.md` (đường dẫn component mới).
  5. Chạy `qualityGate` qua skill `gradle-run` để xác nhận test, format và Konsist đều xanh.
- **Rủi ro**: sửa import ở mọi màn hình đang dùng component/theme. Không đổi hành vi.
