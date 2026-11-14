package com.karadesk.internal.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.karadesk.KaraDesk
import com.karadesk.R
import com.karadesk.internal.model.AuthorKind
import com.karadesk.internal.model.MessageAuthor
import com.karadesk.internal.ui.ActivityTracker
import com.karadesk.internal.ui.InAppMessages
import com.karadesk.internal.ui.MessengerActivity
import kotlinx.coroutines.launch

internal object PushHandler {
    const val CHANNEL_ID = "karadesk_messages"
    private const val NOTIFICATION_TAG = "karadesk"
    private const val ICON_META_DATA = "com.karadesk.notification_icon"

    private var lastHandled: Pair<PushPayload, Long>? = null

    fun installTapHandling() {
        ActivityTracker.onIntent = { activity, intent ->
            if (KaraDesk.opensConversationsFromNotifications) {
                handleTap(activity, intent)
            }
        }
    }

    @Synchronized
    private fun claim(payload: PushPayload): Boolean {
        val now = SystemClock.elapsedRealtime()
        val previous = lastHandled

        if (previous != null && previous.first.conversationId == payload.conversationId &&
            previous.first.messageId == payload.messageId && now - previous.second < 2_000
        ) {
            return false
        }

        lastHandled = payload to now
        return true
    }

    fun handleTap(context: Context, intent: Intent?): Boolean {
        intent ?: return false
        val payload = PushPayload.from(intent.extras) ?: return false

        PushPayload.strip(intent)

        if (KaraDesk.store == null || !claim(payload)) {
            return true
        }

        NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, payload.conversationId.toInt())
        KaraDesk.present(context, KaraDesk.Destination.Conversation(payload.conversationId))
        return true
    }

    fun handleMessage(context: Context, data: Map<String, String>): Boolean {
        val payload = PushPayload.from(data) ?: return false
        val store = KaraDesk.store

        store?.scope?.launch { store.refreshAndAnnounce() }

        if (MessengerActivity.isOpen) {
            return true
        }

        if (ActivityTracker.isForeground && KaraDesk.showsInAppMessages) {
            InAppMessages.show(
                id = payload.messageId ?: "push-${payload.conversationId}",
                conversationId = payload.conversationId,
                author = MessageAuthor(AuthorKind.USER, payload.title, null),
                body = payload.body.orEmpty(),
            ) { id ->
                KaraDesk.present(context, KaraDesk.Destination.Conversation(id))
            }
            return true
        }

        post(context, payload)
        return true
    }

    private fun post(context: Context, payload: PushPayload) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        launch.putExtra(PushPayload.MARKER, "1")
        launch.putExtra(PushPayload.CONVERSATION_ID, payload.conversationId.toString())
        payload.messageId?.let { launch.putExtra(PushPayload.MESSAGE_ID, it) }

        val pending = PendingIntent.getActivity(
            context,
            payload.conversationId.toInt(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(context))
            .setContentTitle(payload.title ?: context.applicationInfo.loadLabel(context.packageManager))
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, payload.conversationId.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.karadesk_notification_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.karadesk_notification_channel_description)
        }

        manager.createNotificationChannel(channel)
    }

    private fun smallIcon(context: Context): Int {
        val configured = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData?.getInt(ICON_META_DATA, 0) ?: 0
        }.getOrDefault(0)

        return configured.takeIf { it != 0 } ?: context.applicationInfo.icon
    }
}
