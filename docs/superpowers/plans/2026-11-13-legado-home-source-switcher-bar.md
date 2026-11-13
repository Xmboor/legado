# 主页顶栏横向滑动源切换条 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在主页发现/订阅现代模式顶栏，用横向滑动源 chip 条替换源名下拉，点按即切换浏览源。

**Architecture:** 复用现成 `RoundedTagBarView`（水平 chip 条）；新增纯函数 `SourceSwitcherBar` 做「源列表→名称+选中索引」映射；两个 Fragment 把下拉接线改为 chip 条接线，切换/加载逻辑零改动。

**Tech Stack:** Kotlin 2.3.10, AGP 8.13.2, Gradle 8.14.4, compileSdk 36, minSdk 21, JDK21, flavor `app`, JUnit4。

## Global Constraints

- 仓库本地路径 `C:\Users\xmboo\source\repos\legado`，分支 `own`，远端 `https://github.com/Xmboor/legado.git`。
- 构建环境：`JAVA_HOME=D:/DevTools/JDK21/jdk-21.0.13+9`，`ANDROID_HOME=D:/DevTools/android-sdk`。
- 构建任务：`compileAppDebugKotlin` / `testAppDebugUnitTest` / `assembleAppDebug`。
- 不动：经典模式、管理页（Phase D）、MainActivity、玻璃逻辑、底栏、`SourceSelectDialog.kt` 本体、现有切换/加载/搜索/标签逻辑。
- 不提交镜像仓库改动（settings.gradle 等）、env / local.properties / 凭据。
- 新提交不 amend；不 force-push；URL 工具只用真实任务对齐地址。
- 每个 Bash 命令前 `cd /c/Users/xmboo/source/repos/legado`（shell cwd 会重置）。

---

### Task 1: SourceSwitcherBar 纯映射 + 单测（TDD）

**Files:**
- Create: `app/src/main/java/io/legado/app/ui/widget/SourceSwitcherBar.kt`
- Test: `app/src/test/java/io/legado/app/ui/widget/SourceSwitcherBarTest.kt`

**Interfaces:**
- Produces: `SourceSwitcherBar.build(sources: List<T>, selectedKey: String?, key: (T)->String, name: (T)->String): Pair<List<String>, Int>`（index=-1 表示无选中）。

- [ ] **Step 1: 写失败测试**

```kotlin
package io.legado.app.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSwitcherBarTest {
    private data class Src(val url: String, val name: String)
    private val list = listOf(Src("a", "甲"), Src("b", "乙"), Src("c", "丙"))
    private val key = { s: Src -> s.url }
    private val name = { s: Src -> s.name }

    @Test fun build_empty_returnsEmptyNamesAndMinusOne() {
        val (names, index) = SourceSwitcherBar.build(emptyList<Src>(), "a", key, name)
        assertEquals(emptyList<String>(), names)
        assertEquals(-1, index)
    }

    @Test fun build_nullSelectedKey_returnsMinusOne() {
        val (names, index) = SourceSwitcherBar.build(list, null, key, name)
        assertEquals(listOf("甲", "乙", "丙"), names)
        assertEquals(-1, index)
    }

    @Test fun build_selectedKeyMatches_returnsIndex() {
        assertEquals(1, SourceSwitcherBar.build(list, "b", key, name).second)
        assertEquals(2, SourceSwitcherBar.build(list, "c", key, name).second)
        assertEquals(0, SourceSwitcherBar.build(list, "a", key, name).second)
    }

    @Test fun build_selectedKeyNotFound_returnsMinusOne() {
        assertEquals(-1, SourceSwitcherBar.build(list, "zzz", key, name).second)
    }

    @Test fun build_namesMappedInOrder() {
        assertEquals(listOf("甲", "乙", "丙"), SourceSwitcherBar.build(list, "a", key, name).first)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /c/Users/xmboo/source/repos/legado && export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9" && export ANDROID_HOME="D:/DevTools/android-sdk" && ./gradlew testAppDebugUnitTest --tests "io.legado.app.ui.widget.SourceSwitcherBarTest"`
Expected: 编译失败 / `SourceSwitcherBar` 未定义。

