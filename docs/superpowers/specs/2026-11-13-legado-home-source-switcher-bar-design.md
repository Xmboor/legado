# 主页顶栏横向滑动源切换条 设计文档

- 日期：2026-11-13
- 状态：已批准（用户选择「方式①：用源 chip 条替换顶栏源名下拉」）
- 仓库：https://github.com/Xmboor/legado （本地 `C:\Users\xmboo\source\repos\legado`，分支 `own`）

## 1. 背景与问题

主页底部导航的「发现」（书源）与「订阅」（订阅源）两个 tab 默认走**现代模式**
（`AppConfig.modernDiscoveryPage` / `modernRssPage` 默认 `true`）。现代模式下，顶栏
（`ll_discover_source_row` / `ll_rss_source_row`）用一个「源名 + 下拉箭头」
（`ll_discover_source_select` / `ll_rss_source_select`）展示当前源，点按后弹出共享的
`SourceSelectDialog`（竖向文本列表 + 搜索）来切换源。

用户要求：**所有书源 / 订阅源在页面顶栏横向平铺，可左右滑动选择不同源**。即把「点源名→弹窗→选」
的下拉式切换，改为顶栏常驻的横向滑动源切换条，点按 chip 即切换浏览内容。

此前 Phase D 平铺的是「我的 → 书源管理 / 订阅源管理」页（`BookSourceActivity` /
`RssSourceActivity`），与本需求是不同界面；用户已确认**管理页平铺保留**，本次不动。

## 2. 目标

在发现 / 订阅现代模式顶栏，用一条横向平铺、可左右滑动的源切换条替换源名下拉：

- 列出该 tab 所有可浏览源（发现 = 启用发现的书源；订阅 = 启用的订阅源）。
- 左右滑动浏览全部源；点按某个源 chip → 立即切换到该源浏览内容。
- 当前选中源 chip 高亮（accent 色）并自动居中。
- 保留顶栏右侧原有动作按钮（发现：搜索 / 筛选 / 更多；订阅：搜索 / 更多）。

## 3. 已确认决策

| 项 | 决策 |
| --- | --- |
| 目标界面 | 主页「发现」+「订阅」两个 tab（现代模式）顶栏 |
| 点按行为 | 进入该源浏览内容（复用现有切换逻辑） |
| 落地方式 | 方式①：源 chip 条**替换**顶栏源名下拉 |
| 管理页（Phase D） | 保留，不动 |
| 源范围 | 各 tab 现有列表（`flowExplore()` / `flowEnabled()`），与现下拉一致 |

## 4. 架构与复用

复用现成组件 **`io.legado.app.ui.widget.RoundedTagBarView`**：水平 `LinearLayoutManager`
的 chip 条，提供 `submitItems(items, selectedIndex)`、`setSelectedIndex(index, smooth)`、
`setOnTagClickListener`、选中 accent 高亮、自动居中 + 平滑滚动、`UiCorner` 圆角背景。
已被书架分组（`rv_discover_selects` / `rv_discover_tags` 等）使用，无需新建组件。

chip 样式沿用 `item_bookshelf_group_tag.xml`（高 28dp、maxWidth 96dp、单行省略、13sp）。
源条高度用 `@dimen/bookshelf_tag_bar_height`（38dp），与现有标签条一致。

新增一个**纯 Kotlin 映射工具** `SourceSwitcherBar`（JVM 可单测，不依赖 Android）：
把「源列表 + 选中 key」映射为「chip 名称列表 + 选中索引」。

## 5. 改动文件

### 5.1 新增

- `app/src/main/java/io/legado/app/ui/widget/SourceSwitcherBar.kt`
  ```kotlin
  package io.legado.app.ui.widget

  /** 把源列表映射为横向切换条所需的名称列表与选中索引（纯函数，便于单测）。 */
  object SourceSwitcherBar {
      fun <T> build(
          sources: List<T>,
          selectedKey: String?,
          key: (T) -> String,
          name: (T) -> String
      ): Pair<List<String>, Int> {
          val names = sources.map(name)
          val index = if (selectedKey == null) -1
                      else sources.indexOfFirst { key(it) == selectedKey }
          return names to index
      }
  }
  ```
  选中索引 `-1` 表示无选中（`RoundedTagBarView.normalizeIndex` 会归一为 `NO_POSITION`）。

