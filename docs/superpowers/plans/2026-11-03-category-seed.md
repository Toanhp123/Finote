# Category Seed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seed 47 danh mục hệ thống chuẩn quốc tế, thêm cờ `isHidden`, CRUD danh mục với luật cây 2 cấp.

**Architecture:** `SystemCategorySeeder` (Room `Callback`) ghi list thuần `SystemCategories` khi tạo DB và sau destructive migration. Domain `Category` + `CategoryRepository` + 5 use case ở `domain/category`. `CategoryRepositoryImpl` ở `data/category` kiểm luật cây trong `db.withWriteTransaction`.

**Tech Stack:** Kotlin, Room 3.0.3 (`androidx.room3`, driver API), Hilt, JUnit4, Robolectric (`InMemoryDatabaseRule`), `kotlinx.coroutines.test.runTest`.

**Spec:** `docs/superpowers/specs/2026-11-03-category-seed-design.md`

## Global Constraints

- Không UI. Map tên icon sang `@DrawableRes` để nhánh `feature/category-ui`.
- Id, tên tiếng Anh, icon, màu của 47 dòng seed lấy **đúng** bảng ở spec mục 3.1. Không sinh id từ tên.
- Xóa mềm qua `deletedAt`. Không xóa cứng.
- Lỗi nghiệp vụ là `IllegalArgumentException` (`require`/`requireNotNull`).
- Không log nội dung DB ra Logcat.
- Tầng và tên theo `docs/architecture.md`; Konsist trong `app/src/test/java/io/github/toanhp123/finote/architecture/` phải pass. Use case chỉ có `operator fun invoke`; file `*Mapper.kt` chỉ chứa `toDomain()`/`toEntity()`; Boolean dùng tiền tố (`is…`).
- Phiên bản thư viện chỉ qua `gradle/libs.versions.toml`.
- Gradle chạy qua skill `gradle-run`, wrapper `'.\gradlew.bat'` (từ Bash).
- Commit chỉ khi user đồng ý, stage file theo tên, không AI attribution (`docs/git-workflow.md`).

## Review Focus

1. Máy dev đang có DB v1 lên v2: Room đi nhánh destructive migration, **không** gọi `onCreate`, và `onDestructiveMigration` chạy khi bảng chưa có → seeder seed ở `onOpen` khi thiếu dòng hệ thống (test ở Task 3).
2. UI truyền tên đã dịch ("Ăn uống") vào `update` khi chỉ đổi màu → `nameKey` mất. Hợp đồng KDoc + test `name` không đổi giữ `nameKey` (Task 5).
3. `update` đổi `parentId` của một danh mục cấp 1 đang có con thành con của danh mục khác → phải bị chặn (Task 5).
4. `update` gán `parentId` trỏ tới chính nó, hoặc tới cha đã xóa mềm → bị chặn (Task 5).
5. Ẩn rồi `update` một danh mục → `isHidden` giữ nguyên, không bị caller ghi đè (Task 5).

---

### Task 0: Nhánh và tài liệu

- [ ] **Step 1:** Từ `dev`, `git switch -c feature/category-seed`. Spec và plan này (chưa commit) đi theo working tree.
- [ ] **Step 2:** Sửa `docs/roadmap.md` mục 6 (dòng 115–116): "chuẩn VN" thành "chuẩn quốc tế"; dòng con thành "UI quản lý cây 2 cấp: nhánh riêng `feature/category-ui` (Must-Have, mục 4)".
- [ ] **Step 3 (cần user đồng ý):** commit spec, plan, roadmap, message `docs(category): add category seed design and plan`.

### Task 1: Cột `isHidden`, DB v2, đếm con

**Files:**
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/category/CategoryEntity.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/category/CategoryDao.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/FinoteDatabase.kt` (`version = 2`)
- Modify: `app/src/test/java/io/github/toanhp123/finote/data/Fixtures.kt` (`category(...)` thêm `isHidden: Boolean = false`)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/category/CategoryDaoTest.kt`
- Generated: `app/schemas/io.github.toanhp123.finote.data.FinoteDatabase/2.json` (KSP xuất; stage cùng commit)

