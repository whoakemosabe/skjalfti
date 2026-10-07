package app.skjalfti.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop

// The glass header, measured down from the bottom of the status bar, as Ljós.
val HeaderPadTop = 14.dp
val HeaderRow = 40.dp
val HeaderBody = 74.dp   // 14 + 40 row + 20 of clear glass below it
val HeaderFade = 28.dp   // then it dissolves over this
// The floating tab bar.
val TabBarHeight = 62.dp
val TabBarGap = 12.dp

private val Ink = Color(0xFFF2F4F7)
private val HeaderTextShadow = TextStyle(shadow = Shadow(Color(0xB3000000), blurRadius = 18f))

/** A round liquid-glass button that swells a little when pressed, as in Ljós. */
@Composable
fun GlassButton(backdrop: LayerBackdrop, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.1f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
    val view = LocalView.current
    Box(
        Modifier
            .requiredSize(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassControl(backdrop, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick()
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The title row over the glass header: "Skjálfti", the place in a glass pill (tap for Setup),
 * then round glass buttons: back when a page is open, sync, and the guide.
 */
@Composable
fun GlassTopBar(
    backdrop: LayerBackdrop,
    place: String,
    accent: Color,
    showBack: Boolean,
    loading: Boolean,
    onBack: () -> Unit,
    onPlace: () -> Unit,
    onSync: () -> Unit,
    onGuide: () -> Unit,
) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp)
            .padding(top = HeaderPadTop)
            .height(HeaderRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            GlassButton(backdrop, onBack) { ChevronIcon() }
            Spacer(Modifier.width(10.dp))
        }
        Txt("Skjálfti", TextStyle(fontFamily = Fonts.geist, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp).merge(HeaderTextShadow), Ink)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.widthIn(max = 170.dp)) {
            Row(
                Modifier
                    .glassControl(backdrop, CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onPlace()
                    }
                    .height(34.dp)
                    .padding(start = 11.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PinIcon(accent, Modifier.size(width = 10.dp, height = 13.dp))
                Spacer(Modifier.width(6.dp))
                Txt(place, TextStyle(fontFamily = Fonts.geist, fontSize = 13.sp), Ink.copy(alpha = 0.92f), Modifier.weight(1f, fill = false))
            }
        }
        Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
        GlassButton(backdrop, onSync, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Ink, strokeWidth = 2.dp)
            else RefreshIcon()
        }
        Spacer(Modifier.width(10.dp))
        GlassButton(backdrop, onGuide) { QuestionIcon() }
    }
}

/**
 * The floating tab bar: one capsule of liquid glass over the page, refracting whatever scrolls
 * under it, with a brighter glass pill that slides to the chosen tab.
 */
@Composable
fun GlassTabBar(backdrop: LayerBackdrop, tab: Tab, accent: Color, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val tabs = Tab.entries
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TabBarHeight)
            .glassControl(backdrop, RoundedCornerShape(50), lensHeight = 16.dp, lensAmount = 26.dp)
            .padding(5.dp),
    ) {
        val itemW: Dp = maxWidth / tabs.size
        val x by animateDpAsState(itemW * tab.ordinal, spring(dampingRatio = 0.72f, stiffness = 420f), label = "tab")
        Box(
            Modifier
                .offset(x = x)
                .width(itemW)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Color(0x2EFFFFFF))
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { t ->
                val on = t == tab
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onTab(t)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(
                        t.label,
                        TextStyle(fontFamily = Fonts.geist, fontSize = 13.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                        if (on) accent else Ink.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChevronIcon() {
    Canvas(Modifier.size(16.dp)) {
        val sw = 2.dp.toPx()
        val p = Path().apply {
            moveTo(size.width * 0.66f, size.height * 0.12f)
            lineTo(size.width * 0.28f, size.height * 0.5f)
            lineTo(size.width * 0.66f, size.height * 0.88f)
        }
        drawPath(p, Ink, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun QuestionIcon() {
    Canvas(Modifier.size(18.dp)) {
        val sw = 2.dp.toPx()
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.28f, h * 0.32f)
            cubicTo(w * 0.28f, h * 0.08f, w * 0.74f, h * 0.06f, w * 0.74f, h * 0.32f)
            cubicTo(w * 0.74f, h * 0.48f, w * 0.5f, h * 0.48f, w * 0.5f, h * 0.66f)
        }
        drawPath(p, Ink, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Ink, sw * 0.7f, Offset(w * 0.5f, h * 0.88f))
    }
}

@Composable
private fun PinIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.width / 2f
        val path = Path().apply {
            moveTo(size.width / 2f, size.height)
            lineTo(0.6f, r * 1.25f)
            lineTo(size.width - 0.6f, r * 1.25f)
            close()
        }
        drawPath(path, color)
        drawCircle(color, r, Offset(r, r))
        drawCircle(Color(0xFF06080B), r * 0.4f, Offset(r, r))
    }
}

/** Circular-arrow refresh icon drawn geometrically so it sits dead centre (from Ljós). */
@Composable
private fun RefreshIcon() {
    Canvas(Modifier.size(18.dp)) {
        val sw = 1.8.dp.toPx()
        val r = size.minDimension / 2f - sw - 1.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        val startDeg = -60f
        val sweep = 290f
        drawArc(
            Ink, startDeg, sweep, false,
            topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(sw, cap = StrokeCap.Round),
        )
        val endDeg = startDeg + sweep
        val a = Math.toRadians(endDeg.toDouble())
        val tip = Offset(c.x + (r * kotlin.math.cos(a)).toFloat(), c.y + (r * kotlin.math.sin(a)).toFloat())
        val head = 4.2.dp.toPx()
        val back = Math.toRadians(endDeg.toDouble() - 90.0)
        fun wing(off: Double) = Offset(
            tip.x + (head * kotlin.math.cos(back + off)).toFloat(),
            tip.y + (head * kotlin.math.sin(back + off)).toFloat(),
        )
        val path = Path().apply {
            moveTo(wing(0.55).x, wing(0.55).y)
            lineTo(tip.x, tip.y)
            lineTo(wing(-0.55).x, wing(-0.55).y)
        }
        drawPath(path, Ink, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
