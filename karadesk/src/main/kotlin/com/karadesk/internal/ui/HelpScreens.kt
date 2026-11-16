package com.karadesk.internal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karadesk.KaraDeskException
import com.karadesk.R
import com.karadesk.internal.model.ArticleReaction
import com.karadesk.internal.model.HelpArticle
import com.karadesk.internal.model.HelpArticleSummary
import com.karadesk.internal.model.HelpCollection
import com.karadesk.internal.model.objects
import com.karadesk.internal.model.withTotals
import com.karadesk.internal.net.Requests
import com.karadesk.internal.store.MessengerStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

private suspend fun MessengerStore.collections(helpCenterId: Long): List<HelpCollection> =
    (help(Requests.collections(helpCenterId)) as? JsonObject)?.get("data").objects().mapNotNull(HelpCollection::from)

private suspend fun MessengerStore.articles(helpCenterId: Long, collectionId: Long): List<HelpArticleSummary> =
    (help(Requests.articles(helpCenterId, collectionId)) as? JsonObject)?.get("data").objects().mapNotNull(HelpArticleSummary::from)

private suspend fun MessengerStore.search(helpCenterId: Long, query: String): List<HelpArticleSummary> =
    (help(Requests.search(helpCenterId, query)) as? JsonObject)?.get("data").objects().mapNotNull(HelpArticleSummary::from)

@Composable
internal fun HelpScreen(store: MessengerStore, router: Router, onClose: () -> Unit) {
    val boot by store.boot.collectAsStateWithLifecycle()
    val helpCenter = boot?.settings?.helpCenter
    val palette = KaraDeskTheme.palette
    var collections by remember { mutableStateOf<List<HelpCollection>?>(null) }
    var results by remember { mutableStateOf<List<HelpArticleSummary>?>(null) }
    var loadError by remember { mutableStateOf<KaraDeskException?>(null) }
    var searchError by remember { mutableStateOf<KaraDeskException?>(null) }
    val query = router.helpQuery

    suspend fun loadCollections() {
        val center = helpCenter

        if (center == null) {
            collections = emptyList()
            return
        }

        loadError = null

        try {
            collections = store.collections(center.id).withTotalsRoots()
        } catch (error: KaraDeskException) {
            loadError = error
        }
    }

    suspend fun runSearch(term: String) {
        val center = helpCenter ?: return

        try {
            results = store.search(center.id, term)
            searchError = null
        } catch (error: KaraDeskException) {
            results = null
            searchError = error
        }
    }

    DefaultStatusBarIcons()

    LaunchedEffect(helpCenter?.id) {
        loadCollections()
    }

    LaunchedEffect(query, helpCenter?.id) {
        val term = query.trim()

        if (helpCenter == null || term.isEmpty()) {
            results = null
            searchError = null
            return@LaunchedEffect
        }

        delay(250)
        runSearch(term)
    }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        MessengerTopBar(
            title = { Text(stringResource(R.string.karadesk_help), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
            router = router,
            onClose = onClose,
            centered = true,
        )

        SearchField(query, onChange = { router.helpQuery = it })

        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
                if (query.isBlank()) {
                    val list = collections

                    if (list != null && list.isEmpty() && loadError == null) {
                        item { EmptyText(stringResource(R.string.karadesk_no_articles)) }
                    }

                    groupedRows(list.orEmpty(), key = { "c${it.id}" }) { collection ->
                        CollectionRow(collection) {
                            helpCenter?.let { router.push(Route.Collection(it.id, collection.id, collection.name)) }
                        }
                    }
                } else {
                    val list = results
                    val failure = searchError

                    if (failure != null) {
                        item { InlineError(failure) { runSearch(query.trim()) } }
                    } else if (list != null && list.isEmpty()) {
                        item { EmptyText(stringResource(R.string.karadesk_no_results)) }
                    }

                    groupedRows(list.orEmpty(), key = { "a${it.id}" }) { article ->
                        ArticleRow(article) { router.push(Route.Article(helpCenter?.id, article.id)) }
                    }
                }
            }

            val failure = loadError

            if (query.isBlank() && failure != null && collections.isNullOrEmpty()) {
                ErrorState(failure, title = stringResource(R.string.karadesk_help_load_failed)) { loadCollections() }
            } else if (collections == null && query.isBlank()) {
                Spinner()
            }
        }
    }
}

