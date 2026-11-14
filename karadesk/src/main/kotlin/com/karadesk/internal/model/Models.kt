package com.karadesk.internal.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant

internal data class Person(
    val id: Long,
    val name: String?,
    val avatarUrl: String?,
    val lastActiveAt: Instant?,
) {
    fun isActive(now: Instant = Instant.now()): Boolean {
        val last = lastActiveAt ?: return false
        return Duration.between(last, now).seconds <= 15 * 60
    }

    companion object {
        fun from(json: JsonObject): Person? {
            val id = json.long("id") ?: return null
            return Person(id, json.str("name"), json.url("avatar"), json.instant("last_active_at"))
        }
    }
}

internal data class ContactInfo(
    val id: String,
    val role: String,
    val name: String?,
    val email: String?,
    val avatarUrl: String?,
) {
    companion object {
        fun from(json: JsonObject): ContactInfo? {
            val id = json.str("id") ?: return null
            return ContactInfo(id, json.str("role", "lead"), json.str("name"), json.str("email"), json.url("avatar"))
        }
    }
}

internal data class BootResponse(
    val token: String,
    val expiresAt: Instant,
    val appId: String,
    val appName: String,
    val spaceName: String,
    val spaceAvatarUrl: String?,
    val settings: MessengerSettings,
    val identityType: String,
    val team: List<Person>,
    val contact: ContactInfo?,
) {
    val isIdentified: Boolean
        get() = identityType == "user"

    companion object {
        fun from(json: JsonObject): BootResponse? {
            val token = json.str("token") ?: return null
            val expiresAt = json.instant("expires_at") ?: return null
            val app = json.obj("app") ?: return null
            val space = json.obj("space") ?: return null
            val settings = json.obj("settings")?.let(MessengerSettings::from) ?: return null

            return BootResponse(
                token = token,
                expiresAt = expiresAt,
                appId = app.str("id", ""),
                appName = app.str("name", ""),
                spaceName = space.str("name", ""),
                spaceAvatarUrl = space.url("avatar"),
                settings = settings,
                identityType = json.obj("identity")?.str("type") ?: "visitor",
                team = json.list("team", Person::from),
                contact = json.obj("contact")?.let(ContactInfo::from),
            )
        }
    }
}

internal enum class ConversationState { OPEN, CLOSED, SNOOZED }

internal enum class AuthorKind {
    CONTACT, USER, BOT;

    companion object {
        fun of(value: String?): AuthorKind = when (value) {
            "contact" -> CONTACT
            "bot" -> BOT
            else -> USER
        }
    }
}

internal data class ConversationPreview(
    val id: String,
    val authorType: AuthorKind,
    val authorName: String?,
    val body: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(json: JsonObject): ConversationPreview? {
            val id = json.str("id") ?: return null
            val createdAt = json.instant("created_at") ?: return null
            return ConversationPreview(id, AuthorKind.of(json.str("author_type")), json.str("author_name"), json.str("body", ""), createdAt)
        }

        fun of(message: Message): ConversationPreview =
            ConversationPreview(message.id, message.author.kind, message.author.name, message.body, message.createdAt)
    }
}

internal data class Conversation(
    val id: Long,
    val state: ConversationState,
    val title: String?,
    val mergedIntoId: Long?,
    val lastMessage: ConversationPreview?,
    val lastMessageAt: Instant?,
    val unread: Boolean,
    val unreadCount: Int,
    val closedAt: Instant?,
    val createdAt: Instant,
    val assignee: Person?,
) {
    val sortDate: Instant
        get() = lastMessageAt ?: createdAt

    companion object {
        fun from(json: JsonObject): Conversation? {
            val id = json.long("id") ?: return null
            val createdAt = json.instant("created_at") ?: return null
            val unreadCount = json.int("unread_count", 0)

            return Conversation(
                id = id,
                state = when (json.str("state")) {
                    "closed" -> ConversationState.CLOSED
                    "snoozed" -> ConversationState.SNOOZED
                    else -> ConversationState.OPEN
                },
                title = json.str("title"),
                mergedIntoId = json.long("merged_into_id"),
                lastMessage = json.obj("last_message")?.let(ConversationPreview::from),
                lastMessageAt = json.instant("last_message_at"),
                unread = json.bool("unread", unreadCount > 0),
                unreadCount = unreadCount,
                closedAt = json.instant("closed_at"),
                createdAt = createdAt,
                assignee = json.obj("assignee")?.let(Person::from),
            )
        }
    }
}

