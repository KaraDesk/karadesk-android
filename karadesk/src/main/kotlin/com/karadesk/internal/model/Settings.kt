package com.karadesk.internal.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

internal enum class ThemeMode { LIGHT, DARK, SYSTEM }

internal enum class HeaderText { AUTO, LIGHT, DARK }

internal enum class BackgroundKind { SOLID, GRADIENT, IMAGE }

internal data class Background(
    val kind: BackgroundKind,
    val color: String?,
    val gradientFrom: String?,
    val gradientTo: String?,
    val imageUrl: String?,
    val fade: Boolean,
) {
    companion object {
        val plain = Background(BackgroundKind.SOLID, null, null, null, null, false)

        fun from(json: JsonObject, imageUrl: String?): Background = Background(
            kind = backgroundKind(json.str("type")) ?: BackgroundKind.SOLID,
            color = json.str("color"),
            gradientFrom = json.str("gradient_from"),
            gradientTo = json.str("gradient_to"),
            imageUrl = imageUrl,
            fade = json.bool("fade", false),
        )
    }
}

internal fun backgroundKind(value: String?): BackgroundKind? = when (value) {
    "solid" -> BackgroundKind.SOLID
    "gradient" -> BackgroundKind.GRADIENT
    "image" -> BackgroundKind.IMAGE
    else -> null
}

internal fun headerText(value: String?): HeaderText? = when (value) {
    "auto" -> HeaderText.AUTO
    "light" -> HeaderText.LIGHT
    "dark" -> HeaderText.DARK
    else -> null
}

internal data class DarkAppearance(
    val color: String?,
    val headerText: HeaderText?,
    val backgroundKind: BackgroundKind?,
    val backgroundColor: String?,
    val gradientFrom: String?,
    val gradientTo: String?,
) {
    companion object {
        val none = DarkAppearance(null, null, null, null, null, null)

        fun from(json: JsonObject): DarkAppearance {
            val background = json.obj("background")

            return DarkAppearance(
                color = json.str("color"),
                headerText = headerText(json.str("header_text")),
                backgroundKind = backgroundKind(background?.str("type")),
                backgroundColor = background?.str("color"),
                gradientFrom = background?.str("gradient_from"),
                gradientTo = background?.str("gradient_to"),
            )
        }
    }
}

internal data class Rules(
    val allowNew: Boolean,
    val singleOpen: Boolean,
    val requireSearch: Boolean,
    val closedReplyDays: Int?,
) {
    companion object {
        val standard = Rules(allowNew = true, singleOpen = false, requireSearch = false, closedReplyDays = null)

        fun from(json: JsonObject): Rules = Rules(
            allowNew = json.bool("allow_new", true),
            singleOpen = json.bool("single_open", false),
            requireSearch = json.bool("require_search", false),
            closedReplyDays = json.int("closed_reply_days"),
        )
    }
}

internal data class Availability(val open: Boolean, val replyTime: String?, val nextOpenAt: Instant?) {
    companion object {
        fun from(json: JsonObject): Availability =
            Availability(json.bool("open", true), json.str("reply_time"), json.instant("next_open_at"))
    }
}