private fun List<HelpCollection>.withTotalsRoots(): List<HelpCollection> =
    withTotals().filter { it.parentId == null }

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val palette = KaraDeskTheme.palette
    val brand = KaraDeskTheme.brand
    val focus = LocalFocusManager.current
    val placeholder = stringResource(R.string.karadesk_search_for_help)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.fill)
            .padding(horizontal = 12.dp),
    ) {
        Icon(KaraDeskIcons.Search, null, tint = palette.secondaryText, modifier = Modifier.size(20.dp))

        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = palette.text),
            cursorBrush = SolidColor(brand.tint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(placeholder, fontSize = 16.sp, color = palette.secondaryText)
                    }
                    field()
                }
            },
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
        )

        if (query.isNotEmpty()) {
            Icon(
                KaraDeskIcons.Cancel,
                stringResource(R.string.karadesk_close),
                tint = palette.tertiaryText,
                modifier = Modifier.size(18.dp).clickableWithRole { onChange("") },
            )
        }
    }
}

@Composable
internal fun CollectionScreen(store: MessengerStore, router: Router, route: Route.Collection, onClose: () -> Unit) {
    val palette = KaraDeskTheme.palette
    var children by remember { mutableStateOf<List<HelpCollection>?>(null) }
    var articles by remember { mutableStateOf<List<HelpArticleSummary>?>(null) }
    var loadError by remember { mutableStateOf<KaraDeskException?>(null) }

    suspend fun load() {
        loadError = null

        try {
            coroutineScope {
                val loadedChildren = async { store.collections(route.helpCenterId) }
                val loadedArticles = async { store.articles(route.helpCenterId, route.collectionId) }

                children = loadedChildren.await().withTotals().filter { it.parentId == route.collectionId }
                articles = loadedArticles.await()
            }
        } catch (error: KaraDeskException) {
            loadError = error
        }
    }

    DefaultStatusBarIcons()

    LaunchedEffect(route) {
        load()
    }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        MessengerTopBar(
            title = { Text(route.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            router = router,
            onClose = onClose,
        )

        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
                val nested = children.orEmpty()
                val list = articles.orEmpty()

                if (nested.isNotEmpty()) {
                    item(key = "collections-header") { SectionHeader(stringResource(R.string.karadesk_collections)) }
                    groupedRows(nested, key = { "c${it.id}" }) { child ->
                        CollectionRow(child) { router.push(Route.Collection(route.helpCenterId, child.id, child.name)) }
                    }
                }

                if (list.isNotEmpty()) {
                    item(key = "articles-header") { SectionHeader(stringResource(R.string.karadesk_articles)) }
                    groupedRows(list, key = { "a${it.id}" }) { article ->
                        ArticleRow(article) { router.push(Route.Article(route.helpCenterId, article.id)) }
                    }
                }

                if (children != null && articles != null && nested.isEmpty() && list.isEmpty()) {
                    item { EmptyText(stringResource(R.string.karadesk_no_articles)) }
                }
            }

            val failure = loadError

            if (failure != null) {
                ErrorState(failure, title = stringResource(R.string.karadesk_help_load_failed)) { load() }
            } else if (children == null || articles == null) {
                Spinner()
            }
        }
    }
}