internal data class ConversationThread(val conversation: Conversation, val messages: List<Message>) {
    companion object {
        fun from(json: JsonObject): ConversationThread? {
            val conversation = json.obj("conversation")?.let(Conversation::from) ?: return null
            return ConversationThread(conversation, json.list("messages", Message::from))
        }
    }
}

internal sealed interface MessageKind {
    data object Comment : MessageKind
    data object ReplyTime : MessageKind
    data object Csat : MessageKind
    data class Other(val raw: String) : MessageKind

    companion object {
        fun of(value: String): MessageKind = when (value) {
            "comment" -> Comment
            "reply_time" -> ReplyTime
            "csat" -> Csat
            else -> Other(value)
        }
    }
}

internal data class MessageAuthor(val kind: AuthorKind, val name: String?, val avatarUrl: String?) {
    companion object {
        fun from(json: JsonObject): MessageAuthor =
            MessageAuthor(AuthorKind.of(json.str("type")), json.str("name"), json.url("avatar"))
    }
}

internal data class MessageMetadata(
    val rating: Int?,
    val remark: String?,
    val ratedAt: Instant?,
    val expiresAt: Instant?,
    val lockUntil: Instant?,
    val teammateName: String?,
    val open: Boolean?,
    val replyTime: String?,
    val nextOpenAt: Instant?,
) {
    companion object {
        fun from(json: JsonObject): MessageMetadata = MessageMetadata(
            rating = json.int("rating"),
            remark = json.str("remark"),
            ratedAt = json.instant("rated_at"),
            expiresAt = json.instant("expires_at"),
            lockUntil = json.instant("lock_until"),
            teammateName = json.str("teammate_name"),
            open = json.bool("open"),
            replyTime = json.str("reply_time"),
            nextOpenAt = json.instant("next_open_at"),
        )
    }
}

internal sealed interface MessageAttachment {
    data class File(
        val assetId: String,
        val name: String,
        val contentType: String,
        val byteSize: Long,
        val url: String,
    ) : MessageAttachment {
        val isImage: Boolean
            get() = contentType in IMAGE_TYPES
    }

    data class Article(
        val articleId: Long,
        val helpCenterId: Long?,
        val title: String,
        val description: String?,
    ) : MessageAttachment

    data class Link(val url: String, val title: String?) : MessageAttachment

    companion object {
        val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp", "image/heic")

        fun from(json: JsonObject): MessageAttachment? = when (json.str("type")) {
            "file" -> {
                val url = json.url("url") ?: return null
                File(
                    assetId = json.str("asset_id") ?: return null,
                    name = json.str("name") ?: url.substringAfterLast('/').substringBefore('?'),
                    contentType = json.str("content_type", "application/octet-stream"),
                    byteSize = json.long("byte_size") ?: 0,
                    url = url,
                )
            }
            "article" -> Article(
                articleId = json.long("article_id") ?: return null,
                helpCenterId = json.long("help_center_id"),
                title = json.str("title", ""),
                description = json.str("description"),
            )
            "link" -> Link(json.url("url") ?: return null, json.str("title"))
            else -> null
        }
    }
}

internal data class Message(
    val id: String,
    val conversationId: Long,
    val kind: MessageKind,
    val author: MessageAuthor,
    val body: String,
    val attachments: List<MessageAttachment>,
    val metadata: MessageMetadata?,
    val createdAt: Instant,
) {
    val isFromContact: Boolean
        get() = author.kind == AuthorKind.CONTACT

    val isNote: Boolean
        get() = kind == MessageKind.Other("note")

    val isRenderable: Boolean
        get() = when (kind) {
            MessageKind.Comment, MessageKind.ReplyTime -> body.isNotEmpty() || attachments.isNotEmpty()
            MessageKind.Csat -> metadata != null
            is MessageKind.Other -> false
        }

    companion object {
        fun from(json: JsonObject): Message? {
            val id = json.str("id") ?: return null
            val conversationId = json.long("conversation_id") ?: return null
            val author = json.obj("author")?.let(MessageAuthor::from) ?: return null
            val createdAt = json.instant("created_at") ?: return null

            return Message(
                id = id,
                conversationId = conversationId,
                kind = MessageKind.of(json.str("type", "comment")),
                author = author,
                body = json.str("body", ""),
                attachments = json.list("attachments", MessageAttachment::from),
                metadata = json.obj("metadata")?.let(MessageMetadata::from),
                createdAt = createdAt,
            )
        }
    }
}

