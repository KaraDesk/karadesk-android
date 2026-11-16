package com.karadesk.internal.ui

import android.graphics.Bitmap
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.karadesk.R
import com.karadesk.internal.model.MessageAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

@Composable
internal fun AttachmentViewer(target: AttachmentViewerTarget, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        when (target) {
            is AttachmentViewerTarget.Images -> ImageViewer(target.images, target.index, onDismiss)
            is AttachmentViewerTarget.Video -> VideoViewer(target.file, onDismiss)
            is AttachmentViewerTarget.Document -> DocumentViewer(target.file, onDismiss)
        }
    }
}

@Composable
private fun DialogBarIcons(light: Boolean) {
    val view = LocalView.current

    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !light
        controller.isAppearanceLightNavigationBars = !light
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, busy: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !busy) {
        if (busy) {
            CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(icon, label, tint = tint)
        }
    }
}

@Composable
private fun ImageViewer(images: List<MessageAttachment.File>, initial: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = initial.coerceIn(0, (images.size - 1).coerceAtLeast(0))) { images.size }
    var chrome by remember { mutableStateOf(true) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    val current = images.getOrNull(pager.currentPage)
    val savedText = stringResource(R.string.karadesk_saved_to_photos)
    val saveFailedText = stringResource(R.string.karadesk_save_failed)
    val shareFailedText = stringResource(R.string.karadesk_open_failed)

    DialogBarIcons(light = true)

    val withLocal: (suspend (File, MessageAttachment.File) -> Unit) -> Unit = { action ->
        val file = current

        if (file != null && !busy) {
            busy = true
            scope.launch {
                val local = runCatching { AttachmentFiles.download(context, file) }.getOrNull()

                if (local == null) {
                    Toast.makeText(context, shareFailedText, Toast.LENGTH_SHORT).show()
                } else {
                    action(local, file)
                }

                busy = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .background(Color.Black.copy(alpha = (1f - abs(dragY) / (height * 0.6f)).coerceIn(0.2f, 1f))),
    ) {
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(
                file = images[page],
                active = pager.currentPage == page,
                onTap = { chrome = !chrome },
                onDrag = { dragY = it },
                onDismiss = onDismiss,
            )
        }

        if (chrome) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 4.dp),
            ) {
                ViewerAction(KaraDeskIcons.Close, stringResource(R.string.karadesk_close), Color.White, onClick = onDismiss)

                Text(
                    if (images.size > 1) stringResource(R.string.karadesk_image_count, pager.currentPage + 1, images.size) else current?.name.orEmpty(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )

                ViewerAction(KaraDeskIcons.Share, stringResource(R.string.karadesk_share), Color.White, busy = busy) {
                    withLocal { local, file ->
                        if (!AttachmentFiles.share(context, local, file)) {
                            Toast.makeText(context, shareFailedText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                if (AttachmentFiles.canSaveToGallery) {
                    ViewerAction(KaraDeskIcons.Download, stringResource(R.string.karadesk_save_to_photos), Color.White, busy = busy) {
                        withLocal { local, file ->
                            val saved = AttachmentFiles.saveToGallery(context, local, file)
                            Toast.makeText(context, if (saved) savedText else saveFailedText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    file: MessageAttachment.File,
    active: Boolean,
    onTap: () -> Unit,
    onDrag: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = size.width * (zoom - 1f) / 2f
        val maxY = size.height * (zoom - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { point ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val target = DOUBLE_TAP_SCALE
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clamp((center - point) * (target - 1f), target)
                            scale = target
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    var dismissing = false
                    var horizontal = false

                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        if (!dismissing && (pressed > 1 || scale > 1f)) {
                            val next = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            scale = next
                            offset = if (next <= 1f) Offset.Zero else clamp(offset + pan, next)
                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        } else if (!horizontal) {
                            total += pan

                            if (!dismissing && total.getDistance() > viewConfiguration.touchSlop) {
                                if (abs(total.y) > abs(total.x)) {
                                    dismissing = true
                                } else {
                                    horizontal = true
                                }
                            }

                            if (dismissing) {
                                dragY += pan.y
                                onDrag(dragY)
                                event.changes.forEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (dismissing) {
                        if (abs(dragY) > size.height * DISMISS_FRACTION) {
                            onDismiss()
                        } else {
                            dragY = 0f
                            onDrag(0f)
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y + dragY
            },
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context).data(file.url).build(),
            imageLoader = Images.loader(context),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(KaraDeskIcons.Error, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val DISMISS_FRACTION = 0.15f

@Composable
private fun VideoViewer(file: MessageAttachment.File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<VideoView?>(null) }

    DialogBarIcons(light = true)

    DisposableEffect(Unit) {
        onDispose { player?.stopPlayback() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { viewContext ->
                VideoView(viewContext).also { video ->
                    val controller = MediaController(viewContext)
                    controller.setAnchorView(video)
                    video.setMediaController(controller)
                    video.setOnPreparedListener {
                        loading = false
                        video.start()
                        controller.show()
                    }
                    video.setOnErrorListener { _, _, _ ->
                        openUrl(context, file.url)
                        onDismiss()
                        true
                    }
                    video.setVideoPath(file.url)
                    player = video
                }
            },
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars),
        )

        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 4.dp),
        ) {
            ViewerAction(KaraDeskIcons.Close, stringResource(R.string.karadesk_close), Color.White, onClick = onDismiss)
        }
    }
}

@Composable
private fun DocumentViewer(file: MessageAttachment.File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = KaraDeskTheme.palette
    val pdf = AttachmentFormat.kind(file) == FileKind.PDF
    var progress by remember { mutableStateOf<Float?>(null) }
    var local by remember { mutableStateOf<File?>(null) }
    var document by remember { mutableStateOf<PdfDocument?>(null) }
    val openFailed = stringResource(R.string.karadesk_open_failed)

    DialogBarIcons(light = palette.surface.luminance() < 0.5f)

    val fallback: () -> Unit = {
        openUrl(context, file.url)
        onDismiss()
    }

    LaunchedEffect(file.url) {
        val downloaded = runCatching { AttachmentFiles.download(context, file) { progress = it } }.getOrNull()

        if (downloaded == null) {
            fallback()
            return@LaunchedEffect
        }

        local = downloaded

        if (pdf) {
            val opened = runCatching { PdfDocument.open(downloaded) }.getOrNull()

            if (opened != null && opened.pageCount > 0) {
                document = opened
                return@LaunchedEffect
            }

            opened?.close()
        }

        if (AttachmentFiles.openExternally(context, downloaded, file)) {
            onDismiss()
        } else {
            fallback()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            document?.let { opened -> CoroutineScope(Dispatchers.IO).launch { opened.release() } }
        }
    }

    Column(Modifier.fillMaxSize().background(palette.surface).windowInsetsPadding(WindowInsets.systemBars)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            ViewerAction(KaraDeskIcons.Close, stringResource(R.string.karadesk_close), palette.text, onClick = onDismiss)

            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(file.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AttachmentFormat.formatBytes(file.byteSize).takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 12.sp, color = palette.secondaryText)
                }
            }

            local?.let { downloaded ->
                ViewerAction(KaraDeskIcons.Share, stringResource(R.string.karadesk_share), palette.text) {
                    if (!AttachmentFiles.share(context, downloaded, file)) {
                        Toast.makeText(context, openFailed, Toast.LENGTH_SHORT).show()
                    }
                }
                ViewerAction(KaraDeskIcons.ArrowOutward, stringResource(R.string.karadesk_open_with), palette.text) {
                    if (!AttachmentFiles.openExternally(context, downloaded, file)) {
                        Toast.makeText(context, openFailed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val opened = document

        if (opened != null) {
            PdfPages(opened)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxSize().padding(32.dp),
            ) {
                val value = progress

                Text(stringResource(R.string.karadesk_downloading), fontSize = 15.sp, color = palette.secondaryText)

                if (value == null) {
                    LinearProgressIndicator(color = KaraDeskTheme.brand.tint, trackColor = palette.fill, modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { value },
                        color = KaraDeskTheme.brand.tint,
                        trackColor = palette.fill,
                        modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth(),
                    )
                    Text("${(value * 100).toInt()}%", fontSize = 13.sp, color = palette.tertiaryText)
                }
            }
        }
    }
}

@Composable
private fun PdfPages(document: PdfDocument) {
    val palette = KaraDeskTheme.palette
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val widthPx = with(density) { (maxWidth - 24.dp).roundToPx() }

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(document.pageCount) { index ->
                var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
                val label = stringResource(R.string.karadesk_page_count, index + 1, document.pageCount)

                LaunchedEffect(index, widthPx) {
                    bitmap = document.render(index, widthPx)
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(document.ratios[index].coerceIn(0.1f, 10f))
                        .background(Color.White)
                        .semantics { contentDescription = label },
                ) {
                    val current = bitmap

                    if (current != null) {
                        Image(current.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        CircularProgressIndicator(color = Color.Gray, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
