package com.karadesk.internal.ui

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.karadesk.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

internal object Formatting {
    private val zone: ZoneId
        get() = ZoneId.systemDefault()

    fun shortTime(context: Context, instant: Instant, now: Instant = Instant.now()): String {
        val date = instant.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()

        if (date == today) {
            return clock(context, instant)
        }

        val days = ChronoUnit.DAYS.between(date, today)

        if (days in 1..6) {
            return DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(instant.atZone(zone))
        }

        return monthDay(instant)
    }

    fun relative(context: Context, instant: Instant, now: Instant = Instant.now()): String {
        val seconds = Duration.between(instant, now).seconds.coerceAtLeast(0)

        return when {
            seconds < 60 -> context.getString(R.string.karadesk_now)
            seconds < 3_600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3_600}h"
            seconds < 604_800 -> "${seconds / 86_400}d"
            else -> monthDay(instant)
        }
    }

    fun clock(context: Context, instant: Instant): String =
        DateFormat.getTimeFormat(context).format(Date.from(instant))

    fun longDate(instant: Instant): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()).format(instant.atZone(zone))

    fun byteCount(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    fun firstName(name: String?): String? = name?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() }

    fun dayOf(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    private fun monthDay(instant: Instant): String {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd")
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(instant.atZone(zone))
    }

    fun plain(markdown: String): String = Markdown.parse(markdown, Color.Unspecified).text.replace('\n', ' ')
}

internal object Markdown {
    private val linkPattern = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
    private val urlPattern = Regex("""https?://[^\s<>()]+[^\s<>().,;:!?'"]""")

    fun parse(text: String, linkColor: Color, underlineLinks: Boolean = true): AnnotatedString = buildAnnotatedString {
        appendInline(text, linkColor, underlineLinks)
    }

    private fun AnnotatedString.Builder.appendInline(text: String, linkColor: Color, underline: Boolean) {
        var index = 0

        while (index < text.length) {
            val link = linkPattern.matchAt(text, index)

            if (link != null) {
                appendLink(link.groupValues[1], link.groupValues[2], linkColor, underline)
                index = link.range.last + 1
                continue
            }

            val url = urlPattern.matchAt(text, index)

            if (url != null && (index == 0 || !text[index - 1].isLetterOrDigit())) {
                appendLink(url.value, url.value, linkColor, underline)
                index = url.range.last + 1
                continue
            }

            val emphasis = emphasisAt(text, index)

            if (emphasis != null) {
                val (marker, end) = emphasis
                val inner = text.substring(index + marker.length, end)
                val style = when (marker) {
                    "**", "__" -> SpanStyle(fontWeight = FontWeight.SemiBold)
                    "~~" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    "`" -> SpanStyle(fontFamily = FontFamily.Monospace)
                    else -> SpanStyle(fontStyle = FontStyle.Italic)
                }

                withStyle(style) {
                    if (marker == "`") append(inner) else appendInline(inner, linkColor, underline)
                }

                index = end + marker.length
                continue
            }

            if (text[index] == '\\' && index + 1 < text.length && text[index + 1] in "*_`~[]()\\") {
                append(text[index + 1])
                index += 2
                continue
            }

            append(text[index])
            index += 1
        }
    }

    private fun AnnotatedString.Builder.appendLink(label: String, href: String, color: Color, underline: Boolean) {
        val scheme = href.substringBefore(':', "").lowercase()

        if (scheme !in setOf("http", "https", "mailto", "tel")) {
            append(label)
            return
        }

        val style = SpanStyle(
            color = color,
            textDecoration = if (underline) TextDecoration.Underline else null,
        )

        withLink(LinkAnnotation.Url(href, TextLinkStyles(style = style))) {
            append(label)
        }
    }

    private fun emphasisAt(text: String, index: Int): Pair<String, Int>? {
        for (marker in listOf("**", "__", "~~", "`", "*", "_")) {
            if (!text.startsWith(marker, index)) {
                continue
            }

            val start = index + marker.length

            if (start >= text.length || text[start].isWhitespace()) {
                continue
            }

            if (marker == "_" && index > 0 && text[index - 1].isLetterOrDigit()) {
                continue
            }

            var search = start

            while (true) {
                val end = text.indexOf(marker, search)

                if (end < 0) {
                    break
                }

                if (end > start && !text[end - 1].isWhitespace()) {
                    if (marker == "_" && end + 1 < text.length && text[end + 1].isLetterOrDigit()) {
                        search = end + 1
                        continue
                    }

                    return marker to end
                }

                search = end + 1
            }
        }

        return null
    }
}
