package com.karadesk.internal.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import com.karadesk.KaraDesk

internal enum class Tab { HOME, MESSAGES, HELP }

internal sealed interface Route {
    data class Chat(val conversationId: Long?, val draft: String?) : Route
    data class Collection(val helpCenterId: Long, val collectionId: Long, val title: String) : Route
    data class Article(val helpCenterId: Long?, val articleId: Long) : Route
}

@Stable
internal class Router(destination: KaraDesk.Destination, val standalone: Boolean) {
    var tab by mutableStateOf(Tab.HOME)
    var root by mutableStateOf<Route?>(null)
        private set
    var helpQuery by mutableStateOf("")

    private val stacks = mapOf(
        Tab.HOME to mutableStateListOf<Route>(),
        Tab.MESSAGES to mutableStateListOf<Route>(),
        Tab.HELP to mutableStateListOf<Route>(),
    )

    init {
        open(destination)
    }

    fun stack(tab: Tab): List<Route> = stacks.getValue(tab)

    val current: Route?
        get() = stack(tab).lastOrNull() ?: root

    val canGoBack: Boolean
        get() = stack(tab).isNotEmpty() || (!standalone && tab != Tab.HOME)

    fun open(destination: KaraDesk.Destination) {
        when (destination) {
            KaraDesk.Destination.Home -> show(Tab.HOME, emptyList())
            KaraDesk.Destination.Messages -> show(Tab.MESSAGES, emptyList())
            KaraDesk.Destination.HelpCenter -> show(Tab.HELP, emptyList())
            is KaraDesk.Destination.NewConversation -> show(Tab.MESSAGES, listOf(Route.Chat(null, destination.message)))
            is KaraDesk.Destination.Conversation -> show(Tab.MESSAGES, listOf(Route.Chat(destination.id, null)))
            is KaraDesk.Destination.Article -> show(Tab.HELP, listOf(Route.Article(null, destination.id)))
        }
    }

    fun search(query: String) {
        helpQuery = query.trim()
        stacks.getValue(Tab.HELP).clear()
        tab = Tab.HELP
    }

    fun push(route: Route) {
        stacks.getValue(tab).add(route)
    }

    fun pop(): Boolean {
        val stack = stacks.getValue(tab)

        if (stack.isNotEmpty()) {
            stack.removeAt(stack.lastIndex)
            return true
        }

        if (!standalone && tab != Tab.HOME) {
            tab = Tab.HOME
            return true
        }

        return false
    }

    fun replaceTop(route: Route) {
        val stack = stacks.getValue(tab)

        if (stack.isEmpty() && root != null) {
            root = route
            return
        }

        if (stack.isEmpty()) {
            stack.add(route)
        } else {
            stack[stack.lastIndex] = route
        }
    }

    fun select(tab: Tab) {
        if (this.tab == tab) {
            stacks.getValue(tab).clear()
        }

        this.tab = tab
    }

    private fun show(tab: Tab, path: List<Route>) {
        this.tab = tab
        stacks.values.forEach { it.clear() }

        if (standalone) {
            root = path.firstOrNull()
            stacks.getValue(tab).addAll(path.drop(1))
        } else {
            root = null
            stacks.getValue(tab).addAll(path)
        }
    }
}

internal fun openUrl(context: Context, url: String, toolbar: Color? = null) {
    val uri = Uri.parse(url)
    val scheme = uri.scheme?.lowercase()

    if (scheme != "http" && scheme != "https") {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
        return
    }

    val builder = CustomTabsIntent.Builder().setShowTitle(true)

    toolbar?.let {
        builder.setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(it.toArgb()).build())
    }

    try {
        builder.build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

internal fun Modifier.clickableWithRole(label: String? = null, role: Role = Role.Button, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = label, role = role, onClick = onClick)
