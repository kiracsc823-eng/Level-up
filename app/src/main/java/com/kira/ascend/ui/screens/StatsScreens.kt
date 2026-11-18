package com.kira.ascend.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kira.ascend.GameViewModel
import com.kira.ascend.engine.Analytics
import com.kira.ascend.engine.RankEngine
import com.kira.ascend.engine.StreakEngine
import com.kira.ascend.engine.XpEngine
import com.kira.ascend.model.AppData
import com.kira.ascend.model.HistoryKind
import com.kira.ascend.model.Rank
import com.kira.ascend.model.Stat
import com.kira.ascend.model.StreakType
import com.kira.ascend.model.shortDayText
import com.kira.ascend.model.todayEpoch
import com.kira.ascend.ui.BarChart
import com.kira.ascend.ui.Chip
import com.kira.ascend.ui.EmptyState
import com.kira.ascend.ui.Flow
import com.kira.ascend.ui.LineChart
import com.kira.ascend.ui.LocalSys
import com.kira.ascend.ui.Nav
import com.kira.ascend.ui.Panel
import com.kira.ascend.ui.RAchievements
import com.kira.ascend.ui.RHistory
import com.kira.ascend.ui.RadarChart
import com.kira.ascend.ui.RankBadge
import com.kira.ascend.ui.SectionTitle
import com.kira.ascend.ui.StatTile
import com.kira.ascend.ui.SysButton
import com.kira.ascend.ui.T
import com.kira.ascend.ui.XpBar
import com.kira.ascend.ui.fmt

/** A stat's "level" is its value / 10 (minimum 1); progress is the remainder towards the next level. */
private fun statLevel(v: Int): Int = maxOf(1, v / 10)

@Composable
fun StatsScreen(vm: GameViewModel, d: AppData, nav: Nav) {
    val c = LocalSys.current
    val today = todayEpoch()
    val p = d.player
    val rank = RankEngine.rankFor(p.level, d.settings)
    val enabled = Stat.values().filter { it in d.settings.enabledStats }
    val power = enabled.sumOf { p.stats[it] ?: 10 } + p.level * 5
    var selected by remember { mutableStateOf(enabled.firstOrNull()) }
    val sel: Stat? = enabled.firstOrNull { it == selected } ?: enabled.firstOrNull()

    Page("PLAYER STATUS") {
        Panel(accent = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("LEVEL ${p.level}", 22, c.text, FontWeight.Black, mono = true)
                    T("RANK ${rank.name}", 14, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
                }
                RankBadge(rank, 52.dp)
            }
            Spacer(Modifier.height(10.dp))
            T("OVERALL POWER", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
            T(fmt(power), 40, c.accent, FontWeight.Black, mono = true)
        }

        if (enabled.isEmpty()) {
            EmptyState("All stats are disabled. Enable some in Settings.")
        }
        if (enabled.size >= 3) {
            SectionTitle("ATTRIBUTE MAP")
            Panel {
                RadarChart(enabled.map { it.code }, enabled.map { p.stats[it] ?: 10 })
            }
        }

        val gains7 = Analytics.gainsByStat(d.history, today - 6, today)
        enabled.forEach { st ->
            val v = p.stats[st] ?: 10
            val lvl = statLevel(v)
            val prog = (v % 10) / 10f
            val related = d.quests.filter { (it.rewards[st] ?: 0) > 0 }.take(3)
            Panel(onClick = { selected = st }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(st.label.uppercase(), 12, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
                        T("Level $lvl", 12, c.sub)
                    }
                    T("$v", 32, c.text, FontWeight.Black, mono = true)
                }
                Spacer(Modifier.height(8.dp))
                XpBar(prog, height = 8.dp)
                Spacer(Modifier.height(8.dp))
                val g = gains7[st] ?: 0
                T(
                    if (g > 0) "Recent: +$g in the last 7 days" else "Recent: no change in the last 7 days",
                    12, if (g > 0) c.good else c.sub
                )
                if (related.isNotEmpty()) {
                    T("Related quests: " + related.joinToString(", ") { it.name }, 12, c.sub, maxLines = 2)
                } else {
                    T("Related quests: none yet", 12, c.sub)
                }
            }
        }

        SectionTitle("STAT HISTORY")
        Panel {
            if (sel == null) {
                EmptyState("No stats to show.")
            } else {
                Flow(Modifier.fillMaxWidth()) {
                    enabled.forEach { st -> Chip(st.code, st == sel) { selected = st } }
                }
                Spacer(Modifier.height(8.dp))
                T("${sel.label} · last 14 days", 12, c.sub)
                LineChart(
                    Analytics.statSeries(d, sel, today, 14),
                    listOf(shortDayText(today - 13), shortDayText(today - 6), shortDayText(today))
                )
            }
        }
    }
}

