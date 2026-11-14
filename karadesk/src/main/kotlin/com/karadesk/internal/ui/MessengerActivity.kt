package com.karadesk.internal.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.karadesk.KaraDesk
import java.lang.ref.WeakReference

internal class MessengerActivity : ComponentActivity() {
    private val router = mutableStateOf<Router?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val store = KaraDesk.store

        if (store == null) {
            finish()
            return
        }

        open.add(WeakReference(this))
        router.value = Router(destinationOf(intent), styleOf(intent) == KaraDesk.PresentationStyle.STANDALONE)

        setContent {
            router.value?.let { current ->
                MessengerRoot(store = store, router = current, onClose = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val standalone = styleOf(intent) == KaraDesk.PresentationStyle.STANDALONE
        val existing = router.value

        if (existing != null && existing.standalone == standalone) {
            existing.open(destinationOf(intent))
        } else {
            router.value = Router(destinationOf(intent), standalone)
        }
    }

    override fun onResume() {
        super.onResume()
        KaraDesk.store?.isMessengerVisible = true
        InAppMessages.dismiss()
    }

    override fun onPause() {
        super.onPause()
        KaraDesk.store?.isMessengerVisible = open.any { it.get()?.let { activity -> activity !== this && !activity.isFinishing } == true }
    }

    override fun onDestroy() {
        open.removeAll { it.get() == null || it.get() === this }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_KIND = "com.karadesk.destination"
        private const val EXTRA_ID = "com.karadesk.destination.id"
        private const val EXTRA_MESSAGE = "com.karadesk.destination.message"
        private const val EXTRA_STYLE = "com.karadesk.style"

        private val open = mutableListOf<WeakReference<MessengerActivity>>()

        val isOpen: Boolean
            get() = open.any { it.get()?.isFinishing == false }

        fun finishAll() {
            open.mapNotNull { it.get() }.forEach(Activity::finish)
        }

        fun start(context: Context, destination: KaraDesk.Destination, style: KaraDesk.PresentationStyle) {
            val intent = Intent(context, MessengerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_STYLE, style.name)

            when (destination) {
                KaraDesk.Destination.Home -> intent.putExtra(EXTRA_KIND, "home")
                KaraDesk.Destination.Messages -> intent.putExtra(EXTRA_KIND, "messages")
                KaraDesk.Destination.HelpCenter -> intent.putExtra(EXTRA_KIND, "help")
                is KaraDesk.Destination.NewConversation -> intent.putExtra(EXTRA_KIND, "new").putExtra(EXTRA_MESSAGE, destination.message)
                is KaraDesk.Destination.Conversation -> intent.putExtra(EXTRA_KIND, "conversation").putExtra(EXTRA_ID, destination.id)
                is KaraDesk.Destination.Article -> intent.putExtra(EXTRA_KIND, "article").putExtra(EXTRA_ID, destination.id)
            }

            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        }

        private fun destinationOf(intent: Intent?): KaraDesk.Destination {
            val id = intent?.getLongExtra(EXTRA_ID, -1) ?: -1

            return when (intent?.getStringExtra(EXTRA_KIND)) {
                "messages" -> KaraDesk.Destination.Messages
                "help" -> KaraDesk.Destination.HelpCenter
                "new" -> KaraDesk.Destination.NewConversation(intent.getStringExtra(EXTRA_MESSAGE))
                "conversation" -> if (id >= 0) KaraDesk.Destination.Conversation(id) else KaraDesk.Destination.Messages
                "article" -> if (id >= 0) KaraDesk.Destination.Article(id) else KaraDesk.Destination.HelpCenter
                else -> KaraDesk.Destination.Home
            }
        }

        private fun styleOf(intent: Intent?): KaraDesk.PresentationStyle =
            runCatching { KaraDesk.PresentationStyle.valueOf(intent?.getStringExtra(EXTRA_STYLE).orEmpty()) }
                .getOrDefault(KaraDesk.PresentationStyle.STANDARD)
    }
}