- `app/src/test/java/io/legado/app/ui/widget/SourceSwitcherBarTest.kt`（JUnit4，见 §8）

### 5.2 布局

- `app/src/main/res/layout/fragment_explore.xml`：`ll_discover_source_row` 内，
  删除 `ll_discover_source_select`（含 `tv_discover_source_select` + `iv_discover_source_arrow`）
  与其后的加权 `Space`；插入
  ```xml
  <io.legado.app.ui.widget.RoundedTagBarView
      android:id="@+id/rv_discover_sources"
      android:layout_width="0dp"
      android:layout_height="@dimen/bookshelf_tag_bar_height"
      android:layout_marginEnd="8dp"
      android:layout_weight="1" />
  ```
  （`layout_marginEnd="8dp"` 让源条与右侧动作按钮保持间距，取代原加权 `Space` 的留白作用。）
  保留 `btn_discover_source_search` / `btn_discover_tag_filter` / `btn_discover_more` 及其间隔 Space。

- `app/src/main/res/layout/fragment_rss.xml`：`ll_rss_source_row` 内，
  删除 `ll_rss_source_select`（含 `tv_rss_source_select` + `iv_rss_source_arrow`）与其后加权 `Space`；
  插入 `rv_rss_sources`（同上，id 改为 `rv_rss_sources`）。保留 `btn_rss_source_search` / `btn_rss_more`。

### 5.3 ExploreFragment.kt

- `bindDiscoverSourceSelector()`：移除 `tvDiscoverSourceSelect.applyUiTitleTypeface`、
  `updateSourceNameWidth` 布局监听与 post、`llDiscoverSourceSelect.setOnClickListener{ showDiscoverSourceMenu() }`；
  新增 `binding.rvDiscoverSources.setOnTagClickListener { index -> discoverSources.getOrNull(index)?.let(::selectDiscoverSource) }`。
  保留 search / filter / more 按钮监听。
- 删除 `updateDiscoverSourceNameWidth()`；移除其在 `updateDiscoverLoginButtonState()`、
  `updateDiscoverSearchButtonState()`、`updateDiscoverSourceTitle()` 中的 post 调用。
- 新增 `renderDiscoverSourceBar()`：用 `SourceSwitcherBar.build(discoverSources, selectedDiscoverSourcePart?.bookSourceUrl, {it.bookSourceUrl}, {it.bookSourceName})`
  → `binding.rvDiscoverSources.submitItems(names.map{ RoundedTagBarView.Item(it) }, index)`。
- `updateDiscoverSourceTitle()` 重命名为 `syncDiscoverSourceBarSelection()`，body 改为
  `binding.rvDiscoverSources.setSelectedIndex(discoverSources.indexOfFirst{ it.bookSourceUrl == selectedDiscoverSourcePart?.bookSourceUrl })`；
  更新 3 处调用点（约 609 / 691 / 782 行）。
- `selectDiscoverSource()`：在 `selectedDiscoverSourcePart = source` 后立即 `syncDiscoverSourceBarSelection()`，
  使点按 chip 即时高亮（异步加载完成后的 sync 调用保留，幂等）。
- `observeDiscoverSources()`：`discoverSources.addAll(list)` 后调用 `renderDiscoverSourceBar()`；
  空列表分支把 `tvDiscoverSourceSelect.text = explore_empty` 改为 `binding.rvDiscoverSources.submitItems(emptyList(), -1)`。
- 删除 `showDiscoverSourceMenu()` 与 `import io.legado.app.ui.widget.SourceSelectDialog`（不再被引用）。

### 5.4 RssFragment.kt

- `initModernRssView()`：移除 `tvRssSourceSelect.applyUiTitleTypeface`、`updateSourceNameWidth`
  布局监听与 post、`llRssSourceSelect.setOnClickListener{ showSourceSelector() }`；
  新增 `binding.rvRssSources.setOnTagClickListener { index -> rssSources.getOrNull(index)?.let{ selectSource(it, reload = true) } }`。
  保留 `llRssSourceRow.applyStatusBarPadding`、swipe 刷新、search / more 监听。
- 删除 `updateRssSourceNameWidth()`；移除 `selectSource()` 中的 post 调用。
- 新增 `renderRssSourceBar()`：`SourceSwitcherBar.build(rssSources, selectedRssSource?.sourceUrl, {it.sourceUrl}, {it.sourceName})`
  → `binding.rvRssSources.submitItems(...)`。
