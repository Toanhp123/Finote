# Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dựng token, `FinoteTheme`, bộ component P1 theo hướng C (đậm, kính mờ) và luật máy kiểm, trước màn feature P1 đầu tiên.

**Architecture:** Token và theme ở `ui/common/theme/`, mỗi nhóm token một class một file. Component ở `ui/common/component/`, bọc hết Material 3, Haze, Compose Unstyled để feature không chạm tới. Konsist chặn feature viết màu, `dp`, `sp`, import M3. Screenshot test (AGP test suites) chụp mọi component ở sáng, tối, chữ lớn và chạy trong `qualityGate`.

**Tech Stack:** Compose BOM 2026.09.00 (Material 3), Haze 2.1.0 (`dev.chrisbanes.haze`), Compose Unstyled 2.10.2 (`com.composables`), AGP 9.5.0 test suites + screenshot engine `0.0.1-alpha16`, Konsist 0.18.1, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-03-design-system-design.md`

## Global Constraints

- Version chỉ khai báo trong `gradle/libs.versions.toml`. Không hardcode trong `build.gradle.kts`.
- Chỉ `ui/common/theme/` được viết số `dp`, `sp`, `Color(...)`, `TextStyle(...)`, `RoundedCornerShape(...)`, `Brush.*`, `MaterialTheme`.
- `ui/` ngoài `ui/common/` chỉ import từ `androidx.compose.material3` hai class `Text` và `Icon`. Không import Haze, Compose Unstyled.
- Một class/interface/object/enum public mỗi file (luật Konsist có sẵn). Enum, state class, token class đều có file riêng.
- File ≤ 300 dòng, hàm ≤ 40 dòng, `@Composable` ≤ 80 dòng, cyclomatic ≤ 10.
- Tiền là `Long`. Không `Double`/`Float` cho tiền.
- Không `@Suppress`, không baseline, không `--no-verify`.
- Mọi chuỗi UI, kể cả `contentDescription`, nằm trong `strings.xml` và thêm đủ 4 file: `values/`, `values-en/`, `values-ja/`, `values-zh-rCN/`. Lint `MissingTranslation` là lỗi.
- Component public: `modifier: Modifier = Modifier` là tham số tùy chọn đầu tiên. Có `@FinotePreviews`.
- detekt `MagicNumber` chạy trên `ui/common/component/`. Số trong preview truyền bằng tham số có tên (`amount = 1_234_567`), vì detekt bỏ qua named argument. Mọi số khác là token ở Task 2.
- Icon: vector drawable Material Symbols Rounded (Apache 2.0), tên `ic_<name>.xml` trong `app/src/main/res/drawable/`. Không thêm `material-icons-extended`.
- Màu sáng/tối theo hệ thống. Dynamic Color từ API 31. Blur thật chỉ từ API 31 (`Build.VERSION_CODES.S`). API 26–30 không gắn `hazeEffect`.
- Gradle chạy qua skill `gradle-run`, wrapper `.\gradlew.bat` (PowerShell) hoặc `'.\gradlew.bat'` (Bash).
- `coder` không chạy `git commit`, `merge`. Main session commit. Mỗi commit hoặc squash cần user cho phép riêng. Không ghi `Co-Authored-By`.
- Branch `feature/design-system` từ `dev`. `reviewer` chạy một lần trước squash vào `dev`.
- Red proof của Konsist không được commit.

## Review Focus

1. Cỡ chữ 200% với tên giao dịch dài và số tiền lớn: tên phải cắt `…`, số tiền không bao giờ bị cắt hay xuống dòng giữa chữ số. Test: Task 5, preview `TransactionRowLongPreview`.
2. Đổi ngôn ngữ app lúc đang chạy (per-app locale): `MoneyText` phải đọc locale từ `LocalConfiguration`, không từ `Locale.getDefault()`. Test: Task 5, preview `MoneyTextEnglishPreview` với `locale = "en"` phải ra `₫1,234,567`.
3. Máy API 26–30 (không blur): thanh điều hướng và sheet vẫn đọc được chữ trên nội dung cuộn bên dưới. Test: Task 7, preview `FinoteNavBarNoBlurPreview` ép `LocalBlurEnabled provides false`.
4. Số dư âm (thẻ tín dụng) trên `BalanceHeroCard`: hiện `\u2212` (U+2212) + giá trị tuyệt đối, không ra `-` ASCII hay `(₫…)`. Test: Task 3 `MoneyFormatTest` và Task 5 preview `BalanceHeroCardNegativePreview`.
5. Ảnh mẫu tạo trên Windows, CI chạy Linux: font hoặc khử răng cưa khác nhau có thể làm gate CI đỏ giả. Test: Task 9 bước 4 chạy suite trên CI một lần và ghi kết quả.

---

### Task 0: Đưa spec và plan vào `dev`

**Files:**
- Commit: `docs/superpowers/specs/2026-11-03-design-system-design.md`, `docs/superpowers/plans/2026-11-03-design-system.md`, `.gitignore`

- [ ] **Step 1: Tạo nhánh**

```bash
git switch dev
git switch -c chore/design-system-docs
```

- [ ] **Step 2: Hỏi user, rồi commit**

```bash
git add docs/superpowers/specs/2026-11-03-design-system-design.md docs/superpowers/plans/2026-11-03-design-system.md .gitignore
git commit -m "docs: add design system spec and plan"
```

- [ ] **Step 3: Hỏi user, rồi squash vào `dev`**

Chỉ có docs nên không chạy `reviewer`.

```bash
git switch dev
git merge --squash chore/design-system-docs
git commit -m "docs: add design system spec and plan"
```

---

### Task 1: Build — Java 17, thư viện, cấu hình detekt

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts` (`compileOptions`, `dependencies`)
- Modify: `config/detekt/detekt.yml`

**Interfaces:**
- Produces: các alias catalog `libs.haze`, `libs.haze.blur`, `libs.composeunstyled` (module lẻ nếu dùng), dùng ở Task 5–8. Tên package import thật của Haze và Compose Unstyled, ghi vào ghi chú cuối task để Task 4 dùng.

- [ ] **Step 1: Tạo branch**

```bash
git switch dev
git switch -c feature/design-system
```

- [ ] **Step 2: Nâng Java 17**

`app/build.gradle.kts`: `sourceCompatibility` và `targetCompatibility` đổi từ `JavaVersion.VERSION_11` sang `JavaVersion.VERSION_17`.

