package io.github.toanhp123.finote.ui.settings.logs.logic

import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.service.masked
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val prettyJson = Json { prettyPrint = true }

/**
 * A corpus-fixture item for [log], digits masked. Lists only id, package, time, channel, title and content:
 * parsed fields, hash and failure reason hold real values and never leave the device.
 */
fun corpusSampleJson(log: NotificationLog): String {
    val sample = log.masked()
    val item =
        buildJsonObject {
            put("id", "real_" + sample.id.take(ID_PREFIX_LENGTH))
            put("packageName", sample.packageName)
            put("postTime", sample.postTime)
            sample.channelId?.let { put("channelId", it) }
            put("title", sample.title)
            put("content", sample.content)
            put("expected", buildJsonObject { put("shouldIgnore", false) })
        }
    return prettyJson.encodeToString(JsonObject.serializer(), item)
}

private const val ID_PREFIX_LENGTH = 8
