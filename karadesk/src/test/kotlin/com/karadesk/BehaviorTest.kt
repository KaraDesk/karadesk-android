package com.karadesk

import androidx.compose.ui.graphics.Color
import com.karadesk.internal.model.AuthorKind
import com.karadesk.internal.model.Message
import com.karadesk.internal.model.MessageAuthor
import com.karadesk.internal.model.MessageKind
import com.karadesk.internal.net.ApiClient
import com.karadesk.internal.net.RealtimeEvent
import com.karadesk.internal.net.Requests
import com.karadesk.internal.push.PushPayload
import com.karadesk.internal.ui.BrandTheme
import com.karadesk.internal.ui.MessageGroup
import com.karadesk.internal.ui.Route
import com.karadesk.internal.ui.Router
import com.karadesk.internal.ui.Tab
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BehaviorTest {
    @Test
    fun recognizesKaraDeskPushData() {
        val payload = PushPayload.from(mapOf("karadesk" to "1", "conversation_id" to "42", "message_id" to "msg_1", "title" to "Leo", "body" to "Hi"))

        assertEquals(42L, payload?.conversationId)
        assertEquals("Leo", payload?.title)
        assertNull(PushPayload.from(mapOf("conversation_id" to "42")))
        assertNull(PushPayload.from(mapOf("karadesk" to "1", "conversation_id" to "abc")))
        assertTrue(KaraDesk.isKaraDeskPush(mapOf("karadesk" to "1", "conversation_id" to "7")))
        assertFalse(KaraDesk.isKaraDeskPush(mapOf("orderId" to "12")))
    }

    @Test
    fun decodesRealtimeEvents() {
        val created = RealtimeEvent.decode(
            """{"type":"message.created","data":{"message":{"id":"m","conversation_id":3,"type":"comment","body":"Hi","author":{"type":"user","name":"Leo"},"created_at":"2026-11-14T09:00:00Z"}}}""",
        )
        val typing = RealtimeEvent.decode("""{"type":"typing","data":{"conversation_id":3,"author":{"type":"bot"}}}""")

        assertEquals("Hi", (created as RealtimeEvent.MessageCreated).message.body)
        assertEquals(AuthorKind.BOT, (typing as RealtimeEvent.Typing).author.kind)
        assertEquals(RealtimeEvent.Ready, RealtimeEvent.decode("""{"type":"ready"}"""))
        assertNull(RealtimeEvent.decode("""{"type":"presence"}"""))
        assertNull(RealtimeEvent.decode("not json"))
    }

    @Test
    fun buildsRequestsLikeTheIosSdk() {
        val send = Requests.send(9, "Hello", listOf("a1"))
        val body = send.body as JsonObject

        assertEquals("/v1/messenger/conversations/9/messages", send.path)
        assertEquals("Hello", body["body"]?.jsonPrimitive?.content)
        assertEquals("a1", ((body["attachments"] as JsonArray)[0] as JsonObject)["asset_id"]?.jsonPrimitive?.content)
        assertFalse(Requests.collections(1).requiresSession)
        assertEquals("/v1/messenger/push-token/tok:abc", Requests.removePushToken("tok:abc").path)
    }

    @Test
    fun mapsErrors() {
        assertTrue(ApiClient.errorFor(401, """{"message":"Identity verification failed"}""") is KaraDeskException.IdentityVerificationFailed)
        assertTrue(ApiClient.errorFor(404, """{"message":"Not here"}""") is KaraDeskException.Unavailable)
        assertEquals("Boom", ApiClient.errorFor(500, """{"message":"Boom"}""").message)
    }

    @Test
    fun picksReadableColors() {
        assertEquals(Color.White, BrandTheme.readableForeground("#111111"))
        assertEquals(Color.Black, BrandTheme.readableForeground("#FFE600"))
        assertEquals(Color.White, BrandTheme.readableForeground("not a color"))
        assertEquals(BrandTheme.parseHex("#abc"), BrandTheme.parseHex("#AABBCC"))
    }

    @Test
    fun groupsMessagesByAuthorWithinFiveMinutes() {
        val leo = MessageAuthor(AuthorKind.USER, "Leo", null)
        val me = MessageAuthor(AuthorKind.CONTACT, null, null)
        fun message(id: String, author: MessageAuthor, seconds: Long, kind: MessageKind = MessageKind.Comment) =
            Message(id, 1, kind, author, "x", emptyList(), null, Instant.ofEpochSecond(seconds))

        val groups = MessageGroup.make(
            listOf(
                message("1", leo, 0),
                message("2", leo, 60),
                message("3", leo, 500),
                message("4", me, 510),
                message("5", leo, 520, MessageKind.Other("note")),
            ),
        )

        assertEquals(listOf(listOf("1", "2"), listOf("3"), listOf("4")), groups.map { group -> group.messages.map { it.id } })
    }

    @Test
    fun routerOpensDestinationsAndGoesBack() {
        val router = Router(KaraDesk.Destination.Conversation(5), standalone = false)

        assertEquals(Tab.MESSAGES, router.tab)
        assertEquals(Route.Chat(5, null), router.current)
        assertTrue(router.pop())
        assertEquals(Tab.MESSAGES, router.tab)
        assertTrue(router.pop())
        assertEquals(Tab.HOME, router.tab)
        assertFalse(router.pop())

        val standalone = Router(KaraDesk.Destination.Article(3), standalone = true)
        assertEquals(Route.Article(null, 3), standalone.current)
        assertFalse(standalone.canGoBack)

        standalone.replaceTop(Route.Chat(null, null))
        assertEquals(Route.Chat(null, null), standalone.current)
    }
}