- [ ] **Step 3: Thêm thư viện vào catalog**

`[versions]`: `haze = "2.1.0"`, `composeUnstyled = "2.10.2"`.
`[libraries]`: `haze` (`dev.chrisbanes.haze:haze`), `haze-blur` (`dev.chrisbanes.haze:haze-blur`), và module Compose Unstyled cho: bottom sheet, dialog, toggle switch. Đọc README hai thư viện để lấy đúng tên artifact. Nếu module lẻ không đủ, dùng `com.composables:composeunstyled`.
`app/build.gradle.kts`: thêm `implementation(...)` cho từng alias.

- [ ] **Step 4: Ghi package import**

Mở một class trong mỗi thư viện (qua IDE hoặc jar), ghi package gốc: Haze (dự kiến `dev.chrisbanes.haze`), Compose Unstyled (dự kiến `com.composeunstyled`). Ghi hai giá trị này vào cuối task để Task 4 dùng.

- [ ] **Step 5: Cho `MagicNumber` bỏ qua thư mục theme**

`config/detekt/detekt.yml`, thêm vào `style`:

```yaml
style:
  MagicNumber:
    excludes: ['**/ui/common/theme/**']
```

Lý do: theme là nơi duy nhất được viết số (Konsist luật 2). Chỗ khác dùng token.

- [ ] **Step 6: Build**

Run qua `gradle-run`: `assembleDebug`
Expected: `exit_status` 0.

- [ ] **Step 7: Hỏi user, rồi commit (hai commit)**

```bash
git add config/detekt/detekt.yml
git commit -m "build: let detekt magic numbers live in the theme package"
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add haze and compose unstyled, target java 17"
```

---

### Task 2: Token và `FinoteTheme`

**Files** (tất cả trong `app/src/main/java/io/github/toanhp123/finote/ui/common/theme/`):
- Create: `BrandColors.kt`, `FixedColors.kt`, `MoneyColorRole.kt`, `HeroColors.kt`, `GlassColors.kt`, `FinoteColors.kt`, `CategoryPalette.kt`, `CategoryIconColors.kt`, `FinoteTypography.kt`, `FinoteSpacing.kt`, `FinoteSizes.kt`, `FinoteRadii.kt`, `FinoteShapes.kt`, `FinoteMotion.kt`, `FinoteBrushes.kt`
- Modify: `FinoteTheme.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/common/theme/ContrastTest.kt`

**Interfaces:**
- Produces:
  - `internal val LightBrandScheme: ColorScheme`, `internal val DarkBrandScheme: ColorScheme` (BrandColors.kt).
  - `data class MoneyColorRole(val color: Color, val onColor: Color, val container: Color, val onContainer: Color)`.
  - `data class HeroColors(val start: Color, val end: Color, val onHero: Color, val chipContainer: Color)`. `chipContainer = onHero.copy(alpha = 0.2f)`.
  - `data class GlassColors(val fill: Color, val solidFill: Color, val stroke: Color)`. `fill` dùng khi có blur, `solidFill` khi không.
  - `data class FinoteColors(val income: MoneyColorRole, val expense: MoneyColorRole, val transfer: MoneyColorRole, val hero: HeroColors, val glass: GlassColors, val background: Color, val card: Color, val disabledAlpha: Float)`. `disabledAlpha = 0.38f`.
  - `internal fun finoteColors(scheme: ColorScheme, isDark: Boolean): FinoteColors`.
  - `val CategoryPalette: List<Color>` (14 màu).
  - `data class CategoryIconColors(val container: Color, val icon: Color)`, `fun Int.toComposeColor(): Color`, `fun categoryIconColors(colorArgb: Int, isDark: Boolean): CategoryIconColors` (cùng file `CategoryIconColors.kt`).
  - `data class FinoteTypography(val material: Typography, val amountLarge: TextStyle, val amountMedium: TextStyle, val amountSmall: TextStyle)`.
  - `data class FinoteSpacing(xs, sm, md, lg, xl, xxl: Dp)`, `data class FinoteSizes(iconSm, iconMd, avatar, touchTarget, border, navBarHeight, emptyIcon, progressStroke, sheetHandleWidth, sheetHandleHeight: Dp)`, `data class FinoteRadii(sm, md, lg, xl: Dp)`, `data class FinoteShapes(sm, md, lg, xl, full, sheet: Shape)`, `data class FinoteMotion(standard: SpringSpec<Float>, emphasized: SpringSpec<Float>, pressedScale: Float, shimmerMillis: Int)`, `data class FinoteBrushes(hero: Brush)`.
  - `@Composable fun FinoteTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)`.
  - `object FinoteTheme` với getter `@Composable @ReadOnlyComposable`: `scheme: ColorScheme`, `colors: FinoteColors`, `typography: FinoteTypography`, `spacing`, `sizes`, `radii`, `shapes`, `motion`, `brushes`, `isDark: Boolean`.

- [ ] **Step 1: Viết test tương phản (fail vì chưa có token)**

