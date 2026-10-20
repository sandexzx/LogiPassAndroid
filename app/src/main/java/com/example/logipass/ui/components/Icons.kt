package com.example.logipass.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Line icons in the SF Symbols spirit: 24×24 viewport, round caps, drawn as strokes and
 * tinted by [AppIcon]. A zero-length segment ("h.01") renders as a dot.
 */
object Icons {
    val ChevronLeft = icon("ChevronLeft", "M15 5l-7 7 7 7", width = 2.2f)
    val ChevronRight = icon("ChevronRight", "M9 5l7 7-7 7")
    val ChevronDown = icon("ChevronDown", "M6 9l6 6 6-6", width = 2.2f)
    val Close = icon("Close", "M17 7L7 17M7 7l10 10", width = 2.2f)
    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7", width = 2.4f)
    val Plus = icon("Plus", "M12 5v14M5 12h14", width = 2.2f)
    val Settings = icon(
        "Settings",
        "M4 7h9M17 7h3M4 17h3M11 17h9" +
            "M17 7a2 2 0 1 1-4 0a2 2 0 1 1 4 0zM11 17a2 2 0 1 1-4 0a2 2 0 1 1 4 0z",
    )
    val Search = icon("Search", "M17 10.5a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0zM15.3 15.3L20 20", width = 2.2f)
    val Lock = icon(
        "Lock",
        "M8 11V8a4 4 0 0 1 8 0v3M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM12 15v2",
    )
    val Eye = icon(
        "Eye",
        "M2.5 12C4 8.5 7.5 6 12 6s8 2.5 9.5 6c-1.5 3.5-5 6-9.5 6s-8-2.5-9.5-6zM15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
    )
    val Hide = icon(
        "Hide",
        "M3 3l18 18M10.6 6.1A9.6 9.6 0 0 1 12 6c5 0 8.5 4 9.5 6a13 13 0 0 1-2.4 3.2M6.6 6.6C4.5 8 3.1 10 2.5 12c1 2 4.5 6 9.5 6a9.3 9.3 0 0 0 5.4-1.6" +
            "M9.9 9.9a3 3 0 0 0 4.2 4.2",
    )
    val Copy = icon(
        "Copy",
        "M15.5 8V6a2 2 0 0 0-2-2h-7.5a2 2 0 0 0-2 2v7.5a2 2 0 0 0 2 2H8" +
            "M10.5 8.5h7.5a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2h-7.5a2 2 0 0 1-2-2v-7.5a2 2 0 0 1 2-2z",
    )
    val Key = icon(
        "Key",
        "M11.5 15.5a4 4 0 1 1-8 0a4 4 0 1 1 8 0zM10.4 12.6L19 4M15.5 7.5l2.5 2.5M13.5 9.5l1.5 1.5",
    )
    val Fingerprint = icon(
        "Fingerprint",
        "M6.8 5.3A8 8 0 0 1 20 11.5v1.5M4.3 9A8 8 0 0 0 4 11.5V14" +
            "M8 19.5c.7-1.6 1-3.3 1-5v-3a3 3 0 0 1 6 0v2.5" +
            "M12 11.5v3c0 2.4-.6 4.6-1.7 6.5M15 17.5c-.2 1.2-.5 2.4-1 3.5M18.6 16.6c-.1.5-.2 1-.4 1.4",
        width = 1.8f,
    )
    val Trash = icon("Trash", "M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3")
    val Export = icon("Export", "M12 14V3.5M8 7.5l4-4 4 4M7 10.5H6a1 1 0 0 0-1 1V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-8.5a1 1 0 0 0-1-1h-1")
    val Import = icon("Import", "M12 3.5V14M8 10l4 4 4-4M5 14.5V19a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4.5")
    val Shield = icon("Shield", "M12 3l7 3v5.5c0 4.3-3 8-7 9.5c-4-1.5-7-5.2-7-9.5V6zM9 12l2 2 4-4")
    val Warning = icon("Warning", "M10.3 4.6L2.8 17.5A2 2 0 0 0 4.5 20.5h15a2 2 0 0 0 1.7-3L13.7 4.6a2 2 0 0 0-3.4 0zM12 10v4M12 17h.01")
    val Pencil = icon("Pencil", "M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17zM14 8l3 3")
    val Person = icon("Person", "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0zM4.5 20.5a7.5 7.5 0 0 1 15 0")
    val Info = icon("Info", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0zM12 11v5M12 8h.01")
    val Clock = icon("Clock", "M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0zM12 7.5V12l3 2")
    val Undo = icon("Undo", "M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
}

private fun icon(name: String, pathData: String, width: Float = 2f): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

@Composable
fun AppIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
