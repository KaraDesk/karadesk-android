package com.karadesk.internal.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karadesk.internal.model.RichNode
import com.karadesk.internal.model.double
import com.karadesk.internal.model.str

@Composable
internal fun RichText(node: RichNode, baseUrl: String?) {
    SelectionContainer {
        RichChildren(node, baseUrl)
    }
}

@Composable
private fun RichChildren(node: RichNode, baseUrl: String?, color: Color = KaraDeskTheme.palette.text) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        node.content.forEach { child -> RichBlock(child, baseUrl, color) }
    }
}

@Composable
private fun RichBlock(node: RichNode, baseUrl: String?, color: Color) {
    val palette = KaraDeskTheme.palette
    val tint = KaraDeskTheme.brand.tint

    when (node.type) {
        "paragraph" -> if (node.content.isNotEmpty()) {
            Text(RichInline.text(node.content, baseUrl, tint), fontSize = 17.sp, lineHeight = 25.sp, color = color)
        }
        "heading" -> {
            val level = node.attrs.double("level")?.toInt() ?: 1

            Text(
                RichInline.text(node.content, baseUrl, tint),
                fontSize = if (level <= 1) 21.sp else 18.sp,
                lineHeight = if (level <= 1) 27.sp else 24.sp,
                fontWeight = if (level <= 1) FontWeight.Bold else FontWeight.SemiBold,
                color = color,
                modifier = Modifier.padding(top = 6.dp).semantics { heading() },
            )
        }
        "bulletList", "orderedList" -> {
            val ordered = node.type == "orderedList"
            val start = node.attrs.double("start")?.toInt() ?: 1

            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 4.dp)) {
                node.content.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (ordered) "${start + index}." else "•", fontSize = 17.sp, lineHeight = 25.sp, color = palette.secondaryText)
                        Box(Modifier.weight(1f)) { RichChildren(item, baseUrl, color) }
                    }
                }
            }
        }
        "blockquote" -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(palette.separator))
            Box(Modifier.weight(1f)) { RichChildren(node, baseUrl, palette.secondaryText) }
        }
        "codeBlock" -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.bubble)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(RichInline.plain(node), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = color, modifier = Modifier.padding(12.dp))
        }
        "horizontalRule" -> HorizontalDivider(color = palette.separator, modifier = Modifier.padding(vertical = 6.dp))
        "image" -> RichImage(node, baseUrl)
        "table" -> RichTable(node, baseUrl)
        else -> if (node.content.isNotEmpty()) {
            RichChildren(node, baseUrl, color)
        }
    }
}

@Composable
private fun RichImage(node: RichNode, baseUrl: String?) {
    val url = RichInline.resolve(node.attrs.str("src"), baseUrl) ?: return
    val width = node.attrs.double("width")
    val height = node.attrs.double("height")
    val ratio = if (width != null && height != null && height > 0) (width / height).toFloat() else 16f / 9f
    val alignment = when (node.attrs.str("align")) {
        "left" -> Alignment.CenterStart
        "right" -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        RemoteImage(
            url = url,
            contentDescription = node.attrs.str("alt"),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .then(if (width != null) Modifier.widthIn(max = width.dp) else Modifier)
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(10.dp))
                .background(KaraDeskTheme.palette.bubble),
        )
    }
}

@Composable
private fun RichTable(node: RichNode, baseUrl: String?) {
    val palette = KaraDeskTheme.palette
    val tint = KaraDeskTheme.brand.tint

    Box(Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            node.content.forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    row.content.forEach { cell ->
                        val header = cell.type == "tableHeader"
                        val text = buildAnnotatedString {
                            cell.content.forEachIndexed { index, paragraph ->
                                if (index > 0) append("\n")
                                append(RichInline.text(paragraph.content, baseUrl, tint))
                            }
                        }

                        Text(
                            text,
                            fontSize = 15.sp,
                            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                            color = palette.text,
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(min = 100.dp, max = 240.dp)
                                .border(0.5.dp, palette.separator)
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

internal object RichInline {
    fun text(nodes: List<RichNode>, baseUrl: String?, linkColor: Color): AnnotatedString = buildAnnotatedString {
        append(nodes, baseUrl, linkColor)
    }

    private fun AnnotatedString.Builder.append(nodes: List<RichNode>, baseUrl: String?, linkColor: Color) {
        nodes.forEach { node ->
            when (node.type) {
                "text" -> styled(node, baseUrl, linkColor)
                "hardBreak" -> append("\n")
                else -> append(node.content, baseUrl, linkColor)
            }
        }
    }

    private fun AnnotatedString.Builder.styled(node: RichNode, baseUrl: String?, linkColor: Color) {
        var style = SpanStyle()
        var link: String? = null
        val decorations = mutableListOf<TextDecoration>()

        node.marks.forEach { mark ->
            when (mark.type) {
                "bold" -> style = style.merge(SpanStyle(fontWeight = FontWeight.SemiBold))
                "italic" -> style = style.merge(SpanStyle(fontStyle = FontStyle.Italic))
                "code" -> style = style.merge(SpanStyle(fontFamily = FontFamily.Monospace))
                "strike" -> decorations += TextDecoration.LineThrough
                "underline" -> decorations += TextDecoration.Underline
                "link" -> link = resolve(mark.attrs.str("href"), baseUrl)
            }
        }

        if (decorations.isNotEmpty()) {
            style = style.merge(SpanStyle(textDecoration = TextDecoration.combine(decorations)))
        }

        val text = node.text.orEmpty()
        val href = link

        if (href != null) {
            withLink(LinkAnnotation.Url(href, TextLinkStyles(style.merge(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))))) {
                append(text)
            }
        } else {
            withStyle(style) { append(text) }
        }
    }

    fun plain(node: RichNode): String = node.text ?: node.content.joinToString("") { plain(it) }

    fun resolve(value: String?, baseUrl: String?): String? {
        if (value.isNullOrEmpty()) {
            return null
        }

        if (value.startsWith("/") && baseUrl != null) {
            return runCatching { Uri.parse(baseUrl).buildUpon().encodedPath(value.substringBefore('?')).encodedQuery(value.substringAfter('?', "").ifEmpty { null }).build().toString() }
                .getOrNull()
        }

        val scheme = Uri.parse(value).scheme?.lowercase() ?: return null
        return value.takeIf { scheme in setOf("http", "https", "mailto", "tel") }
    }
}
