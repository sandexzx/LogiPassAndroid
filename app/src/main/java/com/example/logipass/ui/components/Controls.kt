package com.example.logipass.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.logipass.ui.theme.AppType
import com.example.logipass.ui.theme.Style
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** iOS-style tap feedback: dims while pressed instead of a ripple. */
fun Modifier.tappable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.45f else 1f, label = "tapAlpha")
    this
        .graphicsLayer { this.alpha = alpha }
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = role,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
            } else {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = role,
                    onClick = onClick,
                )
            },
        )
}

/** Like [tappable] but shrinks slightly; for chunky buttons. */
fun Modifier.bouncy(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "bounceScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Content card: frosted glass refracting the background. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = Style
    val shape = remember(style.cardCorner) { RoundedRectangle(style.cardCorner) }
    Glass(
        backdrop = LocalCardBackdrop.current,
        style = style.cardGlass,
        shape = shape,
        onClick = onClick,
        contentAlignment = Alignment.TopStart,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/**
 * Floating glass capsule for bars: icon and/or text. [tint] = filled accent button.
 * Inside [AppScaffold]'s content, pass [LocalCardBackdrop]: the bar backdrop exists only in the bar and overlay slots.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    height: Dp = 44.dp,
    backdrop: Backdrop = LocalBarBackdrop.current,
    content: @Composable RowScope.() -> Unit,
) {
    Glass(
        backdrop = backdrop,
        style = Style.barGlass,
        tint = tint,
        onClick = onClick,
        modifier = modifier.height(height),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            content = content,
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Style.textPrimary,
    fill: Color = Color.Unspecified,
    size: Dp = 44.dp,
) {
    Glass(
        backdrop = LocalBarBackdrop.current,
        style = Style.barGlass,
        tint = fill,
        onClick = onClick,
        modifier = modifier.size(size),
    ) {
        AppIcon(icon, tint)
    }
}

/** Top bar of a pushed screen: glass back button, optional trailing actions. Titles live in the content. */
@Composable
fun NavBar(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) GlassIconButton(Icons.ChevronLeft, onBack)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val style = Style
    Column(modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(title, style = AppType.largeTitle, color = style.textPrimary)
        if (subtitle != null) {
            Text(subtitle, style = AppType.subheadline, color = style.textSecondary)
        }
    }
}

/** Small grey caps label above a grouped card, iOS Settings style. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
        color = Style.textSecondary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

/** Grey explanatory text under a grouped card. */
@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppType.footnote,
        color = Style.textSecondary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
    )
}

/** Big filled call to action inside the content. [loading] swaps the label for a spinner. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Style.accent,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val active = enabled && !loading
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .bouncy(enabled = active, onClick = onClick)
            .graphicsLayer { alpha = if (enabled || loading) 1f else 0.4f }
            .clip(RoundedCornerShape(50))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "primaryLoading",
        ) { busy ->
            if (busy) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        AppIcon(icon, Color.White, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text, style = AppType.headline, color = Color.White)
                }
            }
        }
    }
}

/** Tinted secondary action: field-colored fill, accent label. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Style.accentText,
    fill: Color = Style.fieldFill,
    height: Dp = 44.dp,
) {
    Row(
        modifier
            .height(height)
            .tappable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AppIcon(icon, color, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = AppType.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
        )
    }
}

/** Plain text button, iOS link style. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Style.accentText) {
    Text(
        text,
        style = AppType.subheadline.copy(fontWeight = FontWeight.Medium),
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .tappable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/** iOS segmented control: white thumb sliding over a translucent track. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = Style
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(style.insetFill)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segment * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
            label = "segmentOffset",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .height(30.dp)
                .shadow(2.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
        Row(Modifier.fillMaxWidth().height(30.dp)) {
            options.forEachIndexed { index, option ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        style = AppType.footnote.copy(
                            fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = style.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** One row inside a card: title, optional subtitle, trailing value/content, chevron when clickable. */
@Composable
fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = Style.textPrimary,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val style = Style
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.tappable(enabled = enabled, onClick = onClick) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppType.body,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = AppType.footnote,
                    color = style.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            Text(trailingText, style = AppType.body, color = style.textSecondary, maxLines = 1)
        }
        trailing?.invoke()
        if (showChevron) AppIcon(Icons.ChevronRight, style.textGhost, size = 16.dp)
    }
}

@Composable
fun Separator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Style.separator),
    )
}

/** Rounded square behind a row icon: solid color, white glyph, like iOS Settings. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, Color.White, size = 18.dp)
    }
}

/** Small round icon button for use inside cards (copy, reveal). */
@Composable
fun InlineIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Style.textSecondary,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .tappable(onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, tint, size = 19.dp)
    }
}

/**
 * [InlineIconButton] for copy actions: on tap the copy glyph gives way to a green check that
 * draws itself in over a soft green halo, holds for a moment, then fades back.
 */
@Composable
fun CopyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val style = Style
    var copies by remember { mutableIntStateOf(0) }
    val shown = remember { Animatable(0f) }
    val stroke = remember { Animatable(0f) }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        stroke.snapTo(0f)
        launch { shown.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 700f)) }
        stroke.animateTo(1f, tween(280, delayMillis = 60, easing = FastOutSlowInEasing))
        delay(1200)
        shown.animateTo(0f, tween(220))
    }
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .drawBehind { drawCircle(style.positive.copy(alpha = 0.14f * shown.value.coerceIn(0f, 1f))) }
            .tappable {
                copies++
                onClick()
            }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            Icons.Copy,
            style.textSecondary,
            size = 19.dp,
            modifier = Modifier.graphicsLayer {
                val t = shown.value.coerceIn(0f, 1f)
                alpha = 1f - t
                scaleX = 1f - 0.4f * t
                scaleY = scaleX
            },
        )
        // Same geometry as Icons.Check, drawn here so the stroke can be trimmed.
        Canvas(
            Modifier
                .size(19.dp)
                .graphicsLayer {
                    alpha = shown.value.coerceIn(0f, 1f)
                    scaleX = 0.6f + 0.4f * shown.value
                    scaleY = scaleX
                },
        ) {
            val k = size.width / 24f
            val check = Path().apply {
                moveTo(5f * k, 12.5f * k)
                lineTo(9.5f * k, 17f * k)
                lineTo(19f * k, 7f * k)
            }
            val measure = PathMeasure().apply { setPath(check, false) }
            val drawn = Path()
            measure.getSegment(0f, measure.length * stroke.value, drawn, true)
            drawPath(drawn, style.positive, style = Stroke(2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    val style = Style
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(style.fieldFill),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, style.textSecondary, size = 30.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = AppType.headline, color = style.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = AppType.subheadline, color = style.textSecondary, textAlign = TextAlign.Center)
    }
}

/** iOS switch. */
@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val style = Style
    val track by animateColorAsState(if (checked) style.positive else Color(0x29787880), label = "toggleTrack")
    val offset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "toggleThumb",
    )
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(50))
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) { onCheckedChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
