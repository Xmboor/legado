# 书源/订阅管理页平铺卡片化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把书源管理页与订阅源管理页改成 2 列卡片网格，点按卡片主体即可切换启用/停用，实现快速切换书源。

**Architecture:** 就地重做两套 item 布局为圆角卡片（保留 ViewBinding 类名与现有视图 ID，名称迁到独立 tv_name，复选框缩为纯勾选标记）；RecyclerView 改 GridLayoutManager(spanCount=2)；adapter 新增 upCardBackground（启用/选中高亮）与 toggleSelection；activity 用 RecyclerAdapter.setOnItemClickListener 实现"非多选态点按=启用、多选态点按=选中"。长按拖拽、左滑条多选、编辑/更多/校验/发现/分组/批量操作全部保留。

**Tech Stack:** Kotlin 2.3.10 / AGP 8.13.2 / Gradle 8.14.4 / compileSdk 36 / minSdk 21 / ViewBinding / RecyclerView / ThemeStore。

## Global Constraints

- 分支 `own`；镜像仓库 `settings.gradle` 改动不提交；`local.properties`、`~/.gradle` 等环境文件不提交。
- 不改动：`RecyclerAdapter` 基类、`DragSelectTouchHelper`、`ItemTouchCallback`、`GlassBarHelper`、玻璃布局结构、`MainActivity`、换源对话框 `ChangeBookSourceDialog`。
- spanCount 固定 2；卡片容器用 `LinearLayout` + 圆角 `GradientDrawable`。
- 复用现有 `R.string.select`（"选择"），不新增字符串资源。
- 颜色取 ThemeStore：`Context.primaryColor` / `Context.secondaryTextColor`（`io.legado.app.lib.theme`），夜间主题自动适配。
- 构建 flavor=app；`JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9"`；`ANDROID_HOME="D:/DevTools/android-sdk"`。
- 已知 `ReadBookConfigTest.sanitize_clampsUnsafeLineSpacing` 为基线失败（与本次无关），不阻断打包。
- 新建提交不 amend；不 force-push main；不提交 secrets。
- 每次 Bash 调用后 shell cwd 会重置到 Obsidian 目录，所有 gradle/git 命令必须先 `cd /c/Users/xmboo/source/repos/legado`。

## File Structure

| 文件 | 职责 | 动作 |
|---|---|---|
| `app/src/main/res/layout/item_rss_source.xml` | 订阅卡片布局 | 重做 |
| `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt` | 订阅卡绑定/高亮/选择/菜单 | 修改 |
| `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt` | 订阅页网格+点按 | 修改 |
| `app/src/main/res/menu/rss_source_item.xml` | 订阅更多菜单 | 加 menu_select |
| `app/src/main/res/layout/activity_rss_source.xml` | 订阅页 RecyclerView 网格化 | 修改 |
| `app/src/main/res/layout/item_book_source.xml` | 书源卡片布局 | 重做 |
| `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt` | 书源卡绑定/高亮/选择/菜单 | 修改 |
| `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt` | 书源页网格+点按 | 修改 |
| `app/src/main/res/layout/activity_book_source.xml` | 书源页 RecyclerView 网格化 | 修改 |

验证方式说明：本次为 ViewBinding 布局 + adapter 绑定改造，无 Robolectric adapter 测试 harness，
不新增单元测试；每个任务以 `compileAppDebugKotlin` 为验证门（XML 缺 ID 或签名不符会编译失败），
最终任务 `assembleAppDebug` 出 APK 并按真机清单验收。

---

### Task 1: 订阅管理页平铺

**Files:**
- Modify: `app/src/main/res/layout/item_rss_source.xml`（整文件重做）
- Modify: `app/src/main/res/layout/activity_rss_source.xml:38-43`
- Modify: `app/src/main/res/menu/rss_source_item.xml`
- Modify: `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt`
- Modify: `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt:228-241`

