package com.karadesk.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import com.karadesk.KaraDeskException
import com.karadesk.R
import kotlinx.coroutines.launch

internal val Throwable.isCancellation: Boolean
    get() = this is kotlinx.coroutines.CancellationException

internal fun KaraDeskException.userMessage(context: Context): String = when (this) {
    is KaraDeskException.Unavailable, is KaraDeskException.IdentityVerificationFailed -> context.getString(R.string.karadesk_unavailable_detail)
    is KaraDeskException.Network -> when {
        offline -> context.getString(R.string.karadesk_offline_error)
        timedOut -> context.getString(R.string.karadesk_timeout_error)
        else -> context.getString(R.string.karadesk_network_error)
    }
    is KaraDeskException.Server -> if (statusCode >= 500) context.getString(R.string.karadesk_server_error) else message.orEmpty()
    is KaraDeskException.UnexpectedResponse -> context.getString(R.string.karadesk_unexpected_error)
    else -> message.orEmpty()
}

internal fun Throwable.userMessage(context: Context): String =
    (this as? KaraDeskException)?.userMessage(context) ?: context.getString(R.string.karadesk_unexpected_error)

internal fun KaraDeskException.detail(context: Context): String =
    if (isOffline) context.getString(R.string.karadesk_offline_detail) else userMessage(context)

@Composable
private fun KaraDeskException.title(): String = when {
    this is KaraDeskException.Unavailable || this is KaraDeskException.IdentityVerificationFailed -> stringResource(R.string.karadesk_messenger_unavailable)
    isOffline -> stringResource(R.string.karadesk_offline_title)
    else -> stringResource(R.string.karadesk_error_title)
}

private val KaraDeskException.icon: ImageVector
    get() = when {
        this is KaraDeskException.Unavailable || this is KaraDeskException.IdentityVerificationFailed -> KaraDeskIcons.ChatOutlined
        isOffline -> KaraDeskIcons.WifiOff
        else -> KaraDeskIcons.Error
    }

@Composable
internal fun ErrorState(
    error: KaraDeskException,
    modifier: Modifier = Modifier,
    title: String? = null,
    retry: (suspend () -> Unit)? = null,
) {
    val palette = KaraDeskTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retrying by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(error.icon, null, tint = palette.secondaryText, modifier = Modifier.size(44.dp))
        Text(
            title ?: error.title(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            error.detail(context),
            color = palette.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).widthIn(max = 320.dp),
        )

        if (retry != null && error.isRetryable) {
            ProminentButton(
                text = stringResource(R.string.karadesk_retry),
                enabled = !retrying,
                modifier = Modifier.padding(top = 20.dp),
                icon = if (retrying) {
                    { CircularProgressIndicator(color = KaraDeskTheme.brand.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
                onClick = {
                    scope.launch {
                        retrying = true
                        try {
                            retry()
                        } finally {
                            retrying = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
internal fun InlineError(error: KaraDeskException, retry: suspend () -> Unit) {
    val palette = KaraDeskTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retrying by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.card, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Icon(error.icon, null, tint = palette.secondaryText, modifier = Modifier.padding(top = 2.dp).size(20.dp))

        Column(Modifier.weight(1f)) {
            Text(error.userMessage(context), color = palette.secondaryText, fontSize = 15.sp)

            if (error.isRetryable) {
                TextButton(
                    onClick = {
                        scope.launch {
                            retrying = true
                            try {
                                retry()
                            } finally {
                                retrying = false
                            }
                        }
                    },
                    enabled = !retrying,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                ) {
                    Text(stringResource(R.string.karadesk_retry), color = KaraDeskTheme.brand.tint, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun OfflineBanner() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF8E8E93))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(vertical = 8.dp),
    ) {
        Icon(KaraDeskIcons.WifiOff, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.karadesk_offline_banner), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
