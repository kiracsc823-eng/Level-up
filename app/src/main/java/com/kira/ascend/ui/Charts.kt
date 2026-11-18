package com.kira.ascend.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LineChart(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 130.dp) {
    val c = LocalSys.current
    if (values.size < 2) {
        EmptyState("Not enough data yet.")
        return
    }
    val maxV = maxOf(values.max(), 1f)
    val minV = minOf(values.min(), maxV - 1f).coerceAtLeast(0f)
    val span = maxOf(maxV - minV, 1f)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            T("max ${maxV.toInt()}", 10, c.sub, mono = true, modifier = Modifier.weight(1f))
            T("min ${minV.toInt()}", 10, c.sub, mono = true, align = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val pad = 6.dp.toPx()
            for (i in 0..2) {
                val y = pad + (h - 2 * pad) * i / 2f
                drawLine(c.border, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
            val path = Path()
            val pts = ArrayList<Offset>()
            values.forEachIndexed { i, v ->
                val x = pad + (w - 2 * pad) * i / (values.size - 1).toFloat()
                val y = pad + (h - 2 * pad) * (1f - (v - minV) / span)
                pts.add(Offset(x, y))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, c.accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            for (p in pts) drawCircle(c.accent, radius = 3.dp.toPx(), center = p)
        }
        Row(Modifier.fillMaxWidth()) {
            val first = labels.firstOrNull() ?: ""
            val mid = if (labels.size > 2) labels[labels.size / 2] else ""
            val last = labels.lastOrNull() ?: ""
            T(first, 10, c.sub, mono = true, modifier = Modifier.weight(1f))
            T(mid, 10, c.sub, mono = true, align = TextAlign.Center, modifier = Modifier.weight(1f))
            T(last, 10, c.sub, mono = true, align = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    valueLabels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 110.dp,
    color: Color = LocalSys.current.accent
) {
    val c = LocalSys.current
    if (values.isEmpty()) {
        EmptyState("Not enough data yet.")
        return
    }
    val maxV = maxOf(values.max(), 1f)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            valueLabels.forEach { T(it, 10, c.sub, mono = true, align = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val slot = size.width / values.size
            val barW = slot * 0.55f
            values.forEachIndexed { i, v ->
                val bh = (size.height - 4.dp.toPx()) * (v / maxV)
                val x = slot * i + (slot - barW) / 2f
                drawRoundRect(
                    color = if (v <= 0f) c.border else color,
                    topLeft = Offset(x, size.height - maxOf(bh, 3.dp.toPx())),
                    size = Size(barW, maxOf(bh, 3.dp.toPx())),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { T(it, 10, c.sub, mono = true, align = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
    }
}
