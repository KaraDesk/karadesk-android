package com.karadesk.internal.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karadesk.KaraDesk
import com.karadesk.R
import com.karadesk.internal.model.AuthorKind
import com.karadesk.internal.model.MessageAuthor
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

internal data class InAppItem(
    val id: String,
    val conversationId: Long,
    val author: MessageAuthor,
    val preview: String,
)

internal object InAppMessages {
    private const val VISIBLE_MS = 8_000L
    private const val TAG_KEY = 0x4B44_4D53

    private val handler = Handler(Looper.getMainLooper())
    private val visibility = MutableTransitionState(false)
    private var item by mutableStateOf<InAppItem?>(null)
    private var onOpen: ((Long) -> Unit)? = null
    private var host: WeakReference<ComposeView>? = null
    private val hide = Runnable { dismiss() }

    fun show(id: String, conversationId: Long, author: MessageAuthor, body: String, open: (Long) -> Unit) {
        handler.post {
            val preview = Formatting.plain(body).trim()
            item = InAppItem(id, conversationId, author, preview)
            onOpen = open
            visibility.targetState = true

            ActivityTracker.current?.let(::attach)
            announce(author, preview)

            handler.removeCallbacks(hide)
            handler.postDelayed(hide, VISIBLE_MS)
        }
    }

    fun dismiss() {
        handler.removeCallbacks(hide)
        visibility.targetState = false
    }

    fun attach(activity: Activity) {
        if (activity !is ComponentActivity || item == null || !visibility.targetState) {
            return
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        if (content.findViewWithTag<View>(TAG_KEY) != null) {
            return
        }

        val view = ComposeView(activity).apply {
            tag = TAG_KEY
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { Overlay() }
        }

        content.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM),
        )
        host = WeakReference(view)
    }

    fun detach(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.findViewWithTag<View>(TAG_KEY)?.let(content::removeView)
    }

    private fun announce(author: MessageAuthor, preview: String) {
        val view = host?.get() ?: return
        val name = author.name ?: view.context.getString(R.string.karadesk_team)

        @Suppress("DEPRECATION")
        view.announceForAccessibility("$name: $preview")
    }

    private fun open(conversationId: Long) {
        val action = onOpen
        dismiss()
        action?.invoke(conversationId)
    }

    @Composable
    private fun Overlay() {
        val current = item ?: return

        KaraDeskThemed(KaraDesk.store?.settings) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = slideInVertically(spring(dampingRatio = 0.82f)) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    Card(current)
                }
            }
        }
    }

    @Composable
    private fun Card(current: InAppItem) {
        val palette = KaraDeskTheme.palette
        val shape = RoundedCornerShape(24.dp)
        var drag by remember { mutableFloatStateOf(0f) }
        val name = current.author.name?.takeIf { it.isNotEmpty() }
            ?: if (current.author.kind == AuthorKind.BOT) "Kara" else stringResource(R.string.karadesk_team)
        val openLabel = stringResource(R.string.karadesk_open_conversation)

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .offset { IntOffset(0, drag.coerceAtLeast(0f).roundToInt()) }
                .pointerInput(current.id) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (drag > 40.dp.toPx()) dismiss()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    ) { _, amount -> drag += amount }
                }
                .shadow(18.dp, shape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f))
                .clip(shape)
                .background(palette.card)
                .border(1.dp, palette.cardBorder, shape)
                .clickableWithRole(openLabel) { open(current.conversationId) }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick(openLabel) {
                        open(current.conversationId)
                        true
                    }
                }
                .padding(14.dp),
        ) {
            if (current.author.kind == AuthorKind.BOT) BotAvatar(40.dp) else Avatar(current.author.name, current.author.avatarUrl, 40.dp)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(stringResource(R.string.karadesk_now), fontSize = 12.sp, color = palette.secondaryText)
                }

                Text(
                    current.preview.ifEmpty { stringResource(R.string.karadesk_sent_an_attachment) },
                    fontSize = 15.sp,
                    color = palette.text.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(palette.fill)
                    .clickableWithRole(stringResource(R.string.karadesk_close)) { dismiss() },
            ) {
                Icon(KaraDeskIcons.Close, stringResource(R.string.karadesk_close), tint = palette.secondaryText, modifier = Modifier.size(14.dp))
            }
        }
    }
}
