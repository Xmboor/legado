# Legado 书源/订阅管理页平铺卡片化 · 设计文档

日期：2026-11-13
仓库：Xmboor/legado（本地克隆 `C:\Users\xmboo\source\repos\legado`，分支 `own`）
状态：设计已获用户批准（长按=拖动排序），待规格审阅

## 1. 背景与目标

用户诉求（原话）："书源和订阅均平铺，我可以快速切换书源"。

当前书源管理页（`BookSourceActivity`）与订阅源管理页（`RssSourceActivity`）
都是单列横向行列表：每行 = 名称复选框 + 启用开关 + 编辑图标 + 更多图标。
一屏看到的源数量有限，启用/停用必须精确点按行尾的小开关，切换效率低。

目标：把这两个管理页改成 **2 列卡片网格（平铺）**，一屏展示更多源，
**点按卡片主体即可切换启用/停用**，实现"快速切换书源"。

经澄清确认的范围与交互：

- 范围 = 书源管理页 + 订阅源管理页。**不含**阅读器内的换源对话框
  （`ChangeBookSourceDialog`）。
- 点按卡片主体 = 启用/停用切换（核心）。
- 长按卡片 = 拖动排序（保留现有行为，零回归）。
- 多选 = 左侧滑条滑选（保留）+ ⋮菜单新增"选择"入口；进入多选后点按卡片切换选中。
- 保留卡片上的 ✎编辑 / ⋮更多 小图标。

## 2. 方案选择

- **方案 A（采纳）：2 列卡片网格 + 紧凑卡片布局。** 真正的平铺观感，
  点按即切换，最贴合诉求。工作量集中在两套 item 布局重做 + adapter 绑定改写，可控。
- 方案 B（否决）：沿用现有横向行布局仅切 2 列。现有行为整行宽度设计，
  半宽下名称/开关/图标挤压换行，观感差。
- 方案 C（否决）：流式 chip 平铺。丢失编辑/更多/校验/发现细节，
  多选与拖拽难做，偏离 app 现有模式，过度设计。

## 3. 可行性依据（已读源码确认）

- `RecyclerAdapter<ITEM, VB>` 已内置 `setOnItemClickListener((holder,item)->Unit)`
  与 `setOnItemLongClickListener((holder,item)->Boolean)`，经 `registerItemListener`
  绑到 `holder.itemView`；并提供 `open fun getSpanSize(viewType,position)=1`，
  `onAttachedToRecyclerView` 在 `GridLayoutManager` 下自动设置 `spanSizeLookup`。
  → 点按/长按监听与网格跨度无需改基类。
- `DragSelectTouchHelper` 支持 `GridLayoutManager`；`activeSlideSelect()` 后
  `onInterceptTouchEvent` 的 ACTION_DOWN **仅在** `isInSlideArea`（x 落在
  slideAreaLeft/Right 之间）时拦截。两页均 `setSlideArea(16, 50)`，
  即只拦截左侧 16–50dp 竖条。→ 卡片主体点按不被滑选拦截，点按=启用与滑条多选共存。
- `ItemTouchCallback.getMovementFlags` 对 `GridLayoutManager` 已返回
  `LEFT or RIGHT or UP or DOWN` 拖拽标志。→ 网格拖拽排序零改动可用。
- 两页 `initRecyclerView` 结构一致：`VerticalDivider` + adapter +
  `DragSelectTouchHelper(...).setSlideArea(16,50)` + `activeSlideSelect()` +
  `ItemTouchHelper(itemTouchCallback)`（书源页另有 `recycledViewPool.setMaxRecycledViews(0,15)`）。
- 两页 RecyclerView（`FastScrollRecyclerView`，id `recycler_view`）在 XML 中
  声明 `app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"`。
- 玻璃结构（`title_bar_container` + `select_action_bar_container`，
  各含 LiquidGlassView + shell overlay + 栏本体）为 Phase C 已验证模式，**本次不改动**。

## 4. 布局与网格

### 4.1 网格声明

两页布局 `activity_book_source.xml` / `activity_rss_source.xml` 的
`recycler_view` 改为：

```xml
app:layoutManager="androidx.recyclerview.widget.GridLayoutManager"
app:spanCount="2"
android:padding="4dp"
android:clipToPadding="false"
```

- spanCount 固定 2（手机竖屏标准）；可配置列数列为非目标。
- `RecyclerAdapter.onAttachedToRecyclerView` 会自动接管 `spanSizeLookup`，
  默认每卡 span=1，无需额外代码。
- `FastScrollRecyclerView` 继承 RecyclerView，网格下快速滚动条仍可用；
  若 thumb 行为异常作为非阻塞问题记录，不在本次扩大处理。