**Interfaces:**
- Consumes: `RecyclerAdapter.setOnItemClickListener((ItemViewHolder, ITEM)->Unit)`；`RssSourceAdapter.CallBack.update(vararg RssSource)`；`RssSource.sourceUrl/enabled/getDisplayNameGroup()`；`R.string.select`。
- Produces: `RssSourceAdapter.toggleSelection(item: RssSource)`；`RssSourceAdapter.selection: List<RssSource>`（已有）；`ItemRssSourceBinding.tvName`（新视图）。

- [ ] **Step 1: 重做订阅卡片布局**

把 `app/src/main/res/layout/item_rss_source.xml` 整文件替换为：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="4dp"
    android:background="?android:attr/selectableItemBackground"
    android:orientation="vertical"
    android:padding="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/tv_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="end"
            android:maxLines="2"
            android:textColor="@color/primaryText"
            android:textSize="15sp"
            android:textStyle="bold"
            tools:text="@string/rss_source" />

        <io.legado.app.lib.theme.view.ThemeCheckBox
            android:id="@+id/cb_source"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="4dp"
            tools:ignore="TouchTargetSizeCheck" />

    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:gravity="end|center_vertical"
        android:orientation="horizontal">

        <io.legado.app.lib.theme.view.ThemeSwitch
            android:id="@+id/swt_enabled"
            android:name="@string/enable"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            tools:ignore="TouchTargetSizeCheck" />

        <androidx.appcompat.widget.AppCompatImageView
            android:id="@+id/iv_edit"
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:layout_marginStart="4dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/edit"
            android:padding="4dp"
            android:src="@drawable/ic_edit"
            android:tint="@color/primaryText" />

        <androidx.appcompat.widget.AppCompatImageView
            android:id="@+id/iv_menu_more"
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:layout_marginStart="4dp"
            android:background="@drawable/bg_more_icon_button_clear"
            android:contentDescription="@string/more_menu"
            android:padding="4dp"
            android:src="@drawable/ic_more_vert"
            android:tint="@color/primaryText" />

    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: 订阅页 RecyclerView 网格化**

把 `app/src/main/res/layout/activity_rss_source.xml` 中 `recycler_view` 块（38-43 行）替换为：

```xml
    <io.legado.app.ui.widget.recycler.scroller.FastScrollRecyclerView
        android:id="@+id/recycler_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:padding="4dp"
        app:layoutManager="androidx.recyclerview.widget.GridLayoutManager"
        app:spanCount="2" />
```

- [ ] **Step 3: 订阅更多菜单加"选择"**

把 `app/src/main/res/menu/rss_source_item.xml` 整文件替换为：

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">

    <item
        android:id="@+id/menu_select"
        android:title="@string/select" />

    <item
        android:id="@+id/menu_top"
        android:title="@string/to_top" />

    <item
        android:id="@+id/menu_bottom"
        android:title="@string/to_bottom" />

    <item
        android:id="@+id/menu_del"
        android:title="@string/delete" />

</menu>
```

- [ ] **Step 4: RssSourceAdapter 加 import**

在 `RssSourceAdapter.kt` 现有 import 区追加（保持字母序近似即可）：

```kotlin
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.secondaryTextColor
```

- [ ] **Step 5: 替换 RssSourceAdapter.convert**

把现有 `convert(...)` 方法整体替换为：

```kotlin
    override fun convert(
        holder: ItemViewHolder,
        binding: ItemRssSourceBinding,
        item: RssSource,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isEmpty()) {
                tvName.text = item.getDisplayNameGroup()
                swtEnabled.isChecked = item.enabled
                cbSource.isChecked = selected.contains(item)
                upCardBackground(binding, item)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "upName" -> tvName.text = item.getDisplayNameGroup()
                            "enabled" -> {
                                swtEnabled.isChecked = bundle.getBoolean("enabled")
                                upCardBackground(binding, item)
                            }
                            "selected" -> {
                                cbSource.isChecked = selected.contains(item)
                                upCardBackground(binding, item)
                            }
                        }
                    }
                }
            }
        }
    }
