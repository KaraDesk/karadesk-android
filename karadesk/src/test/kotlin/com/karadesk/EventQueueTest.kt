package com.karadesk

import com.karadesk.internal.store.EventMetadata
import com.karadesk.internal.store.EventQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class EventQueueTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val directory: File = Files.createTempDirectory("karadesk-events").toFile()
    private val sent = mutableListOf<Pair<Int, String>>()
    private var status = 200
    private var online = true

    private fun queue() = EventQueue(
        file = File(directory, "events.json"),
        scope = scope,
        isOnline = { online },
        sender = { batch, token ->
            sent += batch.size to token
            status to null
        },
        now = { scope.testScheduler.currentTime },
    )

    private val empty = JsonObject(emptyMap())

    @Test
    fun eventsWaitForASessionThenTakeItsToken() {
        val queue = queue()
        queue.track("Opened app", empty)
        assertNull(queue.events.first().token)

        queue.setToken("token-a")
        queue.track("Viewed plan", empty)
        assertEquals(listOf("token-a", "token-a"), queue.events.map { it.token })
    }

    @Test
    fun eventsKeepTheTokenTheyWereTrackedWith() {
        val queue = queue()
        queue.setToken("token-a")
        queue.track("Before logout", empty)
        queue.setToken(null)
        queue.track("After logout", empty)
        queue.setToken("token-b")
        assertEquals(listOf("token-a", "token-b"), queue.events.map { it.token })
    }

    @Test
    fun sendsInBatchesAfterTwentyEventsOrThirtySeconds() {
        val queue = queue()
        queue.setToken("token-a")
        scope.runCurrent()

        repeat(5) { queue.track("Small $it", empty) }
        scope.advanceTimeBy(29_000)
        assertEquals(0, sent.size)
        scope.advanceTimeBy(1_500)
        assertEquals(listOf(5 to "token-a"), sent)

        repeat(20) { queue.track("Burst $it", empty) }
        scope.runCurrent()
        assertEquals(20, sent.last().first)
        assertEquals(0, queue.events.size)
    }

    @Test
    fun keepsEventsWhileOfflineAndAcrossRelaunches() {
        online = false
        val queue = queue()
        queue.setToken("token-a")
        repeat(25) { queue.track("Offline $it", empty) }
        scope.advanceTimeBy(60_000)
        assertEquals(0, sent.size)
        queue.save()

        val reopened = queue()
        assertEquals(25, reopened.events.size)
        assertEquals("token-a", reopened.events.first().token)

        online = true
        reopened.onReconnect()
        scope.advanceTimeBy(2_000)
        assertEquals(25, sent.single().first)
    }

    @Test
    fun keepsAtMostAThousandEvents() {
        val queue = queue()
        repeat(1005) { queue.track("Event $it", empty) }
        assertEquals(EventQueue.MAX_QUEUE, queue.events.size)
        assertEquals("Event 5", queue.events.first().eventName)
    }

    @Test
    fun responsesDecideWhatHappensToABatch() {
        val queue = queue()
        queue.setToken("token-a")
        repeat(8) { queue.track("Event $it", empty) }
        val batch = queue.events.take(4)

        queue.handle(batch, 500, null)
        assertEquals(8, queue.events.size)

        queue.handle(batch, 413, null)
        assertEquals(8, queue.events.size)
        assertEquals(2, queue.batchSize)

        queue.handle(batch, 200, null)
        assertEquals(4, queue.events.size)
        assertEquals(4, queue.batchSize)

        queue.handle(queue.events.take(2), 401, null)
        assertEquals(2, queue.events.size)

        queue.handle(listOf(queue.events.first()), 413, null)
        assertEquals(1, queue.events.size)
    }

    @Test
    fun retriesServerErrorsWithBackoff() {
        val queue = queue()
        queue.setToken("token-a")
        status = 503
        repeat(20) { queue.track("Event $it", empty) }
        scope.runCurrent()
        assertEquals(1, sent.size)

        status = 200
        scope.advanceTimeBy(2_000)
        assertEquals(2, sent.size)
        assertEquals(0, queue.events.size)
    }

    @Test
    fun metadataBecomesJson() {
        val json = EventMetadata.toJson(
            mapOf(
                "plan" to "Pro",
                "seats" to 12,
                "annual" to true,
                "coupon" to null,
                "items" to listOf(mapOf("sku" to "A-100", "quantity" to 2)),
                "unsupported" to Any(),
            ),
        )

        assertEquals(JsonPrimitive("Pro"), json["plan"])
        assertEquals(JsonPrimitive(12), json["seats"])
        assertEquals(JsonPrimitive(true), json["annual"])
        assertEquals(JsonNull, json["coupon"])
        assertEquals(
            JsonArray(listOf(JsonObject(mapOf("sku" to JsonPrimitive("A-100"), "quantity" to JsonPrimitive(2))))),
            json["items"],
        )
        assertNull(json["unsupported"])
    }
}
