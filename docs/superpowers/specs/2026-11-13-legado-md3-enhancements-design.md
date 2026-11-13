# Legado MD3 优点补齐与增强 设计文档

> 状态：已获用户批准（范围＝补齐+增强）。本文件为实现规格。

## 背景与现状核对

用户要求结合 `https://github.com/HapeLee/legado-with-MD3/releases/tag/3.26.25` 的优点再生成一版 APK，确认纳入三项优点：设置项搜索与定位、MD3 视觉强化、Material You 动态取色。

对当前 fork（分支 `own`，HEAD `03e104c`）核对后发现：

- **设置项搜索与定位：已实现并随 APK 发布**（pre-existing 提交 `caf25d4`/`c051f9f`/`955dd9f`）。
  - `ui/main/my/MyFragment.kt`：`SearchView` + 跨 XML 二级搜索索引（`subSearchSources` 覆盖 11 个配置页），点击结果 `startActivity<ConfigActivity>` 携带 `configTag` + `targetKey`。
  - `lib/prefs/fragment/PreferenceFragment.kt`：`onViewCreated` 自动调用 `consumeActivityTargetKey()` → `scrollPreferenceToTop()` 把目标项滚到顶部 → 移除 `targetKey`。
  - **缺口**：只滚动、不高亮。
- **MD3 视觉强化：已实现**（`lib/theme/UiCorner.kt` 圆角/表面透明度系统 + `applyUiBodyTypeface`/`applyUiMenuStyle` 字体 + `ThemeConfig.Config` 持久化 `uiCornerScale`/`uiLayoutAlpha`/`fontScale`/`uiFontPath`/`titleFontPath`）。
  - **缺口**：Phase D 新建的书源/订阅卡片 `upCardBackground()` 硬编码 `cornerRadius = 12f * density`、固定填充 alpha 20/60，**绕过 UiCorner**，不跟随用户的圆角尺度/透明度设置。
- **Material You 动态取色：完全缺失**（全仓库无 `DynamicColors`/`system_accent`/`dynamicColor` 引用）。

因此本版不重建已有功能，而是：新增动态取色 + 补全定位高亮 + 把卡片接入 UiCorner。

## 目标

1. 新增「Material You 动态取色」开关：Android 12+ 跟随系统壁纸取色，写入既有 `ThemeStore`，玻璃栏/卡片/全页面自动跟随，零玻璃改动。
2. 搜索定位在滚动基础上增加高亮闪烁，所有配置页统一生效。
3. 书源/订阅卡片圆角与填充透明度接入 `UiCorner`，跟随全局设置，默认外观不变。

## 非目标 / 约束

- 不合并 Mochi/Compose 主题分支（重写级，已排除）。
- 不改玻璃逻辑（`GlassBarHelper`）、`MainActivity`、base adapter、touch helpers、`ChangeBookSourceDialog`。
- 不提交镜像仓库改动（`settings.gradle`）、env 文件（`local.properties`、`~/.gradle`）。
- 零回归：现有 EInk/日/夜主题分支、手动主题色、搜索过滤逻辑保持不变；新增均为叠加。
- minSdk 21；动态取色仅 API≥31 生效，低版本禁用开关并提示。

## 架构与组件

### 组件 1：DynamicColor 调色板 helper（新增）

文件：`app/src/main/java/io/legado/app/lib/theme/DynamicColor.kt`

职责：把系统 Monet 色调资源解析为 legado 四色（primary/accent/background/bottomBackground），带兜底。

- `data class Palette(primary, accent, background, bottomBackground)`（均为 `@ColorInt Int`）。
- `fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`。
- `internal fun buildPalette(isNight: Boolean, fallback: Palette, resolve: (String) -> Int?): Palette`：**纯函数**，按日/夜选择色调资源名，`resolve(name)` 返回 null 时回退到 `fallback` 对应色。便于 JVM 单测。
- `fun palette(context: Context, isNight: Boolean, fallback: Palette): Palette`：用 `resources.getIdentifier(name, "color", "android")` 取资源 id（id==0 视为缺失），`runCatching { ContextCompat.getColor(context, id) }.getOrNull()` 解析，委托 `buildPalette`。用 `getIdentifier` 而非直接引用 `android.R.color.system_*` 常量，避免 API31 常量 lint 与 OEM 缺失崩溃。

色调映射（legado 的 primary＝顶栏/表面色，accent＝品牌色）：

| 角色 | 日间资源 | 夜间资源 |
|---|---|---|
| primary（表面） | `system_accent1_50` | `system_neutral1_800` |
| accent（品牌） | `system_accent1_600` | `system_accent1_300` |
| background | `system_neutral1_50` | `system_neutral1_900` |
| bottomBackground | `system_neutral1_100` | `system_neutral1_800` |

### 组件 2：开关与配置接线

- `constant/PreferKey.kt`：在 `cBBackground` 后新增 `const val cDynamicColor = "dynamicColor"`。
- `help/config/AppConfig.kt`：在 `uiCornerReplyFollow` 后新增
  ```kotlin
  var isDynamicColor: Boolean
      get() = appCtx.getPrefBoolean(PreferKey.cDynamicColor, false)
      set(value) = appCtx.putPrefBoolean(PreferKey.cDynamicColor, value)
  ```
