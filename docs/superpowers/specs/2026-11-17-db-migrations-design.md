# Design: real database migrations (roadmap step 6, `chore/db-migrations`)

Short design. Small change.

## Why

From dogfood (step 9) on, the database on the user's phone holds real money records. Today any schema version change runs `fallbackToDestructiveMigration(dropAllTables = true)` and wipes the ledger. Step 6 ends that before real data exists, so step 10 (`fix/account-identity`, a structural schema change) lands on a tested migration path.

## Goals

- Schema v8 (the schema after step 5) is the baseline. Every later version has a migration.
- A JVM test runs every migration from v8 to the latest version and validates the result against the exported schema. A version bump without a migration fails `testDebugUnitTest`.
- A missing migration or a downgrade on a device crashes and keeps the database file.

## Non-goals

- No schema change in this branch. The DB stays at version 8.
- No backup or export before migrating (export is step 27).
- No change to the lost-key path (D10). It still wipes, by design.

## Anti-goals

- Any path that silently drops a v8+ database. Losing the ledger quietly is worse than a crash.

## Decided with the user

- **Failure behaviour:** crash, keep the file. Remove `fallbackToDestructiveMigration`. Do not add `fallbackToDestructiveMigrationOnDowngrade`.
- **Approach B:** Room `AutoMigration` for simple changes (add a column, add a table). A spec class (`@RenameColumn`, `@DeleteColumn`, …) or a hand-written `Migration` for renames, deletes and data moves.

## Approaches considered

| | Approach | Trade-off | Verdict |
|---|---|---|---|
| A | Hand-written `Migration` for every change | Every SQL line visible; even one column needs a hand `ALTER TABLE`, can drift from the schema JSON | Rejected |
| B | `AutoMigration` by default, hand-written when needed | Least code, SQL always matches the schema; a rename must be declared or Room drops and re-adds the column | **Picked** |
| C | Remove the fallback only, build test tooling at the first real migration | Fastest now; step 10 would build the tooling and the hardest schema change at once, on real data | Rejected |

## Design

1. **Builder config in one place.** `RoomDatabase.Builder<FinoteDatabase>.applyMigrationPolicy()` in `data/database` sets only the hand-written migrations (`addMigrations(...)`) and the fallback policy. It never sets the driver or the passphrase: production uses SQLCipher, JVM tests use `AndroidSQLiteDriver`. `createFinoteDatabase` and the migration test both call it, so the test covers the shipped config, not a copy.
2. **Policy:** `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4, 5, 6, 7)`. These versions never held real data. Nothing else is destructive. The `shortcut:` comment in `FinoteDatabaseFactory.kt` goes away.
3. **Where migrations live.** `AutoMigration` entries go on `@Database(autoMigrations = [...])`; Room and `MigrationTestHelper` read them from there. Hand-written `Migration` objects go in the list `applyMigrationPolicy()` adds. Both are empty today; the first entry arrives with the first schema change.
4. **One version constant.** `const val DATABASE_VERSION = 8` next to `FinoteDatabase`, used by `@Database(version = DATABASE_VERSION)` and by the test. A test that hardcodes 8 would stay green after a bump to 9.
5. **Seeding needs no migration work.** `SystemCategorySeeder`, `SystemAccountSeeder` and `ParserRuleSeeder` already repair in `onOpen`. A migration only changes the structure and moves existing rows.
6. **Test dependency and schema wiring:** `room3-testing` 3.0.3 (same version as Room) through the version catalog, `testImplementation` only. Add `$projectDir/schemas` to the `test` source set's assets in `app/build.gradle.kts`; `isIncludeAndroidResources = true` is already on, so Robolectric can read them.
7. **Migration tests** (Robolectric, `AndroidSQLiteDriver`, same as `InMemoryDatabaseRule`):
   - **v8 data survives.** Create a v8 database from `8.json`. Insert one fake row in each of the 8 tables, parents first (foreign keys from `8.json`): `accounts`, `categories`, `notification_logs`, `tags`, a user-custom `parser_rules` row → `transactions` → `account_balance_snapshots`, `transaction_tag_cross_ref`. Run `runMigrationsAndValidate` to `DATABASE_VERSION`. Then open through `applyMigrationPolicy()` with the seeders and check each inserted row by `id` and key values. Never assert row counts: the seeders add system rows on open. The user-custom rule must keep its content.
   - **Downgrade crashes and keeps the file.** Create a v8 database with one row, set `PRAGMA user_version` above `DATABASE_VERSION`, open through `applyMigrationPolicy()`: the open throws, and the row is still in the file afterwards. This guards the anti-goal directly.
   - **v7 is wiped.** Create a v7 database from `7.json` with one row, open through `applyMigrationPolicy()`: it opens at `DATABASE_VERSION` without a crash and the row is gone. This checks the `1..7` list.
   - While `DATABASE_VERSION` is 8, the first test checks only the plumbing (schema files load, the helper runs); the other two check the policy for real.
8. **Docs:** one rule in `docs/architecture.md`: a version bump commits the new `N.json`, adds an `AutoMigration` or a `Migration`, and extends the migration test when rows move. Fix `docs/roadmap.md` section 1 (DB is version 8, not 7; no more destructive migration) and mark step 6 done.

## Left to the builder

- If the `test` assets wiring does not work with `room3-testing` 3.0.3 on Robolectric, use whatever the helper supports (for example a file-based constructor) and note it in the plan.
- The exact name and file of the builder-config function, following `docs/architecture.md`.
- Whether Konsist needs a rule. Default: no; the test is the guard.

## Verification

- `testDebugUnitTest` and `qualityGate` through `gradle-run`.
- `EncryptedDatabaseTest` (instrumented) is not run unless the user asks; the SQLCipher open path does not change apart from the builder config call.