internal data class AttachmentRules(val enabled: Boolean, val maxBytes: Long, val accept: List<String>) {
    companion object {
        val disabled = AttachmentRules(false, 0, emptyList())

        fun from(json: JsonObject): AttachmentRules = AttachmentRules(
            enabled = json.bool("enabled", false),
            maxBytes = json.long("max_bytes") ?: 0,
            accept = (json["accept"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
        )
    }
}

internal data class HelpCenterReference(
    val id: Long,
    val name: String,
    val url: String?,
    val articleReactions: Boolean,
) {
    companion object {
        fun from(json: JsonObject): HelpCenterReference? = HelpCenterReference(
            id = json.long("id") ?: return null,
            name = json.str("name", ""),
            url = json.url("url"),
            articleReactions = json.bool("article_reactions", false),
        )
    }
}

internal sealed interface HomeCard {
    val id: String

    data class Messages(override val id: String) : HomeCard

    data class Help(
        override val id: String,
        val title: String?,
        val showSearch: Boolean,
        val helpCenter: HelpCenterReference,
        val articles: List<ArticleLink>,
    ) : HomeCard

    data class Link(
        override val id: String,
        val title: String,
        val description: String?,
        val url: String,
        val buttonLabel: String?,
    ) : HomeCard

    data class Text(override val id: String, val title: String?, val body: String) : HomeCard

    data class ArticleLink(val id: Long, val title: String)

    companion object {
        fun from(json: JsonObject): HomeCard? {
            val id = json.str("id") ?: return null

            return when (json.str("type")) {
                "messages" -> Messages(id)
                "help" -> Help(
                    id = id,
                    title = json.str("title"),
                    showSearch = json.bool("show_search", true),
                    helpCenter = json.obj("help_center")?.let(HelpCenterReference::from) ?: return null,
                    articles = json.list("articles") { article ->
                        article.long("id")?.let { ArticleLink(it, article.str("title", "")) }
                    },
                )
                "link" -> Link(
                    id = id,
                    title = json.str("title", ""),
                    description = json.str("description"),
                    url = json.url("url") ?: return null,
                    buttonLabel = json.str("button_label"),
                )
                "text" -> Text(id, json.str("title"), json.str("body", ""))
                else -> null
            }
        }
    }
}

internal data class MessengerSettings(
    val color: String,
    val theme: ThemeMode,
    val logoUrl: String?,
    val darkLogoUrl: String?,
    val headerText: HeaderText,
    val background: Background,
    val darkBackgroundImageUrl: String?,
    val dark: DarkAppearance,
    val visitorRules: Rules,
    val userRules: Rules,
    val homeCards: List<HomeCard>,
    val greeting: String,
    val intro: String,
    val showTeam: Boolean,
    val showBranding: Boolean,
    val showHelp: Boolean,
    val helpCenter: HelpCenterReference?,
    val helpCenters: List<HelpCenterReference>,
    val availability: Availability?,
    val attachments: AttachmentRules,
) {
    fun rules(identified: Boolean): Rules = if (identified) userRules else visitorRules

    fun helpCenter(id: Long?): HelpCenterReference? {
        if (id == null) {
            return helpCenter
        }

        if (helpCenter?.id == id) {
            return helpCenter
        }

        return helpCenters.firstOrNull { it.id == id }
    }

    companion object {
        fun from(json: JsonObject): MessengerSettings {
            val rules = json.obj("rules")

            return MessengerSettings(
                color = json.str("color", "#111111"),
                theme = when (json.str("theme")) {
                    "light" -> ThemeMode.LIGHT
                    "dark" -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                },
                logoUrl = json.url("logo_url"),
                darkLogoUrl = json.url("dark_logo_url"),
                headerText = headerText(json.str("header_text")) ?: HeaderText.AUTO,
                background = json.obj("background")?.let { Background.from(it, json.url("background_image_url")) }
                    ?: Background.plain.copy(imageUrl = json.url("background_image_url")),
                darkBackgroundImageUrl = json.url("dark_background_image_url"),
                dark = json.obj("dark")?.let(DarkAppearance::from) ?: DarkAppearance.none,
                visitorRules = rules?.obj("visitors")?.let(Rules::from) ?: Rules.standard,
                userRules = rules?.obj("users")?.let(Rules::from) ?: Rules.standard,
                homeCards = json.list("home_cards", HomeCard::from),
                greeting = json.str("greeting", ""),
                intro = json.str("intro", ""),
                showTeam = json.bool("show_team", true),
                showBranding = json.bool("show_branding", true),
                showHelp = json.bool("show_help", true),
                helpCenter = json.obj("help_center")?.let(HelpCenterReference::from),
                helpCenters = json.list("help_centers", HelpCenterReference::from),
                availability = json.obj("availability")?.let(Availability::from),
                attachments = json.obj("attachments")?.let(AttachmentRules::from) ?: AttachmentRules.disabled,
            )
        }
    }
}
