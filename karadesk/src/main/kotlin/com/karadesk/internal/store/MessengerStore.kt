package com.karadesk.internal.store

import android.content.Context
import android.util.Log
import com.karadesk.KaraDeskException
import com.karadesk.KaraDeskUser
import com.karadesk.internal.model.ArticleReaction
import com.karadesk.internal.model.BootResponse
import com.karadesk.internal.model.Conversation
import com.karadesk.internal.model.ConversationPreview
import com.karadesk.internal.model.ConversationState
import com.karadesk.internal.model.ConversationThread
import com.karadesk.internal.model.InteractionResult
import com.karadesk.internal.model.Message
import com.karadesk.internal.model.MessageAuthor
import com.karadesk.internal.model.MessengerSettings
import com.karadesk.internal.model.Rules
import com.karadesk.internal.model.UploadTicket
import com.karadesk.internal.model.UploadedFile
import com.karadesk.internal.model.objects
import com.karadesk.internal.net.ApiClient
import com.karadesk.internal.net.ApiRequest
import com.karadesk.internal.net.BootInput
import com.karadesk.internal.net.RealtimeConnection
import com.karadesk.internal.net.RealtimeEvent
import com.karadesk.internal.net.Requests
import com.karadesk.internal.net.isExpiredSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant

internal sealed interface Phase {
    data object Idle : Phase
    data object Loading : Phase
    data object Ready : Phase
    data class Failed(val error: KaraDeskException) : Phase
}

internal data class Typing(val author: MessageAuthor, val until: Instant)