internal data class UploadedFile(
    val id: String,
    val name: String,
    val contentType: String,
    val byteSize: Long,
    val status: String,
) {
    companion object {
        fun from(json: JsonObject): UploadedFile? = UploadedFile(
            id = json.str("id") ?: return null,
            name = json.str("name", ""),
            contentType = json.str("content_type", "application/octet-stream"),
            byteSize = json.long("byte_size") ?: 0,
            status = json.str("status", ""),
        )
    }
}

internal data class UploadTicket(
    val asset: UploadedFile,
    val method: String,
    val url: String,
    val fields: Map<String, String>,
) {
    companion object {
        fun from(json: JsonObject): UploadTicket? {
            val asset = json.obj("asset")?.let(UploadedFile::from) ?: return null
            val upload = json.obj("upload") ?: return null
            val fields = upload.obj("fields")?.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.content?.let { key to it }
            }?.toMap() ?: emptyMap()

            return UploadTicket(asset, upload.str("method", "POST"), upload.url("url") ?: return null, fields)
        }
    }
}

internal data class HelpCollection(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val description: String?,
    val position: Int,
    val articleCount: Int,
) {
    companion object {
        fun from(json: JsonObject): HelpCollection? = HelpCollection(
            id = json.long("id") ?: return null,
            parentId = json.long("parent_id"),
            name = json.str("name", ""),
            description = json.str("description"),
            position = json.int("position", 0),
            articleCount = json.int("article_count", 0),
        )
    }
}

internal fun List<HelpCollection>.withTotals(): List<HelpCollection> {
    val totals = mutableMapOf<Long, Int>()

    fun total(collection: HelpCollection, seen: Set<Long>): Int {
        totals[collection.id]?.let { return it }

        val path = seen + collection.id
        val sum = filter { it.parentId == collection.id && it.id !in path }
            .fold(collection.articleCount) { acc, child -> acc + total(child, path) }

        totals[collection.id] = sum
        return sum
    }

    return map { it.copy(articleCount = total(it, emptySet())) }
        .filter { it.articleCount > 0 }
        .sortedWith(compareBy({ it.position }, { it.id }))
}

internal data class HelpArticleSummary(val id: Long, val title: String, val description: String?) {
    companion object {
        fun from(json: JsonObject): HelpArticleSummary? =
            HelpArticleSummary(json.long("id") ?: return null, json.str("title", ""), json.str("description"))
    }
}

internal data class RichMark(val type: String, val attrs: JsonObject) {
    companion object {
        fun from(json: JsonObject): RichMark? =
            RichMark(json.str("type") ?: return null, json.obj("attrs") ?: JsonObject(emptyMap()))
    }
}

internal data class RichNode(
    val type: String,
    val attrs: JsonObject,
    val content: List<RichNode>,
    val text: String?,
    val marks: List<RichMark>,
) {
    companion object {
        fun from(json: JsonObject): RichNode = RichNode(
            type = json.str("type", ""),
            attrs = json.obj("attrs") ?: JsonObject(emptyMap()),
            content = json.list("content") { from(it) },
            text = json.str("text"),
            marks = json.list("marks", RichMark::from),
        )
    }
}

internal data class HelpArticle(
    val id: Long,
    val title: String,
    val description: String?,
    val body: RichNode?,
    val author: Person?,
    val updatedAt: Instant?,
) {
    companion object {
        fun from(json: JsonObject): HelpArticle? = HelpArticle(
            id = json.long("id") ?: return null,
            title = json.str("title", ""),
            description = json.str("description"),
            body = json.obj("body")?.let(RichNode::from),
            author = json.obj("author")?.let(Person::from),
            updatedAt = json.instant("updated_at"),
        )
    }
}

internal enum class ArticleReaction(val wire: String, val emoji: String) {
    SAD("sad", "😞"),
    NEUTRAL("neutral", "😐"),
    HAPPY("happy", "😃"),
}

internal fun JsonElement?.objects(): List<JsonObject> =
    (this as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
