package com.karadesk.internal.model

import kotlinx.serialization.json.JsonObject
import java.time.Instant

internal data class ComponentAnswer(val buttonId: String?, val value: String?, val answeredAt: Instant?) {
    companion object {
        fun from(json: JsonObject): ComponentAnswer =
            ComponentAnswer(json.str("button_id"), json.str("value"), json.instant("answered_at"))
    }
}

internal data class ComponentAction(val type: String, val url: String?, val step: String?, val name: String?) {
    val opensUrl: Boolean
        get() = type == "open_url" && url != null

    companion object {
        val none = ComponentAction("event", null, null, null)

        fun from(json: JsonObject): ComponentAction =
            ComponentAction(json.str("type", "event"), json.url("url"), json.str("step"), json.str("name"))
    }
}

internal data class ComponentButton(
    val id: String,
    val label: String,
    val value: String?,
    val primary: Boolean,
    val echo: Boolean,
    val action: ComponentAction,
) {
    companion object {
        fun from(json: JsonObject): ComponentButton? {
            return ComponentButton(
                id = json.str("id") ?: return null,
                label = json.str("label", ""),
                value = json.str("value"),
                primary = json.str("style") == "primary",
                echo = json.bool("echo", false),
                action = json.obj("action")?.let(ComponentAction::from) ?: ComponentAction.none,
            )
        }
    }
}

internal data class ComponentArticle(
    val id: String,
    val title: String,
    val description: String?,
    val url: String?,
    val helpCenterId: Long?,
) {
    val articleId: Long?
        get() = id.toLongOrNull()

    companion object {
        fun from(json: JsonObject): ComponentArticle? {
            return ComponentArticle(
                id = json.str("id") ?: return null,
                title = json.str("title", ""),
                description = json.str("description"),
                url = json.url("url"),
                helpCenterId = json.long("help_center_id"),
            )
        }
    }
}

internal data class InputOption(val value: String, val label: String) {
    companion object {
        fun from(json: JsonObject): InputOption? {
            val value = json.str("value") ?: return null
            return InputOption(value, json.str("label") ?: value)
        }
    }
}

internal enum class InputKind {
    TEXT, EMAIL, PHONE, NUMBER, SELECT;

    companion object {
        fun of(value: String?): InputKind = when (value) {
            "email" -> EMAIL
            "phone" -> PHONE
            "number" -> NUMBER
            "select" -> SELECT
            else -> TEXT
        }
    }
}

internal sealed interface MessageComponent {
    val id: String
    val fallbackText: String?
    val answer: ComponentAnswer?

    val isRenderable: Boolean
        get() = true

    data class Buttons(
        override val id: String,
        override val fallbackText: String?,
        override val answer: ComponentAnswer?,
        val prompt: String?,
        val row: Boolean,
        val buttons: List<ComponentButton>,
    ) : MessageComponent

    data class Articles(
        override val id: String,
        override val fallbackText: String?,
        override val answer: ComponentAnswer?,
        val articles: List<ComponentArticle>,
    ) : MessageComponent

    data class Input(
        override val id: String,
        override val fallbackText: String?,
        override val answer: ComponentAnswer?,
        val label: String,
        val kind: InputKind,
        val placeholder: String?,
        val required: Boolean,
        val options: List<InputOption>,
        val saveTo: String?,
        val echo: Boolean,
        val action: ComponentAction,
    ) : MessageComponent {
        val answerLabel: String?
            get() {
                val value = answer?.value ?: return null
                return options.firstOrNull { it.value == value }?.label ?: value
            }
    }

    data class Card(
        override val id: String,
        override val fallbackText: String?,
        override val answer: ComponentAnswer?,
        val title: String,
        val body: String?,
        val imageUrl: String?,
        val buttons: List<ComponentButton>,
    ) : MessageComponent

    data class Unknown(
        override val id: String,
        override val fallbackText: String?,
        override val answer: ComponentAnswer?,
        val type: String,
    ) : MessageComponent {
        override val isRenderable: Boolean
            get() = !fallbackText.isNullOrBlank()
    }

    companion object {
        fun from(json: JsonObject): MessageComponent? {
            val id = json.str("id") ?: return null
            val fallbackText = json.str("fallback_text")
            val answer = json.obj("answer")?.let(ComponentAnswer::from)

            return when (val type = json.str("type", "")) {
                "buttons" -> Buttons(
                    id = id,
                    fallbackText = fallbackText,
                    answer = answer,
                    prompt = json.str("prompt"),
                    row = json.str("layout") == "row",
                    buttons = json.list("buttons", ComponentButton::from),
                )
                "articles" -> Articles(id, fallbackText, answer, json.list("articles", ComponentArticle::from))
                "input" -> Input(
                    id = id,
                    fallbackText = fallbackText,
                    answer = answer,
                    label = json.str("label", ""),
                    kind = InputKind.of(json.str("kind")),
                    placeholder = json.str("placeholder"),
                    required = json.bool("required", false),
                    options = json.list("options", InputOption::from),
                    saveTo = json.str("save_to"),
                    echo = json.bool("echo", false),
                    action = json.obj("action")?.let(ComponentAction::from) ?: ComponentAction.none,
                )
                "card" -> Card(
                    id = id,
                    fallbackText = fallbackText,
                    answer = answer,
                    title = json.str("title", ""),
                    body = json.str("body"),
                    imageUrl = json.url("image_url"),
                    buttons = json.list("buttons", ComponentButton::from),
                )
                else -> Unknown(id, fallbackText, answer, type)
            }
        }
    }
}

internal data class InteractionResult(val message: Message, val reply: Message?) {
    companion object {
        fun from(json: JsonObject): InteractionResult? {
            val message = json.obj("message")?.let(Message::from) ?: return null
            return InteractionResult(message, json.obj("reply")?.let(Message::from))
        }
    }
}
