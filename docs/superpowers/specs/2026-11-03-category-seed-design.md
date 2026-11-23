# Thiết kế `feature/category-seed`

Ngày: 2026-11-03. Phase: P1 (roadmap mục 3, việc 6). Nguồn: spec mục 3.2 (dòng 83–92), Must-Have "Category Core" (dòng 293–295); `docs/architecture.md`; `docs/design-system.md`.

## 1. Mục tiêu

Seed cây danh mục hệ thống, CRUD danh mục với luật cây 2 cấp, ẩn danh mục.

Đã chốt với user:
- Không UI. Chỉ domain, data, DI, test. UI quản lý cây tách sang nhánh `feature/category-ui`.
- Bộ danh mục theo chuẩn quốc tế, không theo văn hóa chi tiêu VN. Lệch spec dòng 295 có chủ đích: app có 4 ngôn ngữ (D9). Roadmap mục 6 sửa theo.
- Ẩn qua cột mới `isHidden`. Mọi danh mục ẩn được. Danh mục hệ thống không xóa mềm được, chỉ ẩn.
- Seed bằng `RoomDatabase.Callback.onCreate`, id cố định.
- `type` không đổi sau khi tạo.
- Danh mục đã có giao dịch vẫn xóa mềm được.

Ngoài phạm vi: UI; map tên icon sang `@DrawableRes` (nhánh UI); sắp xếp lại `sortOrder`; chặn giao dịch mới chọn danh mục ẩn hoặc đã xóa (picker ở nhánh UI lo); WorkManager dọn thùng rác; `CategoryKeywordRule` (P2).

## 2. Schema

- `CategoryEntity` thêm `isHidden: Boolean`. Danh mục mới và seed đặt `false`.
- `FinoteDatabase` lên `version = 2`. Export `2.json`.
- `createFinoteDatabase` thêm `fallbackToDestructiveMigration(dropAllTables = true)`. Roadmap dòng 84 cho phép tạo lại DB trước v1.0. Không có dòng này thì máy dev đang giữ DB v1 sẽ crash. Ghi chú cạnh dòng: `shortcut: drops user data on a version change, replace with real migrations before v1.0`.

## 3. Seed

### 3.1 Dữ liệu

`data/category/SystemCategories.kt`: list `SystemCategory(id, nameKey, name, icon, colorArgb, type, parentId, sortOrder)`.

- `id` cố định, liệt kê đủ ở bảng dưới. Không sinh từ tên: id là hợp đồng với `CategoryKeywordRule` (P2), rule OTA và `strings.xml`, nên chỉ đổi qua migration. Dạng `sys_<cha>`, `sys_<cha>_<con>` bên chi, `sys_income_<tên>` bên thu. Chữ thường, `[a-z_]`.
- `nameKey` trùng `id`. Dùng làm tên string resource.
- `name` là tên tiếng Anh, chỉ để dự phòng. UI tra `nameKey` khi khác null.
- `icon` là tên drawable bỏ tiền tố `ic_` (`"restaurant"` ứng với `R.drawable.ic_restaurant`). Con dùng icon của cha.
- `colorArgb` là ARGB `Int` lấy từ `CategoryPalette`. Tầng data không import `ui`, nên seed giữ literal; test chặn lệch. Con dùng màu của cha.
- `sortOrder` theo thứ tự trong bảng, đánh từ 0 trong từng nhóm anh em.

**EXPENSE**

| id cha | Tên | icon | màu | id con (tên) |
|---|---|---|---|---|
| `sys_food` | Food & Drinks | `restaurant` | `0xFFE65100` | `sys_food_groceries` (Groceries), `sys_food_restaurants` (Restaurants), `sys_food_coffee` (Coffee & Snacks) |
| `sys_transport` | Transport | `directions_car` | `0xFF1565C0` | `sys_transport_fuel` (Fuel), `sys_transport_parking` (Parking & Tolls), `sys_transport_taxi` (Taxi & Ride-hailing), `sys_transport_transit` (Public Transit), `sys_transport_maintenance` (Vehicle Maintenance) |
| `sys_housing` | Housing | `home` | `0xFF4E342E` | `sys_housing_rent` (Rent), `sys_housing_utilities` (Utilities), `sys_housing_internet` (Internet & Phone), `sys_housing_maintenance` (Maintenance) |
| `sys_shopping` | Shopping | `shopping_bag` | `0xFFAD1457` | `sys_shopping_clothing` (Clothing), `sys_shopping_electronics` (Electronics), `sys_shopping_household` (Household) |
| `sys_health` | Health | `medical_services` | `0xFFC62828` | `sys_health_medical` (Medical), `sys_health_pharmacy` (Pharmacy), `sys_health_insurance` (Insurance) |
| `sys_education` | Education | `school` | `0xFF283593` | `sys_education_tuition` (Tuition), `sys_education_courses` (Books & Courses) |
| `sys_entertainment` | Entertainment | `movie` | `0xFF6A1B9A` | `sys_entertainment_media` (Movies & Music), `sys_entertainment_travel` (Travel), `sys_entertainment_sports` (Sports & Hobbies) |
| `sys_family` | Family & Personal | `family_restroom` | `0xFF00838F` | `sys_family_children` (Children), `sys_family_personal_care` (Personal Care), `sys_family_pets` (Pets) |
| `sys_gifts` | Gifts & Donations | `redeem` | `0xFF4527A0` | `sys_gifts_gifts` (Gifts), `sys_gifts_charity` (Charity) |
| `sys_fees` | Fees & Charges | `receipt_long` | `0xFF37474F` | `sys_fees_bank` (Bank Fees), `sys_fees_taxes` (Taxes) |
| `sys_other` | Other | `more_horiz` | `0xFF37474F` | |

