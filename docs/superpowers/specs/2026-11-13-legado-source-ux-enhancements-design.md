# Legado 源管理体验增强 设计文档

日期：2026-11-13
分支：own
仓库：https://github.com/Xmboor/legado

## 1. 背景与目标

在主页顶栏横向源切换条（Phase F）落地后，用户提出三项体验增强：

1. 主页「发现/订阅」顶栏的源标签，长按可删除该源。
2. 「我的 → 其他设置」中 4 项数字设置改为手动输入数字，不再用滚轮选择。
3. 「我的 → 书源/订阅源管理」默认单列、按域名分区显示。

目标：在不破坏既有切换/加载/搜索/校验/分组/批量操作的前提下，补齐上述交互。

## 2. 已确认决策

- 长按删除范围：仅现代模式（默认）顶栏标签条；经典模式纵向列表不改。
- 管理页列数：默认单列，菜单保留「双列平铺」开关，选择持久化；书源、订阅源两页一致。
- 长按删除交互：复用现有「确定删除？+源名」确认框，确认后删除（ destructive 操作需确认）。
- 其他设置仅改用户点名的 4 项；Web 端口、源编辑最大行数保持滚轮不动。

## 3. 现状（探索结论）

- `RoundedTagBarView` 已内置 `setOnTagLongClickListener((Int)->Boolean)`，但 `ExploreFragment`/`RssFragment` 未接线。
- `ExploreFragment.deleteSource(BookSourcePart)` 与 `RssFragment.del(RssSource)` 已存在，均为确认框 → `viewModel.deleteSource` / `viewModel.del`。
- `OtherConfigFragment` 对 `bitmapCacheSize`/`imageRetainNum`/`preDownloadNum`/`threadCount` 使用滚轮 `NumberPickerDialog`（`android.widget.NumberPicker`）。
- 两管理页 `activity_book_source.xml`/`activity_rss_source.xml` 均为 `GridLayoutManager spanCount=2`。
- 书源页已有域名分组：`groupSourcesByDomain`（默认 false）、菜单 `menu_group_sources_by_domain`、`adapter.showSourceHost`、`item_book_source.xml` 的 `tv_host_text`、`getSourceHost` 用 `NetworkUtils.getSubDomainOrNull`，分组时禁用拖拽。
- 订阅源页无域名分组，需移植；`RssSource` 具备 `lastUpdateTime`/`customOrder` 字段。

## 4. 设计

### Part 1 · 主页顶栏长按删除（仅现代模式）

- `ExploreFragment.bindDiscoverSourceSelector()` 内为 `binding.rvDiscoverSources` 接 `setOnTagLongClickListener { index -> discoverSources.getOrNull(index)?.let { deleteSource(it) }; true }`。
- `RssFragment.initModernRssView()` 内为 `binding.rvRssSources` 接 `setOnTagLongClickListener { index -> rssSources.getOrNull(index)?.let { del(it) }; true }`。
- 复用现有确认框与 viewModel 删除；删除后 DB flow 自动刷新标签条与选中态。
- 不新增删除逻辑，点按切换不受影响。

### Part 2 · 其他设置手动数字输入

- 新增纯函数 `NumberInput.parse(text: String?, min: Int, max: Int, fallback: Int): Int`：
  - 解析失败/空白 → fallback；否则 `coerceIn(min, max)`。
- 新增 `NumberInputDialog`（包 `io.legado.app.ui.widget.number`），builder API 与 `NumberPickerDialog` 一致：`setTitle/setMaxValue/setMinValue/setValue/show((Int)->Unit)`。
  - 布局 `dialog_number_input.xml`：EditText，`inputType=number`，仅数字键。
  - 标题用 `setUiTitle`，整体 `applyTint`；显示范围提示；打开时全选文本并弹软键盘。
  - 确定：`NumberInput.parse(...)` 收敛后回调；取消：不回调。
