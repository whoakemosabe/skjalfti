package app.skjalfti.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.cos
import kotlin.math.sin

/*
 * Liquid glass, the same recipe as Ljós: Kyant's Backdrop library records the glowing background
 * (layerBackdrop) and every card or control redraws it through a little extra colour, a frost and
 * the library's lens, which bends the rounded edge with depth and colour fringing. The lens needs
 * Android 13; older phones get a translucent tint instead.
 */

/** The background every glass piece on screen refracts. Null in the pixel skin. */
val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

private val glassOk get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** A card: frosted enough for text, a gentle lens, a rim highlight from the top left. */
fun Modifier.glassCard(backdrop: LayerBackdrop, shape: CornerBasedShape): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        if (glassOk) {
            colorControls(saturation = 1.25f)
            blur(14.dp.toPx())
            lens(14.dp.toPx(), 22.dp.toPx(), chromaticAberration = true)
        }
    },
    highlight = { Highlight(style = HighlightStyle.Default(angle = 45f)) },
    shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.35f)) },
    onDrawSurface = {
        drawRect(Color(0x33070A10))
        drawRect(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x05FFFFFF))))
    },
)

/** A floating control: barely frosted, the lens bending its whole edge. */
fun Modifier.glassControl(backdrop: LayerBackdrop, shape: CornerBasedShape, selected: Boolean): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        if (glassOk) {
            colorControls(saturation = 1.3f)
            blur(3.dp.toPx())
            lens(10.dp.toPx(), 16.dp.toPx(), chromaticAberration = true)
        }
    },
    highlight = { Highlight(style = HighlightStyle.Default(angle = 45f)) },
    shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.25f)) },
    onDrawSurface = {
        drawRect(
            when {
                selected -> Color(0x33FFFFFF)
                glassOk -> Color(0x1405080F)
                else -> Color(0x22FFFFFF)
            }
        )
    },
)

/**
 * Molten glow behind the glass: a lava blob, a cold blue one and a violet one drifting slowly.
 * [modifier] should record it with layerBackdrop so the glass can see it.
 */
@Composable
fun GlassBackground(accent: Color, modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "drift")
    val a by drift.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "a",
    )
    Canvas(modifier) {
        drawRect(Color(0xFF06080B))
        val w = size.width
        val h = size.height
        fun blob(c: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
            drawCircle(
                Brush.radialGradient(
                    listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.4f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        blob(accent, w * (0.15f + 0.08f * cos(a)), h * (0.3f + 0.05f * sin(a)), w * 0.95f, 0.55f)
        blob(Color(0xFF2A6CFF), w * (0.95f + 0.06f * sin(a * 2)), h * (0.66f + 0.04f * cos(a)), w * 0.8f, 0.42f)
        blob(Color(0xFF9B3BFF), w * (0.45f + 0.1f * sin(a)), h * (1.02f + 0.03f * cos(a * 2)), w * 0.6f, 0.32f)
    }
}
