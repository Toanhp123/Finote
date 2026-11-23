# Edit Sheet In App Implementation Plan

> **For agentic workers:** `coder` làm cả plan trong một lượt, không commit. Bước dùng `- [ ]`.

**Goal:** Sửa giao dịch từ danh sách mở sheet nhập tiền ngay trong `MainActivity`, không `startActivity`.

**Architecture:** `EditTransaction(id)` là một `NavKey` trên back stack. Một `SceneStrategy` nhỏ biến entry trên cùng có metadata "sheet" thành `OverlayScene`, nên danh sách bên dưới vẫn được vẽ. Entry gọi `CashEntryRoute`; `FinoteBottomSheet` tự mở cửa sổ modal của nó trong cùng Activity. `rememberViewModelStoreNavEntryDecorator` (đã có) cho mỗi entry một `ViewModelStore` riêng và xóa nó khi key rời stack. `CashEntryViewModel` nhận id qua Hilt assisted injection: một cách cho cả hai host.

**Tech Stack:** Navigation3 1.2.0, Hilt 2.60.1 (`@AssistedInject`), androidx.hilt 1.4.0 (`hiltViewModel(creationCallback)`), Robolectric + compose ui-test.

**Spec:** brief của phiên (không có file spec). Nhánh `fix/edit-sheet-in-app`.

## Global Constraints

- Sheet không đổi hình, không đổi hành vi thêm/sửa/xóa. Ảnh screenshot không sinh lại.
- Không thêm Activity. Không thêm dependency. Tile và shortcut vẫn mở `CashEntryActivity` ở task riêng.
- File ≤ 400 dòng (`CashEntryScreen.kt` đang 381: không thêm vào đó). Dialog/sheet ở file riêng.
- Không log gì về giao dịch. Tiền là `Long`.
- Codegraph index checkout chính, KHÔNG phải worktree này: đọc file bằng Read/Grep.

## Review Focus

- Chạm hai lần nhanh vào một dòng (hoặc hai dòng khác nhau): một sheet, một ViewModel. Test ở Task 2.
- Mở id A, đóng, mở id B: form của B, ViewModel của A đã `onCleared`. Test ở Task 3.
- Thanh nav dưới không biến mất khi sheet mở trên tab Giao dịch. Test ở Task 2 (`isTabBarVisible`).
- Dòng không sửa được (`CashEntryEffect.Close` khi đang loading): key bị gỡ, không kẹt overlay rỗng. Test ở Task 3.
- Process death khi sheet mở: stack khôi phục có `EditTransaction`, sheet mở lại ở chế độ sửa. Không test tự động; ghi vào báo cáo.

---

### Task 1: ViewModel nhận id bằng assisted injection

**Files:**
- Modify: `ui/cashentry/CashEntryViewModel.kt`, `ui/cashentry/CashEntryActivity.kt`
- Delete: `test/.../ui/cashentry/CashEntryActivityTest.kt`
- Test: `CashEntryViewModelTest.kt`, `CashEntryEditViewModelTest.kt` (đổi cách dựng)

**Interfaces — Produces:**
- `@HiltViewModel(assistedFactory = CashEntryViewModel.Factory::class) class CashEntryViewModel @AssistedInject constructor(@Assisted transactionId: String?, …use case như cũ…)`. Bỏ tham số `SavedStateHandle`.
- `@AssistedFactory interface Factory { fun create(transactionId: String?): CashEntryViewModel }`

- [ ] Đổi hai test class: `open(transactionId: String? = …)` thay cho `SavedStateHandle`. Chạy, thấy lỗi biên dịch.
- [ ] Sửa ViewModel theo Interfaces.
- [ ] `CashEntryActivity`: `by viewModels(extrasProducer = { defaultViewModelCreationExtras.withCreationCallback<CashEntryViewModel.Factory> { it.create(null) } })`. Xóa `EXTRA_TRANSACTION_ID`, `editIntent`, `shouldRestartFor`, `onNewIntent` (Activity chỉ còn chế độ thêm; `singleTask` dùng lại instance là đúng hành vi cũ của `null → null`).
- [ ] Test hai class xanh.

### Task 2: NavKey, back stack, scene strategy

