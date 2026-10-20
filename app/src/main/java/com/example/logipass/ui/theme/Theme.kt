package com.example.logipass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.logipass.ui.components.GlassStyle

/**
 * The app chrome: clear refracting glass over a cool mist mesh, with an iOS-blue accent.
 * Light only.
 */
@Immutable
data class AppStyle(
    /** Fills: primary buttons, toggles. */
    val accent: Color,
    /** Accent used for text and links. */
    val accentText: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textGhost: Color,
    val fieldFill: Color,
    /** Field fill inside a glass card, where the plain fill would vanish. */
    val insetFill: Color,
    val separator: Color,
    val cardGlass: GlassStyle,
    val barGlass: GlassStyle,
    val cardCorner: Dp = 26.dp,
    val destructive: Color,
    val positive: Color,
    val warning: Color,
)

val MistGlass = AppStyle(
    accent = Color(0xFF0A66E8),
    accentText = Color(0xFF0A5FD8),
    textPrimary = Color(0xFF111317),
    textSecondary = Color(0xA32E3440),
    textGhost = Color(0x6B2E3440),
    fieldFill = Color(0x8CFFFFFF),
    insetFill = Color(0x14303A50),
    separator = Color(0x262E3440),
    cardGlass = GlassStyle(
        surface = Color(0x66FFFFFF),
        blur = 16.dp,
        lensHeight = 10.dp,
        lensAmount = 16.dp,
        shadowAlpha = 0.05f,
    ),
    barGlass = GlassStyle(
        surface = Color(0x4DFFFFFF),
        blur = 3.dp,
        lensHeight = 16.dp,
        lensAmount = 36.dp,
        chromaticAberration = true,
    ),
    destructive = Color(0xFFE5372B),
    positive = Color(0xFF1E9E55),
    warning = Color(0xFFC77A00),
)

val LocalAppStyle = staticCompositionLocalOf { MistGlass }

/** Shortcut for the app style inside composables. */
val Style: AppStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalAppStyle.current

// Material components are rarely used; the scheme just keeps them on-palette.
@Composable
fun LogiPassTheme(content: @Composable () -> Unit) {
    val style = MistGlass
    val colors = lightColorScheme(
        primary = style.accent,
        onPrimary = Color.White,
        background = Color(0xFFECEEF3),
        onBackground = style.textPrimary,
        surface = Color.White,
        onSurface = style.textPrimary,
        onSurfaceVariant = style.textSecondary,
        error = style.destructive,
    )
    CompositionLocalProvider(LocalAppStyle provides style) {
        MaterialTheme(colorScheme = colors, typography = Typography, content = content)
    }
}
