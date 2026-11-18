package com.kira.ascend.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import com.kira.ascend.model.AnimLevel
import com.kira.ascend.model.Rank
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun T(
    text: String,
    size: Int = 14,
    color: Color = LocalSys.current.text,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    spacing: Float = 0f,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        letterSpacing = spacing.sp,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalSys.current
    val shape = RoundedCornerShape(14.dp)
    val borderBrush: Brush = if (accent) Brush.linearGradient(listOf(c.accent, c.accent2)) else SolidColor(c.border)
    var m = modifier.fillMaxWidth()
    if (c.glow && accent) {
        m = m.drawBehind {
            drawRoundRect(
                color = c.accent.copy(alpha = 0.12f),
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(width = 8.dp.toPx())
            )
        }
    }
    m = m.clip(shape).background(c.surface, shape).border(1.dp, borderBrush, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(modifier = m.padding(14.dp), content = content)
}

@Composable
fun SysButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    danger: Boolean = false
) {
    val c = LocalSys.current
    val col = if (danger) c.bad else c.accent
    val shape = RoundedCornerShape(12.dp)
    val bg = if (!enabled) c.sub.copy(alpha = 0.18f) else if (filled) col else Color.Transparent
    val fg = if (!enabled) c.sub else if (filled) readableOn(col) else col
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (enabled) col else c.border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        T(text, 14, fg, FontWeight.Bold, mono = true, spacing = 1.5f, align = TextAlign.Center)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalSys.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        T(text, 13, if (selected) c.accent else c.text, if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Flow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
fun XpBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 12.dp, color: Color = LocalSys.current.accent) {
    val c = LocalSys.current
    val anim = LocalAnim.current
    val spec: AnimationSpec<Float> = if (anim == AnimLevel.OFF) snap() else tween(700)
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = spec, label = "xpbar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(c.text.copy(alpha = 0.10f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(p)
                .background(Brush.horizontalGradient(listOf(color, c.accent2)))
        )
    }
}

@Composable
fun Particles(modifier: Modifier = Modifier) {
    val c = LocalSys.current
    val anim = LocalAnim.current
    if (anim == AnimLevel.OFF) return
    val t = rememberInfiniteTransition(label = "particles")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (anim == AnimLevel.REDUCED) 60000 else 24000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "phase"
    )
    val seeds = remember { List(34) { Triple(Random.nextFloat(), Random.nextFloat(), 0.3f + Random.nextFloat() * 0.7f) } }
    Canvas(modifier) {
        for ((x, y, s) in seeds) {
            val yy = (((y - phase * s) % 1f) + 1f) % 1f
            drawCircle(
                color = c.accent.copy(alpha = 0.28f * s),
                radius = 1.2.dp.toPx() + 1.6.dp.toPx() * s,
                center = Offset(x * size.width, yy * size.height)
            )
        }
    }
}