```kotlin
package io.github.toanhp123.finote.ui.common.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val modes = listOf(false to LightBrandScheme, true to DarkBrandScheme)

    @Test
    fun `money colors read on cards`() =
        modes.forEach { (isDark, scheme) ->
            val colors = finoteColors(scheme, isDark)
            mapOf("income" to colors.income, "expense" to colors.expense, "transfer" to colors.transfer)
                .forEach { (name, role) -> assertContrast(TEXT, role.color, colors.card, "$name dark=$isDark") }
        }

    @Test
    fun `hero text reads on both gradient ends`() =
        modes.forEach { (isDark, scheme) ->
            val hero = finoteColors(scheme, isDark).hero
            assertContrast(TEXT, hero.onHero, hero.start, "hero start dark=$isDark")
            assertContrast(TEXT, hero.onHero, hero.end, "hero end dark=$isDark")
        }

    @Test
    fun `category icons read on their tiles`() =
        modes.forEach { (isDark, scheme) ->
            val card = finoteColors(scheme, isDark).card
            CategoryPalette.forEachIndexed { index, color ->
                val tile = categoryIconColors(color.toArgb(), isDark)
                assertContrast(GRAPHIC, tile.icon, tile.container.compositeOver(card), "palette[$index] dark=$isDark")
            }
        }

    @Test
    fun `palette holds 12 to 16 distinct colors`() {
        assertTrue(CategoryPalette.size in 12..16)
        assertTrue(CategoryPalette.distinct().size == CategoryPalette.size)
    }

    private fun assertContrast(min: Float, fg: Color, bg: Color, label: String) {
        val (hi, lo) = listOf(fg.luminance() + 0.05f, bg.luminance() + 0.05f).sortedDescending()
        val ratio = hi / lo
        assertTrue("$label contrast $ratio < $min", ratio >= min)
    }

    private companion object {
        const val TEXT = 4.5f
        const val GRAPHIC = 3f
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run qua `gradle-run`: `testDebugUnitTest --tests "*ContrastTest"`
Expected: FAIL, lỗi biên dịch `Unresolved reference: LightBrandScheme`.

- [ ] **Step 3: Viết token**

Giá trị cố định:

| Token | Sáng | Tối |
|---|---|---|
| Brand scheme | Material Theme Builder, seed `#006A6A`, TonalSpot, contrast chuẩn, export Jetpack Compose. Dán `lightColorScheme(...)` | `darkColorScheme(...)` cùng lần export |
| `income` color / on / container / onContainer | `#1B6D24` / `#FFFFFF` / `#A3F69C` / `#002204` | `#88D982` / `#003909` / `#005313` / `#A3F69C` |
| `expense` | `#BA1A1A` / `#FFFFFF` / `#FFDAD6` / `#410002` | `#FFB4AB` / `#690005` / `#93000A` / `#FFDAD6` |
| `transfer` | `#345CA8` / `#FFFFFF` / `#D9E2FF` / `#001A43` | `#B0C6FF` / `#002D6E` / `#1A438F` / `#D9E2FF` |
| `hero` start → end, onHero | `primary` → `tertiary`, `onPrimary` | `primaryContainer` → `tertiaryContainer`, `onPrimaryContainer` |
| `glass` fill / solidFill / stroke | `surfaceContainerLowest` α0.75 / α0.95 / `onSurface` α0.06 | `surfaceContainerHigh` α0.70 / α0.95 / `onSurface` α0.08 |
| `background` / `card` | `surfaceContainer` / `surfaceContainerLowest` | `surface` / `surfaceContainer` |

`hero`, `glass`, `background`, `card` lấy role từ `scheme` được truyền vào, nên đổi theo Dynamic Color. Thu/chi/chuyển cố định.

`CategoryPalette`, 14 màu khởi điểm: `#C62828`, `#AD1457`, `#6A1B9A`, `#4527A0`, `#283593`, `#1565C0`, `#0277BD`, `#00838F`, `#00695C`, `#2E7D32`, `#558B2F`, `#E65100`, `#4E342E`, `#37474F`. Màu nào fail `ContrastTest` thì chỉnh tối hơn. Không hạ ngưỡng.

`categoryIconColors`: container = màu với alpha `0.16f` (sáng) hoặc `0.24f` (tối). Icon = chính màu đó (sáng), hoặc `lerp(color, Color.White, 0.4f)` (tối).

Typography: `material` = `Typography()` mặc định. `amountLarge` 32sp Bold, `amountMedium` 20sp SemiBold, `amountSmall` 14sp SemiBold, cả ba `fontFeatureSettings = "tnum"`.

Spacing `4, 8, 12, 16, 24, 32`. Sizes `iconSm 20`, `iconMd 24`, `avatar 36`, `touchTarget 48`, `border 1`, `navBarHeight 56`, `emptyIcon 72`, `progressStroke 2`, `sheetHandleWidth 32`, `sheetHandleHeight 4`. Radii `12, 16, 20, 24`. Shapes: `RoundedCornerShape` của từng radius, `full = RoundedCornerShape(percent = 50)`, `sheet = RoundedCornerShape(topStart = radii.xl, topEnd = radii.xl)`.

Motion: `standard = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)`, `emphasized = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)`, `pressedScale = 0.96f`, `shimmerMillis = 1200`.

Task 5–8 cần số mới thì thêm token ở đây trong cùng commit của task đó, không viết số ở component.

Brushes: `hero = Brush.linearGradient(listOf(hero.start, hero.end))`, dựng trong `FinoteTheme` từ `FinoteColors`.

- [ ] **Step 4: Viết `FinoteTheme`**

Thay `FinoteTheme.kt` hiện có. Chọn scheme: `dynamicColor && SDK >= S` → `dynamicLight/DarkColorScheme`, ngược lại → `LightBrandScheme`/`DarkBrandScheme`. Cấp mọi token qua `staticCompositionLocalOf` (khai báo `private` trong cùng file), bọc `MaterialTheme(colorScheme, typography = material)`. `MainActivity` gọi `FinoteTheme { … }` như cũ, không sửa.

- [ ] **Step 5: Chạy test, xác nhận pass**

Run qua `gradle-run`: `testDebugUnitTest`
Expected: `exit_status` 0, `ContrastTest` 4 test pass, Konsist cũ vẫn pass.

- [ ] **Step 6: Hỏi user, rồi commit**

```bash
git add app/src/main/java/io/github/toanhp123/finote/ui/common/theme app/src/test/java/io/github/toanhp123/finote/ui/common/theme/ContrastTest.kt
git commit -m "feat(ui): add design tokens and finote theme"
```

---

### Task 3: Format tiền