```

- [ ] **Step 6: RssSourceAdapter 新增 upCardBackground 与 toggleSelection**

在 `convert` 方法之后、`registerListener` 之前插入：

```kotlin
    private fun upCardBackground(binding: ItemRssSourceBinding, item: RssSource) {
        val density = context.resources.displayMetrics.density
        val isSelected = selected.contains(item)
        val strokeColor = if (item.enabled) context.primaryColor else context.secondaryTextColor
        val fillColor = when {
            isSelected -> ColorUtils.setAlphaComponent(context.primaryColor, 60)
            item.enabled -> ColorUtils.setAlphaComponent(context.primaryColor, 20)
            else -> Color.TRANSPARENT
        }
        binding.root.background = GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(fillColor)
            setStroke(density.toInt().coerceAtLeast(1), strokeColor)
        }
    }

    fun toggleSelection(item: RssSource) {
        if (selected.contains(item)) {
            selected.remove(item)
        } else {
            selected.add(item)
        }
        val position = getItems().indexOfFirst { it.sourceUrl == item.sourceUrl }
        if (position >= 0) {
            notifyItemChanged(position, bundleOf(Pair("selected", null)))
        }
        callBack.upCountView()
    }
```

`registerListener` 保持不变（swtEnabled→callBack.update；cbSource→选择；ivEdit→edit；ivMenuMore→showMenu）。

- [ ] **Step 7: 替换 RssSourceAdapter.showMenu**

把现有 `showMenu(...)` 整体替换为：

```kotlin
    private fun showMenu(view: View, position: Int) {
        val source = getItem(position) ?: return
        view.showPopupMenu(
            R.menu.rss_source_item,
        ) { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_select -> toggleSelection(source)
                R.id.menu_top -> callBack.toTop(source)
                R.id.menu_bottom -> callBack.toBottom(source)
                R.id.menu_del -> {
                    callBack.del(source)
                    selected.remove(source)
                }
            }
            true
        }
    }
