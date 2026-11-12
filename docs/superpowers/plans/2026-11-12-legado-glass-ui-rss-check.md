# Legado 管理页面玻璃化 + 订阅源校验所选 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Xmboor/legado 的管理页面（书源/订阅源/设置/发现）加玻璃化效果，并给订阅源管理增加"校验所选"功能，最终编译出可安装 APK。

**Architecture:** 玻璃化复用主界面已有的 LiquidGlassView 库，提炼共享 GlassBarHelper，管理页布局改为内容铺满 + 栏悬浮其上；订阅源校验镜像现有 CheckSourceService 的前台服务 + 并发 + 分组/注释标记模式，新建独立的 CheckRssSourceService。

**Tech Stack:** Kotlin 2.3.10, AGP 8.13.2, Gradle 8.14.4, JDK 17 toolchain, Room, Coroutines Flow, com.qmdeve.liquidglass:core:1.0.3, JUnit4。

## Global Constraints

- compileSdk = 36, minSdk = 21, targetSdk = 36；Java toolchain = 17（所有模块）。
- 不修改 MainActivity 的玻璃逻辑（零回归）；玻璃参数公式以 MainActivity.setupLiquidGlass 为准。
- 玻璃模式/强度读现有 `AppConfig.bottomBarEffectMode`（solid/glass/frosted）、`AppConfig.frostedGlassLevel`、`AppConfig.liquidGlassLevel`；E-ink 模式（`AppConfig.isEInkMode`）回退不透明。不新增设置项。
- 订阅源校验复用 `CheckSource.timeout`、`CheckSource.wSourceComment`；只验文章列表，不验正文；不加 respondTime 字段（避免 DB 迁移）；不接 Debug 体系；不自动禁用失效源。
- 失效分组名：`校验超时`、`js失效`、`网站失效`、`列表失效`；错误注释前缀 `// Error: `（与 BookSource 一致）。
- 字符串需同步 `values`（英文）、`values-zh`、`values-zh-rHK`、`values-zh-rTW`。
- 构建环境：JAVA_HOME=D:/DevTools/JDK21/jdk-21.0.13+9，ANDROID_HOME=D:/DevTools/android-sdk，JDK17=D:/DevTools/JDK17/jdk-17.0.21+9，镜像 init 脚本 ~/.gradle/init.d/aliyun-mirror.gradle（均不提交）。
- 仓库本地路径：C:\Users\xmboo\source\repos\legado，分支 own。每个任务结束提交一次。

---

### Task 1: 构建环境就绪 + 基线编译

**Files:**
- Create（不提交）: `C:\Users\xmboo\.gradle\init.d\aliyun-mirror.gradle`、`local.properties`、`C:\Users\xmboo\.gradle\gradle.properties`（工具链路径）
- 环境: Android SDK（android-36/build-tools 36.0.0）、Gradle 8.14.4（腾讯镜像）、JDK17（清华镜像）

**Interfaces:**
- Produces: 可用的 `./gradlew :app:assembleDebug` 基线构建，供后续任务编译验证。

- [ ] **Step 1: 确认 SDK/Gradle/JDK17 就绪**

SDK 与 Gradle 已装好。JDK17 解压后确认目录：

Run: `ls /d/DevTools/JDK17/`
Expected: 含 `jdk-17.0.21+9` 目录。

- [ ] **Step 2: 配置 Gradle 工具链探测路径（用户级，不提交）**

写入 `C:\Users\xmboo\.gradle\gradle.properties`：

```properties
org.gradle.java.installations.paths=D:/DevTools/JDK17/jdk-17.0.21+9,D:/DevTools/JDK21/jdk-21.0.13+9
org.gradle.java.installations.auto-download=false
```

- [ ] **Step 3: 基线编译**

