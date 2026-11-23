# Real Database Migrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop wiping the database on a version change from v8 on; a missing migration or a downgrade crashes and keeps the file, and JVM tests prove it.

**Architecture:** One extension function in `data/database` holds the migration policy (hand-written migrations + destructive fallback for v1–v7 only). The production factory and the tests both call it. `AutoMigration`s live on `@Database`. Tests use `room3-testing`'s `MigrationTestHelper` on Robolectric with `AndroidSQLiteDriver`.

**Tech Stack:** Kotlin, Room 3.0.3 (`androidx.room3`), `room3-testing` 3.0.3, Robolectric 4.17, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-17-db-migrations-design.md`

## Global Constraints

- Branch `chore/db-migrations` from `dev`; squash into `dev` after `reviewer` (`docs/git-workflow.md`).
- Commit format `<type>(db): <subject>`; no AI attribution trailer. Pre-commit runs spotless and detekt; `qualityGate` also runs detekt on test sources.
- No schema change: `FinoteDatabase` stays at version 8; `8.json` must not change.
- Dependency only through `gradle/libs.versions.toml`, `testImplementation` only, version ref `room3` (3.0.3).
- Gradle through the `gradle-run` skill with `'.\gradlew.bat'` from Bash.
- Test fixtures use fake data only (no real account numbers, no real notification text).
- Skills: `ponytail:ponytail`, `testing-coroutines-with-runtest`, `using-robolectric-correctly`, `gradle-run`, `superpowers:verification-before-completion`.

## Review Focus

1. A v8+ file whose version Room cannot reach (downgrade, missing migration) must throw on open and leave every row in the file — Task 1 `downgrade crashes and keeps the rows`.
2. A future `@Database(version = 9)` without a migration must turn `testDebugUnitTest` red — Task 2 test runs to `DATABASE_VERSION`, never a literal.
3. Seeders run on reopen and add system rows; assertions must look up rows by `id`, never count — Task 2.
4. A user-custom parser rule must survive the reopen unchanged (the seeder must not overwrite it) — Task 2.
5. The SQLCipher open path in `createFinoteDatabase` must keep its driver, seeders and order; only the fallback line changes — Task 1 Step 3 (no JVM test can open SQLCipher; reviewer checks the diff).

---

### Task 1: Migration policy and its guard tests

**Files:**
- Modify: `gradle/libs.versions.toml` (add `room3-testing` library, `version.ref = "room3"`)
- Modify: `app/build.gradle.kts` (`testImplementation(libs.room3.testing)`; add `$projectDir/schemas` to the `test` source set's assets)
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/database/FinoteDatabase.kt` (add `const val DATABASE_VERSION = 8`; `@Database(version = DATABASE_VERSION, ...)`)
- Create: `app/src/main/java/io/github/toanhp123/finote/data/database/MigrationPolicy.kt`
- Modify: `app/src/main/java/io/github/toanhp123/finote/data/database/FinoteDatabaseFactory.kt:34-35`
- Test: `app/src/test/java/io/github/toanhp123/finote/data/database/MigrationPolicyTest.kt`

**Interfaces:**
- Produces:
  - `const val DATABASE_VERSION: Int = 8` (file `FinoteDatabase.kt`)
  - `val FINOTE_MIGRATIONS: List<Migration>` — empty today (file `MigrationPolicy.kt`)
  - `fun RoomDatabase.Builder<FinoteDatabase>.applyMigrationPolicy(): RoomDatabase.Builder<FinoteDatabase>` — calls `addMigrations(*FINOTE_MIGRATIONS.toTypedArray())` and `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4, 5, 6, 7)`. Never sets a driver or a callback.

- [ ] **Step 1: Create the branch**

Run: `git switch -c chore/db-migrations dev`
Expected: `Switched to a new branch 'chore/db-migrations'`

- [ ] **Step 2: Add the dependency and schema assets, then write the failing tests**

`MigrationPolicyTest` runs with `@RunWith(AndroidJUnit4::class)`. A `@get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), context.getDatabasePath(TEST_DB), AndroidSQLiteDriver(), FinoteDatabase::class)`. Helper `suspend fun openWithPolicy(): FinoteDatabase` builds `Room.databaseBuilder<FinoteDatabase>(context, TEST_DB).setDriver(AndroidSQLiteDriver()).applyMigrationPolicy()` plus the three seeders with a fixed `Clock` (same as `InMemoryDatabaseRule`), and forces the open with `db.useReaderConnection { }`. `rowExists(table, id)` reads the file through a raw `AndroidSQLiteDriver().open(path)` connection, so it works whether or not Room could open the file.

```kotlin
@Test
fun `downgrade crashes and keeps the rows`() = runTest {
    helper.createDatabase(DATABASE_VERSION).use { c ->
        c.execSQL(/* INSERT one fake account, id = "acc-test" — columns from 8.json */)
        c.execSQL("PRAGMA user_version = ${DATABASE_VERSION + 1}")
    }
    val error = runCatching { openWithPolicy().close() }.exceptionOrNull()
    assertTrue("$error", error is IllegalStateException)
    assertTrue(rowExists("accounts", "acc-test"))
}

@Test
fun `version 7 is wiped and opens at the current version`() = runTest {
    helper.createDatabase(7).use { c -> c.execSQL(/* INSERT fake account "acc-old", columns from 7.json */) }
    val db = openWithPolicy()
    db.close()
    assertFalse(rowExists("accounts", "acc-old"))
    assertEquals(DATABASE_VERSION, userVersion())
}
```

