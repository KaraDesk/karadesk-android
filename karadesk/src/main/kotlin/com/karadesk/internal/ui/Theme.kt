package com.karadesk.internal.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.karadesk.internal.model.BackgroundKind
import com.karadesk.internal.model.HeaderText
import com.karadesk.internal.model.MessengerSettings
import com.karadesk.internal.model.ThemeMode
import kotlin.math.pow

@Immutable
internal sealed interface HeaderBackground {
    data class Solid(val color: Color) : HeaderBackground
    data class Gradient(val from: Color, val to: Color) : HeaderBackground
    data class Image(val url: String, val fallback: Color) : HeaderBackground
}

@Immutable
internal data class BrandTheme(
    val accent: Color,
    val tint: Color,
    val onAccent: Color,
    val headerForeground: Color,
    val headerBackground: HeaderBackground,
    val dark: Boolean,
) {
    val headerBase: Color
        get() = when (val background = headerBackground) {
            is HeaderBackground.Solid -> background.color
            is HeaderBackground.Gradient -> background.from
            is HeaderBackground.Image -> background.fallback
        }

    companion object {
        private val standardAccent = Color(0xFF111111)

        fun standard(dark: Boolean) = BrandTheme(
            accent = standardAccent,
            tint = if (dark) Color.White else standardAccent,
            onAccent = Color.White,
            headerForeground = Color.White,
            headerBackground = HeaderBackground.Solid(standardAccent),
            dark = dark,
        )

        fun from(settings: MessengerSettings, dark: Boolean): BrandTheme {
            val accentHex = (if (dark) settings.dark.color else null) ?: settings.color
            val accent = parseHex(accentHex) ?: standardAccent
            val background = settings.background
            val overrideKind = if (dark) settings.dark.backgroundKind else null
            val overrideColor = if (dark) settings.dark.backgroundColor else null
            val overrideFrom = if (dark) settings.dark.gradientFrom else null
            val overrideTo = if (dark) settings.dark.gradientTo else null
            val kind = overrideKind ?: background.kind
            val base = parseHex(overrideColor ?: background.color ?: accentHex) ?: accent

            val header = when (kind) {
                BackgroundKind.SOLID -> HeaderBackground.Solid(base)
                BackgroundKind.GRADIENT -> HeaderBackground.Gradient(
                    parseHex(overrideFrom ?: background.gradientFrom ?: accentHex) ?: accent,
                    parseHex(overrideTo ?: background.gradientTo ?: accentHex) ?: accent,
                )
                BackgroundKind.IMAGE -> {
                    val url = if (dark) settings.darkBackgroundImageUrl ?: background.imageUrl else background.imageUrl
                    url?.let { HeaderBackground.Image(it, base) } ?: HeaderBackground.Solid(base)
                }
            }

            val headerText = (if (dark) settings.dark.headerText else null) ?: settings.headerText
            val headerForeground = when (headerText) {
                HeaderText.LIGHT -> Color.White
                HeaderText.DARK -> Color.Black
                HeaderText.AUTO -> readableForeground(overrideColor ?: background.color ?: background.gradientFrom ?: accentHex)
            }

            val accentLuminance = luminance(accentHex) ?: 0.5
            val tint = when {
                dark && accentLuminance < 0.12 -> Color.White
                !dark && accentLuminance > 0.75 -> Color.Black
                else -> accent
            }

            return BrandTheme(
                accent = accent,
                tint = tint,
                onAccent = readableForeground(accentHex),
                headerForeground = headerForeground,
                headerBackground = header,
                dark = dark,
            )
        }

        fun parseHex(hex: String?): Color? {
            val rgb = rgb(hex) ?: return null
            return Color(rgb.first.toFloat(), rgb.second.toFloat(), rgb.third.toFloat())
        }

        fun readableForeground(hex: String?): Color {
            val luminance = luminance(hex) ?: return Color.White
            return if (luminance > 0.45) Color.Black else Color.White
        }

        fun luminance(hex: String?): Double? {
            val rgb = rgb(hex) ?: return null

            fun linear(value: Double) = if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

            return 0.2126 * linear(rgb.first) + 0.7152 * linear(rgb.second) + 0.0722 * linear(rgb.third)
        }

        private fun rgb(hex: String?): Triple<Double, Double, Double>? {
            var value = hex?.trim()?.removePrefix("#") ?: return null

            if (value.length == 3) {
                value = value.map { "$it$it" }.joinToString("")
            }

            if (value.length != 6) {
                return null
            }

            val number = value.toLongOrNull(16) ?: return null

            return Triple(
                ((number shr 16) and 0xFF) / 255.0,
                ((number shr 8) and 0xFF) / 255.0,
                (number and 0xFF) / 255.0,
            )
        }
    }
}