**Files:**
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/common/format/MoneyKind.kt`
- Create: `app/src/main/java/io/github/toanhp123/finote/ui/common/format/MoneyFormat.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/common/format/MoneyFormatTest.kt`

**Interfaces:**
- Produces: `enum class MoneyKind { Income, Expense, Transfer, Neutral }`, `fun formatMoney(amount: Long, kind: MoneyKind, locale: Locale): String`.

- [ ] **Step 1: Viết test fail**

```kotlin
package io.github.toanhp123.finote.ui.common.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    private val vi = Locale.forLanguageTag("vi")
    private val en = Locale.forLanguageTag("en")

    @Test
    fun `vietnamese puts the symbol after a no-break space`() {
        assertEquals("1.234.567\u00A0₫", formatMoney(1_234_567, MoneyKind.Neutral, vi))
    }

    @Test
    fun `english chinese japanese put the symbol first`() {
        listOf("en", "zh-CN", "ja").forEach {
            assertEquals(it, "₫1,234,567", formatMoney(1_234_567, MoneyKind.Neutral, Locale.forLanguageTag(it)))
        }
    }

    @Test
    fun `income gets a plus and expense a minus sign`() {
        assertEquals("+45.000\u00A0₫", formatMoney(45_000, MoneyKind.Income, vi))
        assertEquals("\u221245.000\u00A0₫", formatMoney(45_000, MoneyKind.Expense, vi))
        assertEquals("\u2212₫45,000", formatMoney(45_000, MoneyKind.Expense, en))
    }

    @Test
    fun `transfer and zero carry no sign`() {
        assertEquals("45.000\u00A0₫", formatMoney(45_000, MoneyKind.Transfer, vi))
        assertEquals("0\u00A0₫", formatMoney(0, MoneyKind.Income, vi))
        assertEquals("0\u00A0₫", formatMoney(0, MoneyKind.Expense, vi))
    }

    @Test
    fun `negative neutral shows minus and absolute value`() {
        assertEquals("\u22122.500.000\u00A0₫", formatMoney(-2_500_000, MoneyKind.Neutral, vi))
    }

    @Test
    fun `largest long formats in full`() {
        assertEquals("₫9,223,372,036,854,775,807", formatMoney(Long.MAX_VALUE, MoneyKind.Neutral, en))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `smallest long is rejected`() {
        formatMoney(Long.MIN_VALUE, MoneyKind.Neutral, vi)
    }
}
```

- [ ] **Step 2: Chạy, xác nhận fail**

Run qua `gradle-run`: `testDebugUnitTest --tests "*MoneyFormatTest"`
Expected: FAIL, `Unresolved reference: formatMoney`.

- [ ] **Step 3: Viết `MoneyKind` và `formatMoney`**

`require(amount != Long.MIN_VALUE)`. `NumberFormat.getCurrencyInstance(locale)`, `currency = Currency.getInstance("VND")`, min và max fraction digits `0`, format `abs(amount)`. Dấu: `Income` và `amount > 0` → `+`; `Expense` và `amount > 0` → `\u2212` (U+2212); `amount < 0` (mọi kind) → `\u2212` (U+2212); còn lại không dấu. Ghép dấu ở đầu chuỗi.

- [ ] **Step 4: Chạy, xác nhận pass**

Run qua `gradle-run`: `testDebugUnitTest --tests "*MoneyFormatTest"`
Expected: 7 test pass.

Chuỗi mong đợi đã chạy thử trên JDK 21.0.12 (daemon được ghim Java 21, xem `docs/architecture.md`). `zh-CN` và `ja` ra giống `en`. Nếu JVM chạy test khác bản và một locale ra chuỗi khác, tách assert riêng cho locale đó theo output thật. Luật dấu (`+`, U+2212 ở đầu chuỗi) và không có chữ số thập phân thì giữ nguyên. Trong file test, viết khoảng trắng không ngắt và dấu trừ bằng escape Kotlin (backslash-u `00A0`, backslash-u `2212`), không dán ký tự thô.

- [ ] **Step 5: Hỏi user, rồi commit**

```bash
git add app/src/main/java/io/github/toanhp123/finote/ui/common/format app/src/test/java/io/github/toanhp123/finote/ui/common/format
git commit -m "feat(ui): format money by app locale with explicit signs"
```

---

### Task 4: Luật Konsist cho design system

**Files:**
- Create: `app/src/test/java/io/github/toanhp123/finote/architecture/DesignSystemKonsistTest.kt`
- Modify: `app/src/test/java/io/github/toanhp123/finote/architecture/KonsistScopes.kt` (thêm hằng `UI_THEME`, `UI_COMPONENT`, `UI_FORMAT`)
- Modify: `app/src/main/java/io/github/toanhp123/finote/ui/navigation/FinoteNavHost.kt` (bỏ `Scaffold` của M3)

**Interfaces:**
- Consumes: package Haze và Compose Unstyled ghi ở Task 1 bước 4.
- Produces: annotation `FinotePreviews` được luật 4 đòi. Task 5 tạo annotation đó.

- [ ] **Step 1: Thêm hằng vào `KonsistScopes.kt`**

```kotlin
internal const val UI_THEME = "$ROOT.ui.common.theme"
internal const val UI_COMPONENT = "$ROOT.ui.common.component"
internal const val UI_FORMAT = "$ROOT.ui.common.format"
```

- [ ] **Step 2: Viết test**

```kotlin
package io.github.toanhp123.finote.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class DesignSystemKonsistTest {
    private val rawStyle =
        Regex(
            """\bMaterialTheme\b|\bColor\(|\bColor\.(?!Transparent\b|Unspecified\b)[A-Z]\w*""" +
                """|\.dp\b|\.sp\b|\bTextStyle\(|\bRoundedCornerShape\(|\bBrush\.""",
        )
    private val manualFormat = Regex("""\bNumberFormat\b|\bDecimalFormat\b|\bString\.format\(|"\.format\(""")
    private val publicComposable = Regex("""@Composable\s+(?:public\s+)?fun\s+(?:<[^>]+>\s+)?[A-Z]""")
    private val allowedMaterial = setOf("androidx.compose.material3.Text", "androidx.compose.material3.Icon")
    private val wrappedLibraries = listOf("androidx.compose.material3", HAZE, UNSTYLED)

    private val featureUi = filesIn(UI).filterNot { it.packageName().inPackage(UI_COMMON) }

    @Test
    fun `features use only text and icon from material3 and no wrapped library`() {
        featureUi.assertNoImports("features use only text and icon from material3 and no wrapped library") { import ->
            import.name !in allowedMaterial && wrappedLibraries.any { import.name.inPackage(it) }
        }
    }

    @Test
    fun `only the theme writes raw style values`() {
        filesIn(UI)
            .filterNot { it.packageName().inPackage(UI_THEME) }
            .assertTrue(strict = false, testName = "only the theme writes raw style values") {
                !rawStyle.containsMatchIn(it.codeText())
            }
    }

    @Test
    fun `only the format package formats numbers`() {
        productionFiles
            .filterNot { it.packageName().inPackage(UI_FORMAT) }
            .assertTrue(strict = false, testName = "only the format package formats numbers") {
                !manualFormat.containsMatchIn(it.codeText())
            }
    }

    @Test
    fun `shared components carry finote previews`() {
        filesIn(UI_COMPONENT)
            .filter { publicComposable.containsMatchIn(it.text) }
            .assertTrue(strict = false, testName = "shared components carry finote previews") {
                it.text.contains("@FinotePreviews")
            }
    }

    /** File text without `package` and `import` lines; they name `dp` and `Color` legitimately. */
    private fun KoFileDeclaration.codeText(): String =
        text.lineSequence().filterNot { line ->
            line.trimStart().let { it.startsWith("package ") || it.startsWith("import ") }
        }.joinToString("\n")

    private companion object {
        const val HAZE = "dev.chrisbanes.haze"
        const val UNSTYLED = "com.composeunstyled"
    }
}
```

Nếu Task 1 bước 4 ghi package khác, sửa `HAZE`/`UNSTYLED` cho khớp. `publicComposable` chỉ khớp khi `fun` đứng ngay sau `@Composable` và tên viết hoa. Vì vậy `private fun`, `internal fun` và hàm state viết thường (`rememberFinoteSnackbarState`) tự bị bỏ qua. `fun <T> FinoteSegmented` vẫn bị bắt.

- [ ] **Step 3: Bỏ `Scaffold` M3 khỏi `FinoteNavHost`**

`HomePlaceholder` đổi thành `Box(modifier = Modifier.fillMaxSize())`, xóa import `androidx.compose.material3.Scaffold` và `padding`.

- [ ] **Step 4: Red proof cho từng luật**

Với mỗi luật: tạo file vi phạm tạm, chạy `testDebugUnitTest --tests "*DesignSystemKonsistTest"` qua `gradle-run`, thấy đúng luật đó fail, ghi một dòng kết quả, xóa file. Không commit file nào.

| Luật | File tạm | Nội dung vi phạm |
|---|---|---|
| 1 | `ui/probe/Probe.kt` | `import androidx.compose.material3.Button` |
| 1 | `ui/probe/Probe.kt` | `import dev.chrisbanes.haze.HazeState` |
| 2 | `ui/probe/Probe.kt` | `val x = 16.dp` |
| 2 | `ui/common/component/Probe.kt` | `val c = Color.Red` |
| 2 (được phép) | `ui/probe/Probe.kt` | `val c = Color.Transparent` → luật 2 phải **pass** |
| 3 | `domain/probe/Probe.kt` | `val s = "%d".format(1)` |
| 4 | `ui/common/component/Probe.kt` | `@Composable fun Probe() {}` không có `@FinotePreviews` |
| 4 (được phép) | `ui/common/component/Probe.kt` | `@Composable fun rememberProbe(): Int = 0` → luật 4 phải **pass** |

- [ ] **Step 5: Chạy toàn bộ unit test**

Run qua `gradle-run`: `testDebugUnitTest`
Expected: `exit_status` 0. Code thật (theme, format, navigation) pass cả 4 luật.

- [ ] **Step 6: Hỏi user, rồi commit**

```bash
git add app/src/test/java/io/github/toanhp123/finote/architecture/DesignSystemKonsistTest.kt app/src/test/java/io/github/toanhp123/finote/architecture/KonsistScopes.kt app/src/main/java/io/github/toanhp123/finote/ui/navigation/FinoteNavHost.kt
git commit -m "test: enforce design system usage with konsist"
```

---

### Task 5: Screenshot suite và component hiển thị

**Files:**
- Modify: `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts` (suite + `qualityGate`)
- Create trong `ui/common/component/`: `FinotePreviews.kt`, `MoneyText.kt`, `CategoryIcon.kt`, `FinoteCard.kt`, `FinoteListRow.kt`, `TransactionRow.kt`, `BalanceHeroCard.kt`
- Create: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/common/component/DisplayScreenshots.kt`
- Create: icon `ic_restaurant.xml`, `ic_work.xml`, `ic_local_gas_station.xml` (dùng cho preview)
- Modify: 4 file `strings.xml` (`balance_total`, `balance_income`, `balance_expense`)

