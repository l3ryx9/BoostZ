package app.boostz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.boostz.audio.MAX_DB
import kotlin.math.abs
import kotlin.math.roundToInt

/** Carte en verre sombre, bordure fine (verte quand elle est mise en avant). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Int = 22,
    pad: Int = 16,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CardHi, Card)), shape)
            .border(
                1.dp,
                if (highlighted) Lime.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                shape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(pad.dp),
        content = content
    )
}

@Composable
fun LimeSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Lime,
            checkedBorderColor = Lime,
            uncheckedThumbColor = TextLo,
            uncheckedTrackColor = CardHi,
            uncheckedBorderColor = Edge
        )
    )
}

/** Pouce argenté, comme sur la maquette. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.silverThumb(c: Offset, r: Float) {
    drawCircle(Color.Black.copy(alpha = 0.45f), radius = r * 1.12f, center = Offset(c.x, c.y + r * 0.18f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, Color(0xFFD6DAD6), Color(0xFF8D938D)),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.35f),
            radius = r * 1.7f
        ),
        radius = r,
        center = c
    )
}

private fun yToDb(y: Float, height: Float, pad: Float): Float {
    val t = ((y - pad) / (height - 2f * pad)).coerceIn(0f, 1f)
    var db = MAX_DB - 2f * MAX_DB * t
    db = (db * 2f).roundToInt() / 2f
    if (abs(db) < 0.4f) db = 0f
    return db
}

/** Curseur vertical d'une bande : remplissage vert lumineux depuis le bas, pouce argenté. */
@Composable
fun EqBandSlider(value: Float, dim: Boolean, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(onChange)
    val alpha = if (dim) 0.45f else 1f
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o -> current(yToDb(o.y, size.height.toFloat(), 11.dp.toPx())) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { o -> current(yToDb(o.y, size.height.toFloat(), 11.dp.toPx())) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        current(yToDb(change.position.y, size.height.toFloat(), 11.dp.toPx()))
                    }
                )
            }
    ) {
        val pad = 11.dp.toPx()
        val cx = size.width / 2f
        val trackW = 7.dp.toPx()
        val bottom = size.height - pad
        val ty = pad + (MAX_DB - value) / (2f * MAX_DB) * (size.height - 2f * pad)

        drawRoundRect(
            color = Color(0xFF1C221C),
            topLeft = Offset(cx - trackW / 2f, pad),
            size = Size(trackW, size.height - 2f * pad),
            cornerRadius = CornerRadius(trackW / 2f)
        )
        // halo puis remplissage
        drawRoundRect(
            color = Lime.copy(alpha = 0.18f * alpha),
            topLeft = Offset(cx - trackW * 1.3f, ty),
            size = Size(trackW * 2.6f, bottom - ty),
            cornerRadius = CornerRadius(trackW)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(LimeBright.copy(alpha = alpha), LimeDark.copy(alpha = alpha)),
                startY = ty,
                endY = bottom
            ),
            topLeft = Offset(cx - trackW / 2f, ty),
            size = Size(trackW, bottom - ty),
            cornerRadius = CornerRadius(trackW / 2f)
        )
        silverThumb(Offset(cx, ty), 9.dp.toPx())
    }
}

/** Curseur horizontal vert (Loudness). value : 0..1 */
@Composable
fun LimeSlider(value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(onChange)
    fun xToValue(x: Float, width: Float, pad: Float) = ((x - pad) / (width - 2f * pad)).coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> current(xToValue(o.x, size.width.toFloat(), 14.dp.toPx())) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> current(xToValue(o.x, size.width.toFloat(), 14.dp.toPx())) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        current(xToValue(change.position.x, size.width.toFloat(), 14.dp.toPx()))
                    }
                )
            }
    ) {
        val pad = 14.dp.toPx()
        val cy = size.height / 2f
        val h = 9.dp.toPx()
        val x = pad + value.coerceIn(0f, 1f) * (size.width - 2f * pad)
        drawRoundRect(
            color = Color(0xFF1C221C),
            topLeft = Offset(pad, cy - h / 2f),
            size = Size(size.width - 2f * pad, h),
            cornerRadius = CornerRadius(h / 2f)
        )
        if (x > pad + 1f) {
            drawRoundRect(
                color = Lime.copy(alpha = 0.2f),
                topLeft = Offset(pad, cy - h * 1.2f),
                size = Size(x - pad, h * 2.4f),
                cornerRadius = CornerRadius(h)
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(LimeDark, Lime, LimeBright), startX = pad, endX = x),
                topLeft = Offset(pad, cy - h / 2f),
                size = Size(x - pad, h),
                cornerRadius = CornerRadius(h / 2f)
            )
        }
        silverThumb(Offset(x, cy), 12.dp.toPx())
    }
}

