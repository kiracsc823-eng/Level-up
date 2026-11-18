package com.kira.ascend.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kira.ascend.model.AnimLevel
import com.kira.ascend.model.Rank
import com.kira.ascend.model.Stat
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Large circular level indicator with the XP progress drawn as a glowing arc. */
@Composable
fun LevelRing(level: Int, progress: Float, rank: Rank, diameter: Dp = 170.dp) {
    val c = LocalSys.current
    val anim = LocalAnim.current
    val spec: AnimationSpec<Float> = if (anim == AnimLevel.OFF) snap() else tween(900)
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), spec, label = "ring")
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 11.dp.toPx()
            val inset = sw / 2f + 6.dp.toPx()
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - 2f * inset, size.height - 2f * inset)
            val brush = Brush.linearGradient(
                listOf(c.accent, c.accent2),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
            drawArc(
                color = c.text.copy(alpha = 0.09f), startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            if (c.glow && p > 0.01f) {
                drawArc(
                    color = c.accent.copy(alpha = 0.20f), startAngle = 135f, sweepAngle = 270f * p, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = sw + 9.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            if (p > 0.005f) {
                drawArc(
                    brush = brush, startAngle = 135f, sweepAngle = 270f * p, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T("LEVEL", 11, c.sub, FontWeight.Bold, mono = true, spacing = 3f)
            T("$level", 54, c.text, FontWeight.Black, mono = true)
            T("RANK ${rank.name}", 12, rankColor(rank), FontWeight.Bold, mono = true, spacing = 2f)
        }
    }
}

/** Compact attribute bars (code, bar, value). */
@Composable
fun StatBars(stats: List<Pair<Stat, Int>>) {
    val c = LocalSys.current
    if (stats.isEmpty()) return
    val top = max(stats.maxOf { it.second }, 20).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        stats.forEach { (st, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(st.code, 12, c.sub, FontWeight.Bold, mono = true, modifier = Modifier.width(40.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c.text.copy(alpha = 0.10f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((v / top).coerceIn(0.03f, 1f))
                            .background(Brush.horizontalGradient(listOf(c.accent, c.accent2)))
                    )
                }
                T("$v", 14, c.text, FontWeight.Black, mono = true, align = TextAlign.End, modifier = Modifier.width(40.dp))
            }
        }
    }
}

/** Spider chart of the player's attributes. Needs at least 3 entries. */
@Composable
fun RadarChart(labels: List<String>, values: List<Int>, modifier: Modifier = Modifier, height: Dp = 250.dp) {
    val c = LocalSys.current
    val n = min(labels.size, values.size)
    if (n < 3) return
    val maxV = max(values.max(), 20) * 1.15f
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val w = maxWidth.value
        val h = height.value
        val r = min(w, h) / 2f - 30f
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rad = r.dp.toPx()
            fun pt(i: Int, f: Float): Offset {
                val a = Math.toRadians((-90.0 + 360.0 / n * i))
                return Offset(cx + rad * f * cos(a).toFloat(), cy + rad * f * sin(a).toFloat())
            }
            for (ring in 1..3) {
                val f = ring / 3f
                val path = Path()
                for (i in 0 until n) {
                    val o = pt(i, f)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                path.close()
                drawPath(path, c.text.copy(alpha = 0.12f), style = Stroke(width = 1.dp.toPx()))
            }
            for (i in 0 until n) {
                drawLine(c.text.copy(alpha = 0.10f), Offset(cx, cy), pt(i, 1f), strokeWidth = 1.dp.toPx())
            }
            val poly = Path()
            for (i in 0 until n) {
                val o = pt(i, (values[i] / maxV).coerceIn(0.04f, 1f))
                if (i == 0) poly.moveTo(o.x, o.y) else poly.lineTo(o.x, o.y)
            }
            poly.close()
            drawPath(poly, c.accent.copy(alpha = 0.28f))
            drawPath(poly, c.accent, style = Stroke(width = 2.5.dp.toPx()))
            for (i in 0 until n) {
                drawCircle(c.accent2, radius = 3.5.dp.toPx(), center = pt(i, (values[i] / maxV).coerceIn(0.04f, 1f)))
            }
        }
        for (i in 0 until n) {
            val a = Math.toRadians(-90.0 + 360.0 / n * i)
            val lx = w / 2f + (r + 18f) * cos(a).toFloat()
            val ly = h / 2f + (r + 16f) * sin(a).toFloat()
            Box(Modifier.offset(x = (lx - 20f).dp, y = (ly - 18f).dp).width(40.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    T(labels[i], 11, c.sub, FontWeight.Bold, mono = true, align = TextAlign.Center)
                    T("${values[i]}", 11, c.text, FontWeight.Black, mono = true, align = TextAlign.Center)
                }
            }
        }
    }
}
