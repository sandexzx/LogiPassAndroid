package com.example.logipass.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.unit.IntOffset

/** Screens of an unlocked session. Locking drops the whole stack. */
enum class Route { Vault, Settings }

/** Back stack of screens. Survives configuration changes. */
@Stable
class Navigator internal constructor(initial: List<Route>) {
    internal val stack = initial.toMutableStateList()
    internal var lastWasPop by mutableStateOf(false)
        private set

    val current: Route get() = stack.last()

    fun push(route: Route) {
        lastWasPop = false
        stack += route
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        lastWasPop = true
        stack.removeAt(stack.lastIndex)
        return true
    }
}

@Composable
fun rememberNavigator(): Navigator = rememberSaveable(
    saver = listSaver(
        save = { navigator -> navigator.stack.map { it.name } },
        restore = { saved -> Navigator(saved.map(Route::valueOf)) },
    ),
) { Navigator(listOf(Route.Vault)) }

private data class Entry(val index: Int, val route: Route) {
    val key = "$index/${route.name}"
}

/**
 * Shows the top of the back stack with iOS push/pop motion: the new screen slides in from
 * the right over the old one, which drifts a third of the way left. Each entry keeps its
 * saveable state (scroll position, inputs) while it is buried in the stack.
 */
@Composable
fun NavHost(navigator: Navigator, content: @Composable (Route) -> Unit) {
    val stateHolder = rememberSaveableStateHolder()
    val entries = navigator.stack.mapIndexed(::Entry)
    val keys = entries.map { it.key }
    val knownKeys = remember { mutableSetOf<String>() }

    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }

    LaunchedEffect(keys) {
        (knownKeys - keys.toSet()).forEach(stateHolder::removeState)
        knownKeys.clear()
        knownKeys += keys
    }

    AnimatedContent(
        targetState = entries.last(),
        contentKey = { it.key },
        transitionSpec = {
            val spec = tween<IntOffset>(380, easing = FastOutSlowInEasing)
            if (navigator.lastWasPop) {
                (slideInHorizontally(spec) { -it / 3 } + fadeIn(tween(380)))
                    .togetherWith(slideOutHorizontally(spec) { it })
                    .apply { targetContentZIndex = -1f }
            } else {
                slideInHorizontally(spec) { it }
                    .togetherWith(slideOutHorizontally(spec) { -it / 3 } + fadeOut(tween(380), targetAlpha = 0.6f))
            }
        },
        label = "navHost",
    ) { entry ->
        stateHolder.SaveableStateProvider(entry.key) {
            content(entry.route)
        }
    }
}