### 4.2 间距与分隔线

- 移除两页 `initRecyclerView` 中的 `binding.recyclerView.addItemDecoration(VerticalDivider(this))`
  （书源页约 276 行、订阅页约 230 行）及对应 import。横线分隔不适合网格。
- 卡片根布局 `layout_margin="4dp"`，配合 RecyclerView 4dp padding + clipToPadding=false
  形成均匀网格间距。不新增 ItemDecoration。

## 5. 卡片视觉设计

**就地重做**现有 `item_book_source.xml` / `item_rss_source.xml`（保留文件名 →
ViewBinding 类名 `ItemBookSourceBinding` / `ItemRssSourceBinding` 不变），
改为圆角卡片容器（`LinearLayout` + 圆角 `GradientDrawable` 背景；
颜色取自 ThemeStore，自动适配夜间主题）。

### 5.1 视图 ID 策略（最小化绑定改动）

保留并复用现有 ID，仅新增名称 TextView：

| ID | 书源卡 | 订阅卡 | 改造 |
|---|---|---|---|
| `tv_name`（新增 TextView） | ✓ | ✓ | 显示名称+分组（`getDisPlayNameGroup()`/`getDisplayNameGroup()`），粗体，maxLines=2，ellipsize=end |
| `cb_book_source` / `cb_source`（ThemeCheckBox） | ✓ | ✓ | **去掉文字**，重定位为右上角纯勾选标记；`isChecked`=选中态；保留 `setOnUserCheckedChangeListener` 选择逻辑 |
| `swt_enabled`（ThemeSwitch） | ✓ | ✓ | 保留为紧凑启用开关（明确启用 affordance）；仍走原启用监听 |
| `iv_edit` | ✓ | ✓ | 保留小图标 |
| `iv_menu_more` | ✓ | ✓ | 保留小图标 |
| `iv_explore`（CircleImageView 红点） | ✓ | — | 保留发现状态点 |
| `iv_debug_text` / `iv_progressBar` | ✓ | — | 保留校验文字/进度 |
| `tv_host_text`（AccentTextView） | ✓ | — | 保留按域名分组首卡 host 标签 |

说明：名称从复选框文字迁到独立 `tv_name`，是为了让"点按卡片主体=启用"
不与"点按复选框=选择"冲突——复选框缩为纯勾选标记，名称区域归属卡片主体点击。

### 5.2 状态高亮

- **启用态**：`convert` 中按 `item.enabled` 设置卡片根背景描边色
  （启用=primaryColor 描边 + 极淡填充；停用=dividerColor 描边/无填充）。
  与 `swt_enabled` 开关状态一致，双重 affordance。
- **选中态**：`selected.contains(item)` 时卡片叠加 primaryColor 半透明填充 +
  `cb_book_source`/`cb_source` 勾选。沿用现有 `"selected"` payload 局部刷新。
- 颜色全部取自 ThemeStore（primaryColor / dividerColor / background），夜间主题自动适配。

### 5.3 按域名分组（书源）

`showSourceHost && isItemHeader` 的卡片在顶部显示 `tv_host_text`。
网格下表现为每个 host 组首张卡片带 host 小标签。整行跨列 header 列为非目标。

## 6. 交互模型

### 6.1 点按卡片主体（核心快切）

两页 `initRecyclerView` 中通过 `adapter.setOnItemClickListener { holder, item -> ... }`：

- **非多选态**（`adapter.selection.isEmpty()`）→ 切换启用：
  - 书源：`callBack.enable(!item.enabled, item)`（沿用 `BookSourceAdapter.CallBack.enable`）。
  - 订阅：`item.enabled = !item.enabled; callBack.update(item)`（沿用 `RssSourceAdapter.CallBack.update`）。
- **多选态**（`adapter.selection.isNotEmpty()`）→ 切换选中：
  调用 adapter 新增的 `toggleSelection(item)` 助手（add/remove + `notifyItemChanged(pos,"selected")` + `callBack.upCountView()`）。

`holder.itemView` 点击只在点按卡片主体（非图标/开关/复选框）时触发；
子控件各自消费点击，不冒泡到 itemView。

### 6.2 多选入口

- 左侧滑条滑选：保留（`DragSelectTouchHelper` + `setSlideArea(16,50)` + `activeSlideSelect()`）。
- ⋮更多菜单新增"选择"项（复用 `R.string.select`）：点击后选中该源（`selected.add` + notify + upCountView），
  SelectActionBar 随之出现；之后点按其他卡片切换选中。
  - 书源：`BookSourceAdapter.showMenu` 的 PopupMenuAction 列表新增"选择"。
  - 订阅：`R.menu.rss_source_item` 新增 `menu_select` 项并在 `showMenu` 处理。
