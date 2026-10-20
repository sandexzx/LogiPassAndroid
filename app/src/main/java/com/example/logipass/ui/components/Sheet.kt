package com.example.logipass.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.logipass.ui.theme.AppType
import com.example.logipass.ui.theme.Style
import com.kyant.shapes.RoundedRectangle

/**
 * Bottom glass sheet over a dimmed screen, shown while [value] is non-null. The last value
 * keeps rendering during the exit animation. Back and a tap on the scrim call [onDismiss].
 *
 * Lives in [AppScaffold]'s overlay slot (it refracts [LocalBarBackdrop]); a Popup or
 * Dialog would be a separate window with nothing to refract.
 */
@Composable
fun <T : Any> SheetHost(
    value: T?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    val lastShown = remember { LastValue<T>() }
    if (value != null) lastShown.value = value
    val last = value ?: lastShown.value
    val visible = value != null
    val style = Style
    val sheetGlass = remember(style) {
        style.barGlass.copy(
            surface = Color.White.copy(alpha = 0.82f),
            blur = 24.dp,
            lensHeight = 16.dp,
            lensAmount = 32.dp,
            shadowAlpha = 0.12f,
        )
    }

    BackHandler(enabled = visible, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = 700f, visibilityThreshold = IntOffset(1, 1))) { it } +
                fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(8.dp),
        ) {
            Glass(
                backdrop = LocalBarBackdrop.current,
                style = sheetGlass,
                shape = remember { RoundedRectangle(34.dp) },
                contentAlignment = Alignment.TopStart,
                modifier = Modifier
                    .fillMaxWidth()
                    // Swallow taps so they don't reach the scrim.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    last?.let { content(it) }
                }
            }
        }
    }
}

/** Not state: only remembers what to draw while the sheet animates out. */
private class LastValue<T : Any> {
    var value: T? = null
}

@Immutable
data class SheetAction(
    val title: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Menu / confirmation content for [SheetHost]: optional title and message, actions, Cancel. */
@Composable
fun ColumnScope.ActionSheetContent(
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    title: String? = null,
    message: String? = null,
) {
    val style = Style
    if (title != null || message != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (title != null) {
                Text(title, style = AppType.headline, color = style.textPrimary, textAlign = TextAlign.Center)
            }
            if (message != null) {
                Text(message, style = AppType.footnote, color = style.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.6f)),
    ) {
        actions.forEachIndexed { index, action ->
            if (index > 0) Separator(Modifier.padding(start = 52.dp))
            val color = if (action.destructive) style.destructive else style.textPrimary
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .tappable {
                        onDismiss()
                        action.onClick()
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(36.dp)) {
                    if (action.icon != null) AppIcon(action.icon, color, size = 20.dp)
                }
                Text(action.title, style = AppType.body, color = color)
            }
        }
    }
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(54.dp)
            .tappable(onClick = onDismiss)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Отмена",
            style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
            color = style.accentText,
        )
    }
}

/** Heading row for form sheets: title with Cancel / confirm text buttons. */
@Composable
fun SheetHeader(
    title: String,
    onCancel: () -> Unit,
    confirm: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    val style = Style
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
    ) {
        Text(
            "Отмена",
            style = AppType.body,
            color = style.accentText,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .tappable(onClick = onCancel),
        )
        Text(
            title,
            style = AppType.headline,
            color = style.textPrimary,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            confirm,
            style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (confirmEnabled) style.accentText else style.textGhost,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .tappable(enabled = confirmEnabled, onClick = onConfirm),
        )
    }
}
