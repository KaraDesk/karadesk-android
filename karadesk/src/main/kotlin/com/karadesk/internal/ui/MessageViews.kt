package com.karadesk.internal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.karadesk.R
import com.karadesk.internal.model.AuthorKind
import com.karadesk.internal.model.Message
import com.karadesk.internal.model.MessageAttachment
import com.karadesk.internal.model.MessageAuthor
import com.karadesk.internal.model.MessageKind
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

internal data class MessageGroup(val id: String, val author: MessageAuthor, val messages: List<Message>) {
    val isMine: Boolean
        get() = author.kind == AuthorKind.CONTACT

    companion object {
        fun make(messages: List<Message>): List<MessageGroup> {
            val groups = mutableListOf<MessageGroup>()

            messages.filter { it.isRenderable }.forEach { message ->
                val last = groups.lastOrNull()
                val previous = last?.messages?.lastOrNull()

                if (last != null && previous != null &&
                    last.author.kind == message.author.kind &&
                    last.author.name == message.author.name &&
                    message.kind != MessageKind.Csat &&
                    previous.kind != MessageKind.Csat &&
                    Duration.between(previous.createdAt, message.createdAt).seconds < 300
                ) {
                    groups[groups.lastIndex] = last.copy(messages = last.messages + message)
                } else {
                    groups += MessageGroup(message.id, message.author, listOf(message))
                }
            }

            return groups
        }
    }
}

internal fun bubbleShape(mine: Boolean, tail: Boolean): Shape {
    val radius = 18.dp
    val small = 5.dp

    return RoundedCornerShape(
        topStart = radius,
        topEnd = radius,
        bottomEnd = if (mine && tail) small else radius,
        bottomStart = if (!mine && tail) small else radius,
    )
}