- 清空选择（SelectActionBar 返回/全不选）后 `selection.isEmpty()`，点按恢复启用切换。

### 6.3 长按 = 拖动排序（保留）

`ItemTouchHelper(itemTouchCallback)` 与 `isCanDrag = sort==Default && !groupSourcesByDomain`
（书源）/ 对应订阅逻辑保持不变。网格拖拽标志已由 `ItemTouchCallback` 提供。
长按不进入多选、不弹菜单，与现状一致。

### 6.4 编辑 / 更多图标

`iv_edit` → `callBack.edit`；`iv_menu_more` → `showMenu`。保留。

## 7. 保留功能清单（零回归）

搜索/排序/分组菜单、校验所选（书源 CheckSource / 订阅校验）、发现开关、
按域名分组、拖拽排序、滑条多选、SelectActionBar 全部批量操作
（启用/停用/发现/置顶/置底/分组增删/导出/分享/选区间）、DiffUtil 增量刷新、
玻璃标题栏与底部多选栏。

## 8. 涉及文件

修改：

- `app/src/main/res/layout/activity_book_source.xml`（recycler_view 网格化）
- `app/src/main/res/layout/activity_rss_source.xml`（recycler_view 网格化）
- `app/src/main/res/layout/item_book_source.xml`（卡片化重做）
- `app/src/main/res/layout/item_rss_source.xml`（卡片化重做）
- `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt`
  （移除 VerticalDivider；setOnItemClickListener；如需网格相关微调）
- `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt`
  （移除 VerticalDivider；setOnItemClickListener）
- `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt`
  （convert 改 tv_name + 启用/选中高亮；新增 toggleSelection；showMenu 加"选择"）
- `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt`
  （同上，订阅版）
- `app/src/main/res/menu/rss_source_item.xml`（新增 menu_select）
- 字符串：复用现有 `R.string.select`（"选择"），无需新增字符串资源

不改动：`RecyclerAdapter` 基类、`DragSelectTouchHelper`、`ItemTouchCallback`、
`GlassBarHelper`、玻璃布局结构、`MainActivity`、换源对话框、`settings.gradle`（镜像仓库改动不提交）。

## 9. 边界与错误处理

- 空列表/搜索无结果：网格自然为空，沿用现有空态逻辑。
- DiffUtil：`areContentsTheSame` 已比较 name/group/enabled，启用高亮随 enabled 变化重绑；
  选中走 `"selected"` payload。无需改 diff 回调。
- 校验进行中：`iv_debug_text`/`iv_progressBar` 在卡片内正常显示，
  `Debug.isChecking` 时 `setItems(..., skipDiff=!Debug.isChecking)` 逻辑不变。
- 复选框去文字后，原 `cbBookSource.text=...` 赋值迁到 `tv_name`；
  确保 convert 的 full-bind 与 payload 分支都更新名称/高亮。
- 主题/EInk：卡片颜色取 ThemeStore；EInk 模式下玻璃栏已按现有逻辑恢复不透明，卡片正常。
- 点按与滑选冲突：已由 slide-area-only 拦截规避；若实测卡片左缘点按被滑条吞掉，
  记录为非阻塞（滑条本就占据左缘 16–50dp）。

## 10. 测试与验收

- 编译：`compileAppDebugKotlin`（flavor=app）。
- 单测：`testAppDebugUnitTest`。已知 `ReadBookConfigTest.sanitize_clampsUnsafeLineSpacing`
  为基线失败（与本次无关），不阻断；新增/相关 adapter 逻辑如可单测则补。
- 打包：`assembleAppDebug` → `app/build/outputs/apk/app/debug/`。
- 真机验收清单：
  1. 两页呈 2 列卡片网格，间距均匀，无横线分隔。
  2. 点按卡片主体切换启用/停用，卡片高亮与开关同步。
  3. 左滑条滑选多选；⋮菜单"选择"进入多选；多选态点按切换选中；SelectActionBar 批量操作正常。
  4. 长按卡片拖动排序（手动排序时）正常。
  5. ✎编辑、⋮更多（置顶/置底/删除/校验/调试/发现）正常。
  6. 书源校验所选进度/红点、按域名分组 host 标签正常。
  7. 搜索/排序/分组菜单正常；夜间主题卡片配色正常；玻璃标题栏/底部栏无回归。

## 11. 非目标

- 阅读器换源对话框平铺。
- 可配置列数（固定 2 列）。
- 整行跨列的 host 分组 header。
- 新增设置项。
- 改动玻璃逻辑、MainActivity、基类适配器/触摸助手。