- [ ] **Step 3: 写最小实现**

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

- [ ] **Step 4: 运行测试确认通过**

Run: 同 Step 2 命令。
Expected: 5 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
cd /c/Users/xmboo/source/repos/legado
git add app/src/main/java/io/legado/app/ui/widget/SourceSwitcherBar.kt app/src/test/java/io/legado/app/ui/widget/SourceSwitcherBarTest.kt
git commit -m "feat: add SourceSwitcherBar mapper for home source switcher bar"
```

---

### Task 2: 发现 tab 顶栏源切换条（布局 + ExploreFragment）

**Files:**
- Modify: `app/src/main/res/layout/fragment_explore.xml`（`ll_discover_source_row` 内）
- Modify: `app/src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt`

**Interfaces:**
- Consumes: `SourceSwitcherBar.build`（Task 1）、`RoundedTagBarView.Item/submitItems/setSelectedIndex/setOnTagClickListener`。
- Produces: 布局 id `rv_discover_sources`；`renderDiscoverSourceBar()`、`syncDiscoverSourceBarSelection()`。

- [ ] **Step 1: 改布局**

把 `ll_discover_source_row` 内的 `ll_discover_source_select`（整块，含 `tv_discover_source_select` + `iv_discover_source_arrow`）及其后加权 `Space`（`layout_weight="1"` 那个）替换为：

```xml
<io.legado.app.ui.widget.RoundedTagBarView
    android:id="@+id/rv_discover_sources"
    android:layout_width="0dp"
    android:layout_height="@dimen/bookshelf_tag_bar_height"
    android:layout_marginEnd="8dp"
    android:layout_weight="1" />
```

保留 `btn_discover_source_search` / `btn_discover_tag_filter` / `btn_discover_more` 及按钮间 10dp `Space`。

- [ ] **Step 2: 改 ExploreFragment 接线**

`bindDiscoverSourceSelector()` 改为（移除 typeface/宽度监听/下拉点击，新增 chip 点击）：

```kotlin
private fun bindDiscoverSourceSelector() {
    binding.rvDiscoverSources.setOnTagClickListener { index ->
        discoverSources.getOrNull(index)?.let { selectDiscoverSource(it) }
    }
    binding.btnDiscoverSourceSearch.setOnClickListener { openDiscoverSearch() }
    binding.btnDiscoverTagFilter.setOnClickListener { showDiscoverSettingsDialog() }
    binding.btnDiscoverMore.setOnClickListener { showDiscoverMoreMenu() }
    updateDiscoverTagFilterButtonState()
    updateDiscoverSearchButtonState()
}
```

删除整个 `updateDiscoverSourceNameWidth()` 函数。

`updateDiscoverLoginButtonState()` 改为：

```kotlin
private fun updateDiscoverLoginButtonState() {
    binding.btnDiscoverMore.alpha = 1f
}
```

`updateDiscoverSearchButtonState()` 删除末行 `binding.llDiscoverSourceRow.post(::updateDiscoverSourceNameWidth)`。

新增两个函数：

```kotlin
private fun renderDiscoverSourceBar() {
    val (names, index) = SourceSwitcherBar.build(
        discoverSources,
        selectedDiscoverSourcePart?.bookSourceUrl,
        { it.bookSourceUrl },
        { it.bookSourceName }
    )
    binding.rvDiscoverSources.submitItems(names.map { RoundedTagBarView.Item(it) }, index)
}

