package com.example.logipass.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.logipass.ui.theme.AppType
import com.example.logipass.ui.theme.Style

/** Single-line (or multi-line) text input with a field-colored fill. */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fill: Color = Style.fieldFill,
    textStyle: TextStyle = AppType.body,
    focusRequester: FocusRequester? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    autoCorrect: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val style = Style
    val text = textStyle.copy(color = style.textPrimary)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        enabled = enabled,
        textStyle = text,
        cursorBrush = SolidColor(style.accent),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            capitalization = capitalization,
            autoCorrectEnabled = autoCorrect,
        ),
        // Without a handler the IME action keeps its default (move focus for Next, hide for Done).
        keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Row(
                Modifier.padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    AppIcon(leadingIcon, style.textSecondary, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = text.copy(color = style.textGhost), maxLines = 1)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * Password field: masked by default with an eye toggle, no autocorrect or suggestions.
 * [revealable] = false hides the toggle (master password confirmation).
 */
@Composable
fun PasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    fill: Color = Style.fieldFill,
    revealable: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    TextInput(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.None,
        imeAction = imeAction,
        onImeAction = onImeAction,
        enabled = enabled,
        fill = fill,
        textStyle = if (visible) AppType.secret.copy(fontSize = AppType.body.fontSize) else AppType.body,
        focusRequester = focusRequester,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        autoCorrect = false,
        trailing = if (revealable) {
            {
                InlineIconButton(
                    icon = if (visible) Icons.Hide else Icons.Eye,
                    onClick = { visible = !visible },
                    contentDescription = if (visible) "Скрыть пароль" else "Показать пароль",
                )
            }
        } else {
            null
        },
    )
}

/** Shakes sideways each time [trigger] changes (except the first composition): wrong password. */
fun Modifier.shake(trigger: Int): Modifier = composed {
    val offset = remember { Animatable(0f) }
    val distance = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 420
                -distance at 60
                distance at 140
                -distance * 0.7f at 220
                distance * 0.5f at 300
                -distance * 0.2f at 370
            },
        )
    }
    graphicsLayer { translationX = offset.value }
}
