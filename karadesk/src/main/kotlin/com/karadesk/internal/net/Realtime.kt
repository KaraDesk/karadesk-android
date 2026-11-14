package com.karadesk.internal.net

import com.karadesk.internal.model.Conversation
import com.karadesk.internal.model.KaraDeskJson
import com.karadesk.internal.model.Message
import com.karadesk.internal.model.MessageAuthor
import com.karadesk.internal.model.long
import com.karadesk.internal.model.obj
import com.karadesk.internal.model.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

internal sealed interface RealtimeEvent {
    data object Ready : RealtimeEvent
    data class MessageCreated(val message: Message) : RealtimeEvent
    data class MessageUpdated(val message: Message) : RealtimeEvent
    data class MessageDeleted(val conversationId: Long, val messageId: String) : RealtimeEvent
    data class ConversationChanged(val conversation: Conversation) : RealtimeEvent
    data class Typing(val conversationId: Long, val author: MessageAuthor) : RealtimeEvent
    data object Unauthorized : RealtimeEvent

    companion object {
        fun decode(text: String): RealtimeEvent? {
            val envelope = runCatching { KaraDeskJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
            val data = envelope.obj("data")

            return when (envelope.str("type")) {
                "ready" -> Ready
                "message.created" -> data?.obj("message")?.let(Message::from)?.let(::MessageCreated)
                "message.updated" -> data?.obj("message")?.let(Message::from)?.let(::MessageUpdated)
                "message.deleted" -> {
                    val conversationId = data?.long("conversation_id") ?: return null
                    val messageId = data.str("message_id") ?: return null
                    MessageDeleted(conversationId, messageId)
                }
                "conversation.created", "conversation.updated" ->
                    data?.obj("conversation")?.let(Conversation::from)?.let(::ConversationChanged)
                "typing" -> {
                    val conversationId = data?.long("conversation_id") ?: return null
                    val author = data.obj("author")?.let(MessageAuthor::from) ?: return null
                    Typing(conversationId, author)
                }
                else -> null
            }
        }
    }
}

internal class RealtimeConnection(
    baseUrl: HttpUrl,
    token: String,
    private val http: OkHttpClient,
) {
    private val url: String = baseUrl.newBuilder()
        .encodedPath("/v1/realtime")
        .addQueryParameter("kind", "messenger")
        .addQueryParameter("token", token)
        .build()
        .toString()
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<RealtimeEvent>(capacity = 100)
    private var socket: WebSocket? = null
    private var loop: Job? = null
    private var attempt = 0

    val events: Flow<RealtimeEvent> = channel.receiveAsFlow()

    fun start() {
        if (loop != null) {
            return
        }

        loop = scope.launch { run() }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        channel.close()
        scope.cancel()
    }

    fun sendTyping(conversationId: Long) {
        send(buildJsonObject {
            put("type", "typing")
            put("conversation_id", conversationId)
        })
    }

    private suspend fun run() {
        while (scope.isActive) {
            val closed = Channel<Int?>(Channel.CONFLATED)
            val listener = object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    attempt = 0
                    RealtimeEvent.decode(text)?.let { channel.trySend(it) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                    closed.trySend(code)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.trySend(code)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed.trySend(null)
                }
            }

            val opened = http.newWebSocket(Request.Builder().url(url).build(), listener)
            socket = opened

            val heartbeat = scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    send(buildJsonObject { put("type", "ping") })
                }
            }

            val code = closed.receive()
            heartbeat.cancel()

            if (!scope.isActive) {
                return
            }

            if (code == UNAUTHORIZED_CLOSE_CODE) {
                channel.trySend(Unauthorized)
                channel.close()
                return
            }

            attempt += 1
            delay(backoff(attempt))
        }
    }

    private fun send(payload: JsonObject) {
        socket?.send(payload.toString())
    }

    companion object {
        const val UNAUTHORIZED_CLOSE_CODE = 4401
        private const val NORMAL_CLOSURE = 1000
        private const val HEARTBEAT_MS = 25_000L

        fun backoff(attempt: Int): Long {
            val base = min(30.0, 2.0.pow(min(attempt, 5)))
            val jitter = Random.nextDouble(0.0, 0.3) * base
            return ((base + jitter) * 1000).toLong()
        }
    }
}

private val Unauthorized = RealtimeEvent.Unauthorized
