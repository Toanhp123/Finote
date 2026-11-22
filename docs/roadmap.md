# Roadmap — Finote

Bản v2, viết lại ngày 2026-11-15 sau khi rà toàn bộ luồng tiền và spec. Bản cũ (chi tiết các nhánh đã xong, ghi chú nghiên cứu SQLCipher, bảng nợ P2a gốc): `docs/roadmap-history.md`.

Nguồn: spec `docs/Tài liệu Đặc tả...(MVP).txt` mục 2.2, 3, 4, 5–9; `CLAUDE.md`; `docs/git-workflow.md`; `docs/architecture.md`.

## 1. Hiện trạng (2026-11-18)

- Đã xong và nằm trên `dev`: P0 (nền tảng), P1 (lõi dữ liệu, nhập tay), P2a–P2c (bắt thông báo, parser, OTA, đối ánh tài khoản, khử trùng, số dư đầu tự động, gộp và bỏ qua tài khoản), nâng cấp clean architecture R1–R6, dọn dẹp nhánh 1–6. Chi tiết ở mục 8.
- Bước 1–8 xong (2026-11-18). Đang làm: bước 9 (dogfood) ở mục 5.
- Mẫu thật chỉ gom được của Techcombank (2026-11-22): user không có tài khoản VCB, MB, MoMo.
- Chưa từng chạy trên máy thật: toàn bộ P2. Bộ mẫu thông báo là mẫu tổng hợp (D4).
- DB version 8, có migration thật: từ v8, thiếu migration hoặc hạ version thì app crash và giữ file; chỉ v1–v7 bị xóa.
- Màn hình đang có: danh sách giao dịch, sheet nhập tiền mặt (Chi, Thu, Chuyển; có chế độ sửa và xóa giao dịch), setup quyền, Cài đặt (bắt thông báo, tài khoản: gộp, bỏ qua). Chưa có: tạo và sửa tài khoản, quản lý danh mục, dashboard.

## 2. Nguyên tắc làm việc

- Mỗi tính năng là một nhánh từ `dev`, squash vào `dev`. `reviewer` chạy một lần cho mỗi nhánh, trước khi squash.
- Thay đổi logic (parser, dedup, pairing, balance) phải chạy `testDebugUnitTest` qua `gradle-run`.
- Tiền là `Long` VND. Mọi thay đổi số dư và `AccountBalanceSnapshot` nằm trong một transaction.
- Xóa mềm bằng `deletedAt`. Chỉ WorkManager dọn thùng rác mới xóa cứng.
- Không log nội dung thông báo, số dư, số tài khoản. Fixture test dùng dữ liệu giả.
- Instrumented test chỉ chạy khi user yêu cầu. Test Room chạy trong `testDebugUnitTest`.
- **Mới:** thứ tự nhánh theo ba ưu tiên: (1) đúng tiền, (2) nền móng dùng chung làm trước tính năng dùng nó, (3) Must-Have trước Should-Have.
- **Mới:** mỗi nhánh tính năng ghi rõ nó dùng nền móng nào ở mục 4. Nếu phải sửa nền móng thì sửa ở nhánh riêng trước, không vá trong nhánh tính năng.

## 3. Mốc phát hành

| Mốc | Nội dung | Điều kiện |
|---|---|---|
| v0.2 | Tự động bắt giao dịch, đã thử trên máy thật | Hết bước 12 ở mục 5, DoD spec mục 6 |
| v0.3 | Đủ Must-Have về UI: tài khoản, danh mục, dashboard; ngân sách, tìm kiếm | Hết bước 21 |
| v0.4 | Hoàn tiền, thẻ tín dụng, định kỳ, import/export, thùng rác | Hết bước 27 |
| v1.0 | Khóa app, hiệu năng, onboarding quyền, đạt spec mục 5–9 | Hết bước 33 |
| v1.x | Could-Have | Sau v1.0 |

Mỗi mốc: `release/x.y.z` từ `dev` → `main` (`--no-ff`) → tag → `dev` (`--no-ff`). v0.1 không phát hành riêng.

## 4. Phân tích kiến trúc: nền móng dùng chung

Rà code ngày 2026-11-15. Mỗi dòng là một chỗ mà nhiều tính năng sau cùng dựa vào. Làm sai thứ tự thì tính năng sau phải viết lại tính năng trước.

