package app.boostz.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP = 270f

private fun angleOf(o: Offset, size: IntSize): Float =
    Math.toDegrees(atan2((o.y - size.height / 2f).toDouble(), (o.x - size.width / 2f).toDouble())).toFloat()

private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val rad = Math.toRadians(deg.toDouble())
    return Offset(c.x + cos(rad).toFloat() * r, c.y + sin(rad).toFloat() * r)
}

/** Flèche courbe autour du bouton (indique qu'il se tourne). */
private fun DrawScope.curvedArrow(c: Offset, r: Float, startDeg: Float, sweepDeg: Float, color: Color, width: Float) {
    drawArc(
        brush = Brush.sweepGradient(listOf(color.copy(alpha = 0f), color, color), center = c),
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2f, r * 2f),
        style = Stroke(width = width, cap = StrokeCap.Round)
    )
    val end = startDeg + sweepDeg
    val p = polar(c, r, end)
    val rad = Math.toRadians(end.toDouble())
    val dir = if (sweepDeg >= 0f) 1f else -1f
    val tx = -sin(rad).toFloat() * dir
    val ty = cos(rad).toFloat() * dir
    val nx = cos(rad).toFloat()
    val ny = sin(rad).toFloat()
    val h = width * 3.2f
    val head = Path().apply {
        moveTo(p.x + tx * h, p.y + ty * h)
        lineTo(p.x + nx * h * 0.65f, p.y + ny * h * 0.65f)
        lineTo(p.x - nx * h * 0.65f, p.y - ny * h * 0.65f)
        close()
    }
    drawPath(head, color)
}

/**
 * Gros bouton rotatif BoostZ : anneau métallique gradué, arc vert lumineux qui suit la valeur,
 * disque central avec icône, titre et valeur. Se tourne du doigt en suivant le cercle.
 */
@Composable
fun BigKnob(
    value: Float,                // 0..1
    icon: ImageVector,
    title: String,
    valueText: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current

    // Pulsation douce du halo quand le moteur tourne.
    val pulse = if (active) {
        val t = rememberInfiniteTransition(label = "pulse")
        val p by t.animateFloat(
            initialValue = 0.75f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulseValue"
        )
        p
    } else 0f

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                var acc = 0f
                var last = 0f
                var lastStep = 0
                detectDragGestures(
                    onDragStart = { o ->
                        acc = latestValue
                        last = angleOf(o, size)
                        lastStep = (acc * 30f).toInt()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val a = angleOf(change.position, size)
                        var delta = a - last
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        last = a
                        acc = (acc + delta / SWEEP).coerceIn(0f, 1f)
                        val step = (acc * 30f).toInt()
                        if (step != lastStep) {
                            lastStep = step
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        latestChange(acc)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val w = size.minDimension
            val c = center

            // Halo vert
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Lime.copy(alpha = (0.10f + 0.30f * value) * (if (active) pulse else 0.5f)), Color.Transparent),
                    center = c,
                    radius = w * 0.5f
                ),
                radius = w * 0.5f,
                center = c
            )

            // Flèches courbes (droite vers le haut, gauche vers le bas)
            val rArrow = w * 0.47f
            curvedArrow(c, rArrow, 20f, -75f, Color.White.copy(alpha = 0.85f), w * 0.012f)
            curvedArrow(c, rArrow, 200f, -75f, Color.White.copy(alpha = 0.85f), w * 0.012f)

            // Anneau métallique
            val rMetal = w * 0.40f
            val metalW = w * 0.075f
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF4A514A), Color(0xFF151915), Color(0xFF5A625A),
                        Color(0xFF181C18), Color(0xFF4A514A)
                    ),
                    center = c
                ),
                radius = rMetal,
                center = c,
                style = Stroke(width = metalW)
            )
            // Graduations
            for (k in 0 until 60) {
                val deg = k * 6f
                val major = k % 5 == 0
                val r1 = rMetal + metalW * 0.30f
                val r0 = rMetal + metalW * (if (major) -0.05f else 0.12f)
                drawLine(
                    Color.White.copy(alpha = if (major) 0.75f else 0.4f),
                    polar(c, r0, deg),
                    polar(c, r1, deg),
                    strokeWidth = w * (if (major) 0.006f else 0.004f),
                    cap = StrokeCap.Round
                )
            }

            // Piste et arc de valeur
            val rArc = rMetal - metalW * 0.5f - w * 0.045f
            val arcW = w * 0.045f
            val tl = Offset(c.x - rArc, c.y - rArc)
            val sz = Size(rArc * 2f, rArc * 2f)
            drawArc(
                color = Color(0xFF1A201A),
                startAngle = START_ANGLE, sweepAngle = SWEEP, useCenter = false,
                topLeft = tl, size = sz, style = Stroke(width = arcW, cap = StrokeCap.Round)
            )
            if (value > 0.003f) {
                val sweep = SWEEP * value
                drawArc(
                    color = Lime.copy(alpha = 0.25f),
                    startAngle = START_ANGLE, sweepAngle = sweep, useCenter = false,
                    topLeft = tl, size = sz, style = Stroke(width = arcW * 2.4f, cap = StrokeCap.Round)
                )
                rotate(START_ANGLE, c) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(LimeDark, Lime, LimeBright, LimeBright),
                            center = c
                        ),
                        startAngle = 0f, sweepAngle = sweep, useCenter = false,
                        topLeft = tl, size = sz, style = Stroke(width = arcW, cap = StrokeCap.Round)
                    )
                }
            }

            // Disque central
            val rDisc = rArc - arcW * 0.5f - w * 0.035f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF232A23), Color(0xFF0B0E0B), Color(0xFF050705)),
                    center = Offset(c.x - rDisc * 0.3f, c.y - rDisc * 0.35f),
                    radius = rDisc * 1.5f
                ),
                radius = rDisc,
                center = c
            )
            drawCircle(Color.White.copy(alpha = 0.08f), radius = rDisc, center = c, style = Stroke(width = w * 0.006f))

            // Point lumineux au bout de l'arc
            val dot = polar(c, rArc, START_ANGLE + SWEEP * value)
            drawCircle(Lime.copy(alpha = 0.35f), radius = arcW * 1.3f, center = dot)
            drawCircle(LimeBright, radius = arcW * 0.75f, center = dot)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            Text(
                title,
                fontSize = 19.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(valueText, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Lime)
        }
    }
}