- `res/xml/pref_config_theme.xml`：在 `defaultHomePage` 之后、`mainTransparentStatusBar` 之前新增 `SwitchPreference`（key=`dynamicColor`，defaultValue=false，title=`@string/dynamic_color`，summary=`@string/dynamic_color_summary`，iconSpaceReserved=false）。
- 字符串（`values/strings.xml` 与 `values-zh/strings.xml`，在 `change_icon_summary` 附近新增）：
  - `dynamic_color`：EN "Material You dynamic color" / ZH "Material You 动态取色"
  - `dynamic_color_summary`：EN "Follow the system wallpaper palette (Android 12+). Overrides manual theme colors." / ZH "跟随系统壁纸自动取色（Android 12+），开启后覆盖手动主题色"
  - `dynamic_color_unavailable`：EN "Requires Android 12 or above" / ZH "需要 Android 12 及以上"

### 组件 3：ThemeConfig.applyTheme 动态分支

文件：`help/config/ThemeConfig.kt`，新增 `import android.os.Build`（若缺）。

在 `applyTheme()` 的 `when` 中，**EInk 分支之后、`AppConfig.isNightTheme` 分支之前**插入：

```kotlin
AppConfig.isDynamicColor && DynamicColor.isAvailable() -> {
    val isNight = AppConfig.isNightTheme
    val fbPrimary = getPrefInt(
        if (isNight) PreferKey.cNPrimary else PreferKey.cPrimary,
        if (isNight) DEFAULT_NIGHT_PRIMARY else DEFAULT_DAY_PRIMARY
    )
    val fbAccent = getPrefInt(
        if (isNight) PreferKey.cNAccent else PreferKey.cAccent,
        getCompatColor(if (isNight) R.color.md_deep_orange_800 else R.color.md_red_600)
    )
    val fbBackground = getPrefInt(
        if (isNight) PreferKey.cNBackground else PreferKey.cBackground,
        getCompatColor(if (isNight) R.color.md_grey_900 else R.color.md_grey_100)
    )
    val fbBottom = getPrefInt(
        if (isNight) PreferKey.cNBBackground else PreferKey.cBBackground,
        getCompatColor(if (isNight) R.color.md_grey_850 else R.color.md_grey_200)
    )
    val p = DynamicColor.palette(
        this, isNight,
        DynamicColor.Palette(fbPrimary, fbAccent, fbBackground, fbBottom)
    )
    val appBackground =
        if (hasUsableBgImage(this)) Color.TRANSPARENT else ColorUtils.withAlpha(p.background, 1f)
    ThemeStore.editTheme(this)
        .primaryColor(ColorUtils.withAlpha(p.primary, 1f))
        .accentColor(ColorUtils.withAlpha(p.accent, 1f))
        .backgroundColor(appBackground)
        .bottomBackground(ColorUtils.withAlpha(p.bottomBackground, 1f))
        .transparentNavBar(true)
        .apply()
}
```

现有 EInk/夜/日分支文本不变。动态分支以用户存储色为兜底，任何缺失色调回退到手动主题色。背景图存在时仍透明（与现有逻辑一致）。

### 组件 4：ThemeConfigFragment 联动

文件：`ui/config/ThemeConfigFragment.kt`（已 import `android.os.Build`、`PreferKey`、`SwitchPreference` 需新增 import）。

- `onCreatePreferences`：API<31 时禁用开关并改摘要：
  ```kotlin
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      findPreference<io.legado.app.lib.prefs.SwitchPreference>(PreferKey.cDynamicColor)?.let {
          it.isEnabled = false
          it.summary = getString(R.string.dynamic_color_unavailable)
      }
  }
  ```
- `onSharedPreferenceChanged`：新增分支
  ```kotlin
  PreferKey.cDynamicColor -> upTheme(AppConfig.isNightTheme)
  ```
  `upTheme(当前模式)` 复用现有逻辑：`ThemeConfig.applyTheme()` + `recreateActivities()`（300ms 后 post `RECREATE`）。

### 组件 5：搜索定位高亮

文件：`lib/prefs/fragment/PreferenceFragment.kt`。

在 `consumeActivityTargetKey()` 的 `listView.post { scrollPreferenceToTop(preference); ... }` 内、滚动之后调用 `highlightPreference(preference)`。新增方法：

