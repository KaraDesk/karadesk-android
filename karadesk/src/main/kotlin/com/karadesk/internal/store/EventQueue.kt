package com.karadesk.internal.store

import android.util.Log
import com.karadesk.KaraDesk
import com.karadesk.internal.model.KaraDeskJson
import com.karadesk.internal.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

internal data class QueuedEvent(
    val id: String,
    val eventName: String,
    val metadata: JsonObject,
    val createdAt: Long,
    val token: String?,
)

internal typealias EventSender = suspend (batch: List<QueuedEvent>, token: String) -> Pair<Int, String?>

internal class EventQueue(
    private val file: File?,
    private val scope: CoroutineScope,
    private val isOnline: () -> Boolean,
    private val sender: EventSender,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var events: List<QueuedEvent> = load()
        private set

    var batchSize = MAX_BATCH
        private set

    private var token: String? = null
    private var timer: Job? = null
    private var saveJob: Job? = null
    private var inFlight = false
    private var failures = 0
    private var waitUntil = 0L

    fun setToken(value: String?) {
        token = value

        if (value == null) {
            return
        }

        if (events.any { it.token == null }) {
            events = events.map { if (it.token == null) it.copy(token = value) else it }
            scheduleSave()
        }

        if (events.isNotEmpty()) {
            schedule(0)
        }
    }

    fun track(name: String, metadata: JsonObject) {
        events = (events + QueuedEvent(UUID.randomUUID().toString(), name, metadata, now(), token)).takeLast(MAX_QUEUE)
        scheduleSave()

        if (sendable().size >= FLUSH_AT) {
            schedule(0)
        } else if (timer?.isActive != true) {
            schedule(FLUSH_INTERVAL_MS)
        }
    }

    fun flushSoon() {
        schedule(0)
    }

    fun flushNow() {
        save()
        schedule(0)
    }

    fun onReconnect() {
        schedule(RECONNECT_DELAY_MS)
    }

    private fun sendable(): List<QueuedEvent> {
        val first = events.firstOrNull { it.token != null }?.token ?: return emptyList()
        return events.filter { it.token == first }.take(batchSize)
    }

    private fun schedule(delayMs: Long) {
        timer?.cancel()
        val wait = max(delayMs, waitUntil - now())

        timer = scope.launch {
            delay(wait)
            timer = null
            flush()
        }
    }

    suspend fun flush() {
        if (inFlight) {
            return
        }

        if (now() < waitUntil) {
            schedule(waitUntil - now() + 10)
            return
        }

        if (!isOnline()) {
            return
        }

        val batch = sendable()
        val batchToken = batch.firstOrNull()?.token ?: return

        inFlight = true
        val (status, retryAfter) = try {
            sender(batch, batchToken)
        } catch (_: Exception) {
            0 to null
        }
        inFlight = false
        handle(batch, status, retryAfter)

        if (sendable().isNotEmpty()) {
            schedule(if (failures == 0) 0 else max(0, waitUntil - now()))
        }
    }

    fun handle(batch: List<QueuedEvent>, status: Int, retryAfter: String?) {
        when (status) {
            in 200..299 -> {
                failures = 0
                batchSize = min(MAX_BATCH, batchSize * 2)
                remove(batch)
            }
            400, 401, 403, 404 -> {
                Log.w(KaraDesk.TAG, "Dropped ${batch.size} events the server refused ($status)")
                remove(batch)
            }
            413 -> {
                if (batch.size == 1) {
                    remove(batch)
                }

                batchSize = max(1, batch.size / 2)
            }
            429 -> {
                failures += 1
                val seconds = retryAfter?.toDoubleOrNull()?.takeIf { it > 0 } ?: RATE_LIMIT_WAIT_S
                waitUntil = now() + (seconds * 1000).toLong()
            }
            else -> {
                failures += 1
                val backoff = min(MAX_BACKOFF_S, 2.0.pow(failures - 1))
                waitUntil = now() + (backoff * 1000 * Random.nextDouble(0.5, 1.5)).toLong()
            }
        }
    }

    private fun remove(batch: List<QueuedEvent>) {
        val ids = batch.map { it.id }.toSet()
        events = events.filterNot { it.id in ids }
        scheduleSave()
    }

    private fun scheduleSave() {
        if (saveJob?.isActive == true) {
            return
        }

        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    fun save() {
        saveJob?.cancel()
        saveJob = null

        val target = file ?: return
        val snapshot = events

        runCatching {
            if (snapshot.isEmpty()) {
                target.delete()
            } else {
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, "${target.name}.tmp")
                temp.writeText(encode(snapshot).toString())
                temp.renameTo(target)
            }
        }.onFailure { Log.w(KaraDesk.TAG, "Couldn't save queued events", it) }
    }

    private fun load(): List<QueuedEvent> {
        val source = file?.takeIf { it.exists() } ?: return emptyList()

        return runCatching {
            (KaraDeskJson.parseToJsonElement(source.readText()) as JsonArray).mapNotNull(::decode).takeLast(MAX_QUEUE)
        }.getOrDefault(emptyList())
    }

    companion object {
        const val MAX_QUEUE = 1000
        const val FLUSH_AT = 20
        const val FLUSH_INTERVAL_MS = 30_000L
        const val MAX_BATCH = 100
        const val MAX_BACKOFF_S = 300.0
        const val RATE_LIMIT_WAIT_S = 30.0
        const val RECONNECT_DELAY_MS = 1_000L
        const val SAVE_DELAY_MS = 500L
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun encode(events: List<QueuedEvent>): JsonArray = buildJsonArray {
            events.forEach { event ->
                add(buildJsonObject {
                    put("id", event.id)
                    put("event_name", event.eventName)
                    put("metadata", event.metadata)
                    put("created_at", event.createdAt)
                    put("token", event.token)
                })
            }
        }

        fun decode(element: JsonElement): QueuedEvent? {
            val item = element as? JsonObject ?: return null

            return QueuedEvent(
                id = item["id"]?.jsonPrimitive?.contentOrNull ?: return null,
                eventName = item["event_name"]?.jsonPrimitive?.contentOrNull ?: return null,
                metadata = item["metadata"] as? JsonObject ?: JsonObject(emptyMap()),
                createdAt = item["created_at"]?.jsonPrimitive?.longOrNull ?: return null,
                token = (item["token"] as? JsonPrimitive)?.contentOrNull,
            )
        }

        fun httpSender(client: ApiClient): EventSender = { batch, token ->
            val body = buildJsonObject {
                put("sent_at", System.currentTimeMillis())
                put("events", buildJsonArray {
                    batch.forEach { event ->
                        add(buildJsonObject {
                            put("id", event.id)
                            put("event_name", event.eventName)
                            put("metadata", event.metadata)
                            put("created_at", event.createdAt)
                        })
                    }
                })
            }

            val request = Request.Builder()
                .url(client.baseUrl.newBuilder().encodedPath("/v1/messenger/events").build())
                .header("Accept", "application/json")
                .header("User-Agent", client.userAgent)
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()

            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { continuation ->
                    val call = client.http.newCall(request)
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            continuation.resume(0 to null)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use { continuation.resume(it.code to it.header("Retry-After")) }
                        }
                    })
                }
            }
        }
    }
}