@Composable
internal fun MessageGroupView(
    group: MessageGroup,
    onOpenArticle: (MessageAttachment.Article) -> Unit,
    onRate: suspend (Message, Int?, String?) -> Unit,
    componentHandlers: ComponentHandlers,
) {
    val palette = KaraDeskTheme.palette
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (group.isMine) Alignment.End else Alignment.Start),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (group.isMine) {
            Spacer(Modifier.width(40.dp))
        } else if (group.author.kind == AuthorKind.BOT) {
            BotAvatar(28.dp)
        } else {
            Avatar(group.author.name, group.author.avatarUrl, 28.dp)
        }

        Column(
            horizontalAlignment = if (group.isMine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            if (!group.isMine) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Text(group.author.name ?: stringResource(R.string.karadesk_team), fontSize = 12.sp, color = palette.secondaryText)

                    if (group.author.kind == AuthorKind.BOT) {
                        Text(
                            stringResource(R.string.karadesk_ai_agent),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.text,
                            modifier = Modifier.clip(CircleShape).background(palette.fill).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            group.messages.forEachIndexed { index, message ->
                MessageView(message, group.isMine, index == group.messages.lastIndex, onOpenArticle, onRate, componentHandlers)
            }

            group.messages.lastOrNull()?.let {
                Text(
                    Formatting.clock(context, it.createdAt),
                    fontSize = 11.sp,
                    color = palette.tertiaryText,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        if (!group.isMine) {
            Spacer(Modifier.width(32.dp))
        }
    }
}

@Composable
private fun MessageView(
    message: Message,
    mine: Boolean,
    tail: Boolean,
    onOpenArticle: (MessageAttachment.Article) -> Unit,
    onRate: suspend (Message, Int?, String?) -> Unit,
    componentHandlers: ComponentHandlers,
) {
    val brand = KaraDeskTheme.brand
    val palette = KaraDeskTheme.palette

    Column(
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (message.kind == MessageKind.Csat) {
            CsatCard(message, onRate)
            return@Column
        }

        if (message.body.isNotEmpty()) {
            val foreground = if (mine) brand.onAccent else palette.text

            SelectionContainer {
                Text(
                    Markdown.parse(message.body, if (mine) brand.onAccent else brand.tint),
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    color = foreground,
                    modifier = Modifier
                        .clip(bubbleShape(mine, tail && message.attachments.isEmpty() && message.components.isEmpty()))
                        .background(if (mine) brand.accent else palette.bubble)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }

        FileAttachments(message.attachments.filterIsInstance<MessageAttachment.File>())

        message.attachments.filterNot { it is MessageAttachment.File }.forEach { attachment ->
            AttachmentView(attachment, onOpenArticle)
        }

        if (!mine && message.components.isNotEmpty()) {
            MessageComponentsView(message, componentHandlers)
        }
    }
}

@Composable
private fun AttachmentView(attachment: MessageAttachment, onOpenArticle: (MessageAttachment.Article) -> Unit) {
    val context = LocalContext.current
    val palette = KaraDeskTheme.palette

    when (attachment) {
        is MessageAttachment.File -> FileAttachments(listOf(attachment))
        is MessageAttachment.Article -> AttachmentCard(KaraDeskIcons.Book, attachment.title, attachment.description, KaraDeskIcons.ChevronRight) {
            onOpenArticle(attachment)
        }
        is MessageAttachment.Link -> {
            val host = runCatching { android.net.Uri.parse(attachment.url).host }.getOrNull()
            AttachmentCard(KaraDeskIcons.Link, attachment.title ?: host ?: attachment.url, host, KaraDeskIcons.ArrowOutward) {
                openUrl(context, attachment.url)
            }
        }
    }
}

@Composable
private fun AttachmentCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String?,
    trailing: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val palette = KaraDeskTheme.palette
    val shape = RoundedCornerShape(14.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .width(260.dp)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.separator, shape)
            .clickableWithRole(title, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(10.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(palette.fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = palette.secondaryText, modifier = Modifier.size(18.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.takeIf { it.isNotEmpty() }?.let {
                Text(it, fontSize = 12.sp, color = palette.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        Icon(trailing, null, tint = palette.tertiaryText, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun TypingRow(author: MessageAuthor) {
    val palette = KaraDeskTheme.palette
    val label = stringResource(R.string.karadesk_is_typing, author.name ?: stringResource(R.string.karadesk_team))
    val transition = rememberInfiniteTransition(label = "typing")

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
    ) {
        if (author.kind == AuthorKind.BOT) BotAvatar(28.dp) else Avatar(author.name, author.avatarUrl, 28.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(bubbleShape(mine = false, tail = true))
                .background(palette.bubble)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(600, delayMillis = index * 200), RepeatMode.Reverse),
                    label = "dot$index",
                )

                Box(Modifier.size(7.dp).graphicsLayer(alpha = alpha).clip(CircleShape).background(palette.secondaryText))
            }
        }
    }
}

private data class Face(val value: Int, val emoji: String, val label: Int)

private val faces = listOf(
    Face(1, "😠", R.string.karadesk_csat_terrible),
    Face(2, "🙁", R.string.karadesk_csat_bad),
    Face(3, "😐", R.string.karadesk_csat_ok),
    Face(4, "😃", R.string.karadesk_csat_great),
    Face(5, "🤩", R.string.karadesk_csat_amazing),
)

@Composable
private fun CsatCard(message: Message, onRate: suspend (Message, Int?, String?) -> Unit) {
    val palette = KaraDeskTheme.palette
    val brand = KaraDeskTheme.brand
    val metadata = message.metadata
    val rating = metadata?.rating
    val openedAt = remember { Instant.now() }
    val expired = rating == null && metadata?.expiresAt?.isBefore(openedAt) == true
    val locked = rating != null && metadata.lockUntil?.isBefore(openedAt) == true
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(false) }
    var remark by rememberSaveable { mutableStateOf("") }
    val teammate = Formatting.firstName(metadata?.teammateName)

    val submit: (Int?, String?) -> Unit = { value, note ->
        pending = true
        scope.launch {
            onRate(message, value, note)
            pending = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (teammate != null) stringResource(R.string.karadesk_csat_intro_teammate, teammate) else stringResource(R.string.karadesk_csat_intro),
            fontSize = 16.sp,
            color = palette.text,
            modifier = Modifier.clip(bubbleShape(mine = false, tail = false)).background(palette.bubble).padding(horizontal = 14.dp, vertical = 9.dp),
        )

        val shape = RoundedCornerShape(20.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .width(272.dp)
                .clip(shape)
                .background(palette.surface)
                .border(1.dp, palette.separator, shape)
                .padding(16.dp)
                .animateContentSize(),
        ) {
            AnimatedContent(
                targetState = rating == null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "csat-title",
            ) { unrated ->
                Text(
                    if (unrated) stringResource(R.string.karadesk_rate_title) else stringResource(R.string.karadesk_thanks_for_rating),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    textAlign = TextAlign.Center,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                faces.forEach { face ->
                    val selected = rating == face.value
                    val scale by animateFloatAsState(if (selected) 1.08f else 1f, label = "face")
                    val label = stringResource(face.label)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(if (selected) palette.fill else androidx.compose.ui.graphics.Color.Transparent)
                            .then(
                                if (!expired && !locked && !pending) {
                                    Modifier.clickableWithRole(label) { submit(face.value, null) }
                                } else {
                                    Modifier
                                },
                            )
                            .semantics {
                                contentDescription = label
                                this.selected = selected
                            },
                    ) {
                        Text(face.emoji, fontSize = 26.sp, modifier = Modifier.graphicsLayer(alpha = if (rating != null && !selected) 0.35f else 1f))
                    }
                }
            }

            if (expired) {
                Text(stringResource(R.string.karadesk_rating_expired), fontSize = 12.sp, color = palette.secondaryText)
            }

            val existing = metadata?.remark

            if (!existing.isNullOrEmpty()) {
                Text("“$existing”", fontSize = 13.sp, fontStyle = FontStyle.Italic, color = palette.secondaryText, textAlign = TextAlign.Center)
            } else if (rating != null && !locked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        textStyle = TextStyle(fontSize = 15.sp, color = palette.text),
                        cursorBrush = SolidColor(brand.tint),
                        minLines = 2,
                        maxLines = 5,
                        decorationBox = { field ->
                            Box {
                                if (remark.isEmpty()) {
                                    Text(stringResource(R.string.karadesk_add_remark), fontSize = 15.sp, color = palette.tertiaryText)
                                }
                                field()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.bubble)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )

                    ProminentButton(
                        text = stringResource(R.string.karadesk_send_feedback),
                        onClick = { submit(null, remark.trim()) },
                        enabled = remark.isNotBlank() && !pending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