**INCOME** (cấp 1, không con)

| id | Tên | icon | màu |
|---|---|---|---|
| `sys_income_salary` | Salary | `work` | `0xFF2E7D32` |
| `sys_income_bonus` | Bonus | `stars` | `0xFF558B2F` |
| `sys_income_business` | Business | `storefront` | `0xFF00695C` |
| `sys_income_investment` | Investment & Interest | `trending_up` | `0xFF0277BD` |
| `sys_income_gifts` | Gifts Received | `card_giftcard` | `0xFF4527A0` |
| `sys_income_other` | Other | `more_horiz` | `0xFF37474F` |

Tổng: 11 cha + 30 con bên chi, 6 bên thu = 47 dòng. 16 icon, trong đó `restaurant`, `home`, `receipt_long`, `work` đã có; 12 icon mới.

### 3.2 Ghi seed

- `data/category/SystemCategorySeeder(clock: Clock) : RoomDatabase.Callback`. Room 3 chỉ có luồng driver (`SQLCipherDriver` ở app, `AndroidSQLiteDriver` ở test), nên callback là `onCreate(connection: SQLiteConnection)`. Nó chèn mọi dòng bằng một prepared `INSERT`, `isSystemDefault = 1`, `isHidden = 0`, `createdAt = updatedAt = clock.millis()`, `deletedAt = NULL`. Cha chèn trước con.
- `onCreate` chạy một lần, lúc tạo DB, trong transaction tạo schema. Không có lúc DB đã mở mà còn rỗng.
- DB v1 lên v2 đi nhánh destructive migration: Room 3.0.3 xóa bảng, gọi `onDestructiveMigration` khi bảng chưa có, rồi tạo lại bảng mà không gọi `onCreate`. Nên seeder override `onOpen`: nếu chưa có dòng `isSystemDefault = 1` thì seed trong `BEGIN IMMEDIATE` riêng (Room gọi `onOpen` ngoài transaction), lỗi thì rollback. Cách này cũng tự sửa khi app chết giữa migration và `onOpen`.
- `createFinoteDatabase` nhận thêm `clock` (Hilt cấp ở `DatabaseModule`) và luôn gắn callback.
- `InMemoryDatabaseRule(seedSystemCategories: Boolean = false)`. Mặc định không seed, nên test cũ và test đếm dòng không gãy khi bộ seed đổi. Test seed và test luật danh mục hệ thống bật `true`.

### 3.3 Icon và chuỗi dịch

- Thêm 12 vector `ic_<icon>.xml` còn thiếu ở bảng 3.1 (Material Symbols, Apache 2.0). Theo luật icon ở `docs/design-system.md`.
- 47 key `<nameKey>` trong `values/strings.xml` (vi), `values-en`, `values-zh-rCN`, `values-ja`.

## 4. Domain

### 4.1 Model, mapper, repository

- `domain/category/model/Category`: cùng field với `CategoryEntity`.
- `data/category/CategoryMapper.kt`: `CategoryEntity.toDomain()`, `Category.toEntity()`.
- `domain/category/repository/CategoryRepository` (interface), impl `data/category/CategoryRepositoryImpl`, bind trong `di/RepositoryModule`:
  - `getCategory(id)`: kể cả dòng đã xóa. Null khi không có id.
  - `observeCategories()`: dòng chưa xóa, kể cả dòng ẩn. UI tự lọc.
  - `add(category)`, `update(category)`, `setHidden(id, hidden, updatedAt)`, `softDelete(id, deletedAt)`.