```

- [ ] **Step 8: RssSourceActivity 网格点按 + 去分隔线**

把 `RssSourceActivity.kt` 的 `initRecyclerView()`（228-241 行）整体替换为：

```kotlin
    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.adapter = adapter
        adapter.setOnItemClickListener { _, item ->
            if (adapter.selection.isEmpty()) {
                item.enabled = !item.enabled
                update(item)
            } else {
                adapter.toggleSelection(item)
            }
        }
        // When this page is opened, it is in selection mode
        val dragSelectTouchHelper: DragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        dragSelectTouchHelper.activeSlideSelect()
        // Note: need judge selection first, so add ItemTouchHelper after it.
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }
```

并删除 import 行 `import io.legado.app.ui.widget.recycler.VerticalDivider`（约 35 行）。

- [ ] **Step 9: 编译验证**

Run:
```bash
cd /c/Users/xmboo/source/repos/legado && export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9" && export ANDROID_HOME="D:/DevTools/android-sdk" && ./gradlew compileAppDebugKotlin
```
Expected: BUILD SUCCESSFUL。若报 `tvName` 未解析→检查 Step1 XML id；若报 `update`/`selection` 未解析→核对 CallBack 签名。

- [ ] **Step 10: 提交**

```bash
cd /c/Users/xmboo/source/repos/legado && git add app/src/main/res/layout/item_rss_source.xml app/src/main/res/layout/activity_rss_source.xml app/src/main/res/menu/rss_source_item.xml app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt && git commit -m "feat(rss): 订阅管理页2列卡片平铺，点按切换启用"
```

---

### Task 2: 书源管理页平铺

**Files:**
- Modify: `app/src/main/res/layout/item_book_source.xml`（整文件重做）
- Modify: `app/src/main/res/layout/activity_book_source.xml:38-43`
- Modify: `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:274-286`

**Interfaces:**
- Consumes: `RecyclerAdapter.setOnItemClickListener`；`BookSourceAdapter.CallBack.enable(enable: Boolean, bookSource: BookSourcePart)`；`BookSourcePart.bookSourceUrl/enabled/getDisPlayNameGroup()`；`R.string.select`；Task1 同款 upCardBackground/toggleSelection 模式。
- Produces: `BookSourceAdapter.toggleSelection(item: BookSourcePart)`；`ItemBookSourceBinding.tvName`（新视图）。

- [ ] **Step 1: 重做书源卡片布局**

把 `app/src/main/res/layout/item_book_source.xml` 整文件替换为：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="4dp"
    android:background="?android:attr/selectableItemBackground"
    android:orientation="vertical"
    android:padding="12dp">

    <io.legado.app.ui.widget.text.AccentTextView
        android:id="@+id/tv_host_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="4dp"
        android:singleLine="true"
        android:textSize="12sp"
        android:visibility="gone"
        tools:text="host"
        tools:visibility="visible" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/tv_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="end"
            android:maxLines="2"
            android:textColor="@color/primaryText"
            android:textSize="15sp"
            android:textStyle="bold"
            tools:text="@string/book_source" />

        <io.legado.app.lib.theme.view.ThemeCheckBox
            android:id="@+id/cb_book_source"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="4dp"
            tools:ignore="TouchTargetSizeCheck" />

    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:gravity="end|center_vertical"
        android:orientation="horizontal">

        <io.legado.app.ui.widget.image.CircleImageView
            android:id="@+id/iv_explore"
            android:layout_width="8dp"
            android:layout_height="8dp"
            android:layout_gravity="center_vertical"
            android:layout_marginEnd="6dp"
            android:scaleType="centerCrop"
            android:src="@color/md_green_600" />

        <io.legado.app.lib.theme.view.ThemeSwitch
            android:id="@+id/swt_enabled"
            android:name="@string/enable"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            tools:ignore="TouchTargetSizeCheck" />

        <androidx.appcompat.widget.AppCompatImageView
            android:id="@+id/iv_edit"
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:layout_marginStart="4dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/edit"
            android:padding="4dp"
            android:src="@drawable/ic_edit"
            android:tint="@color/primaryText" />

        <androidx.appcompat.widget.AppCompatImageView
            android:id="@+id/iv_menu_more"
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:layout_marginStart="4dp"
            android:background="@drawable/bg_more_icon_button_clear"
            android:contentDescription="@string/more_menu"
            android:padding="4dp"
            android:src="@drawable/ic_more_vert"
            android:tint="@color/primaryText" />

    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/iv_debug_text"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:singleLine="true"
            android:textSize="12sp"
            android:visibility="gone"
            tools:text="@string/debug_hint"
            tools:visibility="visible" />

        <io.legado.app.lib.theme.view.ThemeProgressBar
            android:id="@+id/iv_progressBar"
            android:layout_width="25dp"
            android:layout_height="25dp"
            android:animationResolution="1000"
            android:indeterminate="true"
            android:indeterminateBehavior="repeat"
            android:visibility="gone"
            tools:visibility="visible" />

    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: 书源页 RecyclerView 网格化**

把 `app/src/main/res/layout/activity_book_source.xml` 中 `recycler_view` 块（38-43 行）替换为：

```xml
    <io.legado.app.ui.widget.recycler.scroller.FastScrollRecyclerView
        android:id="@+id/recycler_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:padding="4dp"
        app:layoutManager="androidx.recyclerview.widget.GridLayoutManager"
        app:spanCount="2" />
```

- [ ] **Step 3: BookSourceAdapter 加 import**

在 `BookSourceAdapter.kt` 现有 import 区追加：

```kotlin
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.secondaryTextColor
```

（`android.graphics.Color` 已存在，无需重复。）

- [ ] **Step 4: 替换 BookSourceAdapter.convert**

把现有 `convert(...)` 方法整体替换为：

```kotlin
    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookSourceBinding,
        item: BookSourcePart,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isEmpty()) {
                tvName.text = item.getDisPlayNameGroup()
                swtEnabled.isChecked = item.enabled
                cbBookSource.isChecked = selected.contains(item)
                upCardBackground(binding, item)
                upCheckSourceMessage(binding, item)
                upShowExplore(ivExplore, item)
                upSourceHost(binding, holder.layoutPosition)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "enabled" -> {
                                swtEnabled.isChecked = bundle.getBoolean("enabled")
                                upCardBackground(binding, item)
                            }
                            "upName" -> tvName.text = item.getDisPlayNameGroup()
                            "upExplore" -> upShowExplore(ivExplore, item)
                            "selected" -> {
                                cbBookSource.isChecked = selected.contains(item)
                                upCardBackground(binding, item)
                            }
                            "checkSourceMessage" -> upCheckSourceMessage(binding, item)
                            "upSourceHost" -> upSourceHost(binding, holder.layoutPosition)
                        }
                    }
                }
            }
        }
    }
