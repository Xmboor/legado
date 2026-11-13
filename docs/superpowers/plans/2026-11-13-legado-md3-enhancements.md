# Legado MD3 优点补齐与增强 实现计划

> 按已批准规格 `docs/superpowers/specs/2026-11-13-legado-md3-enhancements-design.md` 实现。步骤用 `- [ ]` 跟踪。

**Goal:** 新增 Material You 动态取色 + 搜索定位高亮 + 卡片接入 UiCorner，编译生成 APK 并提交推送。

**Architecture:** 动态取色经 `DynamicColor` helper 解析系统 Monet 色调（带兜底）写入既有 `ThemeStore`，玻璃/卡片自动跟随；定位高亮叠加在基类 `PreferenceFragment`；卡片圆角/透明度改用 `UiCorner`。

**Tech Stack:** Kotlin, AndroidX Preference, Material 1.13.0, JUnit4, Gradle flavor `app`。

## Global Constraints

- minSdk 21；动态取色仅 API≥31（`Build.VERSION_CODES.S`）生效，低版本禁用开关。
- 不改玻璃逻辑/`MainActivity`/base adapter/touch helpers/`ChangeBookSourceDialog`。
- 现有 EInk/日/夜主题分支、手动主题色、搜索过滤逻辑文本不变，新增均为叠加。
- 不提交 `settings.gradle`、`local.properties`、`~/.gradle`。
- 构建环境：`cd /c/Users/xmboo/source/repos/legado && export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9" && export ANDROID_HOME="D:/DevTools/android-sdk"`。
- 构建任务：`compileAppDebugKotlin`、`testAppDebugUnitTest`、`assembleAppDebug`。

---

### Task 1: DynamicColor 调色板 helper（TDD）

**Files:**
- Create: `app/src/main/java/io/legado/app/lib/theme/DynamicColor.kt`
- Test: `app/src/test/java/io/legado/app/lib/theme/DynamicColorTest.kt`

**Interfaces:**
- Produces: `DynamicColor.Palette(primary:Int, accent:Int, background:Int, bottomBackground:Int)`；`DynamicColor.isAvailable():Boolean`；`DynamicColor.buildPalette(isNight:Boolean, fallback:Palette, resolve:(String)->Int?):Palette`（internal）；`DynamicColor.palette(context:Context, isNight:Boolean, fallback:Palette):Palette`。

- [ ] **Step 1: 写失败单测** `DynamicColorTest.kt`

```kotlin
package io.legado.app.lib.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicColorTest {

    private val fallback = DynamicColor.Palette(1, 2, 3, 4)

    @Test
    fun buildPalette_day_overridesAll() {
        val p = DynamicColor.buildPalette(false, fallback) { name ->
            when (name) {
                "system_accent1_50" -> 100
                "system_accent1_600" -> 200
                "system_neutral1_50" -> 300
                "system_neutral1_100" -> 400
                else -> null
            }
        }
        assertEquals(DynamicColor.Palette(100, 200, 300, 400), p)
    }

    @Test
    fun buildPalette_night_overridesAll() {
        val p = DynamicColor.buildPalette(true, fallback) { name ->
            when (name) {
                "system_neutral1_800" -> 100
                "system_accent1_300" -> 200
                "system_neutral1_900" -> 300
                else -> null
            }
        }
        assertEquals(DynamicColor.Palette(100, 200, 300, 100), p)
    }

    @Test
    fun buildPalette_allMissing_fallsBack() {
        val p = DynamicColor.buildPalette(false, fallback) { null }
        assertEquals(fallback, p)
    }

    @Test
    fun buildPalette_partialMissing_fallsBackPerColor() {
        val p = DynamicColor.buildPalette(false, fallback) { name ->
            if (name == "system_accent1_600") 200 else null
        }
        assertEquals(DynamicColor.Palette(1, 200, 3, 4), p)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew testAppDebugUnitTest --tests "io.legado.app.lib.theme.DynamicColorTest"`
Expected: 编译失败（`DynamicColor` 未定义）。

- [ ] **Step 3: 实现 `DynamicColor.kt`**