**Interfaces:**
- Produces: `CategoryEntity.isHidden: Boolean` (đặt sau `isSystemDefault`); `CategoryDao.countLiveChildren(parentId: String): Int`.

- [ ] **Step 1: Test**

```kotlin
@Test
fun `isHidden round-trips`() = runTest {
    val hidden = category(isHidden = true)
    dao.insert(hidden)
    assertEquals(hidden, dao.getById(hidden.id))
}

@Test
fun `countLiveChildren ignores soft-deleted children`() = runTest {
    dao.insert(category(id = "p"))
    dao.insert(category(id = "c1", parentId = "p"))
    dao.insert(category(id = "c2", parentId = "p", deletedAt = 9L))
    assertEquals(1, dao.countLiveChildren("p"))
    assertEquals(0, dao.countLiveChildren("c1"))
}
```

- [ ] **Step 2:** Chạy `testDebugUnitTest --tests "*CategoryDaoTest"` → FAIL (không biên dịch).
- [ ] **Step 3:** Thêm cột, query `SELECT COUNT(*) FROM categories WHERE parentId = :parentId AND deletedAt IS NULL`, `version = 2`.
- [ ] **Step 4:** Chạy lại → PASS. Kiểm `2.json` đã sinh.
- [ ] **Step 5 (cần user đồng ý):** commit `feat(category): add isHidden column and child count`.

### Task 2: Dữ liệu seed, icon, chuỗi dịch

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/data/category/SystemCategories.kt`
- Create: 12 file `app/src/main/res/drawable/ic_<icon>.xml`: `directions_car`, `shopping_bag`, `medical_services`, `school`, `movie`, `family_restroom`, `redeem`, `more_horiz`, `stars`, `storefront`, `trending_up`, `card_giftcard` (Material Symbols Outlined, 24dp, cùng định dạng `ic_restaurant.xml`).
- Modify: `app/src/main/res/values/strings.xml`, `values-en/`, `values-zh-rCN/`, `values-ja/` — 47 chuỗi, tên chuỗi = `nameKey`.
- Test: `app/src/test/java/io/github/toanhp123/finote/data/category/SystemCategoriesTest.kt` (JVM)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/category/SystemCategoryResourcesTest.kt` (Robolectric)

**Interfaces:**
- Produces:
  ```kotlin
  data class SystemCategory(
      val id: String, val name: String, val icon: String, val colorArgb: Int,
      val type: CategoryType, val parentId: String?, val sortOrder: Int,
  ) { val nameKey: String get() = id }
  val SystemCategories: List<SystemCategory>   // cha đứng trước con
  ```

- [ ] **Step 1: Test JVM** (`SystemCategoriesTest`)
  - `there are 47 rows with unique ids`: `size == 47`, `map { it.id }.toSet().size == 47`.
  - `ids are lowercase snake case with sys prefix`: mọi id khớp `^sys_[a-z_]+$`.
  - `every parent exists, is top level, appears earlier and has the same type`.
  - `income rows are top level`: `type == INCOME` ⇒ `parentId == null`.
  - `sortOrder is 0 based within siblings`: theo nhóm `parentId` (và `type` cho cấp 1), `sortOrder` là `0..n-1` theo thứ tự list.
  - `children share icon and color of their parent`.
  - `every color is in CategoryPalette`: `CategoryPalette.map { it.toArgb() }` chứa mọi `colorArgb` (import `ui.common.theme.CategoryPalette`; Konsist chỉ quét production).
  - `spot check matches spec`: `sys_transport_taxi` có name `"Taxi & Ride-hailing"`, parent `sys_transport`; `sys_income_investment` có icon `"trending_up"`, màu `0xFF0277BD.toInt()`.