**Interfaces:**
- Consumes: `FinoteTheme` (Task 2), `formatMoney`, `MoneyKind` (Task 3).
- Produces:
  - `@Preview(name = "Light") @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES) @Preview(name = "Large font", fontScale = 2f) annotation class FinotePreviews`.
  - `MoneyText(amount: Long, kind: MoneyKind, modifier: Modifier = Modifier, style: TextStyle = FinoteTheme.typography.amountSmall)`.
  - `CategoryIcon(@DrawableRes iconRes: Int, colorArgb: Int, contentDescription: String?, modifier: Modifier = Modifier)`.
  - `FinoteCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`.
  - `FinoteListRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null)`.
  - `TransactionRow(title: String, subtitle: String, amount: Long, kind: MoneyKind, @DrawableRes iconRes: Int, colorArgb: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null)`.
  - `BalanceHeroCard(label: String, balance: Long, income: Long, expense: Long, modifier: Modifier = Modifier)`.
  - Task chụp ảnh: `updateScreenshotTestDefaultDebugTestSuite` và `testScreenshotTestDefaultDebugTestSuite` (tên dự kiến, xác nhận ở bước 2).

- [ ] **Step 1: Bật suite screenshot**

`gradle.properties`: `android.experimental.enableScreenshotTest=true`, `android.experimental.testSuiteSupport=true`.
Catalog: `screenshot = "0.0.1-alpha16"`, library `screenshot-validation-api` (`com.android.tools.screenshot:screenshot-validation-api`).
`app/build.gradle.kts`, trong `android.testOptions`:

```kotlin
screenshotTests.create("screenshotTest") {
    engineVersion = libs.versions.screenshot.get()
    targetVariants.add("debug")
    dependencies {
        implementation(libs.androidx.compose.ui.tooling)
        implementation(libs.screenshot.validation.api)
    }
}
```

- [ ] **Step 2: Xác nhận tên task**

Run qua `gradle-run`: `tasks --all`, tìm dòng chứa `ScreenshotTest` và `TestSuite`.
Ghi tên task update và test thật. Nếu khác tên dự kiến, dùng tên thật ở mọi bước sau.

- [ ] **Step 3: Nối vào `qualityGate`**

Thêm task test của suite vào `dependsOn` của `qualityGate`.

- [ ] **Step 4: Kiểm Konsist không đọc nhầm `screenshotTest`**

Run qua `gradle-run`: `testDebugUnitTest`. Nếu luật nào báo file trong `src/screenshotTest`, sửa `productionFiles` trong `KonsistScopes.kt` để loại thư mục đó, và ghi lý do một dòng.

