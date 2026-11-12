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