- [ ] **Step 2: Test Robolectric** (`SystemCategoryResourcesTest`)
  - `every icon has a drawable`: `context.resources.getIdentifier("ic_${icon}", "drawable", context.packageName) != 0`.
  - `every nameKey resolves in all four locales`: với mỗi locale `vi`, `en`, `zh-CN`, `ja`, tạo `context.createConfigurationContext(config.apply { setLocale(locale) })`; `getIdentifier(nameKey, "string", …) != 0` và `getString(id)` không rỗng; chuỗi `en` khác chuỗi `ja` cho `sys_food` (chứng tỏ không rơi về mặc định).
- [ ] **Step 3:** Chạy hai test → FAIL.
- [ ] **Step 4:** Viết `SystemCategories` theo đúng bảng spec 3.1, 12 drawable, 188 chuỗi. Tên `vi` ngắn, tự nhiên (ví dụ `sys_food` = "Ăn uống", `sys_food_groceries` = "Đi chợ"). `values-en` = tên tiếng Anh ở bảng. Ký tự `&` trong XML viết `&amp;`.
- [ ] **Step 5:** Chạy lại → PASS.
- [ ] **Step 6 (cần user đồng ý):** commit `feat(category): add system category seed data`.

### Task 3: Seeder và nối vào DB

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/data/category/SystemCategorySeeder.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/FinoteDatabaseFactory.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/di/DatabaseModule.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/data/InMemoryDatabaseRule.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/category/SystemCategorySeederTest.kt`

**Interfaces:**
- Consumes: `SystemCategories` (Task 2), `CategoryEntity.isHidden` (Task 1).
- Produces:
  - `class SystemCategorySeeder(private val clock: Clock) : RoomDatabase.Callback()` override `suspend fun onCreate(connection: SQLiteConnection)` (seed) và `suspend fun onOpen(connection: SQLiteConnection)` (seed trong transaction riêng khi chưa có dòng `isSystemDefault = 1`).
  - `createFinoteDatabase(context, clock: Clock, name = DATABASE_NAME, keyStore = …)`.
  - `InMemoryDatabaseRule(seedSystemCategories: Boolean = false, clock: Clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC))`.

- [ ] **Step 1: Test** (`SystemCategorySeederTest`, Robolectric)

```kotlin
@get:Rule val dbRule = InMemoryDatabaseRule(seedSystemCategories = true)

@Test
fun `new database holds every system category`() = runTest {
    val rows = dbRule.db.categoryDao().observeCategories().first()
    assertEquals(SystemCategories.map { it.id }.toSet(), rows.map { it.id }.toSet())
    assertTrue(rows.all { it.isSystemDefault && !it.isHidden && it.deletedAt == null && it.nameKey == it.id })
    assertTrue(rows.all { it.createdAt == 1_793_664_000_000L && it.updatedAt == it.createdAt })
}

@Test
fun `destructive migration from an old version seeds again`() = runTest {
    // File DB with user_version = 1 and no Room tables: Room sees 1 != 2, has no migration, drops and recreates.
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = context.getDatabasePath("seed-migration.db").apply { parentFile?.mkdirs(); delete() }
    AndroidSQLiteDriver().open(file.path).apply { execSQL("PRAGMA user_version = 1"); close() }
    val db = Room.databaseBuilder<FinoteDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(SystemCategorySeeder(Clock.systemUTC()))
        .build()
    try { assertEquals(47, db.categoryDao().observeCategories().first().size) } finally { db.close(); file.delete() }
}
```

(`1_793_664_000_000L` là `2026-11-03T00:00:00Z`.) Thêm vào `CategoryDaoTest` một test: rule mặc định → `observeCategories().first()` rỗng.

- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Seeder dùng một `connection.prepare("INSERT INTO categories (...) VALUES (?, …)")`, `bind*`/`step()`/`reset()` cho mỗi dòng theo thứ tự list; `isSystemDefault = 1`, `isHidden = 0`, `deletedAt` `bindNull`. Room đã bọc cả hai callback trong `BEGIN EXCLUSIVE … END TRANSACTION` (đã kiểm bytecode `BaseRoomConnectionManager` 3.0.3), nên seeder không mở transaction riêng.
- [ ] **Step 4:** `createFinoteDatabase` thêm `.fallbackToDestructiveMigration(dropAllTables = true)` với comment một dòng `// shortcut: drops user data on a version change, replace with real migrations before v1.0`, và `.addCallback(SystemCategorySeeder(clock))`. `DatabaseModule.provideDatabase` nhận thêm `clock: Clock`. Rule gắn callback khi `seedSystemCategories`.
- [ ] **Step 5:** Chạy `testDebugUnitTest` (cả bộ) → PASS. Test cũ không đổi hành vi vì rule mặc định không seed.
- [ ] **Step 6 (cần user đồng ý):** commit `feat(category): seed system categories on database create`.