Run:
```bash
cd /c/Users/xmboo/source/repos/legado
export JAVA_HOME="D:/DevTools/JDK21/jdk-21.0.13+9"
export ANDROID_HOME="D:/DevTools/android-sdk"
./gradlew :app:assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL，产出 `app/build/outputs/apk/debug/*.apk`。若失败先修环境，不动业务代码。

- [ ] **Step 4: 记录基线 APK 路径**（无代码提交）

---

### Task 2: RssSource 实体 helper + 单元测试（TDD）

**Files:**
- Modify: `app/src/main/java/io/legado/app/data/entities/RssSource.kt`（在 `removeGroup` 之后、`getDisplayVariableComment` 之前插入）
- Test: `app/src/test/java/io/legado/app/data/entities/RssSourceHelperTest.kt`

**Interfaces:**
- Consumes: 现有 `RssSource.addGroup/removeGroup`、`AppPattern.splitGroupRegex`、`String.splitNotBlank`。
- Produces: `RssSource.getInvalidGroupNames(): String`、`removeInvalidGroups()`、`addErrorComment(e: Throwable)`、`removeErrorComment()`，供 Task 3 服务调用。

- [ ] **Step 1: 写失败测试**

创建 `RssSourceHelperTest.kt`：

```kotlin
package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssSourceHelperTest {

    @Test
    fun getInvalidGroupNames_detectsInvalidAndTimeout() {
        val source = RssSource(sourceUrl = "https://a.com", sourceGroup = "正常,网站失效,校验超时,js失效")
        val names = source.getInvalidGroupNames()
        assertTrue(names.contains("网站失效"))
        assertTrue(names.contains("校验超时"))
        assertTrue(names.contains("js失效"))
        assertTrue(!names.contains("正常"))
    }

    @Test
    fun getInvalidGroupNames_nullGroup_returnsEmpty() {
        val source = RssSource(sourceUrl = "https://a.com", sourceGroup = null)
        assertEquals("", source.getInvalidGroupNames())
    }

    @Test
    fun getInvalidGroupNames_onlyValid_returnsEmpty() {
        val source = RssSource(sourceUrl = "https://a.com", sourceGroup = "科技,新闻")
        assertEquals("", source.getInvalidGroupNames())
    }

    @Test
    fun addErrorComment_prependsAndPreservesUserComment() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = "我的备注")
        source.addErrorComment(RuntimeException("连接超时"))
        assertEquals("// Error: 连接超时\n\n我的备注", source.sourceComment)
    }

    @Test
    fun addErrorComment_nullComment() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = null)
        source.addErrorComment(RuntimeException("失败"))
        assertEquals("// Error: 失败", source.sourceComment)
    }

    @Test
    fun removeErrorComment_keepsUserComment() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = "// Error: 旧错误\n\n我的备注")
        source.removeErrorComment()
        assertEquals("我的备注", source.sourceComment)
    }

    @Test
    fun removeErrorComment_multipleErrors() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = "// Error: 错误1\n\n// Error: 错误2\n\n备注")
        source.removeErrorComment()
        assertEquals("备注", source.sourceComment)
    }

    @Test
    fun addThenRemove_restoresUserComment() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = "备注")
        source.addErrorComment(RuntimeException("x"))
        source.removeErrorComment()
        assertEquals("备注", source.sourceComment)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "io.legado.app.data.entities.RssSourceHelperTest" --console=plain`
Expected: 编译失败（getInvalidGroupNames 等方法未定义）。

- [ ] **Step 3: 实现 helper**

在 `RssSource.kt` 的 `removeGroup(...)` 方法之后插入：

```kotlin
    fun removeInvalidGroups() {
        removeGroup(getInvalidGroupNames())
    }

    fun getInvalidGroupNames(): String {
        return sourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.toHashSet()?.filter {
            "失效" in it || it == "校验超时"
        }?.joinToString() ?: ""
    }

    fun removeErrorComment() {
        sourceComment = sourceComment
            ?.split("\n\n")
            ?.filterNot {
                it.startsWith("// Error: ")
            }?.joinToString("\n")
    }

    fun addErrorComment(e: Throwable) {
        sourceComment =
            "// Error: ${e.localizedMessage}" + if (sourceComment.isNullOrBlank())
                "" else "\n\n${sourceComment}"
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "io.legado.app.data.entities.RssSourceHelperTest" --console=plain`
Expected: BUILD SUCCESSFUL，8 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/io/legado/app/data/entities/RssSource.kt app/src/test/java/io/legado/app/data/entities/RssSourceHelperTest.kt
git commit -m "feat(rss): RssSource 失效分组与错误注释 helper + 单测"
```

---

### Task 3: CheckRssSource 入口 + CheckRssSourceService + 常量/注册

**Files:**
- Create: `app/src/main/java/io/legado/app/model/CheckRssSource.kt`
- Create: `app/src/main/java/io/legado/app/service/CheckRssSourceService.kt`
- Modify: `app/src/main/java/io/legado/app/constant/EventBus.kt`、`NotificationId.kt`、`app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: Task 2 的 RssSource helper；`CheckSource.timeout/wSourceComment`；`Rss.getArticlesAwait`；`RssSource.sortUrls()`；`appDb.rssSourceDao.getByKey/update`；`onEachParallel`。
- Produces: `CheckRssSource.start(context, sources: List<RssSource>)`/`stop`/`resume`；`EventBus.CHECK_RSS_SOURCE`/`CHECK_RSS_SOURCE_DONE`，供 Task 4 接线。

- [ ] **Step 1: 加常量**

`EventBus.kt` 在 `CHECK_SOURCE_DONE` 后加：

```kotlin
    const val CHECK_RSS_SOURCE = "checkRssSource"
    const val CHECK_RSS_SOURCE_DONE = "checkRssSourceDone"
```

`NotificationId.kt` 在 `VideoPlayService = 108` 后加：

```kotlin
    const val CheckRssSourceService = 109
```

- [ ] **Step 2: 注册服务**

`AndroidManifest.xml` 在 `CheckSourceService` 的 `<service>` 之后加：

```xml
        <service
            android:name=".service.CheckRssSourceService"
            android:foregroundServiceType="dataSync" />
```

- [ ] **Step 3: 写 CheckRssSource.kt**

```kotlin
package io.legado.app.model

import android.content.Context
import io.legado.app.constant.IntentAction
import io.legado.app.data.entities.RssSource
import io.legado.app.help.IntentData
import io.legado.app.service.CheckRssSourceService
import io.legado.app.utils.startService

object CheckRssSource {

    fun start(context: Context, sources: List<RssSource>) {
        val selectedIds = sources.map { it.sourceUrl }
        IntentData.put("checkRssSourceSelectedIds", selectedIds)
        context.startService<CheckRssSourceService> {
            action = IntentAction.start
        }
    }

    fun stop(context: Context) {
        context.startService<CheckRssSourceService> {
            action = IntentAction.stop
        }
    }

    fun resume(context: Context) {
        context.startService<CheckRssSourceService> {
            action = IntentAction.resume
        }
    }
}
```

- [ ] **Step 4: 写 CheckRssSourceService.kt**

```kotlin
package io.legado.app.service

import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.script.ScriptException
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.IntentData
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.sortUrls
import io.legado.app.model.CheckSource
import io.legado.app.model.rss.Rss
import io.legado.app.ui.rss.source.manage.RssSourceActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.mozilla.javascript.WrappedException
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * 校验订阅源
 */