@Composable
fun Avatar(style: Int, color: Color, dp: Dp = 72.dp) {
    Canvas(Modifier.size(dp).semantics { contentDescription = "Player avatar" }) {
        val w = size.width
        val cx = w / 2f
        val cy = w / 2f
        val r = w / 2f * 0.92f
        val hex = Path()
        for (i in 0 until 6) {
            val a = Math.toRadians((60 * i - 90).toDouble())
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
        }
        hex.close()
        drawPath(hex, color.copy(alpha = 0.12f))
        drawPath(hex, color, style = Stroke(width = 2.dp.toPx()))
        val sw = Stroke(width = 2.5.dp.toPx())
        when (((style % 6) + 6) % 6) {
            0 -> { // hooded figure
                drawCircle(color, radius = r * 0.22f, center = Offset(cx, cy - r * 0.18f))
                drawArc(
                    color = color, startAngle = 180f, sweepAngle = 180f, useCenter = true,
                    topLeft = Offset(cx - r * 0.5f, cy + r * 0.12f), size = Size(r, r * 0.8f)
                )
            }
            1 -> { // diamond
                val p = Path()
                p.moveTo(cx, cy - r * 0.5f); p.lineTo(cx + r * 0.38f, cy)
                p.lineTo(cx, cy + r * 0.5f); p.lineTo(cx - r * 0.38f, cy); p.close()
                drawPath(p, color.copy(alpha = 0.35f)); drawPath(p, color, style = sw)
            }
            2 -> { // rings
                drawCircle(color, radius = r * 0.5f, center = Offset(cx, cy), style = sw)
                drawCircle(color, radius = r * 0.28f, center = Offset(cx, cy), style = sw)
                drawCircle(color, radius = r * 0.08f, center = Offset(cx, cy))
            }
            3 -> { // ascend arrow
                val p = Path()
                p.moveTo(cx, cy - r * 0.5f); p.lineTo(cx + r * 0.42f, cy + r * 0.3f)
                p.lineTo(cx, cy + r * 0.1f); p.lineTo(cx - r * 0.42f, cy + r * 0.3f); p.close()
                drawPath(p, color.copy(alpha = 0.35f)); drawPath(p, color, style = sw)
            }
            4 -> { // star burst
                for (k in 0 until 4) {
                    val a = Math.toRadians((45 * k).toDouble())
                    val dx = cos(a).toFloat() * r * 0.5f
                    val dy = sin(a).toFloat() * r * 0.5f
                    drawLine(color, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), strokeWidth = 2.5.dp.toPx())
                }
                drawCircle(color, radius = r * 0.1f, center = Offset(cx, cy))
            }
            else -> { // shield
                val p = Path()
                p.moveTo(cx - r * 0.4f, cy - r * 0.4f); p.lineTo(cx + r * 0.4f, cy - r * 0.4f)
                p.lineTo(cx + r * 0.4f, cy + r * 0.05f); p.lineTo(cx, cy + r * 0.5f)
                p.lineTo(cx - r * 0.4f, cy + r * 0.05f); p.close()
                drawPath(p, color.copy(alpha = 0.35f)); drawPath(p, color, style = sw)
            }
        }
    }
}

@Composable
fun RankBadge(rank: Rank, size: Dp = 40.dp) {
    val col = rankColor(rank)
    val shape = RoundedCornerShape(10.dp)
    val label = rank.name
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(col.copy(alpha = 0.14f))
            .border(2.dp, col, shape)
            .semantics { contentDescription = "Rank $label" },
        contentAlignment = Alignment.Center
    ) {
        T(label, if (label.length > 2) 13 else 17, col, FontWeight.Black, mono = true)
    }
}

@Composable
fun CheckBox(checked: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val c = LocalSys.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (checked) "Completed" else "Not completed" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(shape)
                .background(if (checked) c.accent else Color.Transparent)
                .border(2.dp, if (checked) c.accent else c.sub, shape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) T("✓", 16, readableOn(c.accent), FontWeight.Black)
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = LocalSys.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center
            ) { T("←", 22, c.text, FontWeight.Bold) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        T(title, 18, c.text, FontWeight.Bold, mono = true, spacing = 2f, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val c = LocalSys.current
    T(text, 12, c.accent, FontWeight.Bold, mono = true, spacing = 2.5f, modifier = modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
fun EmptyState(text: String) {
    val c = LocalSys.current
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        T(text, 14, c.sub, align = TextAlign.Center)
    }
}

@Composable
fun SysField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    number: Boolean = false,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text)
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalSys.current
    Panel(modifier) {
        T(label, 11, c.sub, FontWeight.Bold, mono = true, spacing = 1f)
        Spacer(Modifier.height(4.dp))
        T(value, 20, c.text, FontWeight.Black, mono = true)
    }
}

fun fmt(n: Int): String = String.format("%,d", n)

fun fmt(n: Long): String = String.format("%,d", n)
