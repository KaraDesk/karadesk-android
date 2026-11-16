package com.karadesk

import com.karadesk.internal.model.InputKind
import com.karadesk.internal.model.InteractionResult
import com.karadesk.internal.model.KaraDeskJson
import com.karadesk.internal.model.Message
import com.karadesk.internal.model.MessageComponent
import com.karadesk.internal.net.RealtimeEvent
import com.karadesk.internal.net.Requests
import com.karadesk.internal.ui.inputValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentTest {
    private fun json(text: String) = KaraDeskJson.parseToJsonElement(text) as JsonObject

    private fun karaMessage(components: String) = Message.from(
        json(
            """
            {"id": "msg_1", "conversation_id": 7, "type": "comment", "body": "",
             "author": {"type": "bot", "id": "kara", "name": "Kara", "avatar": null},
             "created_at": "2026-11-16T09:00:00Z", "attachments": [],
             "components": $components}
            """.trimIndent(),
        ),
    )!!

    @Test
    fun decodesButtons() {
        val message = karaMessage(
            """
            [{"id": "c1", "type": "buttons", "fallback_text": "Did that help?", "prompt": "Did that help?", "layout": "row", "extra": 1,
              "answer": {"button_id": "yes", "value": "yes", "answered_at": "2026-11-16T09:01:00Z"},
              "buttons": [
                {"id": "yes", "label": "Yes", "value": "yes", "style": "primary", "echo": true, "action": {"type": "resolve"}},
                {"id": "docs", "label": "Docs", "value": null, "style": "secondary", "echo": false, "action": {"type": "open_url", "url": "https://x.dev"}},
                {"label": "No id"}
              ]}]
            """.trimIndent(),
        )

        val buttons = message.components.single() as MessageComponent.Buttons
        assertTrue(buttons.row)
        assertEquals("Did that help?", buttons.prompt)
        assertEquals(listOf("yes", "docs"), buttons.buttons.map { it.id })
        assertTrue(buttons.buttons[0].primary)
        assertTrue(buttons.buttons[0].echo)
        assertFalse(buttons.buttons[0].action.opensUrl)
        assertTrue(buttons.buttons[1].action.opensUrl)
        assertEquals("https://x.dev", buttons.buttons[1].action.url)
        assertEquals("yes", buttons.answer?.buttonId)
        assertTrue(message.isRenderable)
        assertEquals("Did that help?", message.previewText)
    }

    @Test
    fun decodesArticlesInputAndCard() {
        val message = karaMessage(
            """
            [
              {"id": "a", "type": "articles", "fallback_text": null, "answer": null, "articles": [
                {"id": 12, "title": "Reset password", "description": null, "url": "https://help.x/12", "help_center_id": 3},
                {"id": "ext-1", "title": "External", "description": "Elsewhere", "url": "https://x/ext", "help_center_id": null}
              ]},
              {"id": "i", "type": "input", "label": "Plan", "kind": "select", "placeholder": null, "required": true,
               "options": [{"value": "pro", "label": "Pro"}, {"value": "basic", "label": "Basic"}], "save_to": "attributes.plan",
               "echo": true, "action": {"type": "goto", "step": "next"},
               "answer": {"button_id": null, "value": "pro", "answered_at": "2026-11-16T09:01:00Z"}},
              {"id": "n", "type": "input", "label": "Seats", "kind": "number", "answer": {"button_id": null, "value": 4, "answered_at": "2026-11-16T09:01:00Z"}},
              {"id": "k", "type": "card", "title": "Upgrade", "body": "**Faster** replies", "image_url": "https://x/img.png"}
            ]
            """.trimIndent(),
        )

        assertEquals(4, message.components.size)

        val articles = message.components[0] as MessageComponent.Articles
        assertEquals(12L, articles.articles[0].articleId)
        assertEquals(3L, articles.articles[0].helpCenterId)
        assertNull(articles.articles[1].articleId)
        assertNull(articles.articles[1].helpCenterId)

        val select = message.components[1] as MessageComponent.Input
        assertEquals(InputKind.SELECT, select.kind)
        assertTrue(select.required)
        assertEquals("attributes.plan", select.saveTo)
        assertEquals("next", select.action.step)
        assertEquals("Pro", select.answerLabel)

        val number = message.components[2] as MessageComponent.Input
        assertEquals(InputKind.NUMBER, number.kind)
        assertEquals("4", number.answerLabel)
        assertTrue(number.options.isEmpty())

        val card = message.components[3] as MessageComponent.Card
        assertEquals("Upgrade", card.title)
        assertEquals("https://x/img.png", card.imageUrl)
        assertTrue(card.buttons.isEmpty())
        assertNull(card.answer)
    }

    @Test
    fun unknownComponentsKeepFallbackAndDoNotBreakDecoding() {
        val message = karaMessage(
            """
            [
              {"id": "u1", "type": "carousel", "fallback_text": "Pick a plan", "slides": [1, 2]},
              {"id": "u2", "type": "poll", "fallback_text": null},
              {"type": "buttons"},
              "garbage"
            ]
            """.trimIndent(),
        )

        assertEquals(2, message.components.size)

        val first = message.components[0] as MessageComponent.Unknown
        assertEquals("carousel", first.type)
        assertTrue(first.isRenderable)
        assertFalse(message.components[1].isRenderable)
        assertTrue(message.isRenderable)
        assertEquals("Pick a plan", message.previewText)

        val silent = karaMessage("""[{"id": "u2", "type": "poll"}]""")
        assertFalse(silent.isRenderable)
    }

    @Test
    fun missingOrInvalidComponentsDecodeAsEmpty() {
        val missing = Message.from(
            json("""{"id": "m", "conversation_id": 1, "body": "Hi", "author": {"type": "user"}, "created_at": "2026-11-16T09:00:00Z"}"""),
        )!!
        assertTrue(missing.components.isEmpty())
        assertTrue(missing.isRenderable)

        val nulled = karaMessage("null")
        assertTrue(nulled.components.isEmpty())
        assertFalse(nulled.isRenderable)
    }

    @Test
    fun decodesInteractionResultAndUpdatedEvents() {
        val result = InteractionResult.from(
            json(
                """
                {"message": {"id": "msg_1", "conversation_id": 7, "body": "", "author": {"type": "bot", "id": "kara", "name": "Kara"},
                             "created_at": "2026-11-16T09:00:00Z",
                             "components": [{"id": "c1", "type": "buttons", "layout": "stack", "buttons": [],
                                             "answer": {"button_id": "yes", "value": null, "answered_at": "2026-11-16T09:01:00Z"}}]},
                 "reply": null}
                """.trimIndent(),
            ),
        )!!
        assertEquals("yes", result.message.components.single().answer?.buttonId)
        assertNull(result.reply)

        val event = RealtimeEvent.decode(
            """{"type": "message.updated", "data": {"message": {"id": "msg_1", "conversation_id": 7, "author": {"type": "bot"}, "created_at": "2026-11-16T09:00:00Z", "components": [{"id": "c1", "type": "card", "title": "T"}]}}}""",
        ) as RealtimeEvent.MessageUpdated
        assertEquals("T", (event.message.components.single() as MessageComponent.Card).title)

        val typing = RealtimeEvent.decode(
            """{"type": "typing", "data": {"conversation_id": 7, "author": {"type": "bot", "id": "kara", "name": "Kara", "avatar": null}}}""",
        ) as RealtimeEvent.Typing
        assertEquals("Kara", typing.author.name)
        assertEquals(com.karadesk.internal.model.AuthorKind.BOT, typing.author.kind)
    }

    @Test
    fun buildsInteractionRequests() {
        val button = Requests.interact(7, "msg_1", "c1", "yes", null)
        val body = button.body as JsonObject
        assertEquals("/v1/messenger/conversations/7/messages/msg_1/interactions", button.path)
        assertEquals("c1", body["component_id"]?.jsonPrimitive?.content)
        assertEquals("yes", body["button_id"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("value"))

        val number = Requests.interact(7, "msg_1", "n", null, inputValue(InputKind.NUMBER, "42")).body as JsonObject
        assertEquals(42L, number["value"]?.jsonPrimitive?.longOrNull)
        assertFalse(number.containsKey("button_id"))

        assertEquals(JsonPrimitive("42"), inputValue(InputKind.TEXT, "42"))
        assertEquals(JsonPrimitive(2.5), inputValue(InputKind.NUMBER, "2.5"))
        assertEquals(JsonPrimitive("abc"), inputValue(InputKind.NUMBER, "abc"))
    }
}