| # | Nền móng | Hiện tại | Tính năng cần nó | Làm ở |
|---|---|---|---|---|
| N1 | **Một đường ghi sổ** | Xong: mọi dòng sổ chèn qua `LedgerWriter.insert`; `book` dựng `Transaction` và chạy `TransactionRules` trước khi ghi | Ghép chuyển khoản, failed-inbox, định kỳ, import, hoàn tiền, tự gán danh mục, FTS | Bước 2 |
| N2 | **Form giao dịch dùng chung** | Sheet tạo mới Chi, Thu, Chuyển (đích, phí). Đã có chế độ sửa | Chuyển, sửa giao dịch, failed-inbox (điền sẵn từ log), hoàn tiền, nhãn, tách giao dịch | Bước 3, 4 |
| N3 | **Bộ so khớp** (matcher) | Hai bộ: `SettlementPlanner` (ngân hàng + ví, cùng chiều, 30 giây, trước khi ghi) và `TransferPairing.kt` (chuyển khoản, ngược chiều, 30 phút, khi ghi dòng thứ hai) | Ghép chuyển khoản; so "ứng viên với sổ" cho bóc lại log, failed-inbox, định kỳ, import | Bước 5 (tách vị từ), bước 11 (ứng viên với sổ) |
| N4 | **Migration thật** | Xong: `applyMigrationPolicy`, test `MigrationPolicyTest` | Mọi nhánh thêm bảng hoặc cột sau khi có dữ liệu thật | Bước 6 |
| N5 | **Định danh tài khoản** | Xong: bảng `account_identifiers` `(packageName, kind, mask)`; gộp chuyển định danh | Thẻ ghi nợ, MoMo nguồn ngân hàng, màn tài khoản (liên kết 4 số cuối), thẻ tín dụng, thêm ngân hàng | Bước 10 |
| N6 | **Lõi báo cáo** (`domain/report`) | Chưa có. Tổng thu chi trong snapshot gồm cả điều chỉnh, phí, hoàn tiền; không biết `excludeFromAnalytics` | Dashboard, ngân sách, hoàn tiền, merchant, tách giao dịch | Bước 17 |
| N7 | **Thông báo của app** (kênh, quyền `POST_NOTIFICATIONS`) | Chưa có | Cảnh báo ngân sách, nhắc hóa đơn định kỳ, nhắc đòi tiền chi hộ | Bước 18 |
| N8 | **Ghi theo lô** | Mỗi lần ghi tính lại toàn bộ tài khoản, O(số giao dịch) | Import (nghìn dòng), định kỳ, hiệu năng 50.000 giao dịch | Bước 25 (lô), bước 30 (tính lại từ tháng sửa) |

Quy ước rút ra:
- **Snapshot chỉ phục vụ số dư và tài sản ròng.** Thu chi cho báo cáo đọc từ giao dịch qua N6. Không thêm cột báo cáo vào snapshot.
- **Bộ đọc snapshot phải tự kéo số dư tháng trước** cho tháng thiếu dòng (snapshot chỉ ghi khi có giao dịch).
- **Nơi duy nhất ẩn số dư là `MoneyText`.** Khóa app (bước 28) không phải sửa từng màn.
- **Export làm cuối P4**, khi schema đã ổn định; sao lưu Drive (P6) dùng lại định dạng export.

## 5. Lộ trình

Trạng thái: ✅ xong, 🔧 đang làm, ⬜ chưa làm, ⏸ chờ điều kiện.

### Đoạn A — Chốt v0.2 (Automation Core)

| # | Nhánh | Loại | Dùng | Trạng thái |
|---|---|---|---|---|
| 1 | `chore/ui-components` | Dọn dẹp 5 (A12–A15) | | ✅ `c635487` |
| 2 | `chore/ledger-write-path` | Nền móng N1 | | ✅ `5fd95a6` |
| 3 | `feature/cash-transfer` | Việc tồn P1 | N1, N2 | ✅ |
| 4 | `feature/transaction-edit` | Must-Have (CRUD giao dịch) | N1, N2 | ✅ |
| 5 | `feature/transfer-pairing` (P2d) | Must-Have | N1, N3 | ✅ |
| 6 | `chore/db-migrations` | Nền móng N4 | | ✅ |
| 7 | `chore/release-readiness` | Dọn dẹp 7 (A4, A17) | | ✅ |
| 8 | `feature/debug-log-screen` | Công cụ (bản debug và dogfood) | | ✅ |
| 9 | **Dogfood 1 tuần trên máy thật** | Cổng, không phải nhánh | | 🔧 |
| 10 | `fix/account-identity` | Sửa lỗi tiền L1, L2; nền móng N5 | N4 | ✅ N5 (L1, L2, N-06 ⏸ chờ mẫu VCB, MoMo) |
| 11 | `fix/double-booking` | Sửa lỗi tiền L3, L4 (A5); N3 | N1 | ⬜ dùng mẫu Techcombank |
| 12 | `release/0.2.0` | | | ⬜ |