- [ ] **Step 5: Viết `FinotePreviews` và 6 component**

Theo hướng C (mockup `.superpowers/brainstorm/*/content/style-c-modes.html`):
- `MoneyText`: gọi `formatMoney` với `LocalConfiguration.current.locales[0]`. Màu: `Income` → `colors.income.color`, `Expense` → `colors.expense.color`, `Transfer` → `colors.transfer.color`, `Neutral` → `scheme.onSurface`. `maxLines = 1`, `softWrap = false`.
- `CategoryIcon`: ô `sizes.avatar`, `shapes.sm`, màu từ `categoryIconColors(colorArgb, FinoteTheme.isDark)`, icon `sizes.iconSm`.
- `FinoteCard`: nền `colors.card`, `shapes.lg`, padding `spacing.md`. Có `onClick` thì co về `motion.pressedScale` khi nhấn, dùng `motion.standard`.
- `FinoteListRow`: hàng cao ≥ `sizes.touchTarget`. Title `bodyLarge`, `maxLines = 1`, cắt `…`. Subtitle `bodySmall`, `onSurfaceVariant`.
- `TransactionRow`: `FinoteCard` + `FinoteListRow` với `CategoryIcon` bên trái, `MoneyText` bên phải. Title nhường chỗ, `MoneyText` không bao giờ co.
- `BalanceHeroCard`: nền `brushes.hero`, `shapes.xl`, chữ `hero.onHero`. Số dư `amountLarge`, `MoneyKind.Neutral`. Hai chip ↑ thu, ↓ chi trên nền `hero.chipContainer`.

Mỗi file có preview `private` gắn `@FinotePreviews`, bọc `FinoteTheme(dynamicColor = false)`.

- [ ] **Step 6: Viết screenshot test**

`DisplayScreenshots.kt`: mỗi component một hàm `@PreviewTest @FinotePreviews @Composable`, bọc `FinoteTheme(dynamicColor = false)`. Thêm các ca của Review Focus:
- `TransactionRowLongPreview`: title 80 ký tự, amount `Long.MAX_VALUE`, `Expense`.
- `MoneyTextEnglishPreview`: `@Preview(locale = "en")`, `1_234_567`, `Neutral`.
- `BalanceHeroCardNegativePreview`: `balance = -2_500_000`.

- [ ] **Step 7: Tạo ảnh mẫu và xem**

Run qua `gradle-run`: task update của suite.
Mở từng PNG trong `app/src/screenshotTestDebug/reference/` (hoặc đường dẫn suite báo), so với mockup C. Sửa component tới khi đúng. Chữ lớn: title cắt `…`, tiền nguyên vẹn.

- [ ] **Step 8: Chạy gate**

Run qua `gradle-run`: `qualityGate`
Expected: `exit_status` 0.

- [ ] **Step 9: Hỏi user, rồi commit (hai commit)**

```bash
git add gradle.properties gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add compose screenshot test suite to the gate"
git add app/src/main/java/io/github/toanhp123/finote/ui/common/component app/src/main/res app/src/screenshotTest app/src/screenshotTestDebug
git commit -m "feat(ui): add money, category and transaction display components"
```

Nếu `tasks --all` cho đường dẫn ảnh mẫu khác `app/src/screenshotTestDebug`, sửa đường dẫn trong `git add`.

---

### Task 6: Component hành động và nhập