- `selectSource()`：`binding.tvRssSourceSelect.text = source.sourceName` 改为
  `binding.rvRssSources.setSelectedIndex(rssSources.indexOfFirst{ it.sourceUrl == source.sourceUrl })`。
- `observeRssSources()`：`rssSources.addAll(sources)` 后调用 `renderRssSourceBar()`。
- `renderEmptyState()`：`tvRssSourceSelect.text = rss` 改为 `binding.rvRssSources.submitItems(emptyList(), -1)`。
- 删除 `showSourceSelector()` 与 `import io.legado.app.ui.widget.SourceSelectDialog`。

### 5.5 不动

`SourceSelectDialog.kt` 组件本体保留（不再被这两个 Fragment 引用，文件不删，零风险）；
经典模式（`ExploreAdapter` / `RssAdapter`）、管理页（Phase D）、`MainActivity`、玻璃逻辑、
底栏、所有现有切换 / 加载 / 搜索 / 标签逻辑均不动。

## 6. 数据流

1. DB flow（`flowExplore()` / `flowEnabled()`）→ `observeDiscoverSources` / `observeRssSources`
   填充 `discoverSources` / `rssSources`。
2. `renderDiscoverSourceBar()` / `renderRssSourceBar()` → `SourceSwitcherBar.build` →
   `RoundedTagBarView.submitItems(names, selectedIndex)`（名称变化才重建，否则仅同步选中）。
3. 用户点按 chip → `setOnTagClickListener(index)` → `selectDiscoverSource` / `selectSource`
   （现有切换 + 内容加载逻辑）→ 立即 `setSelectedIndex` 高亮居中。
4. 左右滑动由 `RoundedTagBarView` 内部水平 RecyclerView 原生支持。

## 7. 边界与错误处理

- 源列表为空：`submitItems(emptyList(), -1)`，条内无 chip；现有空态文案 / 逻辑保留。
- 选中 key 不在列表（如源被禁用 / 删除）：`build` 返回 `-1`，无高亮；现有「回退到第一个源」逻辑不变。
- 源名过长：chip maxWidth 96dp 单行省略（与现有标签一致），不撑破顶栏。
- 搜索过滤（订阅 `observeRssSources(searchKey)`）：chip 条随过滤后的列表更新，行为与现列表一致。
- 重复 `submitItems`：`RoundedTagBarView` 内部 `sameItems` 判定，名称未变只同步选中，开销低。

## 8. 测试

`SourceSwitcherBarTest`（JVM JUnit4，不依赖 Android）：

- `build_empty_returnsEmptyNamesAndMinusOne`
- `build_nullSelectedKey_returnsMinusOne`
- `build_selectedKeyMatches_returnsIndex`（含选中在中间 / 末尾）
- `build_selectedKeyNotFound_returnsMinusOne`
- `build_namesMappedInOrder`

UI 接线（chip 条替换、点按切换、滑动、高亮居中）无法在此环境跑真机，靠编译 + 上机验证清单（§10）。

## 9. 构建与交付

- 环境：`JAVA_HOME=D:/DevTools/JDK21/jdk-21.0.13+9`，`ANDROID_HOME=D:/DevTools/android-sdk`。
- 命令：`compileAppDebugKotlin` → `testAppDebugUnitTest` → `assembleAppDebug`（flavor `app`）。
- 提交推送到真实远端 `origin/own`（https://github.com/Xmboor/legado.git）。
- 交付 APK：`app/build/outputs/apk/app/debug/legado_app_*.apk`。
- 不提交镜像仓库改动（settings.gradle 等）、不提交 env / local.properties / 凭据。

## 10. 上机验证清单（交付时随附）

1. 发现 tab 顶栏出现横向源 chip 条，列出所有启用发现的书源；左右滑动顺畅。
2. 点按某书源 chip → 立即高亮居中 + 下方加载该源发现内容。
3. 订阅 tab 顶栏出现横向源 chip 条；点按切换订阅源文章 / 网页，行为与原下拉一致。
4. 顶栏右侧 搜索 / 筛选 / 更多 按钮仍可用。
5. 源被禁用 / 删除后回到列表，选中态正确回退；空源时显示空态。
6. 经典模式、管理页平铺、玻璃效果、底栏均无回归。