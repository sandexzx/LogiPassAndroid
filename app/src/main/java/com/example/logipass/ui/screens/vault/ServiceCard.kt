package com.example.logipass.ui.screens.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.logipass.model.Credential
import com.example.logipass.model.Service
import com.example.logipass.ui.components.AppIcon
import com.example.logipass.ui.components.CopyButton
import com.example.logipass.ui.components.Icons
import com.example.logipass.ui.components.InlineIconButton
import com.example.logipass.ui.components.SecondaryButton
import com.example.logipass.ui.components.Separator
import com.example.logipass.ui.components.SurfaceCard
import com.example.logipass.ui.components.tappable
import com.example.logipass.ui.format.accountsCount
import com.example.logipass.ui.theme.AppType
import com.example.logipass.ui.theme.Style

/** Callbacks of one card, grouped so the list item stays readable. */
class ServiceCardActions(
    val onToggle: () -> Unit,
    val onEditService: () -> Unit,
    val onAddCredential: () -> Unit,
    val onEditCredential: (Credential) -> Unit,
    val onCopyUsername: (Credential) -> Unit,
    val onCopyPassword: (Credential) -> Unit,
    val onToggleReveal: (Credential) -> Unit,
)

/**
 * Glass card for one service. Collapsed: monogram, name, summary. Expanded: each account
 * as an inset block with copy / reveal buttons; tapping a block edits it.
 */
@Composable
fun ServiceCard(
    service: Service,
    expanded: Boolean,
    terms: List<String>,
    revealed: Set<String>,
    actions: ServiceCardActions,
    modifier: Modifier = Modifier,
) {
    val style = Style
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "cardChevron",
    )
    SurfaceCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .tappable(onLongClick = actions.onEditService, onClick = actions.onToggle)
                .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Monogram(service.name)
            Column(Modifier.weight(1f)) {
                Text(
                    highlight(service.name, terms, style.accentText),
                    style = AppType.headline,
                    color = style.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val summary = service.description.ifBlank { accountsCount(service.credentials.size) }
                Text(
                    highlight(summary, terms, style.accentText),
                    style = AppType.footnote,
                    color = style.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!expanded && service.description.isNotBlank() && service.credentials.size > 1) {
                CountPill(service.credentials.size)
            }
            AppIcon(
                Icons.ChevronDown,
                style.textGhost,
                size = 18.dp,
                modifier = Modifier.graphicsLayer { rotationZ = chevron },
            )
        }

        // The card opens from its header down, uncovering the accounts in place; they fade in and
        // settle a beat later. Closing fades them first, then folds the card back up.
        val rise = with(LocalDensity.current) { 8.dp.roundToPx() }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 380f), expandFrom = Alignment.Top) +
                fadeIn(tween(220, delayMillis = 60)) +
                slideInVertically(tween(280, delayMillis = 60, easing = FastOutSlowInEasing)) { rise },
            exit = fadeOut(tween(120)) +
                shrinkVertically(tween(240, delayMillis = 60, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
        ) {
            Column(
                Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (service.credentials.isEmpty()) {
                    Text(
                        "Аккаунтов пока нет",
                        style = AppType.subheadline,
                        color = style.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                service.credentials.forEach { credential ->
                    CredentialBlock(
                        credential = credential,
                        revealed = credential.id in revealed,
                        terms = terms,
                        actions = actions,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecondaryButton(
                        text = "Аккаунт",
                        icon = Icons.Plus,
                        onClick = actions.onAddCredential,
                        fill = style.fieldFill,
                        modifier = Modifier.weight(1f),
                        height = 40.dp,
                    )
                    SecondaryButton(
                        text = "Изменить",
                        icon = Icons.Pencil,
                        onClick = actions.onEditService,
                        fill = style.fieldFill,
                        modifier = Modifier.weight(1f),
                        height = 40.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialBlock(
    credential: Credential,
    revealed: Boolean,
    terms: List<String>,
    actions: ServiceCardActions,
) {
    val style = Style
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(style.fieldFill)
            .tappable { actions.onEditCredential(credential) }
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        if (credential.username.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(label = "Логин", modifier = Modifier.weight(1f)) {
                    Text(
                        highlight(credential.username, terms, style.accentText),
                        style = AppType.body,
                        color = style.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CopyButton(
                    onClick = { actions.onCopyUsername(credential) },
                    contentDescription = "Скопировать логин",
                )
            }
        }
        if (credential.username.isNotEmpty() && credential.password.isNotEmpty()) {
            Separator(Modifier.padding(end = 10.dp))
        }
        if (credential.password.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(label = "Пароль", modifier = Modifier.weight(1f)) {
                    // Masked with a fixed number of dots so the length doesn't leak.
                    AnimatedContent(
                        targetState = revealed,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "passwordReveal",
                    ) { show ->
                        Text(
                            if (show) credential.password else "••••••••",
                            style = if (show) AppType.secret else AppType.body,
                            color = style.textPrimary,
                            maxLines = if (show) 3 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                InlineIconButton(
                    if (revealed) Icons.Hide else Icons.Eye,
                    onClick = { actions.onToggleReveal(credential) },
                    contentDescription = if (revealed) "Скрыть пароль" else "Показать пароль",
                )
                CopyButton(
                    onClick = { actions.onCopyPassword(credential) },
                    contentDescription = "Скопировать пароль",
                )
            }
        }
        if (credential.note.isNotBlank()) {
            if (credential.username.isNotEmpty() || credential.password.isNotEmpty()) {
                Separator(Modifier.padding(end = 10.dp))
            }
            Text(
                highlight(credential.note, terms, style.accentText),
                style = AppType.footnote,
                color = style.textSecondary,
                modifier = Modifier.padding(end = 10.dp, top = 10.dp, bottom = 10.dp),
            )
        }
    }
}

/** Small grey label over a value. */
@Composable
private fun Field(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier.heightIn(min = 52.dp).padding(vertical = 6.dp), verticalArrangement = Arrangement.Center) {
        Text(label, style = AppType.caption, color = Style.textGhost)
        value()
    }
}

@Composable
private fun CountPill(count: Int) {
    val style = Style
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(style.insetFill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text("$count", style = AppType.caption.merge(AppType.numeric), color = style.textSecondary)
    }
}

// Muted inks: low saturation, mid-dark, so the tile reads as a tint of the glass, not a sticker.
private val monogramInks = listOf(
    Color(0xFF3E5A7E), // slate blue
    Color(0xFF4F6B57), // sage
    Color(0xFF85683F), // sand
    Color(0xFF6E5374), // plum
    Color(0xFF8A5862), // dusty rose
    Color(0xFF3F686B), // sea grey
    Color(0xFF58598A), // lavender grey
    Color(0xFF66653F), // olive
)

/** Rounded square with the service's first letter in a muted tint; the hue is stable per name. */
@Composable
fun Monogram(name: String, modifier: Modifier = Modifier) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    val ink = remember(name) {
        monogramInks[Math.floorMod(name.trim().lowercase().hashCode(), monogramInks.size)]
    }
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier
            .size(44.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ink.copy(alpha = 0.10f), ink.copy(alpha = 0.17f))))
            .border(0.5.dp, ink.copy(alpha = 0.16f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = AppType.title3.copy(fontWeight = FontWeight.SemiBold), color = ink)
    }
}