```kotlin
package io.legado.app.lib.theme

import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

object DynamicColor {

    data class Palette(
        @ColorInt val primary: Int,
        @ColorInt val accent: Int,
        @ColorInt val background: Int,
        @ColorInt val bottomBackground: Int
    )

    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    internal fun buildPalette(
        isNight: Boolean,
        fallback: Palette,
        resolve: (String) -> Int?
    ): Palette {
        fun pick(name: String, fb: Int): Int = resolve(name) ?: fb
        return if (isNight) {
            Palette(
                primary = pick("system_neutral1_800", fallback.primary),
                accent = pick("system_accent1_300", fallback.accent),
                background = pick("system_neutral1_900", fallback.background),
                bottomBackground = pick("system_neutral1_800", fallback.bottomBackground)
            )
        } else {
            Palette(
                primary = pick("system_accent1_50", fallback.primary),
                accent = pick("system_accent1_600", fallback.accent),
                background = pick("system_neutral1_50", fallback.background),
                bottomBackground = pick("system_neutral1_100", fallback.bottomBackground)
            )
        }
    }

    fun palette(context: Context, isNight: Boolean, fallback: Palette): Palette {
        return buildPalette(isNight, fallback) { name ->
            val id = context.resources.getIdentifier(name, "color", "android")
            if (id == 0) null
            else runCatching { ContextCompat.getColor(context, id) }.getOrNull()
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew testAppDebugUnitTest --tests "io.legado.app.lib.theme.DynamicColorTest"`
Expected: PASS（4 测试）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/io/legado/app/lib/theme/DynamicColor.kt app/src/test/java/io/legado/app/lib/theme/DynamicColorTest.kt
git commit -m "feat(theme): DynamicColor 壁纸调色板 helper + 单测"
```

---

### Task 2: 动态取色配置接线

**Files:**
- Modify: `app/src/main/java/io/legado/app/constant/PreferKey.kt`（`cBBackground` 后）
- Modify: `app/src/main/java/io/legado/app/help/config/AppConfig.kt`（`uiCornerReplyFollow` 后）
- Modify: `app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh/strings.xml`（`change_icon_summary` 附近）
- Modify: `app/src/main/res/xml/pref_config_theme.xml`（`defaultHomePage` 后）
- Modify: `app/src/main/java/io/legado/app/help/config/ThemeConfig.kt`（`applyTheme` when 内）
- Modify: `app/src/main/java/io/legado/app/ui/config/ThemeConfigFragment.kt`

**Interfaces:**
- Consumes: `DynamicColor.isAvailable()`、`DynamicColor.palette(context,isNight,fallback)`、`DynamicColor.Palette`。
- Produces: `PreferKey.cDynamicColor`、`AppConfig.isDynamicColor`、`R.string.dynamic_color`/`dynamic_color_summary`/`dynamic_color_unavailable`。

- [ ] **Step 1: PreferKey** 在 `const val cBBackground = "colorBottomBackground"` 后加：

```kotlin
    const val cDynamicColor = "dynamicColor"
```

- [ ] **Step 2: AppConfig** 在 `uiCornerReplyFollow` getter 后加：

```kotlin
    var isDynamicColor: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.cDynamicColor, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.cDynamicColor, value)
```

- [ ] **Step 3: 字符串** EN（`values/strings.xml`，`change_icon_summary` 后）：

```xml
    <string name="dynamic_color">Material You dynamic color</string>
    <string name="dynamic_color_summary">Follow the system wallpaper palette (Android 12+). Overrides manual theme colors.</string>
    <string name="dynamic_color_unavailable">Requires Android 12 or above</string>
```

ZH（`values-zh/strings.xml`，`change_icon_summary` 后）：

```xml
    <string name="dynamic_color">Material You 动态取色</string>
    <string name="dynamic_color_summary">跟随系统壁纸自动取色（Android 12+），开启后覆盖手动主题色</string>
    <string name="dynamic_color_unavailable">需要 Android 12 及以上</string>