**2. `chore/ledger-write-path`** — một cửa ghi cho mọi nguồn (tay, thông báo, điều chỉnh).
- `book` của settlement dựng `Transaction` domain và đi qua cùng rule và cùng hàm ghi với nhập tay. Không tự dựng `TransactionEntity`.
- Model cấm `toAccountId` ở giao dịch không phải `TRANSFER`. SQL "chạm tài khoản" kiểm `type = 'TRANSFER'` cho vế `toAccountId`, khớp `Transaction.touches`.
- Không đổi hành vi. Test hiện có phải xanh nguyên.
- Lý do làm trước: bước 3, 4, 5 đều thêm người ghi mới.

**3. `feature/cash-transfer`** — thêm Chuyển vào sheet (tài khoản đích, phí).
- Sheet trở thành form giao dịch dùng chung (N2): Chi, Thu, Chuyển.
- Lý do: rút ATM là cách tiền vào ví tiền mặt; ví tiền mặt không có thông báo nên P2d không ghép được.

**4. `feature/transaction-edit`** — mở một giao dịch, sửa số tiền, ngày, tài khoản, danh mục, ghi chú; xóa mềm.
- Dùng lại form của bước 3 ở chế độ sửa. `UpdateTransactionUseCase` đã có.
- Chốt chặn tổ hợp sai (`TransactionError.InvalidTransfer`) hiện nằm trong `AddManualTransactionUseCase`. Đường sửa dựng `Transaction` ở chỗ khác nên phải dùng chung chốt chặn đó, không để `Transaction.init` ném lỗi.
- Bước 3 chưa thử trên máy thật: bàn phím số và focus ở trang phí, TalkBack đọc ô đích và ô phí, sheet cao lên ở chế độ Chuyển. Thử cùng lúc với form sửa.
- Giữ nguyên `postBalance`, `source`, `notificationLogId` khi sửa.
- `postBalance` bị xóa khi đổi tài khoản hoặc ngày.
- Lý do đặt trước dogfood: không sửa được thì không chữa được giao dịch tự bắt sai.

**5. `feature/transfer-pairing` (P2d, Module 7)** — spec `docs/superpowers/specs/2026-11-17-transfer-pairing-design.md`.
- Ghi sổ như cũ (chờ 30 giây), rồi ghép ngay trong transaction ghi dòng thứ hai: một trừ ở tài khoản A và một cộng cùng số tiền ở tài khoản B, cách nhau tối đa 30 phút (hai chiều) → một `TRANSFER`, hai dòng gốc xóa mềm với `pairedIntoId`, log `PAIRED_TRANSFER`. Chịu được máy giữ thông báo lâu (Xiaomi).
- Chỉ ghép khi bằng tiền, cả hai dòng từ thông báo và chưa bị sửa; không ghép thẻ tín dụng. Phí nhập tay.
- Cột `toPostBalance`, `toTimestamp`: mốc số dư bên nhận, chỉ dùng khi thứ tự trong sổ khớp thứ tự thật (kiểm lúc đọc).
- Nút "Không phải chuyển khoản" trong sheet sửa tách lại hai dòng gốc; hai dòng đó không bao giờ tự ghép lại. Nhãn "Tự ghép" trên list.
- N-18: thông báo có giờ vô lý `FAILED` ngay lúc lập kế hoạch (`invalid_time`).
- Bộ so khớp nằm ở `TransferPairing.kt` (`pairingTime` là chỗ duy nhất đổi khi đọc giờ ngân hàng).

**6. `chore/db-migrations`** — bỏ `fallbackToDestructiveMigration`.
- Schema sau bước 5 là mốc gốc. Từ đây mỗi thay đổi có migration và test migration.
- Đặt ngay trước dogfood để dữ liệu thật không mất.

**7. `chore/release-readiness`** (A4, A17) — `assembleRelease` trong gate, keep rule cho Room, serialization, SQLCipher JNI; loại `app_setup.xml` khỏi backup.
- Kết quả: không cần thêm keep rule, bản release build qua R8. Lý do: `optimization.packageScope` chỉ gồm `androidx`, `kotlin`, `kotlinx`, nên R8 không đụng code của app, Dagger, SQLCipher. Nới `packageScope` thì phải thêm keep rule cho Room `_Impl`, Hilt và tên lớp worker trước.
- Chưa chạy APK release trên máy thật: bước 9 xác nhận.

