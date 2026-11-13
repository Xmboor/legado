# Legado 源管理体验增强 实现计划

**Goal:** 主页顶栏长按删除源 + 其他设置 4 项手动数字输入 + 管理页默认单列按域名分组。

**Architecture:** 复用既有删除确认流与域名分组机制；新增可复用数字输入对话框与纯函数解析器；订阅源管理页移植书源页域名分组。

**Tech Stack:** Kotlin 2.3.10 / AGP 8.13.2 / flavor app / JDK21。

## Global Constraints

- 仅改用户点名范围；不碰玻璃化/MainActivity/底栏/经典模式/base adapter/触摸助手。
- 其他设置仅 4 项：bitmapCacheSize、imageRetainNum、preDownloadNum、threadCount。
- 管理页默认单列、域名分组默认开；双列与分组均可菜单切换，列数持久化。
- 长按删除仅现代模式顶栏，复用确认框。
- 镜像仓库改动（settings.gradle 等）不提交；env 文件不提交。

---

### Task 1: 数字输入纯函数 + 单测（TDD）

**Files:**
- Create: `app/src/main/java/io/legado/app/ui/widget/number/NumberInput.kt`
- Test: `app/src/test/java/io/legado/app/ui/widget/number/NumberInputTest.kt`

**Interfaces:**
- Produces: `object NumberInput { fun parse(text: String?, min: Int, max: Int, fallback: Int): Int }`

- [ ] 写失败测试 `NumberInputTest`：空白→fallback、非数字→fallback、低于 min→min、高于 max→max、合法→原值、边界 min/max→原值。
- [ ] 运行 `testAppDebugUnitTest --tests "*NumberInputTest*"` 确认红（Unresolved reference）。
- [ ] 实现：

```kotlin
package io.legado.app.ui.widget.number

object NumberInput {
    fun parse(text: String?, min: Int, max: Int, fallback: Int): Int {
        val value = text?.trim()?.toIntOrNull() ?: return fallback
        return value.coerceIn(min, max)
    }
}
```

- [ ] 运行测试确认绿。

### Task 2: NumberInputDialog + 布局

**Files:**
- Create: `app/src/main/java/io/legado/app/ui/widget/number/NumberInputDialog.kt`
- Create: `app/src/main/res/layout/dialog_number_input.xml`

**Interfaces:**
- Consumes: `NumberInput.parse`
- Produces: `NumberInputDialog(context).setTitle(String).setMinValue(Int).setMaxValue(Int).setValue(Int).show((Int)->Unit)`

- [ ] 布局：NestedScrollView + EditText(id=edit_number)，inputType=number，digits 0123456789，minHeight 44dp，padding 同 dialog_edit_text。
- [ ] 实现对话框：AlertDialog.Builder + setUiTitle + applyTint；show 时设 min/max 提示 hint、setText(value)、selectAll、弹软键盘；确定用 NumberInput.parse(text, min, max, value) 回调；取消不回调。
- [ ] 编译 `compileAppDebugKotlin`。

### Task 3: OtherConfigFragment 切换 4 项

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/config/OtherConfigFragment.kt`

- [ ] import NumberInputDialog；将 bitmapCacheSize/imageRetainNum/preDownloadNum/threadCount 四处 `NumberPickerDialog(requireContext())` 改为 `NumberInputDialog(requireContext())`，其余链式调用与回调副作用不变。
- [ ] webPort/sourceEditMaxLine 保持 NumberPickerDialog。
- [ ] 编译。

### Task 4: 主页顶栏长按删除

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt`
- Modify: `app/src/main/java/io/legado/app/ui/main/rss/RssFragment.kt`

- [ ] ExploreFragment.bindDiscoverSourceSelector() 加：

```kotlin
binding.rvDiscoverSources.setOnTagLongClickListener { index ->
    discoverSources.getOrNull(index)?.let { deleteSource(it) }
    true
}
```

- [ ] RssFragment.initModernRssView() 加：

```kotlin
binding.rvRssSources.setOnTagLongClickListener { index ->
    rssSources.getOrNull(index)?.let { del(it) }
    true
}
```

- [ ] 编译。

### Task 5: 列数持久化 + 字符串

**Files:**
- Modify: `app/src/main/java/io/legado/app/constant/PreferKey.kt`
- Modify: `app/src/main/java/io/legado/app/help/config/AppConfig.kt`
- Modify: `app/src/main/res/values-zh/strings.xml`（及 values/values-zh-rTW/rHK 对应项）

- [ ] PreferKey 加 `const val sourceManageDoubleColumn = "sourceManageDoubleColumn"`。
- [ ] AppConfig 加：

```kotlin
var sourceManageDoubleColumn: Boolean
    get() = appCtx.getPrefBoolean(PreferKey.sourceManageDoubleColumn, false)
    set(value) = appCtx.putPrefBoolean(PreferKey.sourceManageDoubleColumn, value)
```

- [ ] strings 加 `source_double_column`（双列平铺）、`number_input_range`（范围 %1$d - %2$d）。

### Task 6: 书源管理页默认单列 + 分组默认开 + 双列开关

**Files:**
- Modify: `app/src/main/res/layout/activity_book_source.xml`（spanCount 2→1）
- Modify: `app/src/main/res/menu/book_source.xml`（加 menu_source_double_column）
- Modify: `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt`

- [ ] `groupSourcesByDomain = true`；initRecyclerView 后 `adapter.showSourceHost = true`；按 AppConfig.sourceManageDoubleColumn 设 spanCount。
- [ ] onPrepareOptionsMenu：勾选 menu_group_sources_by_domain = groupSourcesByDomain；menu_source_double_column = AppConfig.sourceManageDoubleColumn。
- [ ] onCompatOptionsItemSelected 加 menu_source_double_column：翻转持久化 + 设 spanCount。
- [ ] 编译。

### Task 7: 订阅源管理页移植域名分组 + 单列 + 双列开关

**Files:**
- Modify: `app/src/main/res/layout/activity_rss_source.xml`（spanCount 2→1）
- Modify: `app/src/main/res/layout/item_rss_source.xml`（加 tv_host_text）
- Modify: `app/src/main/res/menu/rss_source.xml`（加 menu_group_sources_by_domain + menu_source_double_column）
- Modify: `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt`
- Modify: `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt`

- [ ] Adapter：加 showSourceHost、upSourceHost、getHeaderText、isItemHeader、CallBack.getSourceHost；convert 调 upSourceHost；onCurrentListChanged 触发 upSourceHost 刷新（镜像书源）。
- [ ] item_rss_source.xml 顶部加 tv_host_text（镜像 item_book_source.xml）。
- [ ] Activity：加 groupSourcesByDomain=true、hostMap、getSourceHost；upSourceFlow 加 .map 域名排序；initRecyclerView 设 showSourceHost=true、spanCount、isCanDrag=!groupSourcesByDomain；onPrepareOptionsMenu 勾选两开关；onCompatOptionsItemSelected 加两开关处理。
- [ ] 编译。

### Task 8: 编译 + 单测 + APK + 提交推送

- [ ] `compileAppDebugKotlin`、`testAppDebugUnitTest`、`assembleAppDebug`。
- [ ] 提交（分逻辑提交）、推送 origin own、ls-remote 核验。
- [ ] 给交付与装机核验清单。