```

- [ ] **Step 4: pref_config_theme.xml** 在 `defaultHomePage` 项后、`mainTransparentStatusBar` 项前加：

```xml
    <io.legado.app.lib.prefs.SwitchPreference
        android:defaultValue="false"
        android:key="dynamicColor"
        android:summary="@string/dynamic_color_summary"
        android:title="@string/dynamic_color"
        app:iconSpaceReserved="false" />
```

- [ ] **Step 5: ThemeConfig.applyTheme** 新增 `import android.os.Build`（若无）；在 `when` 的 EInk 分支后、`AppConfig.isNightTheme ->` 前插入规格组件 3 的动态分支（完整代码见规格）。

- [ ] **Step 6: ThemeConfigFragment** `onCreatePreferences` 末尾加 API<31 禁用；`onSharedPreferenceChanged` when 加 `PreferKey.cDynamicColor -> upTheme(AppConfig.isNightTheme)`（完整代码见规格组件 4）。

- [ ] **Step 7: 编译**

Run: `./gradlew compileAppDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/io/legado/app/constant/PreferKey.kt app/src/main/java/io/legado/app/help/config/AppConfig.kt app/src/main/java/io/legado/app/help/config/ThemeConfig.kt app/src/main/java/io/legado/app/ui/config/ThemeConfigFragment.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml app/src/main/res/xml/pref_config_theme.xml
git commit -m "feat(theme): Material You 动态取色开关接入 ThemeStore"
```

---

### Task 3: 搜索定位高亮

**Files:**
- Modify: `app/src/main/java/io/legado/app/lib/prefs/fragment/PreferenceFragment.kt`

**Interfaces:**
- Consumes: `UiCorner.actionRadius(context)`、`Fragment.accentColor`、`androidx.core.graphics.ColorUtils.setAlphaComponent`。

- [ ] **Step 1: 加 import + 在 consumeActivityTargetKey 滚动后调用 highlightPreference + 新增方法**（完整代码见规格组件 5）。`listView.post` 块改为：

```kotlin
        listView.post {
            scrollPreferenceToTop(preference)
            highlightPreference(preference)
            activity?.intent?.removeExtra("targetKey")
        }
```

- [ ] **Step 2: 编译**

Run: `./gradlew compileAppDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/io/legado/app/lib/prefs/fragment/PreferenceFragment.kt
git commit -m "feat(settings): 搜索定位增加高亮闪烁"
```

---

### Task 4: 卡片接入 UiCorner

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt`

**Interfaces:**
- Consumes: `UiCorner.scaledDp(12f)`、`UiCorner.layoutAlpha()`。

- [ ] **Step 1: 两 adapter 各加 `import io.legado.app.lib.theme.UiCorner`，`upCardBackground` 改为规格组件 6 代码**（圆角 `UiCorner.scaledDp(12f)`，alpha 乘 `UiCorner.layoutAlpha()`）。

- [ ] **Step 2: 编译**

Run: `./gradlew compileAppDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt
git commit -m "feat(ui): 书源/订阅卡片圆角与透明度接入 UiCorner"
```

---

### Task 5: 构建 APK + 推送 + 交付

- [ ] **Step 1: 全量单测**

Run: `./gradlew testAppDebugUnitTest`
Expected: DynamicColorTest PASS；已知基线失败 `ReadBookConfigTest.sanitize_clampsUnsafeLineSpacing` 与本次无关。

- [ ] **Step 2: 打包**

Run: `./gradlew assembleAppDebug`
Expected: BUILD SUCCESSFUL；APK 在 `app/build/outputs/apk/app/debug/`。

- [ ] **Step 3: 推送**

```bash
git push origin own
```

- [ ] **Step 4: 交付** 提供 APK 路径 + 真机验证清单（规格测试节）。

---

## Self-Review

- 规格覆盖：动态取色（Task1-2）、定位高亮（Task3）、卡片 UiCorner（Task4）、构建交付（Task5）——三项优点全覆盖。
- 占位符：无 TBD/TODO；代码块完整。
- 类型一致：`DynamicColor.Palette`/`buildPalette`/`palette` 签名 Task1 定义、Task2 消费一致；`PreferKey.cDynamicColor`、`AppConfig.isDynamicColor`、`R.string.dynamic_color*` 命名前后一致。