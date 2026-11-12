# Legado 管理页面玻璃化 + 订阅源校验所选 · 设计文档

日期：2026-11-12
仓库：Xmboor/legado（本地克隆 `C:\Users\xmboo\source\repos\legado`）
状态：已经用户逐节确认

## 1. 背景与目标

本 fork 的主界面底部导航栏、搜索按钮已使用 `com.qmdeve.liquidglass:core:1.0.3`
（`LiquidGlassView`）实现液态玻璃/磨砂效果，模式与强度由
`AppConfig.bottomBarEffectMode`（`solid`/`glass`/`frosted`）、
`AppConfig.frostedGlassLevel`、`AppConfig.liquidGlassLevel` 控制，
设置入口在"导航栏管理"（`NavigationBarManageActivity`）。

两个目标：

1. **管理页面玻璃化**：把玻璃效果推广到主要管理页面（书源管理、订阅源管理、
   设置、发现页）的标题栏与底部多选操作栏，风格与主界面统一，跟随现有设置，
   不新增设置项。
2. **订阅源校验所选**：订阅源管理页多选后新增"校验所选"，照书源校验
   （`CheckSourceService`）的模式：前台服务 + 通知栏进度 + 并发校验 +
   失败原因写入分组/注释。只验文章列表，不验正文。

## 2. 功能一：管理页面玻璃化

### 2.1 共享组件 GlassBarHelper

新文件：`app/src/main/java/io/legado/app/ui/widget/glass/GlassBarHelper.kt`

- 将 `MainActivity` 中的玻璃参数计算公式（blurRadius、refractionHeight、
  refractionOffset、dispersion、tintAlpha 随 glassLevel 的变化，frosted 与
  glass 两种模式的差异）收进 helper，作为管理页的唯一参数来源。
  **MainActivity 本身不改动**，避免主界面回归；公式以 MainActivity 现有
  实现为准复制提炼。
- 对外入口（示意签名）：

  ```kotlin
  fun bind(
      glassView: LiquidGlassView,   // 垫底折射层
      shellOverlay: View,           // 半透明描边壳层
      barView: View,                // 栏本体（TitleBar / SelectActionBar）
      contentContainer: View,       // 被折射的内容容器
      cornerRadius: Float,
      isTopBar: Boolean
  )
  ```

- 行为规则：
  - `bottomBarEffectMode == "solid"` 或 `AppConfig.isEInkMode` →
    玻璃层与壳层 GONE，栏本体恢复现有不透明主题色
    （TitleBar 用 `primaryColor`，SelectActionBar 用 `bottomBackground`），
    与改造前完全一致。
  - `glass`/`frosted` → 玻璃层 VISIBLE 并 `bind(contentContainer)`
    （每个 view 只 bind 一次，沿用 MainActivity 的 boundIds 去重模式），
    栏本体背景透明，壳层用 `GradientDrawable` 画半透明填充 + 1dp 描边
    （照 `createLiquidGlassShellDrawable` 的配色逻辑，颜色取自 ThemeStore，
    自动适配夜间主题）。
  - 内容容器尚未完成布局时，`doOnPreDraw` 延迟重试
    （照 `scheduleLiquidGlassSetup` 机制）。
- 刷新时机：页面 `onResume`、主题变更事件、
  `PreferKey` 中玻璃模式/强度相关键的 observeEvent（与 MainActivity 一致）。

### 2.2 页面布局改造模式

目标页面与布局：

| 页面 | 布局文件 | 玻璃化部位 |
|---|---|---|
| 书源管理 BookSourceActivity | `activity_book_source.xml` | 标题栏 + 底部多选栏 |
| 订阅源管理 RssSourceActivity | `activity_rss_source.xml` | 标题栏 + 底部多选栏 |
| 设置 ConfigActivity | `activity_config.xml` | 仅标题栏（无多选栏） |
| 发现页 ExploreFragment | `fragment_explore.xml` | 仅标题栏（无多选栏） |

每个页面同一套改法：

1. 根布局 LinearLayout → FrameLayout。
2. 内容视图（RecyclerView / fragment 容器）铺满全屏，
   `clipToPadding=false`；运行时用 `doOnLayout` 测得标题栏/多选栏高度后
   设置内容视图的 top/bottom padding，保证首尾条目可完整滚出、不被栏遮挡。
3. 标题栏包一层 FrameLayout：`LiquidGlassView`（match 栏尺寸，垫底）
   + 栏本体 + 壳层 overlay View，结构与 `activity_main.xml` 的
   bottomNavigation 玻璃结构一致。