**8. `feature/debug-log-screen`** — màn chỉ có ở bản debug và dogfood, xem `notification_logs` (trạng thái, lý do lỗi, rule khớp). Nội dung vẫn không ra Logcat.

**9. Dogfood** — cài bản release lên máy thật, dùng 1 tuần. Việc phải làm:
- Cài bản `dogfood` (`assembleDogfood`): giống release, ký bằng khóa debug. Bản release ký khóa Play sau này không cài đè được lên bản này.
- Gom mẫu thật (đã ẩn danh) của Techcombank vào corpus. User không có tài khoản VCB, MB, MoMo.
- Mẫu Techcombank kiểm: L3 (thông báo đăng lại, thêm dòng; A5), N-03, N-05, N-20, N-23, APK release trên máy thật.
- L1, L2, N-06 không kiểm được bằng Techcombank. Xin mẫu VCB (thẻ ghi nợ) và MoMo (nguồn ngân hàng) từ người quen, dạng ảnh chụp, che số tài khoản. Không có thì để mở, không chặn v0.2.
- App không đẩy thông báo ra ngoài thì nhập tay (D15).
- Lưu ý: bản cài ngoài Play trên Android 13+ cần "Allow restricted settings" mới cấp được quyền đọc thông báo.

**10. `fix/account-identity`** (L1, L2, nợ N-01, N-02, N-05, N-06, N-08; N5).
- Một tài khoản có nhiều định danh: số tài khoản, số thẻ, số ví. Đề xuất bảng định danh riêng thay cho một cặp cột trên `Account`; gộp tài khoản thành chuyển định danh (D13).
- Rule nói rõ 4 số cuối là của ai: tài khoản, thẻ, hay nguồn tiền của ví.
- Làm ngay (không cần mẫu ngân hàng khác): bảng định danh (D13), N-01, N-02, N-05, N-08, N-21. Thiết kế chốt trên mẫu Techcombank.
- Chờ mẫu VCB, MoMo: L1, L2, N-06. Bảng định danh phải để sẵn chỗ cho số thẻ và nguồn tiền của ví. Chưa có mẫu thì làm ở nhánh sau, không chặn bước 12.
- Màn tài khoản (bước 14) và thẻ tín dụng (bước 23) xây trên mô hình này.
- Đã làm (spec `docs/superpowers/specs/2026-11-22-account-identity-design.md`): bảng `account_identifiers` `(packageName, kind, mask)`, migration 8 → 9, rule có `maskKind`, gộp chuyển định danh và chỉ gộp cùng loại, writer chọn lại tài khoản trong transaction ghi; N-01 (phần `parseMoney`), N-02, N-05, N-08, N-21. Còn chờ chạy `EncryptedDatabaseTest` trên máy thật để xác nhận nâng cấp SQLCipher.

**11. `fix/double-booking`** (L3, L4; thay cho `fix/notification-text-lines`).
- Nhận thông báo đăng lại theo khóa thông báo, không đòi nội dung giống hệt.
- So "ứng viên với sổ" (N3): trước khi ghi một log cũ, kiểm đã có dòng điều chỉnh bù cho nó chưa. Bóc lại log, failed-inbox, định kỳ và import dùng chung hàm này.

### Đoạn B — v0.3 (đủ Must-Have, báo cáo)

| # | Nhánh | Loại | Dùng | Trạng thái |
|---|---|---|---|---|
| 13 | `feature/failed-inbox` | Should-Have | N1, N2, N3 | ⬜ |
| 14 | `feature/account-manage` | Must-Have | N5 | ⬜ |
| 15 | `feature/category-ui` | Must-Have | | ⬜ |
| 16 | `feature/merchant-normalize` | Should-Have | N1 | ⬜ |
| 17 | `feature/dashboard` | **Must-Have** (bản cũ ghi nhầm Should-Have) | N6 | ⬜ |
| 18 | `feature/budget` | Should-Have | N6, N7 | ⬜ |
| 19 | `feature/net-worth` | Should-Have | | ⬜ |
| 20 | `feature/tags`, `feature/fts-search` | Should-Have | N2 | ⬜ |
| 21 | `release/0.3.0` | | | ⬜ |

