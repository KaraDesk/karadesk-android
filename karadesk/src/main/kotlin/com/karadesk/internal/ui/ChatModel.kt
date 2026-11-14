package com.karadesk.internal.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.karadesk.R
import com.karadesk.internal.model.AttachmentRules
import com.karadesk.internal.model.ConversationState
import com.karadesk.internal.model.Message
import com.karadesk.internal.model.UploadedFile
import com.karadesk.internal.store.MessengerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Stable
internal class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val contentType: String,
    val byteSize: Long,
    val preview: ImageBitmap?,
) {
    var status by mutableStateOf<Status>(Status.Uploading)

    sealed interface Status {
        data object Uploading : Status
        data class Ready(val file: UploadedFile) : Status
        data object Failed : Status
    }
}

@Stable
internal class ChatModel(
    private val store: MessengerStore,
    private val context: Context,
    conversationId: Long?,
    draft: String?,
) {
    var conversationId by mutableStateOf(conversationId)
        private set
    var draft by mutableStateOf(draft.orEmpty())
    var isSending by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
    val attachments = mutableStateListOf<PendingAttachment>()

    private var lastTypingSent = Instant.EPOCH

    val attachmentRules: AttachmentRules
        get() = store.settings?.attachments ?: AttachmentRules.disabled

    val isUploading: Boolean
        get() = attachments.any { it.status == PendingAttachment.Status.Uploading }

    val canSend: Boolean
        get() = !isSending && !isUploading && (draft.isNotBlank() || readyFiles.isNotEmpty())

    private val readyFiles: List<UploadedFile>
        get() = attachments.mapNotNull { (it.status as? PendingAttachment.Status.Ready)?.file }

    fun isLocked(): Boolean {
        val id = conversationId ?: return false
        val conversation = store.conversation(id) ?: return false
        val days = store.rules.closedReplyDays ?: return false
        val closedAt = conversation.closedAt ?: return false

        return conversation.state == ConversationState.CLOSED &&
            Duration.between(closedAt, Instant.now()).seconds >= days * 86_400L
    }

    suspend fun load() {
        val id = conversationId ?: return
        store.visibleConversationId = id

        if (store.threads.value[id] == null) {
            runCatching { store.loadThread(id) }
        }

        store.markRead(id)
    }

    fun disappear() {
        if (store.visibleConversationId == conversationId) {
            store.visibleConversationId = null
        }
    }

    fun draftChanged(value: String) {
        draft = value
        val id = conversationId ?: return

        if (value.isBlank() || Duration.between(lastTypingSent, Instant.now()).seconds <= 3) {
            return
        }

        lastTypingSent = Instant.now()
        store.sendTyping(id)
    }

    suspend fun send(): Long? {
        if (!canSend) {
            return null
        }

        isSending = true
        notice = null

        return try {
            val id = store.send(draft.trim(), readyFiles, conversationId)
            draft = ""
            attachments.clear()

            if (conversationId != id) {
                conversationId = id
                store.visibleConversationId = id
            }

            id
        } catch (error: Exception) {
            notice = error.message
            null
        } finally {
            isSending = false
        }
    }

    suspend fun rate(message: Message, rating: Int?, remark: String?) {
        try {
            store.rate(message, rating, remark)
        } catch (error: Exception) {
            notice = error.message
        }
    }

    fun remove(attachment: PendingAttachment) {
        attachments.remove(attachment)
    }

    fun addUris(uris: List<Uri>) {
        uris.take(MAX_ATTACHMENTS - attachments.size).forEach { uri ->
            store.scope.launch { addUri(uri) }
        }
    }

    private suspend fun addUri(uri: Uri) {
        val resolver = context.contentResolver
        val type = resolver.getType(uri) ?: "application/octet-stream"
        val name = displayName(uri) ?: "Attachment"

        if (type.startsWith("image/") && type != "image/gif") {
            val jpeg = withContext(Dispatchers.IO) { compressImage(uri) }

            if (jpeg == null) {
                notice = context.getString(R.string.karadesk_file_type_not_allowed)
                return
            }

            add(jpeg.first, "Photo.jpg", "image/jpeg", jpeg.second)
            return
        }

        val bytes = withContext(Dispatchers.IO) {
            runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        }

        if (bytes == null) {
            notice = context.getString(R.string.karadesk_upload_failed)
            return
        }

        val resolvedType = if (type == "application/octet-stream") {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: type
        } else {
            type
        }

        val preview = if (resolvedType.startsWith("image/")) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } else {
            null
        }

        add(bytes, name, resolvedType, preview)
    }

    private fun add(bytes: ByteArray, name: String, contentType: String, preview: ImageBitmap?) {
        val rules = attachmentRules

        if (attachments.size >= MAX_ATTACHMENTS) {
            return
        }

        if (rules.accept.isNotEmpty() && contentType !in rules.accept) {
            notice = context.getString(R.string.karadesk_file_type_not_allowed)
            return
        }

        if (rules.maxBytes > 0 && bytes.size > rules.maxBytes) {
            notice = context.getString(R.string.karadesk_file_too_large)
            return
        }

        val pending = PendingAttachment(name = name, contentType = contentType, byteSize = bytes.size.toLong(), preview = preview)
        attachments.add(pending)
        notice = null

        store.scope.launch {
            pending.status = try {
                PendingAttachment.Status.Ready(store.upload(bytes, name, contentType))
            } catch (_: Exception) {
                PendingAttachment.Status.Failed
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun compressImage(uri: Uri): Pair<ByteArray, ImageBitmap>? = runCatching {
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)

                if (longest > MAX_IMAGE_DIMENSION) {
                    val scale = MAX_IMAGE_DIMENSION.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return null
        }

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        output.toByteArray() to bitmap.asImageBitmap()
    }.getOrNull()

    companion object {
        const val MAX_ATTACHMENTS = 10
        private const val MAX_IMAGE_DIMENSION = 2_560
    }
}
