package com.karadesk.internal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karadesk.R
import com.karadesk.internal.model.MessageAttachment
import com.karadesk.internal.store.MessengerStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
internal fun ChatScreen(store: MessengerStore, router: Router, route: Route.Chat, onClose: () -> Unit) {
    val context = LocalContext.current
    val model = remember(route) { ChatModel(store, context.applicationContext, route.conversationId, route.draft) }
    val threads by store.threads.collectAsStateWithLifecycle()
    val conversations by store.conversations.collectAsStateWithLifecycle()
    val typing by store.typing.collectAsStateWithLifecycle()
    val boot by store.boot.collectAsStateWithLifecycle()
    val palette = KaraDeskTheme.palette

    val conversationId = model.conversationId
    val messages = conversationId?.let { threads[it] }.orEmpty()
    val conversation = conversationId?.let { id -> conversations.firstOrNull { it.id == id } }
    val groups = remember(messages) { MessageGroup.make(messages) }
    var now by remember { mutableStateOf(Instant.now()) }
    val typingAuthor = conversationId?.let { typing[it] }?.takeIf { it.until.isAfter(now) }?.author

    LaunchedEffect(typing) {
        now = Instant.now()
        val next = typing.values.minOfOrNull { it.until } ?: return@LaunchedEffect

        if (next.isAfter(now)) {
            delay(java.time.Duration.between(now, next).toMillis() + 50)
            now = Instant.now()
        }
    }

    LaunchedEffect(conversationId) {
        model.load()

        if (route.conversationId == null && conversationId != null) {
            router.replaceTop(Route.Chat(conversationId, null))
        }
    }
    DisposableEffect(model) { onDispose { model.disappear() } }

    DefaultStatusBarIcons()

    val listState = rememberLazyListState()
    val itemCount = groups.size + (if (conversationId == null) 1 else 0) + (if (typingAuthor != null) 1 else 0)

    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    Column(Modifier.fillMaxSize().background(palette.surface)) {
        MessengerTopBar(
            title = {
                val assignee = conversation?.assignee
                val active = assignee?.isActive() == true
                val title = Formatting.firstName(assignee?.name) ?: boot?.spaceName ?: stringResource(R.string.karadesk_team)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                ) {
                    if (assignee != null) {
                        Avatar(assignee.name, assignee.avatarUrl, 34.dp, showsActiveDot = active)
                    } else if (!boot?.team.isNullOrEmpty()) {
                        AvatarStack(boot?.team.orEmpty(), size = 28.dp)
                    }

                    Column {
                        Text(title, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (active) stringResource(R.string.karadesk_active_now) else replyExpectation(boot?.settings?.availability?.replyTime),
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = palette.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            router = router,
            onClose = onClose,
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (conversationId == null) {
                item(key = "empty") { EmptyChat(store) }
            }

            items(groups, key = { it.id }) { group ->
                MessageGroupView(
                    group = group,
                    onOpenArticle = { article: MessageAttachment.Article ->
                        router.push(Route.Article(article.helpCenterId, article.articleId))
                    },
                    onRate = { message, rating, remark -> model.rate(message, rating, remark) },
                )
            }

            if (typingAuthor != null) {
                item(key = "typing") {
                    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                        TypingRow(typingAuthor)
                    }
                }
            }
        }

        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
            val mergedInto = conversation?.mergedIntoId

            when {
                mergedInto != null -> FooterNotice(
                    stringResource(R.string.karadesk_continues_elsewhere),
                    stringResource(R.string.karadesk_continue_conversation),
                ) { router.replaceTop(Route.Chat(mergedInto, null)) }
                model.isLocked() -> FooterNotice(
                    stringResource(R.string.karadesk_conversation_ended),
                    if (store.rules.allowNew) stringResource(R.string.karadesk_start_new_conversation) else null,
                ) { router.replaceTop(Route.Chat(null, null)) }
                else -> Composer(model)
            }
        }
    }
}

@Composable
private fun EmptyChat(store: MessengerStore) {
    val boot by store.boot.collectAsStateWithLifecycle()
    val palette = KaraDeskTheme.palette
    val team = boot?.team.orEmpty()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 24.dp),
    ) {
        if (team.isNotEmpty()) {
            Box(Modifier.padding(bottom = 4.dp)) { AvatarStack(team, size = 48.dp) }
        }

        Text(stringResource(R.string.karadesk_ask_us_anything), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
        Text(
            stringResource(R.string.karadesk_well_reply),
            fontSize = 15.sp,
            color = palette.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Composable
private fun FooterNotice(text: String, action: String?, perform: () -> Unit) {
    val palette = KaraDeskTheme.palette

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.card)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Text(text, fontSize = 15.sp, color = palette.secondaryText, textAlign = TextAlign.Center)

        if (action != null) {
            ProminentButton(action, perform)
        }
    }
}

@Composable
private fun Composer(model: ChatModel) {
    val palette = KaraDeskTheme.palette
    val brand = KaraDeskTheme.brand
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)

    val photos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ChatModel.MAX_ATTACHMENTS),
    ) { uris -> model.addUris(uris) }

    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        model.addUris(uris)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(palette.card)
            .border(1.dp, palette.cardBorder, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        model.notice?.let {
            Text(it, fontSize = 13.sp, color = palette.error, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }

        if (model.attachments.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(model.attachments, key = { it.id }) { attachment ->
                    PendingAttachmentView(attachment) { model.remove(attachment) }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            if (model.attachmentRules.enabled) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(KaraDeskIcons.AttachFile, stringResource(R.string.karadesk_attach), tint = palette.secondaryText)
                    }

                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.karadesk_photos)) },
                            leadingIcon = { Icon(KaraDeskIcons.Photos, null) },
                            onClick = {
                                menuOpen = false
                                photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.karadesk_files)) },
                            leadingIcon = { Icon(KaraDeskIcons.Folder, null) },
                            onClick = {
                                menuOpen = false
                                files.launch(arrayOf("*/*"))
                            },
                        )
                    }
                }
            } else {
                Box(Modifier.size(width = 8.dp, height = 48.dp))
            }

            BasicTextField(
                value = model.draft,
                onValueChange = model::draftChanged,
                textStyle = TextStyle(fontSize = 16.sp, color = palette.text),
                cursorBrush = SolidColor(brand.tint),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { field ->
                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 13.dp), contentAlignment = Alignment.CenterStart) {
                        if (model.draft.isEmpty()) {
                            Text(stringResource(R.string.karadesk_message_placeholder), fontSize = 16.sp, color = palette.tertiaryText)
                        }
                        field()
                    }
                },
                modifier = Modifier.weight(1f),
            )

            val enabled = model.canSend

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(6.dp)
                    .size(36.dp)
                    .graphicsLayer(alpha = if (enabled || model.isSending) 1f else 0.35f)
                    .clip(CircleShape)
                    .background(brand.accent)
                    .then(
                        if (enabled) {
                            Modifier.clickableWithRole(stringResource(R.string.karadesk_send)) { scope.launch { model.send() } }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (model.isSending) {
                    CircularProgressIndicator(color = brand.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(KaraDeskIcons.Send, stringResource(R.string.karadesk_send), tint = brand.onAccent, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentView(attachment: PendingAttachment, onRemove: () -> Unit) {
    val palette = KaraDeskTheme.palette
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(palette.bubble).padding(6.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(palette.fill),
            contentAlignment = Alignment.Center,
        ) {
            val preview = attachment.preview

            if (preview != null) {
                Image(preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(KaraDeskIcons.File, null, tint = palette.secondaryText, modifier = Modifier.size(18.dp))
            }
        }

        Column(Modifier.widthIn(max = 120.dp)) {
            Text(attachment.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)

            when (attachment.status) {
                PendingAttachment.Status.Uploading -> Text(stringResource(R.string.karadesk_uploading), fontSize = 11.sp, color = palette.secondaryText)
                PendingAttachment.Status.Failed -> Text(stringResource(R.string.karadesk_upload_failed), fontSize = 11.sp, color = palette.error)
                is PendingAttachment.Status.Ready -> Text(Formatting.byteCount(context, attachment.byteSize), fontSize = 11.sp, color = palette.secondaryText)
            }
        }

        Icon(
            KaraDeskIcons.Cancel,
            stringResource(R.string.karadesk_remove),
            tint = palette.secondaryText,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickableWithRole(onClick = onRemove),
        )
    }
}