4. 底部多选栏（SelectActionBar，**仅书源/订阅源管理两页有**）同样包玻璃层；
   它只在选中模式出现，隐藏（GONE）时内容视图 bottom padding 同步归零
   （用布局监听联动）。设置页与发现页只处理标题栏，内容视图仅设 top padding。
5. 页面代码中调用 `GlassBarHelper.bind(...)` 完成运行时参数绑定。
6. 发现页布局为嵌套结构（标题栏 + ViewPager2/RecyclerView），内容容器取
   其可滚动内容的根视图，padding 联动按实际结构适配，不强行套用管理页模板。

### 2.3 边界约定

- 状态栏保持现有主题色，本次不做沉浸式/edge-to-edge 延伸。
- 低版本 Android 的降级表现与主界面现状一致（库自身行为），不单独处理。
- 编辑页、调试页、阅读页、所有对话框一律不动。
- 发现页玻璃绑定到 fragment 自己的内容根视图，与主界面底部栏玻璃互不影响。

## 3. 功能二：订阅源校验所选

### 3.1 新增组件

| 文件 | 职责 |
|---|---|
| `model/CheckRssSource.kt` | 入口对象：`start(context, sources)` / `stop` / `resume`；选中源 URL 列表经 `IntentData.put("checkRssSourceSelectedIds", ...)` 传递；超时与写注释开关复用 `CheckSource.timeout`、`CheckSource.wSourceComment`，不新增设置项 |
| `service/CheckRssSourceService.kt` | 前台服务，镜像 `CheckSourceService` 结构 |
| `RssSource` 实体新增 helper | 从 `BookSource` 移植：`removeInvalidGroups()`、`getInvalidGroupNames()`、`addErrorComment(e)`、`removeErrorComment()`；纯字符串逻辑，注释前缀约定与 BookSource 保持一致，不改数据库结构 |

配套常量与注册：

- `EventBus` 新增：`CHECK_RSS_SOURCE`（进度消息）、`CHECK_RSS_SOURCE_DONE`（完成）。
- `NotificationId` 新增：`CheckRssSourceService`。
- `AndroidManifest.xml` 注册：
  `<service android:name=".service.CheckRssSourceService" android:foregroundServiceType="dataSync" />`
- 通知渠道复用 `AppConst.channelIdReadAloud`（与书源校验服务一致）。
- 菜单 `rss_source_sel.xml` 新增 `menu_check_rss_source`，标题"校验所选"。
- 字符串：`values/strings.xml`（英文）与 `values-zh`、`values-zh-rHK`、
  `values-zh-rTW` 同步新增（校验订阅源 / 校验所选 / 将校验所选 N 个订阅源 /
  已有订阅源在校验 / 校验完成 等）。

### 3.2 服务内部流程

```
start(ids) → 前台通知（标题"校验订阅源"，进度 n/total，取消按钮，
             contentIntent → RssSourceActivity）
  → flow 从 appDb.rssSourceDao 逐 id 取源
  → onEachParallel(AppConfig.threadCount) 并发校验
  → 每源完成：更新通知进度 + postEvent(CHECK_RSS_SOURCE) + dao.update(source)
  → 全部完成：stopSelf → postEvent(CHECK_RSS_SOURCE_DONE) → 取消通知
```

单个源校验（`withTimeout(CheckSource.timeout)` 包裹）：

1. `removeInvalidGroups()`；若 `wSourceComment` 则 `removeErrorComment()`。
2. `source.sortUrls()`（现有扩展）取第一个分类；为空则退回
   `sourceUrl` 本身（分类名用 `sourceName`）。
3. `Rss.getArticlesAwait(sortName, sortUrl, source, page = 1)`
   拉文章列表（登录检测 JS、重定向处理等现有逻辑自动生效）。
4. 列表非空 → 成功；为空 → `addGroup("列表失效")` 并抛
   `NoStackTraceException("文章列表为空")`。
5. 失败分类写分组：
   - `TimeoutCancellationException` → `校验超时`
   - `ScriptException` / `WrappedException` → `js失效`
   - `NoStackTraceException` → 不额外加分组（原因已在消息/注释中）
   - 其他异常 → `网站失效`
6. 若 `wSourceComment` → `addErrorComment(throwable)`。
7. `dao.update(source)` → Room Flow 自动推送，管理页列表实时刷新。

保护与取消：

