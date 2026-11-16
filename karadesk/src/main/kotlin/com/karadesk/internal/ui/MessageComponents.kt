package com.karadesk.internal.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karadesk.R
import com.karadesk.internal.model.ComponentArticle
import com.karadesk.internal.model.ComponentButton
import com.karadesk.internal.model.InputKind
import com.karadesk.internal.model.Message
import com.karadesk.internal.model.MessageComponent
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

@Stable
internal class ComponentHandlers(
    val interact: suspend (Message, String, String?, JsonPrimitive?) -> String?,
    val interactInBackground: (Message, String, String) -> Unit,
    val openArticle: (ComponentArticle) -> Unit,
)

@Stable
private class SubmitState {
    var pending by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
}

private val componentShape = RoundedCornerShape(18.dp)

@Composable
internal fun MessageComponentsView(message: Message, handlers: ComponentHandlers) {
    val components = message.components.filter { it.isRenderable }

    if (components.isEmpty()) {
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 288.dp).fillMaxWidth(),
    ) {
        components.forEach { component ->
            when (component) {
                is MessageComponent.Buttons -> ButtonsComponent(message, component, handlers)
                is MessageComponent.Articles -> ArticlesComponent(component, handlers)
                is MessageComponent.Input -> InputComponent(message, component, handlers)
                is MessageComponent.Card -> CardComponent(message, component, handlers)
                is MessageComponent.Unknown -> component.fallbackText?.let { FallbackBubble(it) }
            }
        }
    }
}

@Composable
private fun rememberSubmitState(message: Message, component: MessageComponent): SubmitState =
    remember(message.id, component.id) { SubmitState() }