@Immutable
internal data class Palette(
    val background: Color,
    val card: Color,
    val surface: Color,
    val bubble: Color,
    val fill: Color,
    val text: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val separator: Color,
    val cardBorder: Color,
    val error: Color,
    val active: Color,
    val avatar: Color,
) {
    companion object {
        val light = Palette(
            background = Color(0xFFF2F2F7),
            card = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            bubble = Color(0xFFF0F0F3),
            fill = Color(0x1F767680),
            text = Color(0xFF111111),
            secondaryText = Color(0xFF6B6B73),
            tertiaryText = Color(0xFFA1A1AA),
            separator = Color(0xFFE4E4E7),
            cardBorder = Color(0x14000000),
            error = Color(0xFFDC2626),
            active = Color(0xFF22C55E),
            avatar = Color(0xFFE5E5EA),
        )

        val dark = Palette(
            background = Color(0xFF000000),
            card = Color(0xFF1C1C1E),
            surface = Color(0xFF000000),
            bubble = Color(0xFF1C1C1E),
            fill = Color(0x3D767680),
            text = Color(0xFFF4F4F5),
            secondaryText = Color(0xFFA1A1AA),
            tertiaryText = Color(0xFF71717A),
            separator = Color(0xFF2C2C2E),
            cardBorder = Color(0x1AFFFFFF),
            error = Color(0xFFF87171),
            active = Color(0xFF22C55E),
            avatar = Color(0xFF2C2C2E),
        )
    }
}

internal val LocalBrand = staticCompositionLocalOf { BrandTheme.standard(dark = false) }
internal val LocalPalette = staticCompositionLocalOf { Palette.light }

internal object KaraDeskTheme {
    val brand: BrandTheme
        @Composable get() = LocalBrand.current

    val palette: Palette
        @Composable get() = LocalPalette.current
}

@Composable
internal fun isDarkFor(settings: MessengerSettings?): Boolean = when (settings?.theme) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    else -> isSystemInDarkTheme()
}

@Composable
internal fun KaraDeskThemed(settings: MessengerSettings?, content: @Composable () -> Unit) {
    val dark = isDarkFor(settings)
    val brand = settings?.let { BrandTheme.from(it, dark) } ?: BrandTheme.standard(dark)
    val palette = if (dark) Palette.dark else Palette.light

    val scheme = if (dark) {
        darkColorScheme(
            primary = brand.accent,
            onPrimary = brand.onAccent,
            secondary = brand.tint,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            onSurfaceVariant = palette.secondaryText,
            surfaceVariant = palette.bubble,
            surfaceContainer = palette.card,
            surfaceContainerHigh = palette.card,
            surfaceContainerHighest = palette.fill,
            surfaceContainerLow = palette.card,
            outline = palette.separator,
            outlineVariant = palette.separator,
            error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = brand.accent,
            onPrimary = brand.onAccent,
            secondary = brand.tint,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            onSurfaceVariant = palette.secondaryText,
            surfaceVariant = palette.bubble,
            surfaceContainer = palette.card,
            surfaceContainerHigh = palette.card,
            surfaceContainerHighest = palette.fill,
            surfaceContainerLow = palette.card,
            outline = palette.separator,
            outlineVariant = palette.separator,
            error = palette.error,
        )
    }

    CompositionLocalProvider(LocalBrand provides brand, LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
