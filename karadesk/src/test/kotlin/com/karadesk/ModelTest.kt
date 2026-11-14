package com.karadesk

import com.karadesk.internal.model.AuthorKind
import com.karadesk.internal.model.BackgroundKind
import com.karadesk.internal.model.BootResponse
import com.karadesk.internal.model.Conversation
import com.karadesk.internal.model.ConversationState
import com.karadesk.internal.model.HelpCollection
import com.karadesk.internal.model.HomeCard
import com.karadesk.internal.model.KaraDeskJson
import com.karadesk.internal.model.Message
import com.karadesk.internal.model.MessageAttachment
import com.karadesk.internal.model.MessageKind
import com.karadesk.internal.model.ThemeMode
import com.karadesk.internal.model.withTotals
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    private fun json(text: String) = KaraDeskJson.parseToJsonElement(text) as JsonObject

    @Test
    fun bootDecodesSettingsWithDefaultsAndSkipsUnknownCards() {
        val boot = BootResponse.from(
            json(
                """
                {
                  "token": "tok", "expires_at": "2026-11-14T10:00:00.000Z",
                  "app": {"id": "app_1", "name": "Acme Android"},
                  "space": {"name": "Acme", "avatar": null},
                  "identity": {"type": "user", "verified": true},
                  "team": [{"id": 2, "name": "Leo Park", "avatar": "https://x/leo.png", "last_active_at": "2026-11-14T09:59:00Z"}, {"name": "No id"}],
                  "settings": {
                    "color": "#4F46E5", "theme": "dark", "logo_url": "", "header_text": "light",
                    "background": {"type": "gradient", "gradient_from": "#111111", "gradient_to": "#222222"},
                    "background_image_url": null,
                    "rules": {"visitors": {"allow_new": false}, "users": {"single_open": true, "closed_reply_days": 7}},
                    "home_cards": [
                      {"id": "messages", "type": "messages"},
                      {"id": "x", "type": "poll"},
                      {"id": "h", "type": "help", "help_center": {"id": 3, "name": "Help"}, "articles": [{"id": 9, "title": "Hello"}]},
                      {"id": "l", "type": "link", "title": "Status"}
                    ],
                    "greeting": "Hi",
                    "attachments": {"enabled": true, "max_bytes": 1024, "accept": ["image/png"]}
                  }
                }
                """.trimIndent(),
            ),
        )

        assertNotNull(boot)
        boot!!
        assertTrue(boot.isIdentified)
        assertEquals(listOf("Leo Park"), boot.team.map { it.name })
        assertEquals(ThemeMode.DARK, boot.settings.theme)
        assertNull(boot.settings.logoUrl)
        assertEquals(BackgroundKind.GRADIENT, boot.settings.background.kind)
        assertFalse(boot.settings.rules(identified = false).allowNew)
        assertTrue(boot.settings.rules(identified = true).singleOpen)
        assertEquals(7, boot.settings.rules(identified = true).closedReplyDays)
        assertEquals(listOf("messages", "h"), boot.settings.homeCards.map { it.id })
        assertEquals(9L, (boot.settings.homeCards[1] as HomeCard.Help).articles.single().id)
        assertTrue(boot.settings.showTeam)
        assertEquals("", boot.settings.intro)
        assertEquals(1024L, boot.settings.attachments.maxBytes)
    }

    @Test
    fun conversationFallsBackToUnreadCount() {
        val conversation = Conversation.from(
            json("""{"id": 4, "state": "snoozed", "unread_count": 2, "created_at": "2026-11-14T09:00:00Z", "last_message": {"id": "m", "author_type": "bot", "body": "Hi", "created_at": "2026-11-14T09:01:00Z"}}"""),
        )!!

        assertEquals(ConversationState.SNOOZED, conversation.state)
        assertTrue(conversation.unread)
        assertEquals(AuthorKind.BOT, conversation.lastMessage?.authorType)
        assertEquals(conversation.lastMessage?.createdAt, conversation.lastMessage?.createdAt)
    }

    @Test
    fun messagesKeepKnownAttachmentsAndKinds() {
        val message = Message.from(
            json(
                """
                {"id": "m1", "conversation_id": 4, "type": "reply_time", "body": "",
                 "author": {"type": "bot"}, "created_at": "2026-11-14T09:00:00.123Z",
                 "attachments": [
                   {"type": "file", "asset_id": "a1", "url": "https://x/photo.jpg", "content_type": "image/jpeg", "byte_size": 10},
                   {"type": "file", "asset_id": "a2"},
                   {"type": "article", "article_id": 5, "help_center_id": 1, "title": "Install"},
                   {"type": "sticker"}
                 ]}
                """.trimIndent(),
            ),
        )!!

        assertEquals(MessageKind.ReplyTime, message.kind)
        assertEquals(2, message.attachments.size)
        assertTrue((message.attachments[0] as MessageAttachment.File).isImage)
        assertTrue(message.isRenderable)
        assertEquals(AuthorKind.BOT, message.author.kind)
    }

    @Test
    fun notesAreNotRendered() {
        val note = Message.from(json("""{"id": "n", "conversation_id": 1, "type": "note", "body": "x", "author": {"type": "user"}, "created_at": "2026-11-14T09:00:00Z"}"""))!!
        assertFalse(note.isRenderable)
        assertTrue(note.isNote)
    }

    @Test
    fun collectionTotalsIncludeChildrenAndHideEmpty() {
        val collections = listOf(
            HelpCollection(1, null, "Billing", null, 1, 1),
            HelpCollection(2, 1, "Invoices", null, 0, 2),
            HelpCollection(3, null, "Empty", null, 0, 0),
            HelpCollection(4, null, "Start", null, 0, 1),
        ).withTotals()

        assertEquals(listOf(2L, 4L, 1L), collections.map { it.id })
        assertEquals(3, collections.first { it.id == 1L }.articleCount)
    }
}
