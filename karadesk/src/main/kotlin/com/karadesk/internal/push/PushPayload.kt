package com.karadesk.internal.push

import android.content.Intent
import android.os.Bundle

internal data class PushPayload(
    val conversationId: Long,
    val messageId: String?,
    val title: String?,
    val body: String?,
) {
    companion object {
        const val MARKER = "karadesk"
        const val CONVERSATION_ID = "conversation_id"
        const val MESSAGE_ID = "message_id"
        const val TITLE = "title"
        const val BODY = "body"

        fun from(data: Map<String, String?>): PushPayload? {
            if (data[MARKER] != "1") {
                return null
            }

            val id = data[CONVERSATION_ID]?.toLongOrNull() ?: return null
            return PushPayload(id, data[MESSAGE_ID], data[TITLE], data[BODY])
        }

        fun from(extras: Bundle?): PushPayload? {
            extras ?: return null

            if (extras.getString(MARKER) != "1") {
                return null
            }

            val id = extras.getString(CONVERSATION_ID)?.toLongOrNull()
                ?: extras.getLong(CONVERSATION_ID, -1L).takeIf { it >= 0 }
                ?: return null

            return PushPayload(id, extras.getString(MESSAGE_ID), extras.getString(TITLE), extras.getString(BODY))
        }

        fun strip(intent: Intent) {
            listOf(MARKER, CONVERSATION_ID, MESSAGE_ID, TITLE, BODY).forEach(intent::removeExtra)
        }
    }
}
