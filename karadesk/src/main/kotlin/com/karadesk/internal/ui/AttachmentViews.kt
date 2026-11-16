package com.karadesk.internal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.karadesk.R
import com.karadesk.internal.model.MessageAttachment

internal sealed interface AttachmentViewerTarget {
    data class Images(val images: List<MessageAttachment.File>, val index: Int) : AttachmentViewerTarget
    data class Video(val file: MessageAttachment.File) : AttachmentViewerTarget
    data class Document(val file: MessageAttachment.File) : AttachmentViewerTarget
}

private val attachmentShape = RoundedCornerShape(12.dp)
private val maxAttachmentWidth = 288.dp

@Composable
internal fun FileAttachments(files: List<MessageAttachment.File>) {
    if (files.isEmpty()) {
        return
    }

    var viewer by remember { mutableStateOf<AttachmentViewerTarget?>(null) }
    val images = files.filter { AttachmentFormat.kind(it) == FileKind.IMAGE }
    val videos = files.filter { AttachmentFormat.kind(it) == FileKind.VIDEO }
    val others = files.filter { AttachmentFormat.kind(it) != FileKind.IMAGE && AttachmentFormat.kind(it) != FileKind.VIDEO }

    Column(
        verticalArrangement = Arrangement.spacedBy(AttachmentFormat.GRID_GAP.dp),
        modifier = Modifier.widthIn(max = maxAttachmentWidth),
    ) {
        if (images.size == 1) {
            SingleImage(images.first()) { viewer = AttachmentViewerTarget.Images(images, 0) }
        } else if (images.size > 1) {
            ImageGrid(images) { index -> viewer = AttachmentViewerTarget.Images(images, index) }
        }

        videos.forEach { video ->
            VideoTile(video) { viewer = AttachmentViewerTarget.Video(video) }
        }

        others.forEach { file ->
            FileRow(file) { viewer = AttachmentViewerTarget.Document(file) }
        }
    }

    viewer?.let { target ->
        AttachmentViewer(target) { viewer = null }
    }
}

@Composable
private fun SingleImage(file: MessageAttachment.File, onOpen: () -> Unit) {
    val context = LocalContext.current
    val palette = KaraDeskTheme.palette
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context).data(file.url).build(),
        imageLoader = Images.loader(context),
    )
    val state by painter.state.collectAsState()

    if (state is AsyncImagePainter.State.Error) {
        FileRow(file, onOpen)
        return
    }

    BoxWithConstraints {
        val maxWidth = maxWidth.value
        val success = state as? AsyncImagePainter.State.Success
        val size = if (success != null) {
            AttachmentFormat.fit(success.result.image.width, success.result.image.height, maxWidth)
        } else {
            AttachmentFormat.placeholder(maxWidth)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.width.dp, size.height.dp)
                .clip(attachmentShape)
                .background(if (success == null) palette.bubble else Color.Transparent)
                .clickableWithRole(file.name, onClick = onOpen),
        ) {
            Image(painter, file.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())

            if (success == null) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = palette.secondaryText, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ImageGrid(images: List<MessageAttachment.File>, onOpen: (Int) -> Unit) {
    val palette = KaraDeskTheme.palette
    val gap = AttachmentFormat.GRID_GAP.dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tile: Dp = AttachmentFormat.gridTile(maxWidth.value).dp

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            images.withIndex().chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { (index, image) ->
                        RemoteImage(
                            url = image.url,
                            contentDescription = image.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(tile)
                                .clip(attachmentShape)
                                .background(palette.bubble)
                                .clickableWithRole(image.name) { onOpen(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoTile(file: MessageAttachment.File, onOpen: () -> Unit) {
    val palette = KaraDeskTheme.palette
    var thumbnail by remember(file.url) { mutableStateOf(AttachmentFiles.cachedThumbnail(file.url)) }

    LaunchedEffect(file.url) {
        if (thumbnail == null) {
            thumbnail = AttachmentFiles.videoThumbnail(file.url)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val current = thumbnail
        val size = if (current != null) {
            AttachmentFormat.fit(current.width, current.height, maxWidth.value)
        } else {
            AttachmentFormat.fit(16, 9, maxWidth.value)
        }
        val label = stringResource(R.string.karadesk_play_video)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.width.dp, size.height.dp)
                .clip(attachmentShape)
                .background(palette.fill)
                .clickableWithRole(label, onClick = onOpen)
                .semantics(mergeDescendants = true) { },
        ) {
            if (current != null) {
                Image(current.image, file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Icon(KaraDeskIcons.PlayArrow, label, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun FileRow(file: MessageAttachment.File, onOpen: () -> Unit) {
    val palette = KaraDeskTheme.palette
    val size = AttachmentFormat.formatBytes(file.byteSize)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(attachmentShape)
            .background(palette.surface)
            .border(1.dp, palette.separator, attachmentShape)
            .clickableWithRole(file.name, onClick = onOpen)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(KaraDeskIcons.Document, null, tint = palette.secondaryText, modifier = Modifier.size(20.dp))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                file.name.ifBlank { stringResource(R.string.karadesk_file) },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (size.isNotEmpty()) {
                Text(size, fontSize = 12.sp, color = palette.secondaryText)
            }
        }

        Icon(KaraDeskIcons.Download, null, tint = palette.secondaryText, modifier = Modifier.size(18.dp))
    }
}