class CheckRssSourceService : BaseService() {
    private var threadCount = AppConfig.threadCount
    private var searchCoroutine =
        Executors.newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
    private var notificationMsg = appCtx.getString(R.string.service_starting)
    private var checkJob: Job? = null
    private var originSize = 0
    private var finishCount = 0

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, AppConst.channelIdReadAloud)
            .setSmallIcon(R.drawable.ic_status_bar_r)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.check_rss_source))
            .setContentIntent(
                activityPendingIntent<RssSourceActivity>("activity")
            )
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<CheckRssSourceService>(IntentAction.stop)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.start -> IntentData.get<List<String>>("checkRssSourceSelectedIds")?.let {
                check(it)
            }

            IntentAction.resume -> upNotification()
            IntentAction.stop -> stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        searchCoroutine.close()
        postEvent(EventBus.CHECK_RSS_SOURCE_DONE, 0)
        notificationManager.cancel(NotificationId.CheckRssSourceService)
    }

    private fun check(ids: List<String>) {
        if (checkJob?.isActive == true) {
            toastOnUi(R.string.rss_source_checking)
            return
        }
        checkJob = lifecycleScope.launch(searchCoroutine) {
            flow {
                for (origin in ids) {
                    appDb.rssSourceDao.getByKey(origin)?.let {
                        emit(it)
                    }
                }
            }.onStart {
                originSize = ids.size
                finishCount = 0
                notificationMsg = getString(R.string.progress_show, "", 0, originSize)
                upNotification()
            }.onEachParallel(threadCount) {
                checkSource(it)
            }.onEach {
                finishCount++
                notificationMsg = getString(
                    R.string.progress_show,
                    it.sourceName,
                    finishCount,
                    originSize
                )
                upNotification()
                appDb.rssSourceDao.update(it)
            }.onCompletion {
                stopSelf()
            }.collect()
        }
    }

    private suspend fun checkSource(source: RssSource) {
        kotlin.runCatching {
            withTimeout(CheckSource.timeout) {
                doCheckSource(source)
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            when (it) {
                is TimeoutCancellationException -> source.addGroup("校验超时")
                is ScriptException, is WrappedException -> source.addGroup("js失效")
                !is NoStackTraceException -> source.addGroup("网站失效")
            }
            if (CheckSource.wSourceComment) {
                source.addErrorComment(it)
            }
        }
    }

    private suspend fun doCheckSource(source: RssSource) {
        source.removeInvalidGroups()
        if (CheckSource.wSourceComment) {
            source.removeErrorComment()
        }
        val sort = kotlin.runCatching { source.sortUrls().firstOrNull() }.getOrNull()
        val sortName = sort?.first ?: source.sourceName
        val sortUrl = sort?.second ?: source.sourceUrl
        val articles = Rss.getArticlesAwait(sortName, sortUrl, source, 1)
        if (articles.first.isEmpty()) {
            source.addGroup("列表失效")
            throw NoStackTraceException("文章列表为空")
        } else {
            source.removeGroup("列表失效")
        }
    }

    private fun upNotification() {
        notificationBuilder.setContentText(notificationMsg)
        notificationBuilder.setProgress(originSize, finishCount, false)
        postEvent(EventBus.CHECK_RSS_SOURCE, notificationMsg)
        notificationManager.notify(NotificationId.CheckRssSourceService, notificationBuilder.build())
    }

    override fun startForegroundNotification() {
        notificationBuilder.setContentText(notificationMsg)
        notificationBuilder.setProgress(originSize, finishCount, false)
        postEvent(EventBus.CHECK_RSS_SOURCE, notificationMsg)
        startForeground(NotificationId.CheckRssSourceService, notificationBuilder.build())
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:compileAppDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL（字符串 check_rss_source/rss_source_checking 在 Task 4 添加；若此步因缺字符串报错，先做 Task 4 Step 1 再回此步）。

注：`Rss.getArticlesAwait` 返回 `Pair<MutableList<RssArticle>, String?>`，`articles.first` 为文章列表。`sortUrls()` 为 suspend 扩展，返回 `List<Pair<String,String>>`。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/io/legado/app/model/CheckRssSource.kt app/src/main/java/io/legado/app/service/CheckRssSourceService.kt app/src/main/java/io/legado/app/constant/EventBus.kt app/src/main/java/io/legado/app/constant/NotificationId.kt app/src/main/AndroidManifest.xml
git commit -m "feat(rss): 订阅源校验前台服务与入口"
```

---

### Task 4: 订阅源校验 UI 接线（菜单/字符串/Activity）

**Files:**
- Modify: `app/src/main/res/menu/rss_source_sel.xml`、`values/strings.xml`、`values-zh/strings.xml`、`values-zh-rHK/strings.xml`、`values-zh-rTW/strings.xml`、`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt`

**Interfaces:**
- Consumes: Task 3 的 `CheckRssSource.start/stop`、`EventBus.CHECK_RSS_SOURCE_DONE`；现有 `CheckSourceConfig`、`alert`、`keepScreenOn`、`observeEvent`。
- Produces: 多选菜单"校验所选"完整可用流程。

- [ ] **Step 1: 加字符串（4 个 locale）**

`values/strings.xml`（英文，加在 `check_book_source` 附近）：

```xml
    <string name="check_rss_source">Check RSS sources</string>
    <string name="check_rss_source_selection">Check selected</string>
    <string name="check_rss_source_confirm">Check %1$d selected RSS sources (article list only)?</string>
    <string name="rss_source_checking">RSS sources are already being checked, please try again later</string>
    <string name="rss_source_check_done">Check completed</string>
```

`values-zh/strings.xml`：

```xml
    <string name="check_rss_source">校验订阅源</string>
    <string name="check_rss_source_selection">校验所选</string>
    <string name="check_rss_source_confirm">将校验所选 %1$d 个订阅源（只验文章列表）</string>
    <string name="rss_source_checking">已有订阅源在校验,等完成后再试</string>
    <string name="rss_source_check_done">校验完成</string>
```

`values-zh-rHK/strings.xml` 与 `values-zh-rTW/strings.xml`（繁体）：

```xml
    <string name="check_rss_source">校驗訂閱源</string>
    <string name="check_rss_source_selection">校驗所選</string>
    <string name="check_rss_source_confirm">將校驗所選 %1$d 個訂閱源（只驗文章列表）</string>
    <string name="rss_source_checking">已有訂閱源在校驗,等完成後再試</string>
    <string name="rss_source_check_done">校驗完成</string>
```

- [ ] **Step 2: 加菜单项**

`rss_source_sel.xml` 在 `menu_check_selected_interval` 项之前加：

```xml
    <item
        android:id="@+id/menu_check_rss_source"
        android:title="@string/check_rss_source_selection"
        app:showAsAction="never" />
```

- [ ] **Step 3: RssSourceActivity 接线**

加 import：

```kotlin
import androidx.appcompat.app.AlertDialog
import io.legado.app.constant.EventBus
import io.legado.app.model.CheckRssSource
import io.legado.app.ui.config.CheckSourceConfig
import io.legado.app.utils.keepScreenOn
import io.legado.app.utils.observeEvent
import io.legado.app.utils.toastOnUi
```

`onMenuItemClick` 的 when 中加分支（在 `menu_check_selected_interval` 分支前）：

```kotlin
            R.id.menu_check_rss_source -> checkRssSource()
```

加方法：

```kotlin
    private fun checkRssSource() {
        val selectItems = adapter.selection
        val dialog = alert(titleResource = R.string.check_rss_source) {
            setMessage(getString(R.string.check_rss_source_confirm, selectItems.size))
            okButton {
                keepScreenOn(true)
                CheckRssSource.start(this@RssSourceActivity, selectItems)
            }
            neutralButton(R.string.check_source_config)
            cancelButton()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            showDialogFragment<CheckSourceConfig>()
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<Int>(EventBus.CHECK_RSS_SOURCE_DONE) {
            keepScreenOn(false)
            toastOnUi(R.string.rss_source_check_done)
        }
    }
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:compileAppDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/menu/rss_source_sel.xml app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml app/src/main/res/values-zh-rHK/strings.xml app/src/main/res/values-zh-rTW/strings.xml app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt
git commit -m "feat(rss): 订阅源管理多选菜单接入校验所选"
```

---

### Task 5: GlassBarHelper 共享玻璃组件

**Files:**
- Create: `app/src/main/java/io/legado/app/ui/widget/glass/GlassBarHelper.kt`

**Interfaces:**
- Consumes: `LiquidGlassView`、`AppConfig`、`ColorUtils`、`bottomBackground`/`primaryColor`、`dpToPx`。
- Produces: `GlassBarHelper.bind(glassView, shellOverlay, barView, contentContainer, cornerRadius, isTopBar)`，供 Task 6-9 调用。

- [ ] **Step 1: 写 GlassBarHelper.kt**

```kotlin
package io.legado.app.ui.widget.glass

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.view.doOnPreDraw
import com.qmdeve.liquidglass.widget.LiquidGlassView
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import java.util.Collections
import java.util.WeakHashMap

/**
 * 管理页面栏玻璃化共享组件。
 * 参数公式与 MainActivity.setupLiquidGlass 保持一致；按视图实例去重绑定，
 * 避免跨页面相同资源 id 冲突，WeakHashMap 防止内存泄漏。
 */
object GlassBarHelper {

    private val boundViews = Collections.newSetFromMap(WeakHashMap<LiquidGlassView, Boolean>())

    fun bind(
        glassView: LiquidGlassView,
        shellOverlay: View,
        barView: View,
        contentContainer: View,
        cornerRadius: Float,
        isTopBar: Boolean
    ) {
        val context = barView.context
        if (AppConfig.isEInkMode || AppConfig.bottomBarEffectMode == "solid") {
            glassView.visibility = View.GONE
            shellOverlay.visibility = View.GONE
            barView.setBackgroundColor(
                if (isTopBar) context.primaryColor else context.bottomBackground
            )
            return
        }
        val effectMode = AppConfig.bottomBarEffectMode
        val glassLevel = when (effectMode) {
            "frosted" -> AppConfig.frostedGlassLevel / 100f
            else -> AppConfig.liquidGlassLevel / 100f
        }
        val frostedMode = effectMode == "frosted"
        glassView.visibility = View.VISIBLE
        shellOverlay.visibility = View.VISIBLE
        barView.setBackgroundColor(Color.TRANSPARENT)
        if (!contentContainer.isLaidOut) {
            contentContainer.doOnPreDraw {
                bind(glassView, shellOverlay, barView, contentContainer, cornerRadius, isTopBar)
            }
            return
        }
        val blurRadius = if (frostedMode) (10f + glassLevel * 24f).dpToPx() else 5f.dpToPx()
        val tintAlpha = if (frostedMode) 0.12f + glassLevel * 0.18f else 0.05f
        val dispersion = if (frostedMode) (0.18f + glassLevel * 0.16f).coerceAtMost(0.42f) else 0f
        val refractionHeight = if (frostedMode) (12f + glassLevel * 10f).dpToPx() else 12f.dpToPx()
        val refractionOffset = if (frostedMode) (36f + glassLevel * 18f).dpToPx() else 20f.dpToPx()
        shellOverlay.background = createShellDrawable(context, glassLevel, cornerRadius)
        if (boundViews.add(glassView)) {
            glassView.bind(contentContainer)
        }
        glassView.setCornerRadius(cornerRadius)
        glassView.setRefractionHeight(refractionHeight)
        glassView.setRefractionOffset(refractionOffset)
        glassView.setDispersion(dispersion)
        glassView.setBlurRadius(blurRadius)
        glassView.setTintAlpha(tintAlpha)
        glassView.setTintColorRed(0.70f)
        glassView.setTintColorGreen(0.79f)
        glassView.setTintColorBlue(0.86f)
        glassView.setDraggableEnabled(false)
        glassView.setElasticEnabled(true)
        glassView.setTouchEffectEnabled(true)
        glassView.isClickable = false
        glassView.isFocusable = false
        glassView.invalidate()
    }

    private fun createShellDrawable(
        context: Context,
        glassLevel: Float,
        cornerRadius: Float
    ): GradientDrawable {
        val baseColor = context.bottomBackground
        val isLight = ColorUtils.isColorLight(baseColor)
        val surfaceColor = if (isLight) {
            ColorUtils.blendColors(baseColor, Color.WHITE, 0.72f)
        } else {
            ColorUtils.blendColors(baseColor, Color.BLACK, 0.24f)
        }
        val startAlpha = (0.32f + glassLevel * 0.44f).coerceIn(0f, 0.86f)
        val centerAlpha = (0.24f + glassLevel * 0.38f).coerceIn(0f, 0.74f)
        val endAlpha = (0.18f + glassLevel * 0.32f).coerceIn(0f, 0.66f)
        val strokeAlpha = (0.22f + glassLevel * 0.22f).coerceIn(0f, 0.58f)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.withAlpha(surfaceColor, startAlpha),
                ColorUtils.withAlpha(surfaceColor, centerAlpha),
                ColorUtils.withAlpha(surfaceColor, endAlpha)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            setCornerRadius(cornerRadius)
            setStroke(1.dpToPx(), ColorUtils.withAlpha(surfaceColor, strokeAlpha))
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :app:compileAppDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/io/legado/app/ui/widget/glass/GlassBarHelper.kt
git commit -m "feat(ui): 管理页玻璃化共享组件 GlassBarHelper"
```

---

### Task 6: 订阅源管理页玻璃化（RssSourceActivity）

**Files:**
- Modify: `app/src/main/res/layout/activity_rss_source.xml`（整体替换）
- Modify: `app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt`

**Interfaces:**
- Consumes: Task 5 `GlassBarHelper.bind`。
- Produces: 订阅源管理页标题栏 + 多选栏玻璃化，列表内容铺满并从栏后滚过。

- [ ] **Step 1: 替换布局 activity_rss_source.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <io.legado.app.ui.widget.recycler.scroller.FastScrollRecyclerView
        android:id="@+id/recycler_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />

    <FrameLayout
        android:id="@+id/title_bar_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top">

        <com.qmdeve.liquidglass.widget.LiquidGlassView
            android:id="@+id/title_bar_glass_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <View
            android:id="@+id/title_bar_shell_overlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <io.legado.app.ui.widget.TitleBar
            android:id="@+id/title_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:contentInsetStartWithNavigation="0dp"
            app:contentLayout="@layout/view_search"
            app:displayHomeAsUp="true"
            app:title="@string/rss_source" />

    </FrameLayout>

    <FrameLayout
        android:id="@+id/select_action_bar_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom">

        <com.qmdeve.liquidglass.widget.LiquidGlassView
            android:id="@+id/select_action_bar_glass_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <View
            android:id="@+id/select_action_bar_shell_overlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <io.legado.app.ui.widget.SelectActionBar
            android:id="@+id/select_action_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

    </FrameLayout>

</FrameLayout>
```

- [ ] **Step 2: RssSourceActivity 加玻璃绑定**

加 import：

```kotlin
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import io.legado.app.constant.PreferKey
import io.legado.app.ui.widget.glass.GlassBarHelper
```

`onActivityCreated` 末尾加 `setupGlass()`。加方法：

```kotlin
    private fun setupGlass() {
        binding.run {
            GlassBarHelper.bind(
                titleBarGlassView, titleBarShellOverlay, titleBar,
                recyclerView, 0f, isTopBar = true
            )
            GlassBarHelper.bind(
                selectActionBarGlassView, selectActionBarShellOverlay, selectActionBar,
                recyclerView, 0f, isTopBar = false
            )
            titleBarContainer.doOnLayout {
                recyclerView.setPadding(0, titleBarContainer.height, 0, recyclerView.paddingBottom)
            }
            selectActionBarContainer.doOnLayout {
                val bottom = if (selectActionBarContainer.isVisible) selectActionBarContainer.height else 0
                recyclerView.setPadding(0, recyclerView.paddingTop, 0, bottom)
            }
        }
    }
```

在 `observeLiveBus()`（Task 4 已加）中追加玻璃刷新观察：

```kotlin
        observeEvent<String>(PreferKey.bottomBarEffectMode) { setupGlass() }
        observeEvent<Int>(PreferKey.frostedGlassLevel) { setupGlass() }
        observeEvent<Int>(PreferKey.liquidGlassLevel) { setupGlass() }
```

`onResume()` 末尾加 `setupGlass()`。

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileAppDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/activity_rss_source.xml app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt
git commit -m "feat(ui): 订阅源管理页玻璃化"
```

---

### Task 7: 书源管理页玻璃化（BookSourceActivity）

**Files:**
- Modify: `app/src/main/res/layout/activity_book_source.xml`（整体替换）
- Modify: `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt`

**Interfaces:**
- Consumes: Task 5 `GlassBarHelper.bind`。
- Produces: 书源管理页标题栏 + 多选栏玻璃化。

- [ ] **Step 1: 替换布局 activity_book_source.xml**

与 Task 6 布局完全相同，仅 TitleBar 的 `app:title` 改为 `@string/book_source`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <io.legado.app.ui.widget.recycler.scroller.FastScrollRecyclerView
        android:id="@+id/recycler_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />

    <FrameLayout
        android:id="@+id/title_bar_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top">

        <com.qmdeve.liquidglass.widget.LiquidGlassView
            android:id="@+id/title_bar_glass_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <View
            android:id="@+id/title_bar_shell_overlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <io.legado.app.ui.widget.TitleBar
            android:id="@+id/title_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:contentInsetStartWithNavigation="0dp"
            app:contentLayout="@layout/view_search"
            app:displayHomeAsUp="true"
            app:title="@string/book_source" />

    </FrameLayout>

    <FrameLayout
        android:id="@+id/select_action_bar_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom">

        <com.qmdeve.liquidglass.widget.LiquidGlassView
            android:id="@+id/select_action_bar_glass_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <View
            android:id="@+id/select_action_bar_shell_overlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <io.legado.app.ui.widget.SelectActionBar
            android:id="@+id/select_action_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

    </FrameLayout>

</FrameLayout>
```

- [ ] **Step 2: BookSourceActivity 加玻璃绑定**

加 import：

```kotlin
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import io.legado.app.constant.PreferKey
import io.legado.app.ui.widget.glass.GlassBarHelper
```

`onActivityCreated` 末尾加 `setupGlass()`。加方法（与 Task 6 相同）：

```kotlin
    private fun setupGlass() {
        binding.run {
            GlassBarHelper.bind(
                titleBarGlassView, titleBarShellOverlay, titleBar,
                recyclerView, 0f, isTopBar = true
            )
            GlassBarHelper.bind(
                selectActionBarGlassView, selectActionBarShellOverlay, selectActionBar,
                recyclerView, 0f, isTopBar = false
            )
            titleBarContainer.doOnLayout {
                recyclerView.setPadding(0, titleBarContainer.height, 0, recyclerView.paddingBottom)
            }
            selectActionBarContainer.doOnLayout {
                val bottom = if (selectActionBarContainer.isVisible) selectActionBarContainer.height else 0
                recyclerView.setPadding(0, recyclerView.paddingTop, 0, bottom)
            }
        }
    }
```

在现有 `observeLiveBus()` 中追加：

```kotlin
        observeEvent<String>(PreferKey.bottomBarEffectMode) { setupGlass() }
        observeEvent<Int>(PreferKey.frostedGlassLevel) { setupGlass() }
        observeEvent<Int>(PreferKey.liquidGlassLevel) { setupGlass() }
```

`onResume()` 末尾加 `setupGlass()`（若无 onResume 则新增 override）。

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileAppDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/activity_book_source.xml app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt
git commit -m "feat(ui): 书源管理页玻璃化"
```

---

### Task 8: 设置页玻璃化（ConfigActivity，仅标题栏）

**Files:**
- Modify: `app/src/main/res/layout/activity_config.xml`（整体替换）
- Modify: `app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt`

**Interfaces:**
- Consumes: Task 5 `GlassBarHelper.bind`。
- Produces: 设置页标题栏玻璃化，配置 fragment 内容铺满并从标题栏后滚过。

- [ ] **Step 1: 替换布局 activity_config.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:id="@+id/configFrameLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical" />

    <FrameLayout
        android:id="@+id/title_bar_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top">

        <com.qmdeve.liquidglass.widget.LiquidGlassView
            android:id="@+id/title_bar_glass_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <View
            android:id="@+id/title_bar_shell_overlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <io.legado.app.ui.widget.TitleBar
            android:id="@+id/title_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:title="设置" />

    </FrameLayout>

</FrameLayout>
```

- [ ] **Step 2: ConfigActivity 加玻璃绑定**

加 import：

```kotlin
import androidx.core.view.doOnLayout
import io.legado.app.constant.PreferKey
import io.legado.app.ui.widget.glass.GlassBarHelper
```

`onActivityCreated` 开头（applyHeaderColors 之后）加 `setupGlass()`。加方法：

```kotlin
    private fun setupGlass() {
        binding.run {
            GlassBarHelper.bind(
                titleBarGlassView, titleBarShellOverlay, titleBar,
                configFrameLayout, 0f, isTopBar = true
            )
            titleBarContainer.doOnLayout {
                configFrameLayout.setPadding(0, titleBarContainer.height, 0, 0)
            }
        }
    }
```

`applyHeaderColors()` 中删除 `binding.titleBar.setBackgroundColor(Color.TRANSPARENT)` 与 `binding.titleBar.elevation = 0f` 两行（改由 GlassBarHelper 统一管理背景），保留 setTextColor/setColorFilter。

`onResume()` 末尾加 `setupGlass()`。`observeLiveBus()` 追加：

```kotlin
        observeEvent<String>(PreferKey.bottomBarEffectMode) { setupGlass() }
        observeEvent<Int>(PreferKey.frostedGlassLevel) { setupGlass() }
        observeEvent<Int>(PreferKey.liquidGlassLevel) { setupGlass() }
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileAppDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/activity_config.xml app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt
git commit -m "feat(ui): 设置页标题栏玻璃化"
```

---

### Task 9: 发现页玻璃化（ExploreFragment，仅标题栏，高风险页）

**Files:**
- Modify: `app/src/main/res/layout/fragment_explore.xml`（标题栏包玻璃容器 + SwipeRefreshLayout 约束改到 parent 顶）
- Modify: `app/src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt`

**Interfaces:**
- Consumes: Task 5 `GlassBarHelper.bind`。
- Produces: 发现页经典模式标题栏玻璃化；现代发现模式（titleBar 隐藏）玻璃容器同步隐藏。

- [ ] **Step 1: 改布局 fragment_explore.xml**

将顶部 TitleBar 替换为玻璃容器包裹（保留原 TitleBar 全部属性），并把 SwipeRefreshLayout 的 `app:layout_constraintTop_toBottomOf="@id/title_bar"` 改为 `app:layout_constraintTop_toTopOf="parent"`：

```xml
    <FrameLayout
        android:id="@+id/title_bar_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:layout_constraintTop_toTopOf="parent">

        <com.qmdeve.liquidglass.widget.LiquidGlassView
            android:id="@+id/title_bar_glass_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <View
            android:id="@+id/title_bar_shell_overlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clickable="false"
            android:focusable="false" />

        <io.legado.app.ui.widget.TitleBar
            android:id="@+id/title_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:attachToActivity="false"
            app:contentLayout="@layout/view_search"
            app:title="@string/discovery" />

    </FrameLayout>
```

注意：原 TitleBar 的 `app:layout_constraintTop_toTopOf="parent"` 移到容器上；TitleBar 自身不再需要约束属性。

- [ ] **Step 2: ExploreFragment 加玻璃绑定**

加 import：

```kotlin
import androidx.core.view.doOnLayout
import io.legado.app.ui.widget.glass.GlassBarHelper
```

`onFragmentCreated` 末尾加 `setupGlass()`。加方法：

```kotlin
    private fun setupGlass() {
        binding.run {
            GlassBarHelper.bind(
                titleBarGlassView, titleBarShellOverlay, titleBar,
                swipeRefreshLayout, 0f, isTopBar = true
            )
            titleBarContainer.doOnLayout {
                swipeRefreshLayout.setPadding(0, titleBarContainer.height, 0, 0)
            }
        }
    }
```

`applyDiscoveryMode` 中 `binding.titleBar.isGone = modern` 之后加：

```kotlin
        binding.titleBarContainer.isGone = modern
```

（现代模式隐藏整个玻璃容器；经典模式恢复显示。`isGone` 为现有扩展。）

- [ ] **Step 3: 编译验证**

Run: `./gradlew :app:compileAppDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/fragment_explore.xml app/src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt
git commit -m "feat(ui): 发现页标题栏玻璃化"
```

---

### Task 10: 全量编译 + 单测 + APK 交付

**Files:**
- 无新增代码；产出 APK。

**Interfaces:**
- Consumes: Task 1-9 全部成果。
- Produces: 可安装 debug APK + 验收清单。

- [ ] **Step 1: 全量单测**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL，RssSourceHelperTest 8 项通过，无回归失败。

- [ ] **Step 2: 全量编译 APK**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 定位 APK**

Run: `find app/build/outputs/apk -name "*.apk"`
Expected: 列出 debug APK 路径。

- [ ] **Step 4: 交付**

向用户报告 APK 绝对路径、已提交 commit 列表、真机验收清单（见设计文档 §4.2），并说明 ExploreFragment 为高风险页需重点验证、UI 效果需真机确认。

---

## Self-Review 记录

- **Spec 覆盖**：玻璃化 4 页（Task 6-9）+ GlassBarHelper（Task 5）；订阅源校验服务/入口/常量/注册（Task 3）+ UI 接线（Task 4）+ 实体 helper/单测（Task 2）；编译/单测/APK/验收（Task 10）。设计文档 §4.2 验收清单在 Task 10 交付。全覆盖。
- **占位符**：无 TBD/TODO；所有代码步骤含完整代码。
- **类型一致性**：`GlassBarHelper.bind` 六参签名在 Task 5 定义、Task 6-9 一致调用；`CheckRssSource.start(context, List<RssSource>)` 在 Task 3 定义、Task 4 调用；`getInvalidGroupNames/removeInvalidGroups/addErrorComment/removeErrorComment` 在 Task 2 定义、Task 3 调用；`articles.first`（Pair 的 MutableList）与 Rss.getArticlesAwait 返回类型一致。
- **已知风险**：ExploreFragment 现代发现模式与现有 padding 体系交互复杂，列为高风险页，真机重点验证；玻璃 UI 效果无法在本机（无模拟器/真机）验证，需用户设备验收。