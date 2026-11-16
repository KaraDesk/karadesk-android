package com.karadesk.internal.ui

import com.karadesk.internal.model.MessageAttachment
import java.util.Locale
import kotlin.math.roundToLong

internal enum class FileKind { IMAGE, VIDEO, PDF, OTHER }

internal data class TileSize(val width: Float, val height: Float)

internal object AttachmentFormat {
    val inlineImageTypes = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

    const val MAX_IMAGE_HEIGHT = 384f
    const val GRID_GAP = 6f

    fun isInlineImage(contentType: String?): Boolean = contentType != null && contentType.lowercase(Locale.ROOT) in inlineImageTypes

    fun kind(contentType: String?): FileKind {
        val type = contentType?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim().orEmpty()

        return when {
            type in inlineImageTypes -> FileKind.IMAGE
            type.startsWith("video/") -> FileKind.VIDEO
            type == "application/pdf" -> FileKind.PDF
            else -> FileKind.OTHER
        }
    }

    fun kind(file: MessageAttachment.File): FileKind {
        val kind = kind(file.contentType)

        if (kind == FileKind.OTHER && file.name.lowercase(Locale.ROOT).endsWith(".pdf")) {
            return FileKind.PDF
        }

        return kind
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) {
            return ""
        }

        if (bytes < 1024) {
            return "$bytes B"
        }

        if (bytes < 1024 * 1024) {
            return "${(bytes / 1024.0).roundToLong()} KB"
        }

        return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0)).removeSuffix(".0") + " MB"
    }

    fun placeholder(maxWidth: Float, maxHeight: Float = MAX_IMAGE_HEIGHT): TileSize =
        TileSize(maxWidth, minOf(maxHeight, maxWidth * 0.75f))

    fun fit(width: Int, height: Int, maxWidth: Float, maxHeight: Float = MAX_IMAGE_HEIGHT): TileSize {
        if (width <= 0 || height <= 0 || maxWidth <= 0f) {
            return placeholder(maxWidth, maxHeight)
        }

        val ratio = width.toFloat() / height.toFloat()
        val fitted = maxWidth / ratio

        if (fitted <= maxHeight) {
            return TileSize(maxWidth, fitted)
        }

        return TileSize(maxHeight * ratio, maxHeight)
    }

    fun gridTile(maxWidth: Float, columns: Int = 2, gap: Float = GRID_GAP): Float =
        ((maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0f)

    fun gridRows(count: Int, columns: Int = 2): Int = (count + columns - 1) / columns

    fun safeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().trimStart('.')
        return cleaned.take(120).ifEmpty { "file" }
    }
}
