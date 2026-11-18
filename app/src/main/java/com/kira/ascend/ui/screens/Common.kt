package com.kira.ascend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kira.ascend.engine.QuestEngine
import com.kira.ascend.model.Quest
import com.kira.ascend.model.Rank
import com.kira.ascend.model.Repeat
import com.kira.ascend.model.Stat
import com.kira.ascend.model.dayText
import com.kira.ascend.ui.CheckBox
import com.kira.ascend.ui.LocalSys
import com.kira.ascend.ui.ScreenHeader
import com.kira.ascend.ui.T
import com.kira.ascend.ui.rankColor

/** Standard scrolling page with a header. Tab screens pass onBack = null. */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack, trailing)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

fun statLine(stats: Map<Stat, Int>): String =
    stats.entries.filter { it.value != 0 }.joinToString("  ") { "${if (it.value > 0) "+" else ""}${it.value} ${it.key.code}" }

fun questMeta(q: Quest, today: Long): String {
    val parts = mutableListOf(q.category, q.type.label)
    if (q.repeat != Repeat.NONE && q.repeat.label != q.type.label) parts.add(q.repeat.label)
    val dl = q.deadlineDay
    if (dl != null) parts.add("due ${dayText(dl)}")
    if (q.timerMinutes > 0) parts.add("${q.timerMinutes} min")
    if (QuestEngine.isExpired(q, today)) parts.add("INCOMPLETE")
    return parts.joinToString(" · ")
}

@Composable
fun QuestRow(q: Quest, today: Long, onOpen: () -> Unit, onCheck: () -> Unit) {
    val c = LocalSys.current
    val done = QuestEngine.isDone(q, today)
    val expired = QuestEngine.isExpired(q, today)
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckBox(done, onCheck, enabled = !done && !expired)
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            T(q.name, 15, if (done) c.sub else c.text, FontWeight.SemiBold, maxLines = 2)
            T(questMeta(q, today), 11, if (expired) c.warn else c.sub, maxLines = 2)
        }
        Column(horizontalAlignment = Alignment.End) {
            T(q.difficulty.name, 14, rankColor(Rank.valueOf(q.difficulty.name)), FontWeight.Black, mono = true)
            T("+${q.xp} XP", 12, c.sub, mono = true)
        }
    }
}