- **13. `failed-inbox`**: liệt kê log `FAILED`, bấm để mở form điền sẵn (N2) thành giao dịch tay. Kèm bộ dạy mẫu: bôi đen số tiền, số dư, 4 số cuối; app sinh regex `isUserCustom = true` rồi gọi `ReparseFailedLogsUseCase`. Gộp `feature/regex-sandbox` của bản cũ vào đây. Còn phải bàn: ngân hàng chưa có rule bị `NotTracked` nên không có dòng `FAILED` để dạy.
- **14. `account-manage`**: tạo, sửa tài khoản; liên kết app ngân hàng + 4 số cuối (N5); `setInitialBalance` (đặt `isInitialized = true` và tính lại); gắn sao tài khoản chính (`isPrimary`); gợi ý gộp khi tài khoản tự tạo giống tài khoản cũ; nhóm tài khoản theo ngân hàng: dòng nhóm hiện tổng số dư (cộng các tài khoản cùng app, không lưu), bấm vào mở các tài khoản con, mỗi tài khoản có số dư riêng (ý user 2026-11-22).
- **15. `category-ui`**: quản lý cây 2 cấp, biểu tượng, màu.
- **16. `merchant-normalize`**: bảng `CategoryKeywordRule`; tự gán danh mục và chuẩn hóa tên cửa hàng ngay trên đường ghi sổ (N1). Đặt trước dashboard, vì giao dịch từ thông báo hiện không có danh mục thì biểu đồ theo danh mục vô nghĩa.
- **17. `dashboard`**: dựng `domain/report` (N6) rồi mới vẽ. Luật thu chi: bỏ `TRANSFER`, `ADJUSTMENT`, dòng `excludeFromAnalytics`; phí tính là chi; để sẵn chỗ cho hoàn tiền giảm chi. Biểu đồ: Vico + Compose Canvas.
- **18. `budget`**: bảng `Budget`; chi theo danh mục lấy từ N6; kết chuyển `carryOverAmount`; cảnh báo tại ngưỡng và 100%, dùng `lastAlertSentThreshold`. Dựng N7 ở đây.
- **19. `net-worth`**: đọc `AccountBalanceSnapshot`, tự kéo số dư cho tháng thiếu dòng; loại `excludeFromNetWorth`, tài khoản bỏ qua.
- **20. `tags`, `fts-search`**: gắn nhãn trong form (N2); `TransactionFts` trên `merchantName`, `note` (D1).

DoD (spec mục 8): tìm kiếm dưới 50 ms và biểu đồ tài sản ròng dưới 16 ms với 50.000 giao dịch, đo trên máy thật (D7).

### Đoạn C — v0.4 (nghiệp vụ nâng cao, dữ liệu)

| # | Nhánh | Dùng | Ghi chú |
|---|---|---|---|
| 22 | `feature/refund` | N1, N2, N6 | `REFUND`, `refundForTransactionId`; giảm chi của danh mục gốc, không cộng vào thu |
| 23 | `feature/credit-card` | N5, bước 5 | `creditLimit`, `statementDay`, `dueDay`; trả nợ thẻ là một `TRANSFER`; chốt cách dùng "hạn mức còn lại" làm mốc |
| 24 | `feature/recurring-plan` | N1, N3, N7 | Chốt D11 trước; giao dịch tự sinh phải so với giao dịch từ thông báo để không trùng |
| 25 | `feature/import-csv` → `import-xlsx` | N1, N3, N8 | Ghi theo lô, tính lại số dư một lần; so ứng viên với sổ |
| 26 | `feature/trash-purge` | | WorkManager xóa cứng mục `deletedAt` quá 30 ngày; kèm màn thùng rác và khôi phục |
| 27 | `feature/export` → `release/0.4.0` | | `.json` mã hóa và `.xlsx`; làm cuối để schema đã ổn định |

### Đoạn D — v1.0 (production)

| # | Nhánh | Ghi chú |
|---|---|---|
| 28 | `feature/app-lock` | Must-Have. `BiometricPrompt`, ẩn số dư qua `MoneyText`, `FLAG_SECURE`, làm mờ ở Recent Apps |
| 29 | `feature/onboarding-permission` | Hướng dẫn Android 13+ và tắt tối ưu pin; thêm bước vào màn setup có sẵn; sửa nợ N-13 |
| 30 | `feature/perf` | Tính lại số dư từ tháng bị sửa (N8); báo lệch bằng truy vấn DAO; cold start dưới 1,2 giây; nhập tiền mặt dưới 0,5 giây; sửa nợ N-09, N-14 |
| 31 | Chính sách quyền riêng tư | Bắt buộc trước khi lên Google Play |
| 32 | `chore/audit` | Test đối soát: công thức số dư khớp mọi tài khoản, mọi thời điểm; chặn xóa DB khi mất khóa sau v1.0; sửa nợ N-12, N-15 |
| 33 | `release/1.0.0` | Đạt toàn bộ spec mục 5–9 |

