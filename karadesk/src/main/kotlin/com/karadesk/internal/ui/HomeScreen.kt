package com.karadesk.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karadesk.R
import com.karadesk.internal.model.AuthorKind
import com.karadesk.internal.model.Conversation
import com.karadesk.internal.model.HomeCard
import com.karadesk.internal.model.Person
import com.karadesk.internal.store.MessengerStore

private const val POWERED_BY_URL = "https://karadesk.com/?utm_source=messenger&utm_medium=powered_by&utm_campaign=android"

@Composable
internal fun HomeScreen(store: MessengerStore, router: Router, onClose: () -> Unit) {
    val boot by store.boot.collectAsStateWithLifecycle()
    val settings = boot?.settings
    val brand = KaraDeskTheme.brand
    val palette = KaraDeskTheme.palette
    val context = LocalContext.current

    StatusBarIcons(light = brand.headerForeground == Color.White)

    Box(Modifier.fillMaxSize().background(palette.background)) {
        HeaderBackground(
            Modifier
                .fillMaxWidth()
                .height(460.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 4.dp),
            ) {
                Box(Modifier.weight(1f)) { Logo(store) }

                val team = boot?.team.orEmpty()

                if (settings?.showTeam == true && team.isNotEmpty()) {
                    AvatarStack(team, size = 34.dp, ring = brand.headerBase)
                }

                CloseButton(onClose, tint = brand.headerForeground)
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 40.dp, bottom = 18.dp).semantics(mergeDescendants = true) { heading() },
                ) {
                    val style = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = brand.headerForeground)

                    settings?.greeting?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = style, modifier = Modifier.graphicsLayer(alpha = 0.7f))
                    }
                    settings?.intro?.takeIf { it.isNotEmpty() }?.let { Text(it, style = style) }
                }

                settings?.homeCards.orEmpty().forEach { card ->
                    when (card) {
                        is HomeCard.Messages -> MessagesCard(store, router)
                        is HomeCard.Help -> HelpCard(card, router)
                        is HomeCard.Link -> LinkCard(card)
                        is HomeCard.Text -> TextCard(card)
                    }
                }

                if (settings?.showBranding == true) {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        PoweredBy { openUrl(context, POWERED_BY_URL) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Logo(store: MessengerStore) {
    val boot by store.boot.collectAsStateWithLifecycle()
    val settings = boot?.settings
    val dark = KaraDeskTheme.brand.dark
    val logo = if (dark) settings?.darkLogoUrl ?: settings?.logoUrl else settings?.logoUrl
    val spaceName = boot?.spaceName.orEmpty()

    when {
        logo != null -> RemoteImage(
            logo,
            spaceName,
            Modifier.widthIn(max = 140.dp).height(32.dp),
            contentScale = ContentScale.Fit,
        )
        boot?.spaceAvatarUrl != null -> RemoteImage(
            boot?.spaceAvatarUrl,
            spaceName,
            Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)),
            contentScale = ContentScale.Fit,
        )
        spaceName.isNotEmpty() -> Text(
            spaceName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = KaraDeskTheme.brand.headerForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MessagesCard(store: MessengerStore, router: Router) {
    val conversations by store.conversations.collectAsStateWithLifecycle()
    val boot by store.boot.collectAsStateWithLifecycle()
    val brand = KaraDeskTheme.brand
    val palette = KaraDeskTheme.palette
    val recent = conversations.filter { it.mergedIntoId == null && it.lastMessage != null }.take(3)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (store.rules.allowNew) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .card()
                    .clickableWithRole { router.push(Route.Chat(store.singleOpenConversationId, null)) }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.karadesk_send_us_a_message), fontWeight = FontWeight.SemiBold, color = palette.text, fontSize = 16.sp)
                    Text(replyExpectation(boot?.settings?.availability?.replyTime), color = palette.secondaryText, fontSize = 14.sp)
                }

                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(brand.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KaraDeskIcons.Send, null, tint = brand.onAccent, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (recent.isNotEmpty()) {
            Column(Modifier.card()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                ) {
                    Text(stringResource(R.string.karadesk_recent_messages), fontWeight = FontWeight.SemiBold, color = palette.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { router.select(Tab.MESSAGES) }) {
                        Text(stringResource(R.string.karadesk_see_all), color = palette.secondaryText, fontSize = 14.sp)
                    }
                }

                recent.forEachIndexed { index, conversation ->
                    if (index > 0) {
                        HorizontalDivider(color = palette.separator, modifier = Modifier.padding(start = 78.dp))
                    }

                    RecentConversationRow(conversation, boot?.team.orEmpty()) {
                        router.push(Route.Chat(conversation.id, null))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentConversationRow(conversation: Conversation, team: List<Person>, onOpen: () -> Unit) {
    val palette = KaraDeskTheme.palette
    val context = LocalContext.current
    val last = conversation.lastMessage
    val author = when (last?.authorType) {
        AuthorKind.CONTACT -> stringResource(R.string.karadesk_you)
        AuthorKind.BOT -> last.authorName ?: "Kara"
        else -> Formatting.firstName(last?.authorName ?: conversation.assignee?.name) ?: stringResource(R.string.karadesk_team)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithRole(onClick = onOpen)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        ConversationAvatar(conversation, team, 44.dp)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                Formatting.plain(last?.body.orEmpty()),
                fontWeight = if (conversation.unread) FontWeight.SemiBold else FontWeight.Normal,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
            )
            Text(
                "$author · ${Formatting.relative(context, conversation.sortDate)}",
                color = palette.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
            )
        }

        if (conversation.unread) {
            UnreadDot()
        }

        Icon(KaraDeskIcons.ChevronRight, null, tint = palette.tertiaryText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HelpCard(card: HomeCard.Help, router: Router) {
    val brand = KaraDeskTheme.brand
    val palette = KaraDeskTheme.palette
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }

    val search = {
        focus.clearFocus()
        router.search(query)
        query = ""
    }

    Column(Modifier.card().padding(bottom = if (card.articles.isEmpty()) 0.dp else 8.dp)) {
        if (card.showSearch) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.fill)
                    .padding(start = 16.dp, end = 4.dp),
            ) {
                val placeholder = stringResource(R.string.karadesk_search_for_help)

                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text),
                    cursorBrush = SolidColor(brand.tint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(placeholder, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                            }
                            field()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )

                androidx.compose.material3.IconButton(onClick = search) {
                    Icon(KaraDeskIcons.Search, placeholder, tint = brand.tint)
                }
            }
        }

        if (!card.title.isNullOrEmpty() && card.articles.isNotEmpty()) {
            Text(
                card.title.uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.secondaryText,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = if (card.showSearch) 6.dp else 18.dp, bottom = 2.dp),
            )
        }

        card.articles.forEach { article ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableWithRole { router.push(Route.Article(card.helpCenter.id, article.id)) }
                    .padding(horizontal = 20.dp, vertical = 13.dp),
            ) {
                Text(article.title, color = palette.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(KaraDeskIcons.ChevronRight, null, tint = brand.tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LinkCard(card: HomeCard.Link) {
    val context = LocalContext.current
    val brand = KaraDeskTheme.brand

    CardRow(card.title, card.description, Modifier.clickableWithRole { openUrl(context, card.url) }) {
        Icon(KaraDeskIcons.ArrowOutward, null, tint = brand.tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TextCard(card: HomeCard.Text) {
    val palette = KaraDeskTheme.palette
    val brand = KaraDeskTheme.brand

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.card().padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        card.title?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontWeight = FontWeight.SemiBold, color = palette.text, fontSize = 16.sp)
        }
        Text(Markdown.parse(card.body, brand.tint), color = palette.secondaryText, fontSize = 14.sp)
    }
}

@Composable
private fun CardRow(title: String, subtitle: String?, modifier: Modifier, accessory: @Composable () -> Unit) {
    val palette = KaraDeskTheme.palette

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.card().then(modifier).padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = palette.text, fontSize = 16.sp)
            subtitle?.takeIf { it.isNotEmpty() }?.let { Text(it, color = palette.secondaryText, fontSize = 14.sp) }
        }
        accessory()
    }
}

@Composable
internal fun UnreadDot() {
    Box(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(KaraDeskTheme.brand.tint)
            .semantics { },
    )
}

@Composable
internal fun replyExpectation(replyTime: String?): String {
    if (replyTime.isNullOrEmpty()) {
        return stringResource(R.string.karadesk_reply_soon)
    }

    return stringResource(R.string.karadesk_reply_time, replyPhrase(replyTime))
}

internal fun replyPhrase(replyTime: String): String =
    if (replyTime.startsWith("in ", ignoreCase = true)) {
        replyTime
    } else {
        "in $replyTime"
    }

@Composable
internal fun ConversationAvatar(conversation: Conversation, team: List<Person>, size: androidx.compose.ui.unit.Dp) {
    val assignee = conversation.assignee
    val first = team.firstOrNull()

    when {
        conversation.lastMessage?.authorType == AuthorKind.BOT -> BotAvatar(size)
        assignee != null -> Avatar(assignee.name, assignee.avatarUrl, size)
        first != null -> Avatar(first.name, first.avatarUrl, size)
        else -> Avatar(null, null, size)
    }
}