### Task 4: Model, mapper, repository interface, use case

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/category/model/Category.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/data/category/CategoryMapper.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/category/repository/CategoryRepository.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/domain/category/usecase/{Validate,Create,Update,SetCategoryHidden,Delete}…UseCase.kt` (tên đủ: `ValidateCategoryUseCase`, `CreateCategoryUseCase`, `UpdateCategoryUseCase`, `SetCategoryHiddenUseCase`, `DeleteCategoryUseCase`)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/category/CategoryMapperTest.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/domain/category/usecase/FakeCategoryRepository.kt` + `*UseCaseTest.kt`
- Test helper: `app/src/test/java/io/github/toanhp123/finote/domain/category/TestCategory.kt` — `fun testCategory(id = "cat-1", name = "Food", type = EXPENSE, parentId = null, …): Category`

**Interfaces:**
- Produces:
  ```kotlin
  data class Category(
      val id: String, val name: String, val nameKey: String?, val icon: String, val color: Int,
      val type: CategoryType, val parentId: String?, val isSystemDefault: Boolean, val isHidden: Boolean,
      val sortOrder: Int, val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
  )
  interface CategoryRepository {
      suspend fun getCategory(id: String): Category?          // kể cả đã xóa
      fun observeCategories(): Flow<List<Category>>            // chưa xóa, kể cả ẩn
      suspend fun add(category: Category)
      suspend fun update(category: Category)
      suspend fun setHidden(id: String, hidden: Boolean, updatedAt: Long)
      suspend fun softDelete(id: String, deletedAt: Long)
  }
  ```
  KDoc của `update` ghi luật 9 của spec nguyên văn ý: caller truyền `name` đã lưu, không truyền chuỗi đã dịch, trừ khi user sửa tên; đổi `name` thì `nameKey = null`.
  Use case: `CreateCategoryUseCase(repo, validate, clock).invoke(category): String` trả id mới; `UpdateCategoryUseCase(repo, validate, clock).invoke(category)`; `SetCategoryHiddenUseCase(repo, clock).invoke(id, hidden)`; `DeleteCategoryUseCase(repo, clock).invoke(id)`; `ValidateCategoryUseCase().invoke(category)`.

- [ ] **Step 1: Test**
  - `CategoryMapperTest`: entity với mọi field khác mặc định (`nameKey`, `parentId`, `deletedAt` khác null, `isHidden = true`) → `toDomain().toEntity() == entity`.
  - `CreateCategoryUseCaseTest` (clock `2026-11-03T00:00:00Z`): tên `""` và `"   "` bị từ chối, `writes == 0`; hợp lệ → id trả về là UUID (`UUID.fromString` không ném) và khác id truyền vào, `createdAt == updatedAt == clock`, `deletedAt = null` dù input đặt khác.
  - `UpdateCategoryUseCaseTest`: tên rỗng bị từ chối; hợp lệ → `updatedAt == clock`.
  - `SetCategoryHiddenUseCaseTest`, `DeleteCategoryUseCaseTest`: truyền `clock.millis()` xuống repo.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Viết code. `FakeCategoryRepository` theo mẫu `FakeAccountRepository` (không kiểm luật, đếm `writes`).