### Đoạn E — sau v1.0 (Could-Have)

- Sao lưu Google Drive (dùng định dạng export).
- Thêm rule cho ACB, VPBank, TPBank, BIDV, VietinBank, ZaloPay, ShopeePay.
- Tách giao dịch (`isSplitParent`, `parentTransactionId`) và ngoại tệ (`originalAmount`, `exchangeRate`). Cả hai đổi công thức ở N6 và bộ tính số dư.
- Đính kèm ảnh hóa đơn.

Won't-Have trong MVP: OCR hóa đơn, sổ nợ và chia tiền nhóm, đồng bộ đa thiết bị, chứng khoán, vàng, crypto.

## 6. Sổ nợ

Một chỗ duy nhất. Mỗi dòng có nhánh sẽ sửa. Sửa xong thì xóa dòng.

### Lỗi có thể sai tiền (rà ngày 2026-11-15)

| Mã | Vấn đề | Chỗ | Sửa ở |
|---|---|---|---|
| L1 | Thẻ ghi nợ VCB: rule `vcb_card` kiểu ngân hàng, lấy 4 số cuối của thẻ và đọc "Số dư khả dụng" → tạo tài khoản thứ hai có số dư đầu bằng số dư tài khoản chính; tài sản tính hai lần. Suy từ regex, cần mẫu thật | `SystemParserRule.kt`, `AccountResolver.kt` | Bước 10 |
| L2 | MoMo trả bằng nguồn ngân hàng: 4 số cuối là của nguồn tiền → tạo ví MoMo thừa. Thông báo ngân hàng trễ quá 30 giây hoặc ngân hàng chưa có rule thì khoản chi ghi hai lần hoặc vào ví sai, không có cảnh báo. Có hai tài khoản MoMo thì thông báo nhận tiền (không có 4 số cuối) bị `ambiguous_account`. Cần mẫu thật. Ưu tiên thấp: MoMo có thể không đẩy thông báo hệ thống (D15) | `SystemParserRule.kt` (`MOMO_PAY_SOURCE`), `AccountResolver.kt` | Bước 10 |
| L3 | A5: nhận thông báo đăng lại đòi nội dung giống hệt. Thông báo thêm dòng, hoặc không có số dư và đăng lại sau 3 giây, bị ghi hai lần | `SettlementPlanner.kt` (`sameText`, `findReposts`) | Bước 11 |
| L4 | Bóc lại log `FAILED` sau khi user đã bấm Điều chỉnh: khoản tiền tính hai lần; báo lệch im vì có dòng điều chỉnh sau mốc | `ReparseFailedLogsUseCase.kt`, `BalanceDriftDetector.kt` | Bước 11 |

### Nợ thiết kế (phải chốt trước nhánh dùng nó)

| Mã | Vấn đề | Sửa ở |
|---|---|---|
| T2 | Giao dịch ngày tương lai (tới 366 ngày) tính ngay vào số dư hiện tại | D11, trước bước 24 |
| T3 | Tổng thu chi trong snapshot gồm điều chỉnh, phí, hoàn tiền | Bước 17 (báo cáo không đọc snapshot) |
| T4 | Không có khóa chống trùng giữa import, định kỳ và thông báo | Bước 11 dựng hàm, bước 24, 25 dùng |

### Nợ nhỏ