private fun <T> LazyListScope.groupedRows(items: List<T>, key: (T) -> Any, row: @Composable (T) -> Unit) {
    items(items.withIndex().toList(), key = { key(it.value) }) { (index, item) ->
        val palette = KaraDeskTheme.palette
        val first = index == 0
        val last = index == items.lastIndex
        val shape = RoundedCornerShape(
            topStart = if (first) 14.dp else 0.dp,
            topEnd = if (first) 14.dp else 0.dp,
            bottomStart = if (last) 14.dp else 0.dp,
            bottomEnd = if (last) 14.dp else 0.dp,
        )

        Column(Modifier.clip(shape).background(palette.card)) {
            row(item)

            if (!last) {
                HorizontalDivider(color = palette.separator, modifier = Modifier.padding(start = 60.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = KaraDeskTheme.palette.secondaryText,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp).semantics { heading() },
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = KaraDeskTheme.palette.secondaryText, modifier = Modifier.padding(16.dp))
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = KaraDeskTheme.brand.tint, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun HelpRowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val palette = KaraDeskTheme.palette

    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(palette.fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = palette.secondaryText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DisclosureRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithRole(onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.weight(1f)) { content() }
        Icon(KaraDeskIcons.ChevronRight, null, tint = KaraDeskTheme.palette.tertiaryText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CollectionRow(collection: HelpCollection, onClick: () -> Unit) {
    val palette = KaraDeskTheme.palette

    DisclosureRow(onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HelpRowIcon(KaraDeskIcons.Folder)

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(collection.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = palette.text)
                collection.description?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 14.sp, color = palette.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    pluralStringResource(R.plurals.karadesk_article_count, collection.articleCount, collection.articleCount),
                    fontSize = 12.sp,
                    color = palette.tertiaryText,
                )
            }
        }
    }
}

@Composable
private fun ArticleRow(article: HelpArticleSummary, onClick: () -> Unit) {
    val palette = KaraDeskTheme.palette

    DisclosureRow(onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HelpRowIcon(KaraDeskIcons.Document)

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(article.title, fontSize = 16.sp, color = palette.text)
                article.description?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 14.sp, color = palette.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun ArticleScreen(store: MessengerStore, router: Router, route: Route.Article, onClose: () -> Unit) {
    val boot by store.boot.collectAsStateWithLifecycle()
    val palette = KaraDeskTheme.palette
    val helpCenter = boot?.settings?.helpCenter(route.helpCenterId)
    var article by remember { mutableStateOf<HelpArticle?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<KaraDeskException?>(null) }
    var reaction by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DefaultStatusBarIcons()

    suspend fun load() {
        val id = route.helpCenterId ?: boot?.settings?.helpCenter?.id

        if (id == null) {
            failed = true
            return
        }

        loadError = null

        try {
            article = (store.help(Requests.article(id, route.articleId)) as? JsonObject)?.let(HelpArticle::from)
            failed = article == null
        } catch (error: KaraDeskException) {
            loadError = error
            failed = true
        }
    }

    LaunchedEffect(route) {
        load()
    }

    Column(Modifier.fillMaxSize().background(palette.surface)) {
        MessengerTopBar(title = {}, router = router, onClose = onClose)

        Box(Modifier.weight(1f)) {
            val current = article

            when {
                current != null -> Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            current.title,
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.text,
                            modifier = Modifier.semantics { heading() },
                        )
                        current.description?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, fontSize = 19.sp, lineHeight = 25.sp, color = palette.secondaryText)
                        }
                    }

                    Byline(current)

                    current.body?.let { RichText(it, helpCenter?.url) }
                }
                loadError?.isRetryable == true -> ErrorState(
                    loadError!!,
                    title = stringResource(R.string.karadesk_article_load_failed),
                ) { load() }
                failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().padding(top = 80.dp),
                ) {
                    Icon(KaraDeskIcons.Document, null, tint = palette.secondaryText, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.karadesk_article_unavailable), color = palette.secondaryText, modifier = Modifier.padding(top = 12.dp))
                }
                else -> Spinner()
            }
        }

        if (article != null && helpCenter?.articleReactions == true) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.card)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = 16.dp, bottom = 8.dp),
            ) {
                HorizontalDivider(color = palette.separator, modifier = Modifier.padding(bottom = 6.dp))

                AnimatedContent(reaction == null, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "reaction") { asking ->
                    Text(
                        stringResource(if (asking) R.string.karadesk_was_this_helpful else R.string.karadesk_thanks_for_reaction),
                        fontSize = 15.sp,
                        color = palette.secondaryText,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    ArticleReaction.entries.forEach { option ->
                        val chosen = reaction == option.wire
                        val scale by animateFloatAsState(if (chosen) 1.15f else 1f, label = "reaction-scale")
                        val label = stringResource(
                            when (option) {
                                ArticleReaction.SAD -> R.string.karadesk_reaction_sad
                                ArticleReaction.NEUTRAL -> R.string.karadesk_reaction_neutral
                                ArticleReaction.HAPPY -> R.string.karadesk_reaction_happy
                            },
                        )

                        Text(
                            option.emoji,
                            fontSize = 32.sp,
                            modifier = Modifier
                                .scale(scale)
                                .graphicsLayer(alpha = if (reaction == null || chosen) 1f else 0.35f)
                                .clickableWithRole(label) {
                                    reaction = option.wire
                                    scope.launch { store.react(option, route.articleId) }
                                }
                                .semantics {
                                    contentDescription = label
                                    selected = chosen
                                },
                        )
                    }
                }
            }
        } else {
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Byline(article: HelpArticle) {
    val palette = KaraDeskTheme.palette
    val author = Formatting.firstName(article.author?.name)
    val updated = article.updatedAt?.let { Formatting.longDate(it) }

    if (author == null && updated == null) {
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        article.author?.let { Avatar(it.name, it.avatarUrl, 40.dp) }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            author?.let { Text(stringResource(R.string.karadesk_written_by, it), fontSize = 14.sp, color = palette.secondaryText) }
            updated?.let { Text(stringResource(R.string.karadesk_updated, it), fontSize = 14.sp, color = palette.secondaryText) }
        }
    }
}