If Robolectric cannot find `8.json` through assets (`FileNotFoundException`), switch to the helper's `Path`-based constructor with `Path("schemas")` (Gradle runs unit tests from `app/`) and drop the assets wiring; note the switch in the commit body.

- [ ] **Step 3: Run the tests to verify they fail**

Run via `gradle-run`: `testDebugUnitTest --tests "*.MigrationPolicyTest"`
Expected: compile failure, `applyMigrationPolicy` and `DATABASE_VERSION` not defined.

- [ ] **Step 4: Implement**

Add `DATABASE_VERSION` and use it in `@Database`. Create `MigrationPolicy.kt` with `FINOTE_MIGRATIONS` and `applyMigrationPolicy()` as in Interfaces; one KDoc line on why v1–v7 may be wiped (never held real data) and why nothing else is. In `FinoteDatabaseFactory.kt` replace lines 34–35 (the `shortcut:` comment and `fallbackToDestructiveMigration`) with `.applyMigrationPolicy()`; keep `setDriver` and the three `addCallback` calls unchanged.

- [ ] **Step 5: Run the tests to verify they pass**

Run via `gradle-run`: `testDebugUnitTest --tests "*.MigrationPolicyTest"`
Expected: `exit_status` 0, 2 tests passed.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/specs/2026-11-17-db-migrations-design.md docs/superpowers/plans/2026-11-17-db-migrations.md
git commit -m "docs: db migrations design and plan"
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/io/github/toanhp123/finote/data/database app/src/test/java/io/github/toanhp123/finote/data/database/MigrationPolicyTest.kt
git commit -m "fix(db): keep the database on a missing migration or downgrade"
```

---

### Task 2: v8 data survives migration, docs

**Files:**
- Modify: `app/src/test/java/io/github/toanhp123/finote/data/database/MigrationPolicyTest.kt`
- Modify: `docs/architecture.md` (section "Storage rules", after line 58)
- Modify: `docs/roadmap.md` (section 1 lines 10 and 12; section 5 step 6 row and status line 10)

**Interfaces:**
- Consumes: `DATABASE_VERSION`, `FINOTE_MIGRATIONS`, `applyMigrationPolicy()`, the test helpers `openWithPolicy()` and `rowExists()` from Task 1.

- [ ] **Step 1: Write the test**

```kotlin
@Test
fun `v8 rows survive every migration and the reopen`() = runTest {
    helper.createDatabase(8).use { c ->
        // Parents first, per the foreign keys in 8.json:
        // accounts "acc-a", "acc-b"; categories "cat-1"; notification_logs "log-1"; tags "tag-1";
        // parser_rules "rule-custom" (isUserCustom = 1, regexPattern = "fake-pattern")
        // → transactions "tx-1" (accountId acc-a, toAccountId acc-b, type TRANSFER, categoryId cat-1, notificationLogId log-1)
        // → account_balance_snapshots for acc-a; transaction_tag_cross_ref (tx-1, tag-1)
    }
    helper.runMigrationsAndValidate(DATABASE_VERSION, FINOTE_MIGRATIONS).close()
    openWithPolicy().close()
    listOf("accounts" to "acc-a", "accounts" to "acc-b", "categories" to "cat-1", "notification_logs" to "log-1",
        "tags" to "tag-1", "parser_rules" to "rule-custom", "transactions" to "tx-1").forEach { (t, id) ->
        assertTrue("$t/$id", rowExists(t, id))
    }
    assertEquals(150_000L, longColumn("SELECT amount FROM transactions WHERE id = 'tx-1'"))
    assertEquals("fake-pattern", textColumn("SELECT regexPattern FROM parser_rules WHERE id = 'rule-custom'"))
    assertEquals(1L, longColumn("SELECT COUNT(*) FROM account_balance_snapshots WHERE accountId = 'acc-a'"))
    assertEquals(1L, longColumn("SELECT COUNT(*) FROM transaction_tag_cross_ref WHERE transactionId = 'tx-1'"))
}
```

Fill every `NOT NULL` column from `8.json`; amount `150_000L`. Never assert a table's total row count (seeders add system rows).

- [ ] **Step 2: Run it**

Run via `gradle-run`: `testDebugUnitTest --tests "*.MigrationPolicyTest"`
Expected: 3 tests passed. At version 8 this test checks the plumbing; it passes first time. To prove it guards a bump, temporarily set `DATABASE_VERSION = 9` and run: expect a failure (missing migration 8→9). Revert the constant and delete the generated `app/schemas/.../9.json` before committing; `git status` must show no schema file change.

- [ ] **Step 3: Docs**

`docs/architecture.md`, Storage rules, add one bullet: "A schema version bump commits the new `app/schemas/.../N.json`, adds an `AutoMigration` on `@Database` or a `Migration` in `FINOTE_MIGRATIONS`, and extends `MigrationPolicyTest` when rows move. Destructive fallback is allowed only from v1–v7." `docs/roadmap.md`: section 1 says DB version 8 with real migrations (no destructive fallback from v8); step 6 row ✅; "Bước tiếp theo" points to step 8.

- [ ] **Step 4: Full gate**

Run via `gradle-run`: `qualityGate`
Expected: `exit_status` 0.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/io/github/toanhp123/finote/data/database/MigrationPolicyTest.kt docs/architecture.md docs/roadmap.md
git commit -m "test(db): v8 data survives migration to the current version"
```

Then `reviewer` checks the whole branch; squash into `dev` only on the user's yes.