| Mã | Vấn đề | Sửa ở |
|---|---|---|
| N-01 | Rule hệ thống chưa đọc được số dư âm (thấu chi): `MONEY` không bắt dấu `-`. `parseMoney` đã sẵn sàng | Khi có mẫu thấu chi thật (thêm `-?` vào nhóm balance, tăng version) |
| N-03 | Hai thông báo đến sai thứ tự gây báo lệch giả (sổ xếp theo `postTime`) | Bước 9 xem có xảy ra không |
| N-06 | `vcb_card` có thể đọc hạn mức thẻ tín dụng thành số dư (nợ #8 cũ) | Bước 10 |
| N-07 | Rule `isActive = false` vẫn lọc và vẫn tính để quyết định `NotTracked` | Bước 13 |
| N-09 | `HttpParserRuleFeed` đọc chặn trên dispatcher của worker | Bước 30 |
| N-10 | Màn Tài khoản gom mọi lỗi thành một snackbar; sheet không tự cập nhật khi danh sách đổi | Bước 14 |
| N-11 | Fake `FakeRuleUpdateSettingsRepository` mặc định bật tự cập nhật (thật: tắt); `CategoryDao.observeCategories` thiếu tie-break `id`; Konsist lách được bằng tên đầy đủ và hàm top-level trong `di` | Bước 15 (DAO), còn lại khi chạm tới |
| N-12 | Konsist chưa bắt `useWriterConnection { withTransaction }` và `::StorageException`; `return` trong `immediateTransaction` bỏ qua COMMIT | Bước 32 |
| N-13 | `SetupEffect.Finished` là tên sự kiện; tên ngân hàng viết cứng trong 4 file strings (nợ #5, #7 cũ) | Bước 29 |
| N-14 | `MainActivity` inject thẳng `SetupRepository`, đọc prefs trên main thread; `settleNow()` chạy mỗi `onCreate` (nợ #6 cũ) | Bước 30 |
| N-15 | Hash dòng bí mật dò ngược được OTP 6 số (nợ #4 cũ); OTA một khóa ghim, chưa xoay khóa | Bước 32 |
| N-16 | Chuỗi `ja`, `zh-rCN` do máy viết, cần người bản ngữ xem | Trước bước 33 |
| N-17 | Mục nhỏ của audit 2026-11-10: UI không dùng (`FinoteDatePicker`; `BalanceHeroCard` giữ cho bước 17), test mẫu, dependency thừa, test dùng đồng hồ thật | Bước 32 |
| N-19 | Dòng gốc bị ẩn vì ghép (`pairedIntoId`) và chuyển khoản đã tách: thùng rác phải ẩn; không dọn dòng gốc khi `T` còn sống, dọn cùng `T` | Bước 26 |
| N-20 | Ghép theo giờ thông báo đến máy; đọc giờ ngân hàng trong nội dung (nhóm `time`, định dạng từng ngân hàng, cột ở log, đổi `pairingTime`) | Sau bước 9 |
| N-22 | Tách lại bị từ chối vì tài khoản đã lưu trữ chỉ hiện lỗi lưu chung, không nói lý do (giống nút Xóa) | Bước 14 |
| N-23 | Listener chết trên Vivo/Xiaomi khi Autostart tắt: chỉ sống lại khi mở app (`rebindIfDisconnected`). Thông báo user xóa trước lúc đó thì mất, chỉ còn báo lệch số dư bắt được. Cần: màn Capture báo listener đang dừng (không chỉ quyền), hướng dẫn bật Autostart theo hãng máy | Bước 29 |
| N-24 | Nút Copy mẫu (`masked()`) chỉ che chữ số; tên người chuyển trong ghi chú vẫn lộ. Che tên chung thì phá dạng ghi chú (chữ hoa). Tạm thời: đổi tên bằng tay trước khi đưa mẫu vào corpus | Khi có cách che theo rule |
| N-25 | Hộp thoại gộp (`MergeDialog.kt`), `BalanceDriftBanner.kt` và `Selectors.kt` đưa số tiền vào câu bằng `formatMoney`, ngoài `MoneyText`; ẩn số dư ở bước 28 sẽ không che được | Bước 28 |

## 7. Quyết định

### Đã chốt

| Mã | Nội dung |
|---|---|
| D2 | Khóa SQLCipher: 32 byte ngẫu nhiên, bọc bằng Android Keystore (AES-GCM), không bắt sinh trắc học. DB và khóa bọc loại khỏi Auto Backup |
| D3 | `targetSdk` 37 (spec ghi 35) |
| D4 | Corpus tổng hợp ~60 mẫu làm baseline; chỉ tiêu 98% thật đo trên mẫu dogfood Techcombank (sửa 2026-11-22: không có mẫu VCB, MB, MoMo). Ghi chú v0.2: rule VCB, MB, MoMo chưa kiểm trên mẫu thật |
| D6 | Hilt |
| D8 | Room 3, Navigation3 |
| D9 | Ngôn ngữ: `vi` (mặc định), `en`, `zh-rCN`, `ja`. Màn chọn ngôn ngữ trong app chưa làm |
| D10 | Mất khóa Keystore: xóa DB và khóa bọc, tạo DB rỗng. Chi tiết từng loại lỗi ở `roadmap-history.md` |
| D12 | P2d: ghép sau khi ghi, cửa sổ 30 phút hai chiều; chỉ ghép khi bằng tiền; phí nhập tay |
| D13 | Định danh tài khoản: bảng riêng `account_identifiers` `(packageName, kind, mask)`, kind `ACCOUNT`/`CARD`/`WALLET`; rule khai `maskKind`, null thì suy từ `accountType`. Chốt 2026-11-22, spec `2026-11-22-account-identity-design.md` |
| D15 | App không đẩy thông báo hệ thống (chỉ báo trong app, như MoMo theo quan sát của user 2026-11-22) thì user nhập tay. Không tìm cách bắt khác. Rule MoMo giữ nguyên, chưa kiểm trên mẫu thật |

### Còn mở

| Mã | Câu hỏi | Đề xuất | Chốt trước |
|---|---|---|---|
| D1 | FTS4 hay FTS5? | FTS4, đủ cho 50 ms | Bước 20 |
| D5 | XLSX cùng lúc với CSV? | CSV trước | Bước 25 |
| D7 | Ai chạy benchmark trên máy thật? | User, khi tới bước 20 và 30 | Bước 20 |
| D11 | Giao dịch ngày tương lai có tính vào số dư hiện tại không? | Không: số dư hiện tại chỉ tính tới bây giờ | Bước 24 |
| D14 | Failed-inbox đưa vào v0.2 hay v0.3? | v0.3, để v0.2 chỉ gồm Must-Have | Bước 12 |

## 8. Đã xong

Chi tiết từng nhánh: `docs/roadmap-history.md` và `git log dev`.

| Phase | Nhánh | Commit trên `dev` |
|---|---|---|
| P0 | `gradle-catalog`, `quality-gate`, `app-shell`, `mvi-base` | xong 2026-11-02 |
| P1 | `db-schema`, `db-encryption`, `balance-engine`, `transaction-crud` | xong 2026-11-04 |
| P1 | `account-crud` | `443a03a` |
| P1 | `category-seed` | `2b2fa6f` |
| P1 | `design-system` | xong 2026-11-03 |
| P1 | `cash-entry` (Chi, Thu, Tile, Shortcut) | `7ee3df0` |
| P1 | `reconciliation` | `0629eb5` |
| P1 | `transaction-list` | `7d66551` |
| P1 | `reimbursement-flag` | `58dcfbd` |
| P2a | `notif-capture` | `1f03be2` |
| P2b-1 | `parser-engine` (12 rule; corpus VCB, MB, MoMo 100%) | `29a94e2` |
| P2b-2 | `rule-ota` (ký ECDSA, mặc định tắt) | `2956fae` |
| P2c | `account-dedup-balance` | `c5ea2b5` |
| P2c | `account-merge-ignore` | `0e68c87` |
| Dọn dẹp 1 | `fix/balance-adjust` (A1, A3) | `991301c` |
| Dọn dẹp 2 | `fix/settle-stuck-logs` (A2, A7) | `92cf798` |
| Dọn dẹp 3 | `chore/domain-account-rules` (A8, A9, A11, A20) | `03b3f1b` |
| Dọn dẹp 6 | `chore/test-hygiene` (A18, A19) | `7eb1b2e` |
| R1 | Thư mục theo vai trò cho domain và data | `f53ef23` |
| R2 | `TransactionRunner`, `StorageException` | `8396d68` |
| R3–R6 | Lỗi có kiểu (`Outcome`) cho danh mục, tài khoản, giao dịch, thông báo | `c8b3f06`, `2431e59`, `fb12678`, `8a6c77e` |
| | Test rollback khi tràn số cho mọi đường ghi | `57e9fa4` |
| Dọn dẹp 4 | `chore/ui-overlay-api` (A10, A6, A16) | `97278c5` |
| Dọn dẹp 5 | `chore/ui-components` (A12–A15) | `c635487` |

Ghi chú vận hành còn hiệu lực:
- Feed rule `rules/v1/feed.json` ký ECDSA P-256; khóa riêng ở `~/.finote/finote-rules.pem` (ngoài repo). Ký lại: `testDebugUnitTest --tests '*RuleFeedSigningTool*' -PsignRuleFeed=true`. Feed phải lên `main` cùng hoặc trước bản phát hành đầu có OTA.
- Test Room dùng DB không mã hóa; mã hóa kiểm bằng instrumented test.
- Nhánh local chờ user xóa (nếu còn): `chore/ui-components`, `chore/ui-overlay-api`, `chore/test-hygiene`, `chore/errors-transaction` (kiểm đã vào `dev` trước khi xóa).
