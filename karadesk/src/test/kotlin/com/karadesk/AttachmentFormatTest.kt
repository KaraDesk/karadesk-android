package com.karadesk

import com.karadesk.internal.model.MessageAttachment
import com.karadesk.internal.ui.AttachmentFormat
import com.karadesk.internal.ui.FileKind
import com.karadesk.internal.ui.TileSize
import com.karadesk.internal.ui.replyPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentFormatTest {
    private fun file(name: String, type: String) = MessageAttachment.File("a1", name, type, 10, "https://x/$name")

    @Test
    fun classifiesContentTypesLikeTheWebWidget() {
        assertTrue(AttachmentFormat.isInlineImage("image/png"))
        assertTrue(AttachmentFormat.isInlineImage("image/jpeg"))
        assertTrue(AttachmentFormat.isInlineImage("image/gif"))
        assertTrue(AttachmentFormat.isInlineImage("image/webp"))
        assertFalse(AttachmentFormat.isInlineImage("image/heic"))
        assertFalse(AttachmentFormat.isInlineImage("image/svg+xml"))
        assertFalse(AttachmentFormat.isInlineImage(null))

        assertEquals(FileKind.IMAGE, AttachmentFormat.kind("IMAGE/PNG"))
        assertEquals(FileKind.VIDEO, AttachmentFormat.kind("video/mp4"))
        assertEquals(FileKind.VIDEO, AttachmentFormat.kind("video/quicktime"))
        assertEquals(FileKind.PDF, AttachmentFormat.kind("application/pdf"))
        assertEquals(FileKind.PDF, AttachmentFormat.kind("application/pdf; charset=binary"))
        assertEquals(FileKind.OTHER, AttachmentFormat.kind("image/heic"))
        assertEquals(FileKind.OTHER, AttachmentFormat.kind("application/zip"))
        assertEquals(FileKind.OTHER, AttachmentFormat.kind(null as String?))

        assertEquals(FileKind.PDF, AttachmentFormat.kind(file("Invoice.PDF", "application/octet-stream")))
        assertEquals(FileKind.OTHER, AttachmentFormat.kind(file("notes.txt", "text/plain")))
        assertEquals(FileKind.IMAGE, AttachmentFormat.kind(file("a.png", "image/png")))
    }

    @Test
    fun formatsBytesLikeTheWebWidget() {
        assertEquals("", AttachmentFormat.formatBytes(0))
        assertEquals("", AttachmentFormat.formatBytes(-5))
        assertEquals("512 B", AttachmentFormat.formatBytes(512))
        assertEquals("1023 B", AttachmentFormat.formatBytes(1023))
        assertEquals("1 KB", AttachmentFormat.formatBytes(1024))
        assertEquals("2 KB", AttachmentFormat.formatBytes(1536))
        assertEquals("1000 KB", AttachmentFormat.formatBytes(1024 * 1000))
        assertEquals("1 MB", AttachmentFormat.formatBytes(1024 * 1024))
        assertEquals("2.5 MB", AttachmentFormat.formatBytes(1024 * 1024 * 5 / 2))
        assertEquals("12.3 MB", AttachmentFormat.formatBytes((12.3 * 1024 * 1024).toLong()))
    }

    @Test
    fun fitsSingleImagesWithinWidthAndMaxHeight() {
        assertEquals(TileSize(288f, 216f), AttachmentFormat.fit(4000, 3000, 288f))
        assertEquals(TileSize(288f, 144f), AttachmentFormat.fit(100, 50, 288f))
        assertEquals(TileSize(192f, 384f), AttachmentFormat.fit(1000, 2000, 288f))
        assertEquals(TileSize(48f, 384f), AttachmentFormat.fit(100, 800, 288f))
        assertEquals(TileSize(288f, 216f), AttachmentFormat.fit(0, 0, 288f))
        assertEquals(TileSize(288f, 216f), AttachmentFormat.placeholder(288f))
        assertEquals(TileSize(600f, 384f), AttachmentFormat.placeholder(600f))
    }

    @Test
    fun sizesGridTiles() {
        assertEquals(141f, AttachmentFormat.gridTile(288f), 0.001f)
        assertEquals(0f, AttachmentFormat.gridTile(4f), 0.001f)
        assertEquals(1, AttachmentFormat.gridRows(2))
        assertEquals(2, AttachmentFormat.gridRows(3))
        assertEquals(2, AttachmentFormat.gridRows(4))
    }

    @Test
    fun makesSafeCacheFileNames() {
        assertEquals("report 2026.pdf", AttachmentFormat.safeFileName("report 2026.pdf"))
        assertEquals("_.._etc_passwd", AttachmentFormat.safeFileName("/../etc/passwd"))
        assertEquals("file", AttachmentFormat.safeFileName("..."))
        assertEquals("file", AttachmentFormat.safeFileName(""))
    }

    @Test
    fun replyPhraseAddsInOnlyWhenMissing() {
        assertEquals("in a few minutes", replyPhrase("a few minutes"))
        assertEquals("in under 5 minutes", replyPhrase("in under 5 minutes"))
        assertEquals("In a day", replyPhrase("In a day"))
    }
}
