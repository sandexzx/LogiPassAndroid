package com.example.logipass.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Static "wallpaper" behind the content; the glass refracts and blurs it. Cool mist with
 * periwinkle, mint, peach and lilac spots: soft enough that glass cards stay readable.
 */
@Composable
fun MistBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFFECEEF3))
        blob(Color(0xFFC6D2F4), Offset(size.width * 0.02f, size.height * 0.04f), size.width * 0.95f)
        blob(Color(0xFFCBE7DB), Offset(size.width * 1.00f, size.height * 0.30f), size.width * 0.85f)
        blob(Color(0xFFF2DACB), Offset(size.width * 0.00f, size.height * 0.66f), size.width * 0.90f)
        blob(Color(0xFFDAD2F0), Offset(size.width * 0.98f, size.height * 0.96f), size.width * 0.85f)
    }
}

/** A soft color spot: a radial gradient fading to transparent reads as a blurred circle. */
private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.35f), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