internal class MessengerStore(
    context: Context,
    val spaceId: String,
    val appId: String,
    private val client: ApiClient,
    private val identity: IdentityStore,
) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    private val _boot = MutableStateFlow<BootResponse?>(null)
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val _threads = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())
    private val _typing = MutableStateFlow<Map<Long, Typing>>(emptyMap())
    private val _unread = MutableStateFlow(0)
    private val _conversationsError = MutableStateFlow<KaraDeskException?>(null)

    val phase: StateFlow<Phase> = _phase.asStateFlow()
    val boot: StateFlow<BootResponse?> = _boot.asStateFlow()
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()
    val threads: StateFlow<Map<Long, List<Message>>> = _threads.asStateFlow()
    val typing: StateFlow<Map<Long, Typing>> = _typing.asStateFlow()
    val unreadConversationCount: StateFlow<Int> = _unread.asStateFlow()
    val conversationsError: StateFlow<KaraDeskException?> = _conversationsError.asStateFlow()

    var user: KaraDeskUser? = identity.user()
        private set

    var visibleConversationId: Long? = null
    var isMessengerVisible: Boolean = false
    var onIncomingMessage: ((Message) -> Unit)? = null
    var onUnreadWaiting: ((Conversation, ConversationPreview) -> Unit)? = null
    var onUnreadCountChange: ((Int) -> Unit)? = null

    private var bootTask: Deferred<BootResponse>? = null
    private var realtime: RealtimeConnection? = null
    private var realtimeJob: Job? = null
    private var generation = 0
    private var realtimeRejections = 0
    private var pushToken: String? = null
    private var pushRegisteredSession: String? = null
    private var pushUnregister: Job? = null

    val settings: MessengerSettings?
        get() = _boot.value?.settings

    val isIdentified: Boolean
        get() = _boot.value?.isIdentified == true

    val rules: Rules
        get() = settings?.rules(isIdentified) ?: Rules.standard

    val singleOpenConversationId: Long?
        get() = if (rules.singleOpen) {
            _conversations.value.firstOrNull { it.state != ConversationState.CLOSED && it.mergedIntoId == null }?.id
        } else {
            null
        }

    fun conversation(id: Long): Conversation? = _conversations.value.firstOrNull { it.id == id }

    // Identity

    suspend fun login(user: KaraDeskUser) {
        identity.setUser(user)
        this.user = user
        reset()
        start()
    }

    suspend fun update(changes: KaraDeskUser) {
        val current = user ?: throw KaraDeskException.NotLoggedIn()
        val merged = current.merging(changes)

        if (merged == current) {
            return
        }

        identity.setUser(merged)
        user = merged
        _boot.value = null
        session()
    }

    fun logout() {
        unregisterPushToken()
        identity.setUser(null)
        identity.rotateVisitorId()
        user = null
        reset()
    }

    // Session

    suspend fun start() {
        session()
        refreshConversations()
        announceWaitingReply()
    }

    fun startQuietly() {
        scope.launch { runCatching { start() } }
    }

    suspend fun refreshAndAnnounce() {
        if (_phase.value != Phase.Ready) {
            return
        }

        refreshConversations()
        announceWaitingReply()
    }

    private fun announceWaitingReply() {
        if (isMessengerVisible) {
            return
        }

        val waiting = _conversations.value
            .filter { it.unread && it.mergedIntoId == null }
            .mapNotNull { conversation -> conversation.lastMessage?.let { conversation to it } }
            .filter { it.second.authorType != com.karadesk.internal.model.AuthorKind.CONTACT }
            .maxByOrNull { it.second.createdAt } ?: return

        if (identity.announcedMessageId() == waiting.second.id) {
            return
        }

        markAnnounced(waiting.second.id)
        onUnreadWaiting?.invoke(waiting.first, waiting.second)
    }

    fun markAnnounced(messageId: String) {
        identity.setAnnouncedMessageId(messageId)
    }

    suspend fun session(): BootResponse {
        _boot.value?.let { boot ->
            if (Duration.between(Instant.now(), boot.expiresAt).seconds > 60) {
                return boot
            }
        }

        bootTask?.let { return awaitBoot(it) }

        val generation = generation
        val user = user
        val input = BootInput(
            spaceId = spaceId,
            appId = appId,
            visitorId = identity.visitorId(),
            userId = user?.id,
            email = user?.email,
            name = user?.name,
            phone = user?.phone,
            avatar = user?.avatarUrl,
            signedUpAt = user?.signedUpAt?.epochSecond,
            userHash = user?.userHash,
            device = DeviceContext.current(appContext),
        )

        val task = scope.async {
            val json = client.sendObject(Requests.boot(input), null)
            BootResponse.from(json) ?: throw KaraDeskException.UnexpectedResponse()
        }

        bootTask = task

        if (_phase.value != Phase.Ready) {
            _phase.value = Phase.Loading
        }

        return try {
            val response = awaitBoot(task)

            if (generation != this.generation) {
                return response
            }

            bootTask = null
            _boot.value = response
            _phase.value = Phase.Ready
            connectRealtime(response.token)
            scope.launch { registerPushToken() }
            response
        } catch (error: KaraDeskException) {
            if (generation == this.generation) {
                bootTask = null
                _phase.value = Phase.Failed(error)
            }

            when (error) {
                is KaraDeskException.Unavailable -> Log.e(TAG, "The messenger is unavailable: ${error.message}")
                is KaraDeskException.IdentityVerificationFailed -> Log.e(TAG, "Identity verification failed: ${error.message}")
                else -> Unit
            }

            throw error
        }
    }

    private suspend fun awaitBoot(task: Deferred<BootResponse>): BootResponse = try {
        task.await()
    } catch (error: KaraDeskException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw KaraDeskException.Network(error)
    }

    suspend fun refreshConversations() {
        val json = try {
            authorized(Requests.conversations()) as? JsonObject ?: throw KaraDeskException.UnexpectedResponse()
        } catch (error: KaraDeskException) {
            _conversationsError.value = error
            return
        }

        _conversationsError.value = null
        _conversations.value = json["data"].objects()
            .mapNotNull { runCatching { Conversation.from(it) }.getOrNull() }
            .sortedByDescending { it.sortDate }
        publishUnreadCount()
    }

    suspend fun reconnected() {
        when (_phase.value) {
            is Phase.Failed -> runCatching { start() }
            Phase.Ready -> {
                resumeRealtime()
                refreshConversations()
            }
            else -> Unit
        }
    }

    // Conversations

    suspend fun loadThread(conversationId: Long) {
        val json = authorized(Requests.conversation(conversationId)) as? JsonObject
            ?: throw KaraDeskException.UnexpectedResponse()
        val thread = ConversationThread.from(json) ?: throw KaraDeskException.UnexpectedResponse()

        _threads.value = _threads.value + (conversationId to thread.messages)
        upsert(thread.conversation)
    }

    suspend fun send(body: String, attachments: List<UploadedFile>, conversationId: Long?): Long {
        val assetIds = attachments.map { it.id }

        if (conversationId == null) {
            val hadContact = _boot.value?.contact != null
            val json = authorized(Requests.startConversation(body, assetIds)) as? JsonObject
                ?: throw KaraDeskException.UnexpectedResponse()
            val thread = ConversationThread.from(json) ?: throw KaraDeskException.UnexpectedResponse()

            _threads.value = _threads.value + (thread.conversation.id to thread.messages)
            upsert(thread.conversation)

            if (!hadContact) {
                reconnectRealtime()
            }

            return thread.conversation.id
        }

        val json = authorized(Requests.send(conversationId, body, assetIds)) as? JsonObject
            ?: throw KaraDeskException.UnexpectedResponse()
        val sent = Message.from(json) ?: throw KaraDeskException.UnexpectedResponse()

        append(sent)
        return sent.conversationId
    }

    suspend fun markRead(conversationId: Long, force: Boolean = false) {
        if (!force && conversation(conversationId)?.unread != true) {
            return
        }

        val json = runCatching { authorized(Requests.markRead(conversationId)) }.getOrNull() as? JsonObject ?: return
        Conversation.from(json)?.let(::upsert)
    }

    suspend fun rate(message: Message, rating: Int?, remark: String?) {
        val json = authorized(Requests.rate(message.conversationId, message.id, rating, remark)) as? JsonObject
            ?: throw KaraDeskException.UnexpectedResponse()
        Message.from(json)?.let(::replace)
    }

    suspend fun interact(message: Message, componentId: String, buttonId: String?, value: JsonPrimitive?) {
        val request = Requests.interact(message.conversationId, message.id, componentId, buttonId, value)
        val json = authorized(request) as? JsonObject ?: throw KaraDeskException.UnexpectedResponse()
        val result = InteractionResult.from(json) ?: throw KaraDeskException.UnexpectedResponse()

        replace(result.message)
        result.reply?.let(::append)
    }

    suspend fun upload(bytes: ByteArray, filename: String, contentType: String): UploadedFile {
        val ticketJson = authorized(Requests.startUpload(filename, contentType, bytes.size)) as? JsonObject
            ?: throw KaraDeskException.UnexpectedResponse()
        val ticket = UploadTicket.from(ticketJson) ?: throw KaraDeskException.UnexpectedResponse()

        client.upload(bytes, filename, contentType, ticket)

        val completed = authorized(Requests.completeUpload(ticket.asset.id)) as? JsonObject
            ?: throw KaraDeskException.UnexpectedResponse()
        return UploadedFile.from(completed) ?: throw KaraDeskException.UnexpectedResponse()
    }

    suspend fun react(reaction: ArticleReaction, articleId: Long) {
        runCatching { authorized(Requests.react(articleId, reaction.wire)) }
    }

    fun sendTyping(conversationId: Long) {
        realtime?.sendTyping(conversationId)
    }

    // Requests

    suspend fun help(request: ApiRequest) = client.send(request, null)

    suspend fun authorized(request: ApiRequest): kotlinx.serialization.json.JsonElement {
        val token = session().token

        return try {
            client.send(request, token)
        } catch (error: KaraDeskException) {
            if (error.isMessengerUnavailable) {
                Log.e(TAG, "The messenger became unavailable: ${error.message}")
                suspendRealtime()
                _phase.value = Phase.Failed(error)
                throw error
            }

            if (!error.isExpiredSession) {
                throw error
            }

            _boot.value = null
            client.send(request, session().token)
        }
    }

    // Push

    fun setPushToken(token: String) {
        if (token == pushToken) {
            return
        }

        pushToken = token
        pushRegisteredSession = null
        scope.launch { registerPushToken() }
    }

    private suspend fun registerPushToken() {
        pushUnregister?.join()

        val token = pushToken ?: return
        val session = _boot.value?.token ?: return

        if (_phase.value != Phase.Ready || pushRegisteredSession == session) {
            return
        }

        pushRegisteredSession = session

        try {
            client.send(Requests.registerPushToken(token), session)
        } catch (error: Exception) {
            if (pushRegisteredSession == session) {
                pushRegisteredSession = null
            }
            Log.w(TAG, "Couldn't register for push notifications: ${error.message}")
        }
    }

    private fun unregisterPushToken() {
        val token = pushToken ?: return
        val session = _boot.value?.token ?: return
        val previous = pushUnregister

        pushRegisteredSession = null
        pushUnregister = scope.launch {
            previous?.join()
            runCatching { client.send(Requests.removePushToken(token), session) }
        }
    }

    // Realtime

    fun suspendRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        realtime?.stop()
        realtime = null
    }

    suspend fun resumeRealtime() {
        if (_phase.value != Phase.Ready || realtime != null) {
            return
        }

        val response = runCatching { session() }.getOrNull() ?: return

        if (realtime == null) {
            connectRealtime(response.token)
        }
    }

    private fun connectRealtime(token: String) {
        realtimeJob?.cancel()
        realtime?.stop()

        val connection = RealtimeConnection(client.baseUrl, token, client.http)
        realtime = connection
        connection.start()

        realtimeJob = scope.launch {
            connection.events.collect { apply(it) }
        }
    }

    private fun reconnectRealtime() {
        _boot.value?.token?.let(::connectRealtime)
    }

    fun apply(event: RealtimeEvent) {
        when (event) {
            RealtimeEvent.Ready -> {
                realtimeRejections = 0
                scope.launch { refreshConversations() }
            }
            is RealtimeEvent.MessageCreated -> append(event.message)
            is RealtimeEvent.MessageUpdated -> replace(event.message)
            is RealtimeEvent.MessageDeleted -> {
                val thread = _threads.value[event.conversationId] ?: return
                _threads.value = _threads.value + (event.conversationId to thread.filterNot { it.id == event.messageId })
            }
            is RealtimeEvent.ConversationChanged -> upsert(event.conversation)
            is RealtimeEvent.Typing -> {
                _typing.value = _typing.value + (event.conversationId to Typing(event.author, Instant.now().plusSeconds(6)))
            }
            RealtimeEvent.Unauthorized -> {
                realtimeRejections += 1
                val delaySeconds = minOf(60, 5 * (1 shl minOf(realtimeRejections - 1, 4)))
                val generation = generation

                scope.launch {
                    delay(delaySeconds * 1000L)

                    if (generation != this@MessengerStore.generation) {
                        return@launch
                    }

                    _boot.value = null
                    runCatching { session() }
                }
            }
        }
    }

    // State

    private fun append(message: Message) {
        val id = message.conversationId
        val existing = _threads.value[id]

        if (existing != null) {
            val index = existing.indexOfFirst { it.id == message.id }
            val updated = if (index >= 0) {
                existing.toMutableList().also { it[index] = message }
            } else {
                (existing + message).sortedBy { it.createdAt }
            }
            _threads.value = _threads.value + (id to updated)
        }

        if (!message.isFromContact) {
            _typing.value = _typing.value - id
        }

        val current = conversation(id)

        if (current == null) {
            scope.launch { refreshConversations() }
            return
        }

        var conversation = current.copy(lastMessageAt = message.createdAt)

        if (!message.isNote) {
            conversation = conversation.copy(lastMessage = ConversationPreview.of(message))
        }

        if (!message.isFromContact && !message.isNote) {
            if (visibleConversationId == id) {
                scope.launch { markRead(id, force = true) }
            } else {
                conversation = conversation.copy(unread = true, unreadCount = conversation.unreadCount + 1)

                if (!isMessengerVisible) {
                    markAnnounced(message.id)
                    onIncomingMessage?.invoke(message)
                }
            }
        }

        upsert(conversation)
    }

    private fun replace(message: Message) {
        val thread = _threads.value[message.conversationId] ?: return
        val index = thread.indexOfFirst { it.id == message.id }

        if (index < 0) {
            return
        }

        _threads.value = _threads.value + (message.conversationId to thread.toMutableList().also { it[index] = message })
    }

    private fun upsert(conversation: Conversation) {
        val list = _conversations.value.toMutableList()
        val index = list.indexOfFirst { it.id == conversation.id }

        if (index >= 0) {
            list[index] = conversation
        } else {
            list += conversation
        }

        _conversations.value = list.sortedByDescending { it.sortDate }
        publishUnreadCount()
    }

    private fun publishUnreadCount() {
        val count = _conversations.value.count { it.unread && it.mergedIntoId == null }

        if (count == _unread.value) {
            return
        }

        _unread.value = count
        onUnreadCountChange?.invoke(count)
    }

    private fun reset() {
        generation += 1
        bootTask?.cancel()
        bootTask = null
        suspendRealtime()
        _boot.value = null
        _conversations.value = emptyList()
        _conversationsError.value = null
        _threads.value = emptyMap()
        _typing.value = emptyMap()
        _phase.value = Phase.Idle
        publishUnreadCount()
    }

    companion object {
        const val TAG = "KaraDesk"
    }
}

internal val KaraDeskException.isMessengerUnavailable: Boolean
    get() = this is KaraDeskException.Unavailable && message.orEmpty().contains("messenger", ignoreCase = true)