```kotlin
private fun highlightPreference(preference: Preference) {
    val adapter = listView.adapter as? PreferenceGroupAdapter ?: return
    val index = (0 until adapter.itemCount).firstOrNull { adapter.getItem(it) == preference } ?: return
    val radius = UiCorner.actionRadius(requireContext())
    val tint = ColorUtils.setAlphaComponent(accentColor, 70)
    listView.post {
        val holder = listView.findViewHolderForAdapterPosition(index) ?: return@post
        val itemView = holder.itemView
        val original = itemView.background
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 1100L
            addUpdateListener { anim ->
                if (holder.bindingAdapterPosition != index) {
                    anim.cancel()
                    return@addUpdateListener
                }
                val alpha = (anim.animatedValue as Float)
                itemView.background = GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(ColorUtils.setAlphaComponent(tint, (alpha * 70).toInt()))
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (holder.bindingAdapterPosition == index) itemView.background = original
                }
                override fun onAnimationCancel(animation: Animator) {
                    if (holder.bindingAdapterPosition == index) itemView.background = original
                }
            })
        }
        animator.start()
    }
}
```

新增 import：`android.animation.Animator`、`android.animation.AnimatorListenerAdapter`、`android.animation.ValueAnimator`、`android.graphics.drawable.GradientDrawable`、`androidx.core.graphics.ColorUtils`、`io.legado.app.lib.theme.UiCorner`、`io.legado.app.lib.theme.accentColor`。

防回收：每帧与结束/取消时校验 `holder.bindingAdapterPosition == index`，不等则不还原（避免串色到复用后的其它条目）。仅当带 `targetKey`（搜索跳转）触发，普通导航无影响。

### 组件 6：卡片接入 UiCorner

文件：`ui/rss/source/manage/RssSourceAdapter.kt`、`ui/book/source/manage/BookSourceAdapter.kt`（两者均 import `androidx.core.graphics.ColorUtils`，新增 `import io.legado.app.lib.theme.UiCorner`）。

`upCardBackground()` 改为：

```kotlin
val density = context.resources.displayMetrics.density
val isSelected = selected.contains(item)
val strokeColor = if (item.enabled) context.primaryColor else context.secondaryTextColor
val alphaScale = UiCorner.layoutAlpha()
val fillColor = when {
    isSelected -> ColorUtils.setAlphaComponent(context.primaryColor, (60 * alphaScale).toInt())
    item.enabled -> ColorUtils.setAlphaComponent(context.primaryColor, (20 * alphaScale).toInt())
    else -> Color.TRANSPARENT
}
binding.root.background = GradientDrawable().apply {
    cornerRadius = UiCorner.scaledDp(12f)
    setColor(fillColor)
    setStroke(density.toInt().coerceAtLeast(1), strokeColor)
}
```

默认 `uiCornerScale=1` → `scaledDp(12f)=12dp`（与原一致）；默认 `uiLayoutAlpha=100` → `layoutAlpha()=1.0` → alpha 20/60 不变。启用/选中语义保留。

## 数据流

壁纸 → 系统 `system_*` 色调资源 → `DynamicColor.palette()`（带兜底）→ `ThemeConfig.applyTheme()` 动态分支 → `ThemeStore`（SharedPreferences）→ `MaterialValueHelper` 扩展（`primaryColor`/`accentColor`/`backgroundColor`/`bottomBackground`）→ 玻璃栏/卡片/全页面。开关变更 → `ThemeConfigFragment.upTheme()` → `applyTheme()` + `RECREATE`。

搜索：`MyFragment` 索引（含新 `dynamicColor` 项，自动纳入）→ 点击 → `ConfigActivity(configTag, targetKey)` → 基类 `consumeActivityTargetKey()` → 滚动 + 高亮。

## 错误处理

- 色调资源缺失（id==0 或 getColor 抛异常）→ 回退用户存储主题色，不崩溃。
- API<31 → `isAvailable()` false，动态分支不进入；开关禁用并提示。
- 墨水屏 → EInk 分支优先，动态取色不生效。
- 高亮期间条目回收 → 位置校验失败即取消/不还原，杜绝串色。
- 背景图存在 → 背景透明，与现有逻辑一致。

## 测试

- JVM 单测 `app/src/test/java/io/legado/app/lib/theme/DynamicColorTest.kt`（JUnit4，匹配 `RssSourceHelperTest` 风格）：
  - `buildPalette` 日间：resolver 返回指定色 → 四色按映射覆盖。
  - `buildPalette` 夜间：同上验证夜间映射。
  - resolver 全返回 null → 四色等于 fallback（兜底）。
  - resolver 部分返回 null → 缺失项回退、其余覆盖。
- 编译：`compileAppDebugKotlin`、`testAppDebugUnitTest`、`assembleAppDebug`（flavor app）。
- 真机验证清单（无法在此运行，交用户）：
  1. Android 12+ 开启动态取色，顶栏/卡片/玻璃随壁纸变色；关闭恢复手动主题色。
  2. 日/夜切换下动态取色均正确。
  3. Android 11 及以下开关禁用并显示"需要 Android 12+"。
  4. 搜索任一设置项 → 跳转滚动到顶 + 高亮闪烁约 1s。
  5. 调整圆角尺度/透明度 → 书源/订阅卡片圆角与透明度跟随。
  6. 卡片点按切换启用、长按拖拽、滑条多选、⋮菜单、校验/发现/分组/批量操作无回归。

## 交付

编译生成 APK（`app/build/outputs/apk/app/debug/`），提交推送 `origin/own`，提供 APK 路径与真机验证清单。