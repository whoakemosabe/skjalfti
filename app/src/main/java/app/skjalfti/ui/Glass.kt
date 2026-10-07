package app.skjalfti.ui

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.cos
import kotlin.math.sin

/*
 * Liquid glass, the Ljós recipe on Kyant's Backdrop library. Two recordings drive it:
 *  - the sky (the molten background alone), which cards and the controls on them refract, since
 *    they can't refract a recording of the page they're part of;
 *  - the page (sky plus everything scrolling on it), which the header and the floating tab bar
 *    refract, so content visibly slides under them, frosted and bent.
 * The lens and the dissolve need Android 13; older phones get a plain tint.
 */

/** The sky behind cards and the controls on them. Null in the pixel skin. */
val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

val glassOk: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Fades the frosted copy out over the pane's last stretch, (1 - x)², so there's no edge. */
private const val Dissolve = """
uniform shader content;
uniform float2 offset;
uniform float fadeTop;
uniform float fadeBottom;

half4 main(float2 coord) {
    float y = coord.y + offset.y;
    float x = clamp((y - fadeTop) / max(fadeBottom - fadeTop, 1.0), 0.0, 1.0);
    float a = (1.0 - x) * (1.0 - x);
    return content.eval(coord) * half(a);
}
"""

private fun BackdropEffectScope.dissolve(fadeTop: Float, fadeBottom: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val shader = obtainRuntimeShader("SkjalftiDissolve", Dissolve).apply {
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("fadeTop", fadeTop)
        setFloatUniform("fadeBottom", fadeBottom)
    }
    effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
}

/**
 * A pane of liquid glass across the top of the screen, exactly as Ljós: frost and colour, no lens
 * (a bar that fades out has no real edge to bend), dissolving over [fade] at the bottom, the
 * page mirrored into the off-screen margins so the frost never pulls in emptiness at the sides.
 */
@Composable
fun GlassHeader(
    backdrop: LayerBackdrop,
    bodyPx: () -> Float,
    fade: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    frost: Dp = 6.dp,
) {
    val density = LocalDensity.current
    val fadePx = with(density) { fade.toPx() }
    val margin = with(density) { 22.dp.roundToPx() }
    val screenW = remember { floatArrayOf(0f) }
    val shownTint = if (glassOk) tint.copy(alpha = tint.alpha * 0.22f) else tint
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val h = (bodyPx() + fadePx).toInt().coerceAtLeast(1)
                val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(p.width, h) { p.place(0, 0) }
            }
    ) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    screenW[0] = constraints.maxWidth.toFloat()
                    val w = constraints.maxWidth + margin * 2
                    val h = constraints.maxHeight + margin
                    val p = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(-margin, -margin) }
                }
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = {
                        if (glassOk) {
                            colorControls(saturation = 1.15f)
                            blur(frost.toPx())
                            val bottom = size.height
                            dissolve(bottom - fadePx, bottom)
                        }
                    },
                    onDrawBackdrop = { drawPage ->
                        if (!glassOk) return@drawPlainBackdrop
                        drawPage()
                        val m = margin.toFloat()
                        val right = m + screenW[0]
                        withTransform({ scale(-1f, 1f, pivot = Offset(m, 0f)) }) { drawPage() }
                        withTransform({ scale(-1f, 1f, pivot = Offset(right, 0f)) }) { drawPage() }
                        withTransform({ scale(1f, -1f, pivot = Offset(0f, m)) }) { drawPage() }
                    },
                    onDrawSurface = {
                        val h = size.height
                        val k = ((h - fadePx) / h).coerceIn(0f, 1f)
                        drawRect(Brush.verticalGradient(0f to shownTint, k to shownTint.copy(alpha = shownTint.alpha * 0.6f), 1f to Color.Transparent))
                    },
                )
        )
    }
}

/**
 * Liquid glass for a card, as Ljós: frosted enough for text, a gentle lens round its rounded edge
 * with colour fringing, a rim highlight from the top left, a soft shadow and a dark wash.
 */
fun Modifier.glassCard(backdrop: LayerBackdrop, shape: CornerBasedShape): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        if (glassOk) {
            colorControls(saturation = 1.2f)
            blur(10.dp.toPx())
            lens(16.dp.toPx(), 24.dp.toPx(), chromaticAberration = true)
        }
    },
    highlight = { Highlight(style = HighlightStyle.Default(angle = 45f)) },
    shadow = { Shadow(radius = 20.dp, color = Color.Black.copy(alpha = 0.25f)) },
    onDrawSurface = {
        drawRect(Color(0x4D070B16))
        drawRect(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x05FFFFFF))))
    },
)

/**
 * True liquid glass for a floating control (button, pill, tab bar), as Ljós and iOS 26: barely
 * frosted, the lens bending its whole edge with colour fringing, a rim highlight, a soft shadow.
 */
fun Modifier.glassControl(
    backdrop: LayerBackdrop,
    shape: CornerBasedShape,
    lensHeight: Dp = 10.dp,
    lensAmount: Dp = 18.dp,
    wash: Color = Color(0x2605080F),
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        if (glassOk) {
            colorControls(saturation = 1.25f)
            blur(2.dp.toPx())
            lens(lensHeight.toPx(), lensAmount.toPx(), chromaticAberration = true)
        }
    },
    highlight = { Highlight(style = HighlightStyle.Default(angle = 45f)) },
    shadow = { Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.22f)) },
    onDrawSurface = {
        // A faint dark wash so white text and icons read over bright things behind.
        drawRect(if (glassOk) wash else Color(0x33FFFFFF))
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