- 重复启动：`checkJob` 活跃时 toast"已有订阅源在校验,等完成后再试"，不叠加。
- 通知栏取消按钮 → `IntentAction.stop` → `stopSelf()`，协程随
  lifecycleScope 取消。
- `sortUrls()` 自身抛错（远程分类规则失效等）按第 5 步异常分类处理。

### 3.3 界面接线（RssSourceActivity）

- `onMenuItemClick` 新增分支：`menu_check_rss_source` → 确认弹窗：
  - 消息："将校验所选 N 个订阅源（只验文章列表）"
  - 确定 → `keepScreenOn(true)` + `CheckRssSource.start(this, adapter.selection)`
  - 中性按钮"校验设置" → 打开现有 `CheckSourceConfig` 弹窗（复用，
    手动设置监听避免点开后确认框关闭，照 BookSourceActivity 写法）
  - 取消 → 关闭
- `observeEvent(CHECK_RSS_SOURCE_DONE)` → toast"校验完成" +
  `keepScreenOn(false)`。列表刷新由 Room Flow 自动完成，无需手动。
- 进度展示以通知栏为准，页面内不另做进度条。

### 3.4 明确不做（Non-goals）

- 不加 `respondTime` 字段（避免数据库迁移）。
- 不接 `Debug` 调试日志体系（其 `startChecking` 目前只支持 BookSource）。
- 不自动禁用失效源（本次为标记模式）。
- 不验正文、不新增校验设置界面。

### 3.5 已知边界

- 需要登录且未登录的源会被判为失效，错误注释可见原因，与实际使用表现一致。
- 校验结果分组名与书源校验共用词汇（校验超时/js失效/网站失效），
  订阅源新增"列表失效"；`getInvalidGroupNames()` 覆盖这四个名字。

## 4. 验证计划

### 4.1 编译与单元测试

- `gradlew assembleDebug` 产出 APK（本机 JDK21 位于 `D:\DevTools\JDK21`；
  Android SDK 是否齐全在实施时确认，缺失则先补命令行工具，
  无法补齐时如实报告阻塞点）。
- 单元测试（`app/src/test/java/io/legado/app/`，已有 JUnit 体系）：
  RssSource 新增 helper 的纯逻辑用例——失效分组增删、错误注释追加/清除、
  与用户自写注释互不破坏、`getInvalidGroupNames` 覆盖四个失效分组名。

### 4.2 真机验收清单（用户执行）

玻璃化：

- [ ] 4 个页面在 液态玻璃 / 磨砂 / 纯色 三种模式下标题栏与多选栏显示正确
- [ ] 列表滚动时内容从栏后经过，玻璃有折射/模糊内容
- [ ] 强度滑杆调节即时生效；夜间主题配色正常
- [ ] E-ink 模式回退为不透明；多选栏隐藏时列表底部无空洞
- [ ] 主界面底部栏玻璃与改造前无差异（回归）

订阅源校验：

- [ ] 多选 → 校验所选 → 确认弹窗数量正确 → 通知栏出现进度
- [ ] 失效源出现对应分组标记与错误注释；有效源无残留旧标记
- [ ] 列表随校验进度自动刷新；切后台校验继续
- [ ] 通知栏取消按钮立即停止；重复启动有提示
- [ ] "校验设置"弹窗可打开且超时/写注释配置生效
- [ ] 书源校验全流程不受影响（回归）

## 5. 变更文件清单

新增：

- `app/src/main/java/io/legado/app/ui/widget/glass/GlassBarHelper.kt`
- `app/src/main/java/io/legado/app/model/CheckRssSource.kt`
- `app/src/main/java/io/legado/app/service/CheckRssSourceService.kt`
- `app/src/test/java/io/legado/app/data/entities/RssSourceHelperTest.kt`
- 玻璃壳层 drawable（如需新增，复用主界面现有 `bg_liquid_glass_*` 优先）

修改：

- 布局：`activity_book_source.xml`、`activity_rss_source.xml`、
  `activity_config.xml`、`fragment_explore.xml`
- 页面：`BookSourceActivity.kt`、`RssSourceActivity.kt`、
  `ConfigActivity.kt`、`ExploreFragment.kt`（调用 helper + padding 联动）
- 实体：`RssSource.kt`（4 个 helper）
- 常量：`EventBus.kt`、`NotificationId.kt`
- 菜单：`rss_source_sel.xml`
- 注册：`AndroidManifest.xml`
- 字符串：`values/strings.xml`、`values-zh/strings.xml`、
  `values-zh-rHK/strings.xml`、`values-zh-rTW/strings.xml`