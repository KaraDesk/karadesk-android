package com.karadesk.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karadesk.R
import com.karadesk.internal.model.AuthorKind
import com.karadesk.internal.model.Conversation
import com.karadesk.internal.model.Person
import com.karadesk.internal.store.MessengerStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessagesScreen(store: MessengerStore, router: Router, onClose: () -> Unit) {
    val conversations by store.conversations.collectAsStateWithLifecycle()
    val conversationsError by store.conversationsError.collectAsStateWithLifecycle()
    val boot by store.boot.collectAsStateWithLifecycle()
    val palette = KaraDeskTheme.palette
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    DefaultStatusBarIcons()

    Column(Modifier.fillMaxSize()) {
        MessengerTopBar(
            title = { Text(stringResource(R.string.karadesk_messages), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
            router = router,
            onClose = onClose,
            centered = true,
        )

        Box(Modifier.fillMaxSize()) {
            val loadError = conversationsError

            if (conversations.isEmpty() && loadError != null) {
                ErrorState(loadError, title = stringResource(R.string.karadesk_messages_load_failed)) {
                    store.refreshConversations()
                }
            } else if (conversations.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(KaraDeskIcons.ChatOutlined, null, tint = palette.secondaryText, modifier = Modifier.size(44.dp))
                    Text(
                        stringResource(R.string.karadesk_no_conversations),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = palette.text,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Text(
                        stringResource(R.string.karadesk_no_conversations_detail),
                        color = palette.secondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp).widthIn(max = 300.dp),
                    )
                    NewConversationButton(store, router, Modifier.padding(top = 20.dp))
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            store.refreshConversations()
                            refreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(conversations, key = { it.id }) { conversation ->
                            ConversationRow(conversation, boot?.team.orEmpty()) {
                                router.push(Route.Chat(conversation.id, null))
                            }
                            HorizontalDivider(color = palette.separator, modifier = Modifier.padding(start = 68.dp))
                        }
                    }
                }

                NewConversationButton(store, router, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun NewConversationButton(store: MessengerStore, router: Router, modifier: Modifier) {
    if (!store.rules.allowNew) {
        return
    }

    ProminentButton(
        text = stringResource(R.string.karadesk_send_us_a_message),
        onClick = { router.push(Route.Chat(store.singleOpenConversationId, null)) },
        modifier = modifier,
        icon = { Icon(KaraDeskIcons.Send, null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun ConversationRow(conversation: Conversation, team: List<Person>, onOpen: () -> Unit) {
    val palette = KaraDeskTheme.palette
    val context = LocalContext.current
    val last = conversation.lastMessage
    val title = conversation.assignee?.name
        ?: last?.takeIf { it.authorType != AuthorKind.CONTACT }?.authorName
        ?: conversation.title
        ?: stringResource(R.string.karadesk_team)
    val body = Formatting.plain(last?.body.orEmpty())
    val preview = if (last?.authorType == AuthorKind.CONTACT) "${stringResource(R.string.karadesk_you)}: $body" else body

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithRole(onClick = onOpen)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ConversationAvatar(conversation, team, 40.dp)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = if (conversation.unread) FontWeight.SemiBold else FontWeight.Medium,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formatting.shortTime(context, conversation.sortDate),
                    fontSize = 12.sp,
                    color = palette.secondaryText,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    preview,
                    fontSize = 15.sp,
                    color = if (conversation.unread) palette.text else palette.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (conversation.unread) {
                    UnreadDot()
                }
            }
        }
    }
}