private fun syncDiscoverSourceBarSelection() {
    val index = discoverSources.indexOfFirst {
        it.bookSourceUrl == selectedDiscoverSourcePart?.bookSourceUrl
    }
    binding.rvDiscoverSources.setSelectedIndex(index)
}
```

删除旧 `updateDiscoverSourceTitle()`，把其 3 处调用（`selectDiscoverSource` 异步块内、`observeDiscoverSources` else 分支、`loadDiscoverKindsAndDefault` 前的异步块）改为 `syncDiscoverSourceBarSelection()`。

`selectDiscoverSource()` 在 `selectedDiscoverSourcePart = source` 下一行加 `syncDiscoverSourceBarSelection()`。

`observeDiscoverSources()`：`discoverSources.addAll(list)` 下一行加 `renderDiscoverSourceBar()`；空列表分支 `binding.tvDiscoverSourceSelect.text = getString(R.string.explore_empty)` 改为 `binding.rvDiscoverSources.submitItems(emptyList(), -1)`。

删除 `showDiscoverSourceMenu()` 整个函数；删除 `import io.legado.app.ui.widget.SourceSelectDialog`；新增 `import io.legado.app.ui.widget.RoundedTagBarView` 与 `import io.legado.app.ui.widget.SourceSwitcherBar`。

- [ ] **Step 3: 编译验证**

Run: `cd /c/Users/xmboo/source/repos/legado && export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9" && export ANDROID_HOME="D:/DevTools/android-sdk" && ./gradlew compileAppDebugKotlin`
Expected: BUILD SUCCESSFUL（无 `tvDiscoverSourceSelect`/`llDiscoverSourceSelect`/`updateDiscoverSourceNameWidth`/`showDiscoverSourceMenu` 残留引用）。

- [ ] **Step 4: 提交**

```bash
cd /c/Users/xmboo/source/repos/legado
git add app/src/main/res/layout/fragment_explore.xml app/src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt
git commit -m "feat: replace discovery source dropdown with horizontal sliding source bar"
```

---

### Task 3: 订阅 tab 顶栏源切换条（布局 + RssFragment）

**Files:**
- Modify: `app/src/main/res/layout/fragment_rss.xml`（`ll_rss_source_row` 内）
- Modify: `app/src/main/java/io/legado/app/ui/main/rss/RssFragment.kt`

**Interfaces:**
- Consumes: `SourceSwitcherBar.build`、`RoundedTagBarView`。
- Produces: 布局 id `rv_rss_sources`；`renderRssSourceBar()`。

- [ ] **Step 1: 改布局**

把 `ll_rss_source_row` 内 `ll_rss_source_select`（含 `tv_rss_source_select` + `iv_rss_source_arrow`）及其后加权 `Space` 替换为：

```xml
<io.legado.app.ui.widget.RoundedTagBarView
    android:id="@+id/rv_rss_sources"
    android:layout_width="0dp"
    android:layout_height="@dimen/bookshelf_tag_bar_height"
    android:layout_marginEnd="8dp"
    android:layout_weight="1" />
```

保留 `btn_rss_source_search` / `btn_rss_more` 及 10dp `Space`。

- [ ] **Step 2: 改 RssFragment 接线**

`initModernRssView()` 改为（移除 typeface/宽度监听/下拉点击，新增 chip 点击；保留状态栏 padding 与 swipe）：

```kotlin
private fun initModernRssView() {
    binding.llRssSourceRow.applyStatusBarPadding(withInitialPadding = true)
    binding.swipeRefreshLayout.setColorSchemeColors(accentColor)
    binding.swipeRefreshLayout.setProgressViewOffset(true, (-28).dpToPx(), 56.dpToPx())
    binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
        currentRssScrollTarget()?.canScrollVertically(-1) == true
    }
    binding.swipeRefreshLayout.setOnRefreshListener { refreshCurrentRssContent() }
    binding.rvRssSources.setOnTagClickListener { index ->
        rssSources.getOrNull(index)?.let { selectSource(it, reload = true) }
    }
    binding.btnRssSourceSearch.setOnClickListener { openRssSearch() }
    binding.btnRssMore.setOnClickListener { showRssMoreMenu() }
    binding.btnOpenRss.applyUiBodyTypeface(requireContext())
}
```

删除整个 `updateRssSourceNameWidth()` 函数。

新增：

```kotlin
private fun renderRssSourceBar() {
    val (names, index) = SourceSwitcherBar.build(
        rssSources,
        selectedRssSource?.sourceUrl,
        { it.sourceUrl },
        { it.sourceName }
    )
    binding.rvRssSources.submitItems(names.map { RoundedTagBarView.Item(it) }, index)
}
```

