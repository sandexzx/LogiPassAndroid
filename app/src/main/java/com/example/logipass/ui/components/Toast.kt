package com.example.logipass.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.logipass.ui.theme.AppType
import com.example.logipass.ui.theme.Style
import kotlinx.coroutines.delay

data class ToastMessage(
    val text: String,
    val icon: ImageVector? = null,
    val action: String? = null,
    val onAction: (() -> Unit)? = null,
    val error: Boolean = false,
    /** Distinguishes two identical messages in a row so the timer restarts. */
    val id: Long = System.nanoTime(),
)

/** One toast at a time; a new one replaces the current one. */
@Stable
class ToastState {
    var current by mutableStateOf<ToastMessage?>(null)
        private set

    fun show(text: String, icon: ImageVector? = null) {
        current = ToastMessage(text, icon)
    }

    fun error(text: String) {
        current = ToastMessage(text, Icons.Warning, error = true)
    }

    fun undo(text: String, onUndo: () -> Unit) {
        current = ToastMessage(text, Icons.Trash, action = "Вернуть", onAction = onUndo)
    }

    fun dismiss() {
        current = null
    }
}

/**
 * Floating glass capsule above the bottom bar. Belongs in [AppScaffold]'s overlay slot so it
 * refracts the screen. Clears the navigation bar and keyboard; [bottomInset] lifts it
 * further, over the screen's own bottom bar.
 */
@Composable
fun ToastHost(state: ToastState, bottomInset: Dp = 0.dp) {
    val toast = state.current
    // Not state: only remembers what to draw while the toast animates out.
    val last = remember { arrayOfNulls<ToastMessage>(1) }
    if (toast != null) last[0] = toast
    LaunchedEffect(toast?.id) {
        if (toast == null) return@LaunchedEffect
        delay(if (toast.action != null) 5_000 else 2_600)
        if (state.current?.id == toast.id) state.dismiss()
    }
    val style = Style
    Box(
        Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(bottom = bottomInset + 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 500f)) { it / 2 } +
                scaleIn(initialScale = 0.9f) + fadeIn(),
            exit = slideOutVertically { it / 3 } + scaleOut(targetScale = 0.94f) + fadeOut(),
        ) {
            val shown = last[0] ?: return@AnimatedVisibility
            Glass(
                backdrop = LocalBarBackdrop.current,
                style = style.barGlass.copy(surface = Color.White.copy(alpha = 0.78f), blur = 12.dp, shadowAlpha = 0.12f),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 420.dp)
                    .heightIn(min = 48.dp),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = if (shown.action != null) 6.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (shown.icon != null) {
                        AppIcon(shown.icon, if (shown.error) style.destructive else style.textPrimary, size = 18.dp)
                    }
                    Text(
                        shown.text,
                        style = AppType.subheadline.copy(fontWeight = FontWeight.Medium),
                        color = style.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(vertical = 12.dp),
                    )
                    if (shown.action != null) {
                        Text(
                            shown.action,
                            style = AppType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            color = style.accentText,
                            modifier = Modifier
                                .tappable {
                                    state.dismiss()
                                    shown.onAction?.invoke()
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