@Composable
private fun ErrorLine(error: String?) {
    if (error.isNullOrEmpty()) {
        return
    }

    Text(error, fontSize = 12.sp, color = KaraDeskTheme.palette.error, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun ButtonsComponent(message: Message, component: MessageComponent.Buttons, handlers: ComponentHandlers) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        component.prompt?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, color = KaraDeskTheme.palette.secondaryText, modifier = Modifier.padding(horizontal = 4.dp))
        }

        ButtonList(message, component, component.buttons, component.row, handlers)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonList(
    message: Message,
    component: MessageComponent,
    buttons: List<ComponentButton>,
    row: Boolean,
    handlers: ComponentHandlers,
) {
    val state = rememberSubmitState(message, component)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val answeredId = component.answer?.buttonId

    val press: (ComponentButton) -> Unit = { button ->
        val url = button.action.url

        if (button.action.opensUrl && url != null) {
            openUrl(context, url)
            handlers.interactInBackground(message, component.id, button.id)
        } else {
            state.pending = button.id
            state.error = null
            scope.launch {
                state.error = handlers.interact(message, component.id, button.id, null)
                state.pending = null
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        if (row) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                buttons.forEach { button ->
                    ComponentButtonView(button, answeredId, state.pending, Modifier) { press(button) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                buttons.forEach { button ->
                    ComponentButtonView(button, answeredId, state.pending, Modifier.fillMaxWidth()) { press(button) }
                }
            }
        }

        ErrorLine(state.error)
    }
}

@Composable
private fun ComponentButtonView(
    button: ComponentButton,
    answeredId: String?,
    pendingId: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val brand = KaraDeskTheme.brand
    val palette = KaraDeskTheme.palette
    val opensUrl = button.action.opensUrl
    val answered = answeredId != null && !opensUrl
    val chosen = answeredId == button.id && !opensUrl
    val filled = chosen || (button.primary && !answered)
    val enabled = opensUrl || (!answered && pendingId == null)
    val foreground = if (filled) brand.onAccent else palette.text

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .heightIn(min = 38.dp)
            .alpha(if (answered && !chosen) 0.4f else 1f)
            .clip(CircleShape)
            .background(if (filled) brand.accent else palette.surface)
            .then(if (filled) Modifier else Modifier.border(1.dp, palette.separator, CircleShape))
            .then(
                if (enabled) {
                    Modifier.clickableWithRole(button.label, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics { selected = chosen }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (pendingId == button.id) {
            CircularProgressIndicator(color = foreground, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        } else if (chosen) {
            Icon(KaraDeskIcons.Check, null, tint = foreground, modifier = Modifier.size(15.dp))
        }

        Text(button.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)

        if (opensUrl) {
            Icon(KaraDeskIcons.ArrowOutward, null, tint = foreground, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ArticlesComponent(component: MessageComponent.Articles, handlers: ComponentHandlers) {
    val palette = KaraDeskTheme.palette

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(componentShape)
            .background(palette.surface)
            .border(1.dp, palette.separator, componentShape),
    ) {
        component.articles.forEachIndexed { index, article ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.separator))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableWithRole(article.title) { handlers.openArticle(article) }
                    .semantics(mergeDescendants = true) { }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(KaraDeskIcons.Document, null, tint = palette.secondaryText, modifier = Modifier.size(18.dp))

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(article.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    article.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 12.sp, color = palette.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Icon(KaraDeskIcons.ChevronRight, null, tint = palette.tertiaryText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun InputComponent(message: Message, component: MessageComponent.Input, handlers: ComponentHandlers) {
    val palette = KaraDeskTheme.palette
    val brand = KaraDeskTheme.brand
    val state = rememberSubmitState(message, component)
    val scope = rememberCoroutineScope()
    var value by rememberSaveable(message.id, component.id) { mutableStateOf("") }
    val answer = component.answerLabel

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(componentShape)
            .background(palette.surface)
            .border(1.dp, palette.separator, componentShape)
            .padding(12.dp)
            .animateContentSize(),
    ) {
        Text(
            component.label,
            fontSize = 12.sp,
            fontWeight = if (answer == null) FontWeight.Medium else FontWeight.Normal,
            color = if (answer == null) palette.text else palette.secondaryText,
        )

        if (answer != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(KaraDeskIcons.Check, null, tint = palette.secondaryText, modifier = Modifier.size(15.dp))
                Text(answer, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
            }
            return@Column
        }

        val trimmed = value.trim()
        val canSubmit = trimmed.isNotEmpty() && state.pending == null

        val submit: () -> Unit = {
            if (canSubmit) {
                state.pending = component.id
                state.error = null
                scope.launch {
                    state.error = handlers.interact(message, component.id, null, inputValue(component.kind, trimmed))
                    state.pending = null
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val fieldModifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.bubble)

            if (component.kind == InputKind.SELECT) {
                SelectField(component, value, fieldModifier) { value = it }
            } else {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = palette.text),
                    cursorBrush = SolidColor(brand.tint),
                    keyboardOptions = KeyboardOptions(
                        capitalization = if (component.kind == InputKind.TEXT) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                        autoCorrectEnabled = component.kind == InputKind.TEXT,
                        keyboardType = keyboardType(component.kind),
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    decorationBox = { field ->
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                            val placeholder = component.placeholder

                            if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                                Text(placeholder, fontSize = 15.sp, color = palette.tertiaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            field()
                        }
                    },
                    modifier = fieldModifier,
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (canSubmit || state.pending != null) 1f else 0.35f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brand.accent)
                    .then(
                        if (canSubmit) {
                            Modifier.clickableWithRole(stringResource(R.string.karadesk_send), onClick = submit)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (state.pending != null) {
                    CircularProgressIndicator(color = brand.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(KaraDeskIcons.Send, stringResource(R.string.karadesk_send), tint = brand.onAccent, modifier = Modifier.size(17.dp))
                }
            }
        }

        ErrorLine(state.error)
    }
}

@Composable
private fun SelectField(component: MessageComponent.Input, value: String, modifier: Modifier, onSelect: (String) -> Unit) {
    val palette = KaraDeskTheme.palette
    var open by remember { mutableStateOf(false) }
    val selected = component.options.firstOrNull { it.value == value }
    val placeholder = component.placeholder ?: stringResource(R.string.karadesk_choose_one)

    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickableWithRole(component.label) { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                selected?.label ?: placeholder,
                fontSize = 15.sp,
                color = if (selected != null) palette.text else palette.tertiaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(KaraDeskIcons.ChevronRight, null, tint = palette.secondaryText, modifier = Modifier.size(16.dp).rotate(90f))
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            component.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = if (option.value == value) {
                        { Icon(KaraDeskIcons.Check, null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(option.value)
                    },
                )
            }
        }
    }
}

private fun keyboardType(kind: InputKind): KeyboardType = when (kind) {
    InputKind.EMAIL -> KeyboardType.Email
    InputKind.PHONE -> KeyboardType.Phone
    InputKind.NUMBER -> KeyboardType.Decimal
    InputKind.TEXT, InputKind.SELECT -> KeyboardType.Text
}

internal fun inputValue(kind: InputKind, text: String): JsonPrimitive {
    if (kind != InputKind.NUMBER) {
        return JsonPrimitive(text)
    }

    text.toLongOrNull()?.let { return JsonPrimitive(it) }
    text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { return JsonPrimitive(it) }

    return JsonPrimitive(text)
}

@Composable
private fun CardComponent(message: Message, component: MessageComponent.Card, handlers: ComponentHandlers) {
    val palette = KaraDeskTheme.palette
    val brand = KaraDeskTheme.brand

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(componentShape)
            .background(palette.surface)
            .border(1.dp, palette.separator, componentShape),
    ) {
        component.imageUrl?.let { url ->
            RemoteImage(
                url = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f).background(palette.bubble),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
            if (component.title.isNotBlank()) {
                Text(component.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            }

            component.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    Markdown.parse(body, brand.tint),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = palette.text,
                )
            }

            if (component.buttons.isNotEmpty()) {
                ButtonList(message, component, component.buttons, row = false, handlers = handlers)
            }
        }
    }
}

@Composable
private fun FallbackBubble(text: String) {
    Text(
        text,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        color = KaraDeskTheme.palette.text,
        modifier = Modifier
            .clip(bubbleShape(mine = false, tail = false))
            .background(KaraDeskTheme.palette.bubble)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