- Mỗi ghi nằm trong `db.withWriteTransaction`. Lỗi nghiệp vụ là `IllegalArgumentException`.
- DAO thêm truy vấn đếm con sống của một danh mục.

### 4.2 Use case

`domain/category/usecase/`: `CreateCategoryUseCase`, `UpdateCategoryUseCase`, `SetCategoryHiddenUseCase`, `DeleteCategoryUseCase`, `ValidateCategoryUseCase`. Lấy thời gian từ `Clock`, giống account.

### 4.3 Luật

1. `name` không rỗng (`ValidateCategoryUseCase`).
2. Có `parentId` thì cha phải tồn tại, chưa xóa, là cấp 1 (`parentId == null`), cùng `type`, khác chính nó.
3. Không gán cha cho danh mục còn con sống (sẽ thành 3 cấp).
4. `type` không đổi sau khi tạo. `update` đổi `type` bị từ chối.
5. Danh mục hệ thống: đổi được `name` (khi đó `nameKey = null`), `icon`, `color`, `sortOrder`. Không đổi `parentId`. Không xóa mềm. Ẩn được.
6. Xóa mềm: id không tồn tại bị từ chối; đã xóa thì không làm gì; còn con sống bị chặn. Ghi `deletedAt` và `updatedAt = deletedAt`, giống `AccountRepositoryImpl.softDelete`.
7. Danh mục có giao dịch vẫn xóa mềm được. Giao dịch giữ `categoryId`, vẫn đọc được danh mục qua `getCategory`.
8. `update`, `setHidden` trên dòng không tồn tại hoặc đã xóa bị từ chối.
9. `update` giữ `isSystemDefault`, `createdAt`, `deletedAt`, `isHidden` của dòng cũ. `nameKey` giữ nguyên khi `name` bằng `name` đã lưu, thành `null` khi khác. Hợp đồng ghi trong KDoc của `CategoryRepository.update`: caller truyền nguyên `name` đã lưu (tên tiếng Anh dự phòng), không truyền chuỗi đã dịch đang hiện, trừ khi user sửa ô tên. Truyền chuỗi dịch sẽ xóa `nameKey` và mất đa ngôn ngữ. Nhánh UI phải tuân theo.
10. `add` luôn đặt `isSystemDefault = false`, `nameKey = null`, `isHidden = false`.

Ẩn cha không ghi vào con. Lọc con của cha ẩn là việc của UI.

## 5. Test

JVM thuần:
- `SystemCategories`: id duy nhất; mọi `parentId` có trong list; tối đa 2 cấp; con cùng `type` với cha; mọi màu nằm trong `CategoryPalette`.
- `ValidateCategoryUseCase`: tên rỗng, tên toàn khoảng trắng.
- Use case với `FakeCategoryRepository` (như `domain/account/usecase/*Test`): `Create` sinh UUID, lấy `createdAt`/`updatedAt` từ `Clock`, không ghi khi validate lỗi (luật 10 do repository ép, test ở `CategoryRepositoryImplTest`); `Update`, `SetCategoryHidden`, `Delete` truyền thời gian từ `Clock`.

Robolectric (`InMemoryDatabaseRule`):
- Seed (rule bật seed, `Clock.fixed`): DB mới có 47 dòng, đều `isSystemDefault = true`, `nameKey` khác null, `createdAt` bằng giờ của clock.
- `update` với `name` không đổi giữ `nameKey`; đổi `name` thì `nameKey = null`.
- `softDelete` id không tồn tại bị từ chối; xóa mềm đặt `updatedAt = deletedAt`.
- Mỗi `icon` có drawable `ic_<icon>`; mỗi `nameKey` resolve được ở `vi`, `en`, `zh-rCN`, `ja`.
- Mapper round-trip mọi field.
- `CategoryRepositoryImpl`, một test cho mỗi luật 2–10: cha cấp 2, cha khác type, tự làm cha, cha đã xóa, gán cha cho danh mục có con, đổi type, đổi tên danh mục hệ thống, đổi cha danh mục hệ thống, xóa mềm danh mục hệ thống, xóa mềm cha còn con, xóa mềm danh mục có giao dịch, xóa hai lần, update và ẩn dòng đã xóa, ẩn rồi bỏ ẩn.
- Sửa test đang có (`CategoryDaoTest`, `Fixtures`) cho cột `isHidden`. Rule mặc định không seed nên không cần trừ 47 dòng.

Cổng: `testDebugUnitTest` sau mỗi thay đổi logic; `qualityGate` trước khi squash vào `dev`.

## 6. Tài liệu đi kèm

- `docs/roadmap.md` mục 6: "chuẩn VN" thành "chuẩn quốc tế", ghi UI quản lý cây là nhánh `feature/category-ui` riêng.