@Composable
fun ProgressionScreen(vm: GameViewModel, d: AppData, nav: Nav) {
    val c = LocalSys.current
    val today = todayEpoch()
    val p = d.player
    val s = d.settings
    val rank = RankEngine.rankFor(p.level, s)
    val need = XpEngine.required(p.level, s)
    val nextRank = RankEngine.nextRank(p.level, s)
    val weekXp = Analytics.xpTotal(d.history, today - 6, today)
    val monthXp = Analytics.xpTotal(d.history, today - 29, today)
    val questsDone = d.history.count { it.kind == HistoryKind.QUEST }
    val streak = StreakEngine.currentFor(d, StreakType.DAILY, today)
    val bestStreak = StreakEngine.bestFor(d, StreakType.DAILY)
    val trained = Analytics.gainsByStat(d.history, 0L, today).maxByOrNull { it.value }
    val rate = Analytics.completionRate(d.dayLog, today, 30)
    val xp14 = Analytics.xpByDay(d.history, today - 13, today)
    val q7 = Analytics.questsByDay(d.history, today - 6, today)
    val days7 = (today - 6..today).toList()

    Page("RISE") {
        Panel(accent = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("LEVEL ${p.level}", 24, c.text, FontWeight.Black, mono = true)
                    T("${fmt(p.xp)} / ${fmt(need)} XP", 13, c.sub, mono = true)
                }
                RankBadge(rank, 48.dp)
            }
            Spacer(Modifier.height(8.dp))
            XpBar(p.xp.toFloat() / need.toFloat(), height = 12.dp)
            Spacer(Modifier.height(8.dp))
            if (nextRank == null) {
                T("Top rank reached.", 13, c.good)
            } else {
                val away = (nextRank.second - p.level).coerceAtLeast(0)
                T("Next rank ${nextRank.first.name} at level ${nextRank.second} ($away levels away)", 13, c.text)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("XP THIS WEEK", fmt(weekXp), Modifier.weight(1f))
            StatTile("XP THIS MONTH", fmt(monthXp), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("QUESTS DONE", fmt(questsDone), Modifier.weight(1f))
            StatTile("TOTAL XP", fmt(p.totalXp), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("STREAK", "$streak d", Modifier.weight(1f))
            StatTile("BEST STREAK", "$bestStreak d", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("MOST TRAINED", trained?.key?.code ?: "-", Modifier.weight(1f))
            StatTile("COMPLETION", if (rate == null) "-" else "$rate%", Modifier.weight(1f))
        }
        StatTile("MOST PRODUCTIVE DAY", Analytics.mostProductiveWeekday(d.history))

        SectionTitle("XP OVER TIME (14 DAYS)")
        Panel {
            LineChart(
                xp14.map { it.toFloat() },
                listOf(shortDayText(today - 13), shortDayText(today - 6), shortDayText(today))
            )
        }

        SectionTitle("WEEKLY ACTIVITY (QUESTS)")
        Panel {
            BarChart(
                q7.map { it.toFloat() },
                days7.map { Analytics.weekdayName(it).take(2) },
                q7.map { it.toString() }
            )
        }

        SectionTitle("LEVEL PROGRESSION (30 DAYS)")
        Panel {
            LineChart(
                Analytics.levelSeries(d, today, 30),
                listOf(shortDayText(today - 29), shortDayText(today - 14), shortDayText(today))
            )
        }

        SectionTitle("STAT GROWTH (LAST 30 DAYS)")
        Panel {
            val gains = Analytics.gainsByStat(d.history, today - 29, today)
            val en = Stat.values().filter { it in s.enabledStats }
            if (en.isEmpty() || gains.isEmpty()) {
                EmptyState("Complete quests to grow your stats.")
            } else {
                BarChart(
                    en.map { (gains[it] ?: 0).toFloat() },
                    en.map { it.code },
                    en.map { (gains[it] ?: 0).toString() }
                )
            }
        }

        SectionTitle("STREAKS")
        Panel {
            StreakType.values().forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("${t.label} streak", 14, c.text, modifier = Modifier.weight(1f))
                    T(
                        "🔥 ${StreakEngine.currentFor(d, t, today)}  (best ${StreakEngine.bestFor(d, t)})",
                        13, c.warn, FontWeight.Bold, mono = true
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        SectionTitle("RANKS")
        Panel {
            Rank.values().forEachIndexed { i, r ->
                val min = s.rankMinLevels.getOrNull(i)
                val max = s.rankMinLevels.getOrNull(i + 1)?.minus(1)
                val range = when {
                    min == null -> "-"
                    max == null -> "Level $min+"
                    else -> "Level $min-$max"
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RankBadge(r, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    T(range, 13, if (r == rank) c.accent else c.text, if (r == rank) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    if (r == rank) T("YOU", 11, c.accent, FontWeight.Black, mono = true)
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SysButton("ACHIEVEMENTS", { nav.push(RAchievements) }, Modifier.weight(1f), filled = false)
            SysButton("HISTORY", { nav.push(RHistory) }, Modifier.weight(1f), filled = false)
        }
    }
}