- `OtherConfigFragment` 仅将 4 项的 `NumberPickerDialog` 替换为 `NumberInputDialog`，各项 min/max/回调副作用（如 `bitmapCacheSize` 的 `ImageProvider.bitmapLruCache.resize`）保持不变。
- 单测：`NumberInputTest` 覆盖空白/非数字/越界/合法/边界。

### Part 3 · 管理页默认单列 + 按域名分组

- 持久化：`PreferKey.sourceManageDoubleColumn = "sourceManageDoubleColumn"`；`AppConfig.sourceManageDoubleColumn: Boolean`（get 默认 false，set 持久化）。
- 布局：两页 `spanCount` 2 → 1。
- 列数开关：两菜单新增 `menu_source_double_column`（checkable，标题「双列平铺」）。
  - `onPrepareOptionsMenu` 勾选态 = `AppConfig.sourceManageDoubleColumn`。
  - 创建时与切换时设置 `(recyclerView.layoutManager as GridLayoutManager).spanCount = if (double) 2 else 1`，切换即持久化。
- 域名分组默认开：
  - 书源页：`groupSourcesByDomain = true`，初始 `adapter.showSourceHost = true`，`onPrepareOptionsMenu` 勾选 `menu_group_sources_by_domain`。
  - 订阅源页移植：新增 `groupSourcesByDomain=true`、`hostMap`、`getSourceHost()` 与 `CallBack.getSourceHost`；`upSourceFlow` 增加 `.map { }` 域名排序（`compareBy { host=="#" }.thenBy { host }.thenByDescending { lastUpdateTime }`）；菜单 `menu_group_sources_by_domain`；`itemTouchCallback.isCanDrag = !groupSourcesByDomain`；切换处理镜像书源页。
  - `RssSourceAdapter`：新增 `showSourceHost`、`upSourceHost`、`getHeaderText`、`isItemHeader`，`onCurrentListChanged` 触发 `upSourceHost` 刷新，`CallBack.getSourceHost`。
  - `item_rss_source.xml`：新增 `tv_host_text`（AccentTextView，镜像书源 item）。
- 域名取 `NetworkUtils.getSubDomainOrNull`（如 example.com，IP 原样，失败 "#"）。
- 新增字符串：`source_double_column`（双列平铺）、数字输入范围提示；复用 `group_sources_by_domain`。

## 5. 影响面

改动文件：ExploreFragment、RssFragment、OtherConfigFragment、NumberInputDialog（新）、NumberInput（新）、dialog_number_input.xml（新）、BookSourceActivity、RssSourceActivity、RssSourceAdapter、activity_book_source.xml、activity_rss_source.xml、item_rss_source.xml、book_source.xml(menu)、rss_source.xml(menu)、AppConfig、PreferKey、strings.xml（多语言）。

不碰：玻璃化逻辑、MainActivity、底栏、经典模式、base adapter、触摸助手、ChangeBookSourceDialog。

## 6. 错误处理

- 数字输入越界自动收敛、空白保持原值，不弹错误打断。
- 长按删除走确认框，误触可取消。
- 域名解析失败归入 "#" 分区，排序置底。

## 7. 测试与验证

- 单测：`NumberInputTest`（TDD）。
- 编译：`compileAppDebugKotlin`、`testAppDebugUnitTest`、`assembleAppDebug`。
- 装机核验：见 §8。

## 8. 装机核验清单

- 发现/订阅顶栏长按源标签 → 确认框 → 删除后标签条刷新；点按切换仍正常。
- 其他设置 4 项点开为数字输入框，可键入、越界收敛、留空保持；端口/源编辑行数仍为滚轮。
- 书源/订阅源管理默认单列、按域名分区；菜单「双列平铺」可切换且重启保持；「按域名分组」默认勾选、可取消。
- 分组时拖拽排序禁用；取消分组后恢复。
- 经典模式、玻璃化、底栏、批量操作无回归。

## 9. 既有无关问题（不在本次范围）

`ReadBookConfigTest.sanitize_clampsUnsafeLineSpacing` 既有失败（期望 -5→10，生产 coerceIn(0,20) 得 0），与本次改动无关，不修改。