package app.skjalfti.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val glassOk get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
private val CardShape = RoundedCornerShape(26.dp)
private val PillShape = RoundedCornerShape(50)

/** A chunky Win95-style bevel: light on the top and left, dark on the bottom and right. */
fun Modifier.bevel(fill: Color, hi: Color, lo: Color, raised: Boolean = true, width: Dp = 3.dp): Modifier = drawBehind {
    val s = width.toPx()
    drawRect(fill)
    val a = if (raised) hi else lo
    val b = if (raised) lo else hi
    drawRect(a, Offset.Zero, Size(size.width, s))
    drawRect(a, Offset.Zero, Size(s, size.height))
    drawRect(b, Offset(0f, size.height - s), Size(size.width, s))
    drawRect(b, Offset(size.width - s, 0f), Size(s, size.height))
}

/** A flat pixel frame in one colour. */
fun Modifier.pixelFrame(fill: Color, line: Color, width: Dp = 3.dp): Modifier = drawBehind {
    val s = width.toPx()
    drawRect(fill)
    drawRect(line, Offset.Zero, Size(size.width, s))
    drawRect(line, Offset.Zero, Size(s, size.height))
    drawRect(line, Offset(0f, size.height - s), Size(size.width, s))
    drawRect(line, Offset(size.width - s, 0f), Size(s, size.height))
}

fun Color.lighter(f: Float = 0.55f) = lerp(this, Color.White, f)
fun Color.darker(f: Float = 0.6f) = lerp(this, Color.Black, f)

/**
 * The basic surface. Pixel: a raised (or sunken, [raised] = false) bevelled panel, or a flat frame
 * in [frame]'s colour. Glass: a rounded pane of liquid glass.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    raised: Boolean = true,
    frame: Color? = null,
    fill: Color? = null,
    padding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalLook.current
    val c = look.c
    val backdrop = LocalBackdrop.current
    val surface = if (look.pixel) {
        val f = fill ?: if (raised) c.panel else c.bevelLo
        if (frame != null) modifier.pixelFrame(f, frame) else modifier.bevel(f, c.bevelHi, c.bevelLo, raised)
    } else if (backdrop != null && glassOk) {
        modifier.glassCard(backdrop, CardShape)
    } else {
        modifier.clip(CardShape).background(Color(0x99101418)).border(1.dp, frame ?: Color(0x26FFFFFF), CardShape)
    }
    Column(surface.padding(padding), content = content)
}

/** Text in the current skin. Pixel text is upper-cased. */
@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color = LocalLook.current.c.ink,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val look = LocalLook.current
    Text(
        look.say(text),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A button. Pixel: a bevelled key that sinks when pressed, filled with [accent] when [selected].
 * Glass: a liquid glass pill.
 */
@Composable
fun KButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color? = null,
    height: Dp = 48.dp,
) {
    val look = LocalLook.current
    val c = look.c
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val backdrop = LocalBackdrop.current
    val click = Modifier.clickable(source, indication = null, role = Role.Button) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onClick()
    }
    val surface = if (look.pixel) {
        val a = accent ?: c.phosphor
        if (selected) Modifier.bevel(a, a.lighter(), a.darker(), raised = !pressed)
        else Modifier.bevel(c.off, c.bevelHi, c.bevelLo, raised = !pressed)
    } else if (backdrop != null && glassOk) {
        Modifier.glassControl(backdrop, PillShape, selected || pressed)
    } else {
        Modifier.clip(PillShape).background(if (selected) Color(0x40FFFFFF) else Color(0x1AFFFFFF))
    }
    val textColor = when {
        look.pixel && selected -> c.onAccent
        look.pixel -> c.ink
        selected -> c.ink
        else -> c.muted
    }
    Box(
        modifier.defaultMinSize(minWidth = 48.dp).height(height).then(surface).then(click).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, look.t.label.copy(fontSize = look.t.small.fontSize), textColor)
    }
}

/** A row of buttons where one is chosen. */
@Composable
fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            KButton(label(o), { onSelect(o) }, Modifier.weight(1f), selected = o == selected)
        }
    }
}

/** A small label over a big number. */
@Composable
fun Readout(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Panel(modifier, padding = 10.dp) {
        Txt(label, look.t.label, look.c.muted)
        Txt(value, look.t.number, color)
    }
}

/** The bar at the top of each screen: a title on the left, anything on the right. */
@Composable
fun TitleBar(title: String, modifier: Modifier = Modifier, right: @Composable RowScope.() -> Unit = {}) {
    val look = LocalLook.current
    if (look.pixel) {
        Panel(modifier.fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 10.dp).size(14.dp).background(look.c.phosphor))
                Txt(title, look.t.title, look.c.ink, Modifier.weight(1f))
                right()
            }
        }
    } else {
        Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(title, look.t.mark, look.c.ink, Modifier.weight(1f))
            right()
        }
    }
}