```

- [ ] **Step 5: BookSourceAdapter 新增 upCardBackground 与 toggleSelection**

在 `convert` 方法之后、`registerListener` 之前插入：

```kotlin
    private fun upCardBackground(binding: ItemBookSourceBinding, item: BookSourcePart) {
        val density = context.resources.displayMetrics.density
        val isSelected = selected.contains(item)
        val strokeColor = if (item.enabled) context.primaryColor else context.secondaryTextColor
        val fillColor = when {
            isSelected -> ColorUtils.setAlphaComponent(context.primaryColor, 60)
            item.enabled -> ColorUtils.setAlphaComponent(context.primaryColor, 20)
            else -> Color.TRANSPARENT
        }
        binding.root.background = GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(fillColor)
            setStroke(density.toInt().coerceAtLeast(1), strokeColor)
        }
    }

    fun toggleSelection(item: BookSourcePart) {
        if (selected.contains(item)) {
            selected.remove(item)
        } else {
            selected.add(item)
        }
        val position = getItems().indexOfFirst { it.bookSourceUrl == item.bookSourceUrl }
        if (position >= 0) {
            notifyItemChanged(position, bundleOf(Pair("selected", null)))
        }
        callBack.upCountView()
    }
```

`registerListener` 保持不变。

- [ ] **Step 6: BookSourceAdapter.showMenu 加"选择"**

在 `showMenu` 的 `val actions = buildList {` 之后第一行插入：

```kotlin
            add(PopupMenuAction(context.getString(R.string.select)) { toggleSelection(source) })
```

插入后 buildList 开头形如：

```kotlin
        val actions = buildList {
            add(PopupMenuAction(context.getString(R.string.select)) { toggleSelection(source) })
            if (callBack.sort == BookSourceSort.Default) {
                add(PopupMenuAction(context.getString(R.string.to_top)) { callBack.toTop(source) })
                add(PopupMenuAction(context.getString(R.string.to_bottom)) { callBack.toBottom(source) })
            }
            // ...其余原有 action 保持不变
```

- [ ] **Step 7: BookSourceActivity 网格点按 + 去分隔线**

把 `BookSourceActivity.kt` 的 `initRecyclerView()`（274-286 行）整体替换为：

```kotlin
    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.recycledViewPool.setMaxRecycledViews(0, 15)
        adapter.setOnItemClickListener { _, item ->
            if (adapter.selection.isEmpty()) {
                enable(!item.enabled, item)
            } else {
                adapter.toggleSelection(item)
            }
        }
        // When this page is opened, it is in selection mode
        val dragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        dragSelectTouchHelper.activeSlideSelect()
        // Note: need judge selection first, so add ItemTouchHelper after it.
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }
```

并删除 import 行 `import io.legado.app.ui.widget.recycler.VerticalDivider`（约 49 行）。

注：`enable(...)` 为 BookSourceActivity 实现的 CallBack 方法 `override fun enable(enable: Boolean, bookSource: BookSourcePart)`；执行前用 grep 确认其存在。

- [ ] **Step 8: 编译验证**

Run:
```bash
cd /c/Users/xmboo/source/repos/legado && export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9" && export ANDROID_HOME="D:/DevTools/android-sdk" && ./gradlew compileAppDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 9: 提交**

```bash
cd /c/Users/xmboo/source/repos/legado && git add app/src/main/res/layout/item_book_source.xml app/src/main/res/layout/activity_book_source.xml app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt && git commit -m "feat(book): 书源管理页2列卡片平铺，点按切换启用"
```

---

### Task 3: 构建 APK + 验证 + 推送交付

**Files:**
- 产物: `app/build/outputs/apk/app/debug/legado_app_*.apk`

**Interfaces:**
- Consumes: Task1/Task2 已提交的平铺改造。
- Produces: 可安装 APK + 推送到 origin/own 的提交。

- [ ] **Step 1: 全量编译 + 打包**

Run:
```bash
cd /c/Users/xmboo/source/repos/legado && export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9" && export ANDROID_HOME="D:/DevTools/android-sdk" && ./gradlew assembleAppDebug
```
Expected: BUILD SUCCESSFUL，生成 `app/build/outputs/apk/app/debug/legado_app_*.apk`。
（不依赖单元测试；已知 ReadBookConfigTest 基线失败不影响 assembleAppDebug。）

- [ ] **Step 2: 定位 APK**

Run:
```bash
cd /c/Users/xmboo/source/repos/legado && ls -t app/build/outputs/apk/app/debug/*.apk | head -1
```
Expected: 输出最新 APK 绝对/相对路径。

- [ ] **Step 3: 真机验收清单（人工核对）**

1. 两页呈 2 列卡片网格，间距均匀，无横线分隔。
2. 点按卡片主体切换启用/停用，卡片描边/底色与开关同步。
3. 左滑条滑选多选；⋮菜单"选择"进入多选；多选态点按切换选中；SelectActionBar 批量操作正常。
4. 长按卡片拖动排序（手动排序时）正常。
5. ✎编辑、⋮更多（置顶/置底/删除/校验/调试/发现）正常。
6. 书源校验所选进度/红点、按域名分组 host 标签正常。
7. 搜索/排序/分组菜单正常；夜间主题卡片配色正常；玻璃标题栏/底部栏无回归。

- [ ] **Step 4: 推送到真实 origin/own**

Run:
```bash
cd /c/Users/xmboo/source/repos/legado && git remote -v && git push origin own
```
Expected: 推送成功到 `github.com/Xmboor/legado`（核对 remote URL 与任务目标一致，严禁代理替换）。

- [ ] **Step 5: 交付**

向用户报告：APK 路径、提交哈希、推送结果、验收清单状态、已知限制（ReadBookConfigTest 基线失败与本次无关）。

---

## Self-Review

**1. Spec coverage：**
- 2列网格（spec §4）→ Task1 Step2 / Task2 Step2。
- 卡片视觉/启用高亮/选中高亮（§5）→ Task1 Step1/5/6 / Task2 Step1/4/5。
- 点按=启用、多选态点按=选中（§6.1）→ Task1 Step8 / Task2 Step7。
- 多选入口左滑条+⋮"选择"（§6.2）→ Task1 Step3/7/8 / Task2 Step6/7。
- 长按=拖拽保留（§6.3）→ ItemTouchHelper 保留，未改 ItemTouchCallback。
- 编辑/更多图标（§6.4）→ registerListener 保留。
- 保留功能（§7）→ 未改 diff/校验/发现/分组/SelectActionBar/玻璃。
- host 分组（§5.3）→ Task2 布局保留 tv_host_text + upSourceHost 不变。
- 测试验收（§10）→ Task3。
- 非目标（§11）→ 未触碰换源对话框/列数配置/跨列header/设置项/基类。
覆盖完整，无遗漏。

**2. Placeholder scan：** 无 TBD/TODO；所有代码步骤含完整代码；Task2 Step6 用"其余原有 action 保持不变"指代未改动部分（非占位，原有代码已在源码中，执行时仅插入一行）。

**3. Type consistency：**
- `toggleSelection(item: RssSource)` / `toggleSelection(item: BookSourcePart)` 签名一致，activity 调用 `adapter.toggleSelection(item)` 匹配。
- `upCardBackground(binding, item)` 两 adapter 各自类型匹配。
- `tvName` 由 XML `@+id/tv_name` 生成，convert 引用一致。
- `enable(!item.enabled, item)` 匹配 `CallBack.enable(enable: Boolean, bookSource: BookSourcePart)`。
- `update(item)` 匹配 `CallBack.update(vararg source: RssSource)`。
- `R.string.select`、`R.id.menu_select` 已在 Task1 Step3 定义。
一致，无冲突。