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