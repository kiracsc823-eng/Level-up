package com.kira.ascend.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kira.ascend.GameViewModel
import com.kira.ascend.engine.AchievementEngine
import com.kira.ascend.model.AppData
import com.kira.ascend.model.HistoryKind
import com.kira.ascend.model.dayText
import com.kira.ascend.model.nowMs
import com.kira.ascend.model.timeText
import com.kira.ascend.model.todayEpoch
import com.kira.ascend.ui.Chip
import com.kira.ascend.ui.EmptyState
import com.kira.ascend.ui.Flow
import com.kira.ascend.ui.LocalSys
import com.kira.ascend.ui.Nav
import com.kira.ascend.ui.Panel
import com.kira.ascend.ui.RankBadge
import com.kira.ascend.ui.SectionTitle
import com.kira.ascend.ui.SysButton
import com.kira.ascend.ui.T
import com.kira.ascend.ui.XpBar
import com.kira.ascend.ui.fmt
import kotlinx.coroutines.delay

@Composable
fun AchievementsScreen(d: AppData, nav: Nav) {
    val c = LocalSys.current
    val defs = AchievementEngine.defs
    val unlockedCount = defs.count { it.id in d.unlocked }
    Page("ACHIEVEMENTS", onBack = nav.back) {
        Panel(accent = true) {
            T("UNLOCKED", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
            T("$unlockedCount / ${defs.size}", 32, c.text, FontWeight.Black, mono = true)
            Spacer(Modifier.height(8.dp))
            XpBar(if (defs.isEmpty()) 0f else unlockedCount.toFloat() / defs.size)
        }
        // Unlocked first (newest first), then locked.
        val ordered = defs.sortedWith(
            compareBy<com.kira.ascend.engine.AchDef> { it.id !in d.unlocked }
                .thenByDescending { d.unlocked[it.id] ?: 0L }
        )
        ordered.forEach { a ->
            val at = d.unlocked[a.id]
            val got = at != null
            val secret = a.hidden && !got
            Panel(accent = got) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(if (secret) "❔" else a.icon, 30, if (got) c.text else c.sub)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(if (secret) "HIDDEN ACHIEVEMENT" else a.name, 14, if (got) c.text else c.sub, FontWeight.Black, mono = true)
                        T(if (secret) "Keep playing to discover it." else a.desc, 12, c.sub)
                        if (got && at != null) T("Unlocked ${dayText(java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay())}", 11, c.good)
                        else if (!secret) T("Locked", 11, c.sub)
                    }
                    T("+${a.xp} XP", 12, if (got) c.accent else c.sub, FontWeight.Bold, mono = true)
                }
            }
        }
    }
}

private enum class HistFilter(val label: String) { TODAY("Today"), WEEK("Week"), MONTH("Month"), ALL("All time") }

@Composable
fun HistoryScreen(d: AppData, nav: Nav) {
    val c = LocalSys.current
    val today = todayEpoch()
    var filter by remember { mutableStateOf(HistFilter.WEEK) }
    val from = when (filter) {
        HistFilter.TODAY -> today
        HistFilter.WEEK -> today - 6
        HistFilter.MONTH -> today - 29
        HistFilter.ALL -> Long.MIN_VALUE
    }
    val rows = d.history.filter { it.day >= from }.sortedByDescending { it.time }
    val groups = rows.groupBy { it.day }.toList().sortedByDescending { it.first }

    Page("HISTORY", onBack = nav.back) {
        Flow(Modifier.fillMaxWidth()) {
            HistFilter.values().forEach { f -> Chip(f.label, f == filter) { filter = f } }
        }
        val totalXp = rows.filter { it.xp > 0 }.sumOf { it.xp }
        T("${rows.size} entries · +${fmt(totalXp)} XP", 12, c.sub, mono = true)
        if (groups.isEmpty()) {
            EmptyState("Nothing here yet. Complete a quest to start your record.")
        }
        groups.forEach { (day, entries) ->
            SectionTitle(dayText(day).uppercase())
            Panel {
                entries.forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            T(e.title, 14, c.text, FontWeight.SemiBold, maxLines = 2)
                            val sl = statLine(e.stats)
                            if (sl.isNotEmpty()) T(sl, 12, c.accent, mono = true)
                            val meta = buildList {
                                add(timeText(e.time))
                                if (e.minutes > 0) add("${e.minutes} min")
                                if (e.category.isNotEmpty()) add(e.category)
                                if (e.kind == HistoryKind.DAILY_SYSTEM) add("Daily System")
                            }.joinToString(" · ")
                            T(meta, 11, c.sub)
                        }
                        T("${if (e.xp >= 0) "+" else ""}${e.xp} XP", 13, c.text, FontWeight.Bold, mono = true)
                    }
                }
            }
        }
    }
}

private val presets = listOf(25, 45, 60)

private fun clock(ms: Long): String {
    val total = (ms + 999) / 1000
    val m = total / 60
    val s = total % 60
    return String.format("%02d:%02d", m, s)
}