**Files:**
- Modify: `ui/navigation/NavKeys.kt`, `ui/navigation/NavBackStack.kt`
- Create: `ui/navigation/SheetSceneStrategy.kt`
- Test: Create `test/.../ui/navigation/EditTransactionNavigationTest.kt`

**Interfaces — Produces:**
- `@Serializable data class EditTransaction(val id: String) : NavKey`
- `fun openEditor(backStack: MutableList<NavKey>, id: String)` — không làm gì khi key trên cùng đã là `EditTransaction` (bất kể id).
- `fun closeEditor(backStack: MutableList<NavKey>, key: EditTransaction)` — `backStack.remove(key)`; gọi hai lần vô hại.
- `fun isTabBarVisible(backStack: List<NavKey>): Boolean` — key cuối cùng không phải `EditTransaction` nằm trong `TopLevelTabs`.
- `class SheetSceneStrategy<T : Any> : SceneStrategy<T>` với `companion fun sheet(): Map<String, Any>` (metadata). Trả `OverlayScene` khi entry cuối có metadata đó: `entries = [last]`, `overlaidEntries = previousEntries = entries.dropLast(1)`, `content = { last.Content() }`. Ngược lại trả `null`. Coder đối chiếu chữ ký với nguồn `DialogSceneStrategy` của navigation3-ui 1.2.0 trong Gradle cache.

- [ ] Test (JVM thuần, kiểu `SetupNavigationTest`):
  - `openEditor` hai lần với `"a"` rồi `"b"` → stack `[Overview, Transactions, EditTransaction("a")]`.
  - `closeEditor` → `[Overview, Transactions]`; gọi lần hai không đổi.
  - mở `"a"`, đóng, mở `"b"` → key trên cùng `EditTransaction("b")`.
  - `isTabBarVisible`: true cho `[Overview, Transactions, EditTransaction("a")]` và `[Overview]`; false cho `[Overview, Settings, AccountsSettings]`.
- [ ] Viết code, test xanh.

### Task 3: Host trong app

**Files:**
- Modify: `ui/navigation/FinoteNavHost.kt`, `ui/transactions/TransactionListScreen.kt`
- Test: Create `test/.../ui/navigation/EditTransactionHostTest.kt` (Robolectric + `createComposeRule`)

- [ ] `TransactionListRoute` thêm tham số `onOpenEditor: (String) -> Unit`; `OpenEditor` gọi nó. Bỏ `LocalContext` và import `CashEntryActivity`.
- [ ] `FinoteNavHost`: `sceneStrategy = remember { SheetSceneStrategy() }`; thanh nav dùng `isTabBarVisible(backStack)`; `onOpenEditor = { openEditor(backStack, it) }`; thêm
  `entry<EditTransaction>(metadata = SheetSceneStrategy.sheet()) { key -> CashEntryRoute(viewModel = hiltViewModel<CashEntryViewModel, CashEntryViewModel.Factory>(creationCallback = { it.create(key.id) }), onClose = { closeEditor(backStack, key) }) }`.
- [ ] Test host, không Hilt: `NavDisplay` với cùng hai decorator + `SheetSceneStrategy`, entry dưới là một `Text("list")`, entry `EditTransaction` dùng `viewModel { Probe(key.id) }` (ViewModel giả ghi `id` và cờ `cleared` trong `onCleared`) và vẽ `Text(vm.id)`:
  - mở `"a"` → thấy cả `"list"` và `"a"`;
  - `closeEditor`, mở `"b"` → thấy `"b"`, Probe là instance khác, Probe `"a"` đã `cleared`.
- [ ] Test đóng, `CashEntryRoute` với ViewModel thật dựng bằng fake sẵn có trong `CashEntryEditViewModelTest`/`CashEntryViewModelTest` (tách helper dùng chung nếu cần, không chép): `onClose` được gọi đúng một lần sau `Save` thành công; sau `Dismissed`; và ngay khi dòng không tồn tại (effect `Close`). Tham khảo `FinoteBottomSheetTest` về cách chờ animation dưới Robolectric. Nếu cửa sổ modal không chạy được dưới Robolectric, báo lại thay vì giả lập.
- [ ] `testDebugUnitTest` qua `gradle-run`, xanh.

Không làm: gộp với nhánh `fix/edit-sheet-ux`, đổi `FinoteBottomSheet`, đổi manifest.