`selectSource()`：`binding.tvRssSourceSelect.text = source.sourceName` 改为
`binding.rvRssSources.setSelectedIndex(rssSources.indexOfFirst { it.sourceUrl == source.sourceUrl })`；
删除下一行 `binding.llRssSourceRow.post(::updateRssSourceNameWidth)`。

`observeRssSources()`：`rssSources.addAll(sources)` 下一行加 `renderRssSourceBar()`。

`renderEmptyState()`：`binding.tvRssSourceSelect.text = getString(R.string.rss)` 改为 `binding.rvRssSources.submitItems(emptyList(), -1)`。

删除 `showSourceSelector()` 整个函数；删除 `import io.legado.app.ui.widget.SourceSelectDialog`；新增 `import io.legado.app.ui.widget.RoundedTagBarView` 与 `import io.legado.app.ui.widget.SourceSwitcherBar`。

- [ ] **Step 3: 编译验证**

Run: `cd /c/Users/xmboo/source/repos/legado && export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9" && export ANDROID_HOME="D:/DevTools/android-sdk" && ./gradlew compileAppDebugKotlin`
Expected: BUILD SUCCESSFUL（无 `tvRssSourceSelect`/`llRssSourceSelect`/`updateRssSourceNameWidth`/`showSourceSelector` 残留引用）。

- [ ] **Step 4: 提交**

```bash
cd /c/Users/xmboo/source/repos/legado
git add app/src/main/res/layout/fragment_rss.xml app/src/main/java/io/legado/app/ui/main/rss/RssFragment.kt
git commit -m "feat: replace rss source dropdown with horizontal sliding source bar"
```

---

### Task 4: 全量构建 + APK + 推送 + 交付

**Files:** 无新增改动（验证 + 交付）。

- [ ] **Step 1: 单测全跑**

Run: `cd /c/Users/xmboo/source/repos/legado && export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9" && export ANDROID_HOME="D:/DevTools/android-sdk" && ./gradlew testAppDebugUnitTest`
Expected: BUILD SUCCESSFUL，含 `SourceSwitcherBarTest` 5 PASS（及既有 `DynamicColorTest` 等不回归）。

- [ ] **Step 2: 组装 APK**

Run: `cd /c/Users/xmboo/source/repos/legado && export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9" && export ANDROID_HOME="D:/DevTools/android-sdk" && ./gradlew assembleAppDebug`
Expected: BUILD SUCCESSFUL；产物 `app/build/outputs/apk/app/debug/legado_app_*.apk`。

- [ ] **Step 3: 提交规格/计划文档**

```bash
cd /c/Users/xmboo/source/repos/legado
git add docs/superpowers/specs/2026-11-13-legado-home-source-switcher-bar-design.md docs/superpowers/plans/2026-11-13-legado-home-source-switcher-bar.md
git commit -m "docs: home source switcher bar design and implementation plan"
```

- [ ] **Step 4: 推送到真实远端**

Run: `cd /c/Users/xmboo/source/repos/legado && git push origin own`
Expected: 推送成功到 `https://github.com/Xmboor/legado.git`。

- [ ] **Step 5: 核验远端 + 交付**

Run: `cd /c/Users/xmboo/source/repos/legado && git log origin/own -5 --oneline && ls -la app/build/outputs/apk/app/debug/`
Expected: 远端含本次 feat/docs 提交；APK 存在。交付 APK 路径 + §10 上机验证清单。

---

## Self-Review

- **Spec coverage:** §2 目标→Task2/3；§4 复用+映射→Task1；§5 改动文件→Task1-3 全覆盖；§8 测试→Task1；§9 构建交付→Task4。无遗漏。
- **Placeholder scan:** 无 TBD/TODO；每步含完整代码/命令/预期。
- **Type consistency:** `SourceSwitcherBar.build` 签名在 Task1 定义、Task2/3 一致调用；`RoundedTagBarView.Item(CharSequence)`、`submitItems(List<Item>, Int)`、`setSelectedIndex(Int)`、`setOnTagClickListener((Int)->Unit)` 与现有组件一致；新 id `rv_discover_sources`/`rv_rss_sources` → binding `rvDiscoverSources`/`rvRssSources` 一致。