/** Bouton de préréglage avec icône. */
@Composable
fun ChipButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(
                if (selected) Brush.verticalGradient(listOf(Lime.copy(alpha = 0.35f), LimeDark.copy(alpha = 0.18f)))
                else Brush.verticalGradient(listOf(CardHi, Card)),
                shape
            )
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Lime else Color.White.copy(alpha = 0.07f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) LimeBright else TextHi, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Color.White else TextHi
        )
    }
}

/** Puce « AI » dessinée (contour vert + broches). */
@Composable
fun AiBadge(size: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val w = this.size.width
            val sw = w * 0.055f
            val inset = w * 0.2f
            val body = w - 2f * inset
            drawRoundRect(
                color = Lime,
                topLeft = Offset(inset, inset),
                size = Size(body, body),
                cornerRadius = CornerRadius(w * 0.08f),
                style = Stroke(width = sw)
            )
            val pins = 4
            for (k in 0 until pins) {
                val p = inset + body * (k + 0.5f) / pins
                drawLine(Lime, Offset(p, inset * 0.35f), Offset(p, inset), sw, StrokeCap.Round)
                drawLine(Lime, Offset(p, w - inset), Offset(p, w - inset * 0.35f), sw, StrokeCap.Round)
                drawLine(Lime, Offset(inset * 0.35f, p), Offset(inset, p), sw, StrokeCap.Round)
                drawLine(Lime, Offset(w - inset, p), Offset(w - inset * 0.35f, p), sw, StrokeCap.Round)
            }
        }
        Text("AI", fontSize = (size * 0.26f).sp, fontWeight = FontWeight.ExtraBold, color = Lime)
    }
}

/** Onde verticale symétrique (décor des côtés), animée par le son quand c'est possible. */
@Composable
fun WaveBars(levels: List<Float>, active: Boolean, modifier: Modifier = Modifier) {
    val shape = floatArrayOf(0.15f, 0.25f, 0.4f, 0.3f, 0.6f, 0.45f, 0.85f, 0.55f, 1f, 0.6f, 0.8f, 0.4f, 0.55f, 0.3f, 0.2f, 0.12f)
    Canvas(modifier) {
        val n = shape.size
        val step = size.width / n
        val cy = size.height / 2f
        val sum = levels.sum()
        for (i in 0 until n) {
            val live = if (levels.isNotEmpty() && sum > 0.05f) levels[i % levels.size] else 0.55f
            val h = (shape[i] * (0.35f + 0.65f * live)) * size.height * 0.5f
            val x = step * i + step / 2f
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Lime.copy(alpha = if (active) 0.95f else 0.35f), Color.Transparent),
                    startY = cy - h,
                    endY = cy + h
                ),
                start = Offset(x, cy - h),
                end = Offset(x, cy + h),
                strokeWidth = step * 0.42f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Petites barres de niveau (carte « En lecture »). */
@Composable
fun MiniBars(levels: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 9
        val step = size.width / n
        for (i in 0 until n) {
            val lv = levels.getOrElse(i % maxOf(levels.size, 1)) { 0f }
            val h = (0.18f + 0.82f * lv) * size.height
            val x = step * i + step / 2f
            drawLine(
                brush = Brush.verticalGradient(listOf(LimeBright, LimeDark), startY = size.height - h, endY = size.height),
                start = Offset(x, size.height),
                end = Offset(x, size.height - h),
                strokeWidth = step * 0.55f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Logo : petite onde verte. */
@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    val heights = floatArrayOf(0.3f, 0.55f, 0.8f, 1f, 0.8f, 0.55f, 0.3f)
    Canvas(modifier) {
        val n = heights.size
        val step = size.width / n
        val cy = size.height / 2f
        for (i in 0 until n) {
            val h = heights[i] * size.height / 2f
            val x = step * i + step / 2f
            drawLine(
                brush = Brush.verticalGradient(LimeGradient, startY = cy - h, endY = cy + h),
                start = Offset(x, cy - h),
                end = Offset(x, cy + h),
                strokeWidth = step * 0.5f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun SectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Lime, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextHi)
    }
}
