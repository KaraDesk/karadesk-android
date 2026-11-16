package com.karadesk

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.karadesk.internal.model.AuthorKind
import com.karadesk.internal.model.MessageAuthor
import com.karadesk.internal.net.ApiClient
import com.karadesk.internal.net.NetworkMonitor
import com.karadesk.internal.push.PushHandler
import com.karadesk.internal.push.PushPayload
import com.karadesk.internal.store.DeviceContext
import com.karadesk.internal.store.IdentityStore
import com.karadesk.internal.store.MessengerStore
import com.karadesk.internal.ui.ActivityTracker
import com.karadesk.internal.ui.InAppMessages
import com.karadesk.internal.ui.MessengerActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The entry point to the KaraDesk messenger.
 *
 * Initialize KaraDesk once when your app starts, then present the messenger wherever your users
 * need help:
 *
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         KaraDesk.initialize(this, spaceId = "your-space-id", appId = "your-android-app-id")
 *     }
 * }
 * ```
 */
public object KaraDesk {
    /** A place in the messenger to open. */
    public sealed interface Destination {
        /** The messenger home, as if the user tapped your help button. This is the default. */
        public data object Home : Destination

        /** The list of the user's conversations. */
        public data object Messages : Destination

        /** A new conversation, optionally with the composer pre-filled. */
        public data class NewConversation(val message: String? = null) : Destination

        /** An existing conversation. */
        public data class Conversation(val id: Long) : Destination

        /** The help center. */
        public data object HelpCenter : Destination

        /** A help center article. */
        public data class Article(val id: Long) : Destination
    }

    /** How the messenger is shown. */
    public enum class PresentationStyle {
        /** The full messenger, with Home, Messages and Help tabs. This is the default. */
        STANDARD,

        /**
         * Only the destination, without tabs. A conversation or an article opens on its own, and the
         * help center lets people browse and search articles without leaving it.
         */
        STANDALONE,
    }

    /** The version of the KaraDesk SDK. */
    @JvmField
    public val version: String = BuildConfig.SDK_VERSION

    /**
     * Whether a preview card slides up from the bottom of your app when your team replies while the
     * messenger is closed. Tapping the card opens the conversation. Defaults to `true`.
     */
    @JvmStatic
    @Volatile
    public var showsInAppMessages: Boolean = true

    /**
     * Whether tapping a KaraDesk notification opens its conversation automatically. Defaults to `true`.
     *
     * KaraDesk watches the intents your activities are opened with and only acts on its own
     * notifications. Set this to `false` before calling [initialize] to call [handleNotificationTap]
     * yourself instead.
     */
    @JvmStatic
    @Volatile
    public var opensConversationsFromNotifications: Boolean = true

    private val unread = MutableStateFlow(0)

    /**
     * The number of conversations with replies the user hasn't seen yet.
     *
     * Collect it to show a badge on your help button. It stays at zero until [initialize] is called.
     */
    @JvmStatic
    public val unreadConversationCount: StateFlow<Int> = unread.asStateFlow()

    @Volatile
    internal var store: MessengerStore? = null
        private set

    internal var application: Application? = null
        private set

    private var pendingPushToken: String? = null

    /**
     * Connects the SDK to your KaraDesk space.
     *
     * Call this once, as early as possible, from your [Application.onCreate]. Find both identifiers in
     * KaraDesk under Settings → Apps.
     *
     * @param application Your app's [Application].
     * @param spaceId The identifier of your KaraDesk space.
     * @param appId The identifier of your Android app in KaraDesk.
     */
    @JvmStatic
    public fun initialize(application: Application, spaceId: String, appId: String) {
        val space = spaceId.trim()
        val app = appId.trim()

        if (space.isEmpty() || app.isEmpty()) {
            Log.e(TAG, KaraDeskException.InvalidConfiguration().message.orEmpty())
            return
        }

        store?.let { existing ->
            if (existing.spaceId == space && existing.appId == app) {
                return
            }
        }

        val firstStart = this.application == null
        this.application = application
        NetworkMonitor.start(application)

        val created = MessengerStore(
            context = application,
            spaceId = space,
            appId = app,
            client = ApiClient(baseUrl = baseUrl(application), userAgent = DeviceContext.userAgent(application)),
            identity = IdentityStore(application, space, app),
        )

        created.onUnreadCountChange = { count -> unread.value = count }

        created.onIncomingMessage = { message ->
            if (showsInAppMessages) {
                InAppMessages.show(message.id, message.conversationId, message.author, message.previewText) { id ->
                    present(application, Destination.Conversation(id))
                }
            }
        }

        created.onUnreadWaiting = { conversation, preview ->
            if (showsInAppMessages) {
                val author = MessageAuthor(
                    kind = preview.authorType,
                    name = preview.authorName,
                    avatarUrl = if (preview.authorType == AuthorKind.USER) conversation.assignee?.avatarUrl else null,
                )

                InAppMessages.show(preview.id, conversation.id, author, preview.body) { id ->
                    present(application, Destination.Conversation(id))
                }
            }
        }

        store?.suspendRealtime()
        store = created

        if (firstStart) {
            ActivityTracker.install(application)

            if (opensConversationsFromNotifications) {
                PushHandler.installTapHandling()
            }

            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    val current = store ?: return

                    current.scope.launch {
                        current.resumeRealtime()
                        current.refreshAndAnnounce()
                    }
                }

                override fun onStop(owner: LifecycleOwner) {
                    store?.suspendRealtime()
                }
            })
        }

        pendingPushToken?.let(created::setPushToken)
        created.startQuietly()
    }

    /**
     * Signs in a user of your app so their conversations follow them across devices.
     *
     * Call this after your own sign-in completes, and again on each launch while the user stays signed
     * in. Anything the user started before signing in moves to their account.
     *
     * @throws KaraDeskException if the user can't be signed in.
     */
    @JvmStatic
    public suspend fun login(user: KaraDeskUser) {
        val current = store ?: throw KaraDeskException.NotInitialized()

        if (!user.isIdentifiable) {
            throw KaraDeskException.IdentityVerificationFailed("A user needs an id or an email.")
        }

        current.login(user)
    }

    /**
     * Updates details of the signed-in user, such as their name or avatar.
     *
     * Only the properties you set are changed; everything else stays as it was. The user's identity
     * can't change here. To switch to a different person, call [login].
     *
     * @throws KaraDeskException if no user is signed in or the update fails.
     */
    @JvmStatic
    public suspend fun updateUser(changes: KaraDeskUser) {
        val current = store ?: throw KaraDeskException.NotInitialized()
        current.update(changes)
    }

    /**
     * Signs the current user out and starts a fresh anonymous visitor.
     *
     * Call this when the user signs out of your app so the next person on this device can't see their
     * conversations.
     */
    @JvmStatic
    public fun logout() {
        val current = store ?: return
        current.logout()
        current.startQuietly()
    }

    /**
     * Shows the messenger over your app.
     *
     * ```kotlin
     * KaraDesk.present(context)
     * KaraDesk.present(context, KaraDesk.Destination.NewConversation("I need help with my order"))
     * KaraDesk.present(context, KaraDesk.Destination.HelpCenter, KaraDesk.PresentationStyle.STANDALONE)
     * ```
     *
     * If the messenger is already open, it moves to [destination].
     */
    @JvmStatic
    @JvmOverloads
    public fun present(
        context: Context,
        destination: Destination = Destination.Home,
        style: PresentationStyle = PresentationStyle.STANDARD,
    ) {
        if (store == null) {
            Log.e(TAG, KaraDeskException.NotInitialized().message.orEmpty())
            return
        }

        InAppMessages.dismiss()
        MessengerActivity.start(ActivityTracker.current ?: context, destination, style)
    }

    /** Closes the messenger if it's open. */
    @JvmStatic
    public fun dismiss() {
        MessengerActivity.finishAll()
    }

    /** Whether the messenger is currently on screen. */
    @JvmStatic
    public val isPresented: Boolean
        get() = MessengerActivity.isOpen

    /**
     * Registers this device for KaraDesk push notifications.
     *
     * Pass the Firebase Cloud Messaging token your app receives, from
     * `FirebaseMessagingService.onNewToken` and `FirebaseMessaging.getInstance().token`. KaraDesk sends
     * a push when your team replies while the user is away from your app, using the Firebase service
     * account you add in KaraDesk under Settings → Apps.
     */
    @JvmStatic
    public fun setDeviceToken(token: String) {
        val trimmed = token.trim()

        if (trimmed.isEmpty()) {
            return
        }

        pendingPushToken = trimmed
        store?.setPushToken(trimmed)
    }

    /**
     * Whether a push message came from KaraDesk.
     *
     * Pass `remoteMessage.data` from your `FirebaseMessagingService.onMessageReceived`.
     */
    @JvmStatic
    public fun isKaraDeskPush(data: Map<String, String>): Boolean = PushPayload.from(data) != null

    /**
     * Shows a KaraDesk push message that arrived while your app is running.
     *
     * Call this from `FirebaseMessagingService.onMessageReceived` with `remoteMessage.data`. While your
     * app is open, the reply slides up as an in-app preview, or nothing shows when the messenger is
     * already on screen. Otherwise KaraDesk posts a notification on its own notification channel.
     *
     * @return `false` for push messages that didn't come from KaraDesk.
     */
    @JvmStatic
    public fun handlePush(context: Context, data: Map<String, String>): Boolean =
        PushHandler.handleMessage(context.applicationContext, data)

    /**
     * Opens the conversation a tapped KaraDesk notification is about.
     *
     * KaraDesk does this for you unless you turned off [opensConversationsFromNotifications]. Pass the
     * intent your activity was started or resumed with.
     *
     * @return `false` for intents that didn't come from a KaraDesk notification.
     */
    @JvmStatic
    public fun handleNotificationTap(context: Context, intent: Intent?): Boolean =
        PushHandler.handleTap(context, intent)

    private fun baseUrl(application: Application): okhttp3.HttpUrl {
        val configured = runCatching {
            application.packageManager
                .getApplicationInfo(application.packageName, android.content.pm.PackageManager.GET_META_DATA)
                .metaData?.getString("com.karadesk.api_base_url")
        }.getOrNull()

        return configured?.toHttpUrlOrNull() ?: ApiClient.DEFAULT_BASE_URL.toHttpUrl()
    }

    internal const val TAG = "KaraDesk"
}
