package com.karadesk.internal.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.karadesk.R
import com.karadesk.internal.model.Person

internal object Images {
    @Volatile
    private var loader: ImageLoader? = null

    fun loader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
            .also { loader = it }
    }
}

@Composable
internal fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).build(),
        imageLoader = Images.loader(context),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
internal fun Avatar(name: String?, url: String?, size: Dp = 32.dp, showsActiveDot: Boolean = false) {
    val palette = KaraDeskTheme.palette
    val context = LocalContext.current

    Box(modifier = Modifier.size(size).clearAndSetSemantics { }) {
        val initials: @Composable () -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(palette.avatar),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial(name),
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.secondaryText,
                )
            }
        }

        if (url == null) {
            initials()
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(url).build(),
                imageLoader = Images.loader(context),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { initials() },
                error = { initials() },
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(palette.surface),
            )
        }

        if (showsActiveDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.3f)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(palette.active),
            )
        }
    }
}

internal fun initial(name: String?): String =
    name?.trim()?.firstOrNull()?.uppercase() ?: "?"

@Composable
internal fun AvatarStack(people: List<Person>, size: Dp = 32.dp, ring: Color = KaraDeskTheme.palette.surface) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(-size * 0.28f),
        modifier = Modifier.clearAndSetSemantics { },
    ) {
        people.take(3).forEach { person ->
            Box(modifier = Modifier.clip(CircleShape).background(ring).padding(2.dp)) {
                Avatar(person.name, person.avatarUrl, size)
            }
        }
    }
}

private val karaRing = Brush.sweepGradient(
    0f to Color(0xFF7C3AED),
    0.33f to Color(0xFFDB2777),
    0.66f to Color(0xFFF59E0B),
    1f to Color(0xFF7C3AED),
)

@Composable
internal fun BotAvatar(size: Dp = 32.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(karaRing)
            .padding(maxOf(1.dp, size * 0.047f))
            .clip(CircleShape)
            .background(Color(0xFF0A0A0A))
            .semantics { contentDescription = "Kara" },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = BrandVectors.Sparkle,
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
internal fun HeaderBackground(modifier: Modifier = Modifier) {
    when (val background = KaraDeskTheme.brand.headerBackground) {
        is HeaderBackground.Solid -> Box(modifier.background(background.color))
        is HeaderBackground.Gradient -> Box(
            modifier.drawBehind {
                drawRect(Brush.linearGradient(listOf(background.from, background.to), Offset.Zero, Offset(size.width, size.height)))
            },
        )
        is HeaderBackground.Image -> Box(modifier.background(background.fallback)) {
            RemoteImage(background.url, null, Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun Modifier.card(): Modifier {
    val palette = KaraDeskTheme.palette
    val shape = RoundedCornerShape(18.dp)

    return this
        .fillMaxWidth()
        .clip(shape)
        .background(palette.card)
        .border(1.dp, palette.cardBorder, shape)
}

@Composable
internal fun CloseButton(onClose: () -> Unit, tint: Color = KaraDeskTheme.palette.text) {
    IconButton(onClick = onClose) {
        Icon(KaraDeskIcons.Close, contentDescription = stringResource(R.string.karadesk_close), tint = tint)
    }
}

@Composable
internal fun ProminentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    val brand = KaraDeskTheme.brand

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = brand.accent, contentColor = brand.onAccent),
    ) {
        if (icon != null) {
            icon()
            Box(Modifier.size(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun PoweredBy(onOpen: () -> Unit) {
    val palette = KaraDeskTheme.palette
    val label = stringResource(R.string.karadesk_powered_by)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.card.copy(alpha = 0.85f))
            .border(1.dp, palette.cardBorder, CircleShape)
            .clickableWithRole(label) { onOpen() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Image(
            imageVector = BrandVectors.Mark,
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.secondaryText),
            modifier = Modifier.size(13.dp),
        )
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.secondaryText)
    }
}

@Composable
internal fun rememberString(id: Int): String {
    val context = LocalContext.current
    return remember(id) { context.getString(id) }
}