@Composable
fun FocusScreen(vm: GameViewModel, d: AppData, nav: Nav, questId: String?) {
    val c = LocalSys.current
    val timer = d.timer
    val quest = d.quests.firstOrNull { it.id == (timer?.questId ?: questId) }
    var now by remember { mutableStateOf(nowMs()) }
    LaunchedEffect(timer?.running, timer?.finished) {
        while (true) {
            now = nowMs()
            delay(250)
        }
    }

    if (timer == null) {
        // ----- setup
        val minRequired = if (quest != null && quest.verification == com.kira.ascend.model.Verification.TIMER) quest.timerMinutes else 1
        var mode by remember { mutableStateOf(if (quest != null && quest.timerMinutes in presets) quest.timerMinutes else if (quest != null && quest.timerMinutes > 0) -1 else 25) }
        var custom by remember { mutableStateOf(if (quest != null && quest.timerMinutes > 0) quest.timerMinutes.toString() else "30") }
        val minutes = if (mode == -1) custom.toIntOrNull() ?: 0 else mode
        val valid = minutes in minRequired..240
        Page("FOCUS TIMER", onBack = nav.back) {
            Panel(accent = true) {
                T("CURRENT QUEST", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
                T(quest?.name ?: "Free focus session", 18, c.text, FontWeight.Black)
                if (quest != null) {
                    T("Reward: +${quest.xp} XP  ${statLine(quest.rewards)}", 13, c.accent, mono = true)
                } else {
                    T("Free focus sessions give no XP, but count towards focus achievements.", 12, c.sub)
                }
            }
            SectionTitle("SESSION LENGTH")
            Flow(Modifier.fillMaxWidth()) {
                presets.forEach { m -> Chip("$m min", mode == m) { mode = m } }
                Chip("Custom", mode == -1) { mode = -1 }
            }
            if (mode == -1) {
                com.kira.ascend.ui.SysField(custom, { v -> custom = v.filter { it.isDigit() }.take(3) }, "Minutes (1-240)", number = true)
            }
            if (!valid) {
                T(
                    if (minRequired > 1) "This quest needs at least $minRequired minutes." else "Choose between 1 and 240 minutes.",
                    12, c.warn
                )
            }
            T("The timer keeps running if you leave the app. Take breaks and rest when you need to.", 12, c.sub)
            SysButton("START FOCUS SESSION", { vm.startTimer(quest?.id, minutes) }, Modifier.fillMaxWidth(), enabled = valid)
        }
        return
    }

    // ----- active / finished
    val remaining = timer.remaining(now)
    val progress = if (timer.durationMs <= 0L) 1f else 1f - remaining.toFloat() / timer.durationMs.toFloat()
    var confirmExit by remember { mutableStateOf(false) }

    Page("FOCUS MODE", onBack = nav.back) {
        Panel(accent = true) {
            T(if (timer.finished) "FOCUS SESSION COMPLETE" else "CURRENT QUEST", 12, c.accent, FontWeight.Black, mono = true, spacing = 2f)
            T(timer.title, 18, c.text, FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            T("TIME", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
            T(clock(remaining), 60, c.text, FontWeight.Black, mono = true)
            Spacer(Modifier.height(8.dp))
            XpBar(progress, height = 10.dp)
            Spacer(Modifier.height(8.dp))
            if (quest != null) T("XP REWARD: +${quest.xp}", 14, c.accent, FontWeight.Bold, mono = true)
            T(
                when {
                    timer.finished -> "Well done. Claim your reward."
                    timer.running -> "Stay with it. You can pause any time."
                    else -> "Paused."
                },
                12, c.sub
            )
        }

        if (timer.finished) {
            SysButton(
                if (quest != null) "CLAIM REWARD" else "FINISH",
                {
                    if (quest != null) vm.claimTimerReward() else vm.dismissTimer()
                    nav.back()
                },
                Modifier.fillMaxWidth()
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SysButton(
                    if (timer.running) "PAUSE" else "RESUME",
                    { if (timer.running) vm.pauseTimer() else vm.resumeTimer() },
                    Modifier.weight(1f)
                )
                SysButton("COMPLETE", {}, Modifier.weight(1f), enabled = false, filled = false)
            }
            T("COMPLETE unlocks when the timer reaches zero. Rewards cannot be skipped.", 11, c.sub)
            if (!confirmExit) {
                SysButton("EXIT", { confirmExit = true }, Modifier.fillMaxWidth(), filled = false, danger = true)
            } else {
                Panel {
                    T("End this session early?", 14, c.text, FontWeight.Bold)
                    T("No reward will be given for an unfinished session.", 12, c.sub)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SysButton("KEEP GOING", { confirmExit = false }, Modifier.weight(1f), filled = false)
                        SysButton("END", { vm.endTimerEarly(); nav.back() }, Modifier.weight(1f), danger = true)
                    }
                }
            }
        }
        T("Emergency calls and phone functions are never blocked.", 11, c.sub)
    }
}
