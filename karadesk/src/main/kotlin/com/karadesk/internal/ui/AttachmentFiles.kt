package com.karadesk.internal.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.LruCache
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.karadesk.R
import com.karadesk.internal.model.MessageAttachment
import com.karadesk.internal.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

internal class KaraDeskFileProvider : FileProvider(R.xml.karadesk_file_paths)

internal object AttachmentFiles {
    private val http by lazy { ApiClient.defaultHttpClient() }
    private val thumbnails = LruCache<String, VideoThumbnail>(24)
    private val failedThumbnails = mutableSetOf<String>()

    fun authority(context: Context): String = "${context.packageName}.karadesk.files"

    fun cacheFile(context: Context, file: MessageAttachment.File): File {
        val folder = File(context.cacheDir, "karadesk/attachments/${AttachmentFormat.safeFileName(file.assetId)}")
        return File(folder, AttachmentFormat.safeFileName(file.name))
    }

    fun cached(context: Context, file: MessageAttachment.File): File? =
        cacheFile(context, file).takeIf { it.isFile && it.length() > 0 }

    suspend fun download(context: Context, file: MessageAttachment.File, onProgress: (Float?) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            cached(context, file)?.let { return@withContext it }

            val target = cacheFile(context, file)
            val partial = File(target.parentFile, "${target.name}.part")
            target.parentFile?.mkdirs()

            try {
                http.newCall(Request.Builder().url(file.url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Download failed (${response.code})")
                    }

                    val body = response.body ?: throw IOException("Empty download")
                    val total = body.contentLength().takeIf { it > 0 } ?: file.byteSize.takeIf { it > 0 }
                    var written = 0L
                    var reported = -1

                    onProgress(if (total == null) null else 0f)

                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)

                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)

                                if (read < 0) {
                                    break
                                }

                                output.write(buffer, 0, read)
                                written += read

                                if (total != null) {
                                    val percent = (written * 100 / total).toInt().coerceIn(0, 100)

                                    if (percent != reported) {
                                        reported = percent
                                        onProgress(percent / 100f)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!partial.renameTo(target)) {
                    throw IOException("Couldn't store the download")
                }

                target
            } finally {
                partial.delete()
            }
        }

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    fun share(context: Context, local: File, attachment: MessageAttachment.File): Boolean {
        val uri = runCatching { uri(context, local) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_SEND)
            .setType(attachment.contentType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        intent.clipData = ClipData.newRawUri(attachment.name, uri)

        return try {
            context.startActivity(Intent.createChooser(intent, null))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun openExternally(context: Context, local: File, attachment: MessageAttachment.File): Boolean {
        val uri = runCatching { uri(context, local) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, attachment.contentType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    val canSaveToGallery: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    suspend fun saveToGallery(context: Context, local: File, attachment: MessageAttachment.File): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext false
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, AttachmentFormat.safeFileName(attachment.name))
            put(MediaStore.Images.Media.MIME_TYPE, attachment.contentType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val item = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withContext false

        try {
            val output = resolver.openOutputStream(item) ?: throw IOException("No output")
            output.use { stream -> local.inputStream().use { it.copyTo(stream) } }
            resolver.update(item, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (_: Exception) {
            runCatching { resolver.delete(item, null, null) }
            false
        }
    }

    fun cachedThumbnail(url: String): VideoThumbnail? = thumbnails.get(url)

    suspend fun videoThumbnail(url: String): VideoThumbnail? {
        thumbnails.get(url)?.let { return it }

        if (url in failedThumbnails) {
            return null
        }

        val thumbnail = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()

            try {
                retriever.setDataSource(url, emptyMap())
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@withContext null
                val scaled = scaleDown(frame, 720)
                VideoThumbnail(scaled.asImageBitmap(), scaled.width, scaled.height)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

        if (thumbnail == null) {
            failedThumbnails += url
        } else {
            thumbnails.put(url, thumbnail)
        }

        return thumbnail
    }

    private fun scaleDown(bitmap: Bitmap, longest: Int): Bitmap {
        val current = maxOf(bitmap.width, bitmap.height)

        if (current <= longest) {
            return bitmap
        }

        val scale = longest.toFloat() / current
        return bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
    }
}

internal class VideoThumbnail(val image: ImageBitmap, val width: Int, val height: Int)

internal class PdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val ratios: List<Float>,
) : Closeable {
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int
        get() = ratios.size

    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed || index !in ratios.indices || widthPx <= 0) {
                return@withContext null
            }

            runCatching {
                renderer.openPage(index).use { page ->
                    val width = widthPx.coerceAtMost(MAX_PAGE_WIDTH)
                    val height = (width * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    val bitmap = createBitmap(width, height)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()
        }
    }

    suspend fun release() {
        mutex.withLock {
            close()
        }
    }

    override fun close() {
        if (closed) {
            return
        }

        closed = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        private const val MAX_PAGE_WIDTH = 2_048

        suspend fun open(file: File): PdfDocument = withContext(Dispatchers.IO) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

            try {
                val renderer = PdfRenderer(descriptor)
                val ratios = (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page -> page.width.toFloat() / page.height.coerceAtLeast(1) }
                }
                PdfDocument(descriptor, renderer, ratios)
            } catch (error: Exception) {
                runCatching { descriptor.close() }
                throw error
            }
        }
    }
}