- [ ] **Step 4:** Chạy → PASS.
- [ ] **Step 5 (cần user đồng ý):** commit `feat(category): add category model and use cases`.

### Task 5: `CategoryRepositoryImpl` và luật cây

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/data/category/CategoryRepositoryImpl.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/di/RepositoryModule.kt` (`bindCategoryRepository`)
- Test: `app/src/test/java/io/github/toanhp123/finote/data/category/CategoryRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `CategoryRepository`, mapper (Task 4); `CategoryDao.countLiveChildren` (Task 1).
- Produces: `class CategoryRepositoryImpl @Inject constructor(private val db: FinoteDatabase) : CategoryRepository`.

Mỗi ghi nằm trong `db.withWriteTransaction`. Một hàm private kiểm cha dùng chung cho `add` và `update`: cha tồn tại, `deletedAt == null`, `parentId == null`, cùng `type`, `id != category.id`.

- [ ] **Step 1: Test** (Robolectric, `InMemoryDatabaseRule()`; test danh mục hệ thống dùng `InMemoryDatabaseRule(seedSystemCategories = true)` trong class lồng hoặc chèn dòng `isSystemDefault = true` qua DAO). Mỗi test bị từ chối kiểm `IllegalArgumentException` và dòng trong DB không đổi.
  - `add forces isSystemDefault false, nameKey null, isHidden false` (luật 10; input đặt `true`, `"x"`, `true`).
  - `add with a child as parent is rejected` (3 cấp).
  - `add with a parent of the other type is rejected`.
  - `add with a soft-deleted parent is rejected`; `add with a missing parent is rejected`.
  - `update setting parentId to itself is rejected`.
  - `update giving a parent to a category with live children is rejected`.
  - `update changing type is rejected`.
  - `update keeps nameKey when name is unchanged` (đổi `color`, giữ `name`); `update clears nameKey when name changes`.
  - `update keeps isSystemDefault, isHidden, createdAt, deletedAt of the stored row` (caller truyền giá trị khác).
  - `update changing parentId of a system category is rejected`.
  - `update of a missing or soft-deleted row is rejected`.
  - `setHidden flips isHidden and updatedAt, works on system categories, and back`.
  - `setHidden of a missing or soft-deleted row is rejected`.
  - `softDelete of a system category is rejected`.
  - `softDelete of a parent with live children is rejected`; sau khi xóa con thì xóa cha được.
  - `softDelete of a category with transactions succeeds and getCategory still returns it` (chèn account + transaction qua fixture `transaction(categoryId = …)`).
  - `softDelete sets deletedAt and updatedAt; second call does nothing` (`updatedAt` giữ giá trị lần đầu).
  - `softDelete of a missing id is rejected`.
  - `observeCategories includes hidden rows and excludes deleted rows`.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Viết `CategoryRepositoryImpl` theo mẫu `AccountRepositoryImpl` (`requireLive`, `softDelete` trả sớm khi đã xóa). `update` ghi `entity.copy(isSystemDefault = old…, isHidden = old…, createdAt = old…, deletedAt = old…, nameKey = if (category.name == old.name) old.nameKey else null)`. `add` ghi `entity.copy(isSystemDefault = false, nameKey = null, isHidden = false)`. Bind trong `RepositoryModule`.
- [ ] **Step 4:** Chạy `testDebugUnitTest` → PASS.
- [ ] **Step 5 (cần user đồng ý):** commit `feat(category): add category repository with tree rules`.

### Task 6: Cổng cuối

- [ ] **Step 1:** Chạy `qualityGate` qua `gradle-run`; đọc summary. Sửa detekt/Spotless/Konsist nếu có.
- [ ] **Step 2:** Agent `reviewer` soát nhánh với spec và plan này. Chuyển phát hiện cho `coder` sửa, review lại.
- [ ] **Step 3 (cần user đồng ý):** squash vào `dev` theo `docs/git-workflow.md`.