**Files:**
- Create trong `ui/common/component/`: `FinoteButtonStyle.kt`, `FinoteButton.kt`, `FinoteIconButton.kt`, `FinoteTextField.kt`, `FinoteSwitch.kt`, `FinoteChip.kt`, `FinoteSegmented.kt`
- Create: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/common/component/InputScreenshots.kt`
- Modify: 4 file `strings.xml` (`cd_loading`)

**Interfaces:**
- Consumes: `FinoteTheme`, `FinotePreviews`.
- Produces:
  - `enum class FinoteButtonStyle { Primary, Secondary, Ghost, Danger }`.
  - `FinoteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: FinoteButtonStyle = FinoteButtonStyle.Primary, enabled: Boolean = true, isLoading: Boolean = false, @DrawableRes leadingIcon: Int? = null)`.
  - `FinoteIconButton(@DrawableRes iconRes: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`.
  - `FinoteTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String? = null, errorText: String? = null, maxLength: Int? = null, singleLine: Boolean = true, keyboardOptions: KeyboardOptions = KeyboardOptions.Default)`.
  - `FinoteSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`.
  - `FinoteChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes iconRes: Int? = null)`.
  - `<T> FinoteSegmented(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Viết 6 component**

- `FinoteButton`: cao `sizes.touchTarget`, `shapes.md`. Primary nền `scheme.primary`; Secondary nền `colors.card` viền `glass.stroke`; Ghost không nền; Danger nền `colors.expense.color`. Nhấn co `motion.pressedScale`. `isLoading` thay chữ bằng vòng tải, chặn click, `contentDescription` = `cd_loading`. `enabled = false` giảm alpha nội dung về `colors.disabledAlpha`. Vòng tải dày `sizes.progressStroke`, cỡ `sizes.iconSm`.
- `FinoteIconButton`: vùng chạm `sizes.touchTarget`, icon `sizes.iconMd`, `Role.Button`.
- `FinoteTextField`: dùng `TextField` của M3 hoặc `BasicTextField` của foundation, chọn cái nào đạt mockup C. Nền `colors.card`, `shapes.md`, viền `glass.stroke`, viền `colors.expense.color` khi có `errorText`. `maxLength` hiện bộ đếm `n/max` và chặn nhập quá.
- `FinoteSwitch`: toggle của Compose Unstyled, ray `scheme.primary` khi bật. Vùng chạm 48.
- `FinoteChip`: `shapes.full`, chọn → nền `scheme.primary`, chữ `scheme.onPrimary`.
- `FinoteSegmented`: khung `shapes.md` nền `colors.card`, mục chọn trượt bằng `motion.emphasized`. Mỗi mục `Role.Tab`, `selected` trong semantics.

Mỗi file có preview `@FinotePreviews` đủ trạng thái: thường, tắt, lỗi (text field), đang tải (button).

- [ ] **Step 2: Screenshot test, tạo ảnh mẫu, xem**

`InputScreenshots.kt` như Task 5 bước 6. Run task update, mở từng PNG, sửa tới khi khớp mockup C.

- [ ] **Step 3: Chạy gate**

Run qua `gradle-run`: `qualityGate`
Expected: `exit_status` 0.

- [ ] **Step 4: Hỏi user, rồi commit**

```bash
git add app/src/main/java/io/github/toanhp123/finote/ui/common/component app/src/main/res app/src/screenshotTest app/src/screenshotTestDebug
git commit -m "feat(ui): add button, text field, switch, chip and segmented components"
```

---

### Task 7: Component khung

**Files:**
- Create trong `ui/common/component/`: `BlurSupport.kt`, `HazeLocals.kt`, `FinoteScaffold.kt`, `FinoteTopBar.kt`, `FinoteNavItem.kt`, `FinoteNavBar.kt`, `FinoteSnackbarState.kt`, `FinoteSnackbar.kt`, `FinoteEmptyState.kt`, `FinoteSkeleton.kt`
- Create: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/common/component/FrameScreenshots.kt`
- Create: icon `ic_arrow_back.xml`, `ic_home.xml`, `ic_receipt_long.xml`, `ic_pie_chart.xml`, `ic_settings.xml`
- Modify: 4 file `strings.xml` (`cd_back`)

**Interfaces:**
- Consumes: `FinoteTheme`, `FinotePreviews`, `FinoteIconButton` (Task 6).
- Produces:
  - `internal val LocalBlurEnabled: ProvidableCompositionLocal<Boolean>`, mặc định `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` (BlurSupport.kt).
  - `internal val LocalHazeState: ProvidableCompositionLocal<HazeState?>` (HazeLocals.kt).
  - `internal fun Modifier.finoteGlass(shape: Shape): Modifier`: có blur và có `LocalHazeState` → `hazeEffect` + `glass.fill`; không → nền `glass.solidFill`. Luôn viền `glass.stroke`, dày `sizes.border`. Đặt trong `HazeLocals.kt`.
  - `class FinoteSnackbarState internal constructor(internal val host: SnackbarHostState) { suspend fun show(message: String, actionLabel: String? = null): Boolean }` (true khi user bấm action), `@Composable fun rememberFinoteSnackbarState(): FinoteSnackbarState`.
  - `FinoteScaffold(modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {}, bottomBar: @Composable () -> Unit = {}, snackbarState: FinoteSnackbarState? = null, content: @Composable (PaddingValues) -> Unit)`.
  - `FinoteTopBar(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})`.
  - `data class FinoteNavItem(@DrawableRes val iconRes: Int, val label: String)`.
  - `FinoteNavBar(items: List<FinoteNavItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)`.
  - `FinoteEmptyState(@DrawableRes iconRes: Int, title: String, modifier: Modifier = Modifier, message: String? = null, action: (@Composable () -> Unit)? = null)`.
  - `FinoteSkeleton(modifier: Modifier = Modifier, shape: Shape = FinoteTheme.shapes.sm)`.

- [ ] **Step 1: Viết blur và Haze**

`FinoteScaffold` tạo `HazeState` bằng API của Haze 2.1 (README), gắn `hazeSource` vào vùng `content`, `CompositionLocalProvider(LocalHazeState provides state)`. Thanh dưới nổi trên nội dung: `content` nhận `PaddingValues` có đáy = `sizes.navBarHeight` + `spacing.md` × 2 + inset hệ thống. Nền màn `colors.background`. Snackbar hiện trên thanh dưới.

- [ ] **Step 2: Viết các component còn lại**

- `FinoteTopBar`: nút back là `FinoteIconButton` với `ic_arrow_back` và `cd_back`. Tiêu đề `titleLarge`.
- `FinoteNavBar`: cao `sizes.navBarHeight`, cách mép `spacing.md`, `shapes.lg`, `Modifier.finoteGlass`. Mục chọn là khối `scheme.primary` `shapes.sm`, icon `scheme.onPrimary`. Mỗi mục `Role.Tab`, `contentDescription` = `label`.
- `FinoteSnackbar`: `internal`, vẽ `SnackbarHost` với thẻ `finoteGlass`, `shapes.md`.
- `FinoteEmptyState`: icon `sizes.emptyIcon`, title `titleMedium`, message `bodyMedium`.
- `FinoteSkeleton`: khối `scheme.surfaceContainerHigh` với dải sáng chạy ngang, `rememberInfiniteTransition`, chu kỳ `motion.shimmerMillis`.

- [ ] **Step 3: Screenshot test, tạo ảnh mẫu, xem**

`FrameScreenshots.kt` như Task 5 bước 6. Thêm `FinoteNavBarNoBlurPreview`: bọc `CompositionLocalProvider(LocalBlurEnabled provides false)`, đặt thanh trên một `TransactionRow`. Chữ và icon của thanh phải đọc rõ. Một preview màn đủ: `FinoteScaffold` + `FinoteTopBar` + `BalanceHeroCard` + 3 `TransactionRow` + `FinoteNavBar`, so với mockup C sáng và tối.

- [ ] **Step 4: Chạy gate**

Run qua `gradle-run`: `qualityGate`
Expected: `exit_status` 0.

- [ ] **Step 5: Hỏi user, rồi commit**

```bash
git add app/src/main/java/io/github/toanhp123/finote/ui/common/component app/src/main/res app/src/screenshotTest app/src/screenshotTestDebug
git commit -m "feat(ui): add scaffold, top bar, glass nav bar and feedback components"
```

---

### Task 8: Sheet, dialog, chọn ngày

**Files:**
- Create trong `ui/common/component/`: `FinoteBottomSheet.kt`, `FinoteDialog.kt`, `FinoteDatePicker.kt`, `DatePickerDates.kt`
- Test: `app/src/test/java/io/github/toanhp123/finote/ui/common/component/DatePickerDatesTest.kt`
- Create: `app/src/screenshotTest/kotlin/io/github/toanhp123/finote/ui/common/component/OverlayScreenshots.kt`
- Modify: 4 file `strings.xml` (`action_ok`, `action_cancel`)

**Interfaces:**
- Consumes: `finoteGlass`, `LocalHazeState` (Task 7), `FinoteButton` (Task 6).
- Produces:
  - `internal fun LocalDate.toUtcEpochMillis(): Long`, `internal fun Long.utcEpochMillisToLocalDate(): LocalDate`.
  - `FinoteBottomSheet(isVisible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`.
  - `FinoteDialog(isVisible: Boolean, title: String, onDismiss: () -> Unit, confirm: @Composable () -> Unit, modifier: Modifier = Modifier, text: String? = null, dismiss: (@Composable () -> Unit)? = null)`.
  - `FinoteDatePicker(isVisible: Boolean, selected: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Viết test ngày (fail)**

```kotlin
package io.github.toanhp123.finote.ui.common.component

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DatePickerDatesTest {
    @Test
    fun `epoch day two is one day of millis`() {
        assertEquals(86_400_000L, LocalDate.of(1970, 1, 2).toUtcEpochMillis())
    }

    @Test
    fun `round trip keeps the date in any time zone`() {
        val date = LocalDate.of(2026, 11, 3)
        assertEquals(date, date.toUtcEpochMillis().utcEpochMillisToLocalDate())
    }

    @Test
    fun `millis late in a utc day stay on that day`() {
        val lastMillis = LocalDate.of(2026, 11, 4).toUtcEpochMillis() - 1
        assertEquals(LocalDate.of(2026, 11, 3), lastMillis.utcEpochMillisToLocalDate())
    }
}
```

- [ ] **Step 2: Chạy, xác nhận fail**

Run qua `gradle-run`: `testDebugUnitTest --tests "*DatePickerDatesTest"`
Expected: FAIL, `Unresolved reference: toUtcEpochMillis`.

- [ ] **Step 3: Viết `DatePickerDates.kt`**

Dùng `ZoneOffset.UTC`, không dùng múi giờ máy (DatePicker của M3 trả millis theo UTC).

- [ ] **Step 4: Chạy, xác nhận pass**

Run qua `gradle-run`: `testDebugUnitTest --tests "*DatePickerDatesTest"`
Expected: 3 test pass.

- [ ] **Step 5: Viết 3 component**

- `FinoteBottomSheet`: modal bottom sheet của Compose Unstyled. Thẻ `shapes.sheet`, `finoteGlass`, tay nắm `sizes.sheetHandleWidth` × `sizes.sheetHandleHeight` trên đỉnh. Mở/đóng bằng `motion.emphasized`. Đóng khi vuốt xuống, bấm ngoài, hoặc nút back.
- `FinoteDialog`: dialog của Compose Unstyled. Thẻ `colors.card`, `shapes.xl`, title `titleLarge`, text `bodyMedium`, hai nút ở đáy phải.
- `FinoteDatePicker`: `DatePicker` của M3 trong `FinoteBottomSheet`. Tạo `rememberDatePickerState` bên trong từ `selected.toUtcEpochMillis()`. Đổi `DatePickerDefaults.colors` sang role của `FinoteTheme`. Nút `action_cancel` (`Ghost`) và `action_ok` (`Primary`). Không để `DatePickerState` ra ngoài.

- [ ] **Step 6: Screenshot test, tạo ảnh mẫu, xem**

`OverlayScreenshots.kt`. Sheet và dialog vẽ ở trạng thái mở. Nếu layoutlib không vẽ được popup/window, chụp phần nội dung bên trong (tách thành `internal` composable `…Content`) và ghi lý do một dòng.

- [ ] **Step 7: Chạy gate**

Run qua `gradle-run`: `qualityGate`
Expected: `exit_status` 0.

- [ ] **Step 8: Hỏi user, rồi commit**

```bash
git add app/src/main/java/io/github/toanhp123/finote/ui/common/component app/src/test/java/io/github/toanhp123/finote/ui/common/component app/src/main/res app/src/screenshotTest app/src/screenshotTestDebug
git commit -m "feat(ui): add bottom sheet, dialog and date picker"
```

---

### Task 9: Tài liệu, review, squash

**Files:**
- Create: `docs/design-system.md`
- Modify: `CLAUDE.md` (mục Architecture), `docs/architecture.md` (một dòng trỏ tới, thêm 3 luật vào "Rules only `reviewer` can check"), `docs/roadmap.md` (P1: thêm `feature/design-system` trước `feature/cash-entry`)

- [ ] **Step 1: Viết `docs/design-system.md`**

Tiếng Anh, giọng như `docs/architecture.md`. Nội dung:
- Bảng token (tên, nơi đặt, cách gọi qua `FinoteTheme`).
- Bảng component với chữ ký ngắn và khi nào dùng.
- Luật dùng: 4 luật Konsist, 3 luật của `reviewer` (gradient chỉ cho `BalanceHeroCard`, tối đa một chỗ mỗi màn; kính mờ chỉ cho phần nổi; màu không là tín hiệu duy nhất).
- Thêm token hoặc component mới: đặt ở đâu, cần preview và screenshot.
- Cập nhật ảnh mẫu: lệnh update, bắt buộc xem PNG trước khi commit.
- Blur: API 31+ thật, API 26–30 `solidFill`.

- [ ] **Step 2: Cập nhật `CLAUDE.md`, `docs/architecture.md`, `docs/roadmap.md`**

`CLAUDE.md` mục Architecture thêm: `- IMPORTANT: UI tokens, components, and usage rules are in docs/design-system.md.`

- [ ] **Step 3: Chạy gate**

Run qua `gradle-run`: `qualityGate`
Expected: `exit_status` 0.

- [ ] **Step 4: Kiểm screenshot trên Linux**

Nếu repo có remote và CI: đẩy branch, mở PR vào `dev` (hỏi user trước), đọc kết quả job `quality-gate`. Nếu ảnh lệch chỉ vì nền tảng, ghi vào `docs/design-system.md` và hỏi user cách xử lý (ngưỡng sai khác hoặc tạo ảnh mẫu trên CI). Nếu chưa có remote, ghi rủi ro này vào `docs/design-system.md`.

- [ ] **Step 5: Hỏi user, rồi commit**

```bash
git add docs/design-system.md CLAUDE.md docs/architecture.md docs/roadmap.md
git commit -m "docs: add design system guide"
```

- [ ] **Step 6: `reviewer` kiểm cả branch**

Dispatch `reviewer` với spec, plan, và diff `dev...feature/design-system`. Lỗi gửi lại `coder`, rồi review lại.

- [ ] **Step 7: Hỏi user, rồi squash vào `dev`**

```bash
git switch dev
git merge --squash feature/design-system
git commit -m "feat(ui): add design system tokens, theme and components"
```
