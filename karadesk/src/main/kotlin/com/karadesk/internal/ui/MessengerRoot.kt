package com.karadesk.internal.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karadesk.KaraDeskException
import com.karadesk.R
import com.karadesk.internal.store.MessengerStore
import com.karadesk.internal.store.Phase

@Composable
internal fun MessengerRoot(store: MessengerStore, router: Router, onClose: () -> Unit) {
    val phase by store.phase.collectAsStateWithLifecycle()
    val boot by store.boot.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current

    KaraDeskThemed(boot?.settings) {
        val toolbar = KaraDeskTheme.palette.surface
        val uriHandler = androidx.compose.runtime.remember(toolbar) {
            object : androidx.compose.ui.platform.UriHandler {
                override fun openUri(uri: String) = openUrl(context, uri, toolbar)
            }
        }

        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides uriHandler) {
            Box(Modifier.fillMaxSize().background(KaraDeskTheme.palette.surface)) {
                when (val current = phase) {
                    Phase.Ready -> Messenger(store, router, onClose)
                    is Phase.Failed -> Unavailable(current.error, onClose) { store.startQuietly() }
                    else -> {
                        LaunchedEffect(Unit) { store.startQuietly() }
                        Loading(onClose)
                    }
                }
            }
        }
    }

    BackHandler(enabled = phase == Phase.Ready && router.canGoBack) {
        router.pop()
    }
}

@Composable
internal fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !light
            controller.isAppearanceLightNavigationBars = !light
        }
    }
}

@Composable
internal fun DefaultStatusBarIcons() {
    StatusBarIcons(light = KaraDeskTheme.palette.surface.luminance() < 0.5f)
}

private data class ScreenKey(val tab: Tab, val depth: Int, val route: Route?)

@Composable
private fun Messenger(store: MessengerStore, router: Router, onClose: () -> Unit) {
    val unread by store.unreadConversationCount.collectAsStateWithLifecycle()
    val boot by store.boot.collectAsStateWithLifecycle()
    val showsHelp = boot?.settings?.let { it.helpCenter != null && it.showHelp } == true
    val atRoot = router.stack(router.tab).isEmpty()
    val holder = rememberSaveableStateHolder()
    val palette = KaraDeskTheme.palette
    val brand = KaraDeskTheme.brand

    Scaffold(
        containerColor = palette.surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = !router.standalone && atRoot,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = palette.card, tonalElevation = 0.dp) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = brand.tint,
                        selectedTextColor = brand.tint,
                        indicatorColor = palette.fill,
                        unselectedIconColor = palette.secondaryText,
                        unselectedTextColor = palette.secondaryText,
                    )

                    NavigationBarItem(
                        selected = router.tab == Tab.HOME,
                        onClick = { router.select(Tab.HOME) },
                        icon = { Icon(if (router.tab == Tab.HOME) KaraDeskIcons.HomeFilled else KaraDeskIcons.HomeOutlined, null) },
                        label = { Text(stringResource(R.string.karadesk_home)) },
                        colors = colors,
                    )
                    NavigationBarItem(
                        selected = router.tab == Tab.MESSAGES,
                        onClick = { router.select(Tab.MESSAGES) },
                        icon = {
                            BadgedBox(badge = {
                                if (unread > 0) {
                                    Badge { Text(unread.toString()) }
                                }
                            }) {
                                Icon(if (router.tab == Tab.MESSAGES) KaraDeskIcons.ChatFilled else KaraDeskIcons.ChatOutlined, null)
                            }
                        },
                        label = { Text(stringResource(R.string.karadesk_messages)) },
                        colors = colors,
                    )
                    if (showsHelp) {
                        NavigationBarItem(
                            selected = router.tab == Tab.HELP,
                            onClick = { router.select(Tab.HELP) },
                            icon = { Icon(if (router.tab == Tab.HELP) KaraDeskIcons.HelpFilled else KaraDeskIcons.HelpOutlined, null) },
                            label = { Text(stringResource(R.string.karadesk_help)) },
                            colors = colors,
                        )
                    }
                }
            }
        },
    ) { padding ->
        val key = ScreenKey(router.tab, router.stack(router.tab).size, router.current)

        AnimatedContent(
            targetState = key,
            transitionSpec = {
                when {
                    initialState.tab != targetState.tab -> fadeIn() togetherWith fadeOut()
                    targetState.depth == initialState.depth ->
                        androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                    targetState.depth > initialState.depth ->
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 } + fadeOut()
                    else -> slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it }
                }
            },
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            label = "messenger",
        ) { screen ->
            holder.SaveableStateProvider("${screen.tab}-${screen.depth}-${screen.route}") {
                Box(Modifier.fillMaxSize().background(palette.surface)) {
                    when (val route = screen.route) {
                        null -> when (screen.tab) {
                            Tab.HOME -> HomeScreen(store, router, onClose)
                            Tab.MESSAGES -> MessagesScreen(store, router, onClose)
                            Tab.HELP -> HelpScreen(store, router, onClose)
                        }
                        is Route.Chat -> ChatScreen(store, router, route, onClose)
                        is Route.Collection -> CollectionScreen(store, router, route, onClose)
                        is Route.Article -> ArticleScreen(store, router, route, onClose)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessengerTopBar(
    title: @Composable () -> Unit,
    router: Router,
    onClose: () -> Unit,
    centered: Boolean = false,
) {
    val palette = KaraDeskTheme.palette
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = palette.surface,
        scrolledContainerColor = palette.surface,
        titleContentColor = palette.text,
        navigationIconContentColor = palette.text,
        actionIconContentColor = palette.text,
    )
    val navigation: @Composable () -> Unit = {
        if (router.stack(router.tab).isNotEmpty()) {
            androidx.compose.material3.IconButton(onClick = { router.pop() }) {
                Icon(KaraDeskIcons.ArrowBack, contentDescription = stringResource(R.string.karadesk_back))
            }
        }
    }
    val actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = { CloseButton(onClose) }

    if (centered) {
        androidx.compose.material3.CenterAlignedTopAppBar(title = title, navigationIcon = navigation, actions = actions, colors = colors)
    } else {
        TopAppBar(title = title, navigationIcon = navigation, actions = actions, colors = colors)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Loading(onClose: () -> Unit) {
    DefaultStatusBarIcons()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            actions = { CloseButton(onClose) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KaraDeskTheme.brand.tint, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Unavailable(error: KaraDeskException, onClose: () -> Unit, retry: () -> Unit) {
    val palette = KaraDeskTheme.palette
    DefaultStatusBarIcons()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            actions = { CloseButton(onClose) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(KaraDeskIcons.Error, null, tint = palette.secondaryText, modifier = Modifier.size(44.dp))
            Text(
                stringResource(R.string.karadesk_messenger_unavailable),
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                if (error is KaraDeskException.Network) stringResource(R.string.karadesk_network_error) else error.message.orEmpty(),
                color = palette.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 320.dp),
            )
            if (error is KaraDeskException.Network) {
                ProminentButton(stringResource(R.string.karadesk_retry), retry, Modifier.padding(top = 20.dp))
            }
        }
    }
}
