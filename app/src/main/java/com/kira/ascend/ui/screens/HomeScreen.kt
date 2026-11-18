package com.kira.ascend.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kira.ascend.GameViewModel
import com.kira.ascend.engine.QuestEngine
import com.kira.ascend.engine.RankEngine
import com.kira.ascend.engine.StreakEngine
import com.kira.ascend.engine.XpEngine
import com.kira.ascend.model.AppData
import com.kira.ascend.model.Stat
import com.kira.ascend.model.StreakType
import com.kira.ascend.model.Verification
import com.kira.ascend.model.todayEpoch
import com.kira.ascend.ui.Avatar
import com.kira.ascend.ui.CheckBox
import com.kira.ascend.ui.EmptyState
import com.kira.ascend.ui.LocalSys
import com.kira.ascend.ui.Nav
import com.kira.ascend.ui.Panel
import com.kira.ascend.ui.Particles
import com.kira.ascend.ui.RAchievements
import com.kira.ascend.ui.RConsole
import com.kira.ascend.ui.REdit
import com.kira.ascend.ui.RFocus
import com.kira.ascend.ui.RHistory
import com.kira.ascend.ui.RQuest
import com.kira.ascend.ui.RSettings
import com.kira.ascend.ui.RankBadge
import com.kira.ascend.ui.SectionTitle
import com.kira.ascend.ui.SysButton
import com.kira.ascend.ui.T
import com.kira.ascend.ui.Tab
import com.kira.ascend.ui.XpBar
import com.kira.ascend.ui.fmt

@Composable
fun HomeScreen(vm: GameViewModel, d: AppData, nav: Nav) {
    val c = LocalSys.current
    val today = todayEpoch()
    val p = d.player
    val s = d.settings
    val rank = RankEngine.rankFor(p.level, s)
    val need = XpEngine.required(p.level, s)
    val tq = QuestEngine.todayQuests(d, today)
    val sorted = tq.sortedBy { QuestEngine.isDone(it, today) }
    val done = tq.count { QuestEngine.isDone(it, today) }
    val streak = StreakEngine.currentFor(d, StreakType.DAILY, today)
    val best = StreakEngine.bestFor(d, StreakType.DAILY)
    val checkedNow = if (d.dailyDay == today) d.dailyChecked else emptySet()
    val dailyDone = d.dailyItems.count { it.id in checkedNow }
    val pct = if (tq.isEmpty()) 0 else done * 100 / tq.size
    var taps by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Particles(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(
                    "SYSTEM", 14, c.accent, FontWeight.Black, mono = true, spacing = 5f,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            taps++
                            if (taps >= 3) {
                                taps = 0
                                nav.push(RConsole)
                            }
                        }
                        .padding(vertical = 12.dp)
                )
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable { nav.push(RSettings) }
                        .semantics { contentDescription = "Settings" },
                    contentAlignment = Alignment.Center
                ) { T("⚙", 24, c.sub) }
            }

            if (d.devMode) {
                Panel {
                    T("TEST MODE ACTIVE", 13, c.warn, FontWeight.Black, mono = true, spacing = 2f)
                    T("Changes are temporary and are discarded when test mode is turned off.", 12, c.sub)
                }
            }

            // Hero
            Panel(accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.avatarStyle, Color(p.avatarColor), 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(p.name.uppercase(), 17, c.text, FontWeight.Black, mono = true, maxLines = 1)
                        T("@${p.username}", 12, c.sub, maxLines = 1)
                    }
                    RankBadge(rank, 48.dp)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    T("LEVEL", 12, c.sub, FontWeight.Bold, mono = true, spacing = 3f, modifier = Modifier.padding(bottom = 10.dp))
                    Spacer(Modifier.width(10.dp))
                    T("${p.level}", 56, c.text, FontWeight.Black, mono = true)
                }
                XpBar(p.xp.toFloat() / need.toFloat(), height = 14.dp)
                Spacer(Modifier.height(6.dp))
                Row {
                    T("${fmt(p.xp)} / ${fmt(need)} XP", 13, c.text, mono = true, modifier = Modifier.weight(1f))
                    T("Next level: ${fmt(need - p.xp)} XP", 12, c.sub, mono = true)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat.values().filter { it in s.enabledStats }.take(6).forEach { st ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            T(st.code, 11, c.sub, FontWeight.Bold, mono = true)
                            T("${p.stats[st] ?: 10}", 18, c.text, FontWeight.Black, mono = true)
                        }
                    }
                }
            }

            // Active focus session
            val tm = d.timer
            if (tm != null) {
                Panel(accent = true, onClick = { nav.push(RFocus(tm.questId)) }) {
                    T(if (tm.finished) "FOCUS SESSION COMPLETE" else "FOCUS SESSION ACTIVE", 13, c.accent, FontWeight.Black, mono = true, spacing = 1.5f)
                    T(tm.title + "  ·  tap to open", 12, c.sub)
                }
            }

            // Today's progress
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("TODAY'S PROGRESS", 12, c.sub, FontWeight.Bold, mono = true, spacing = 2f, modifier = Modifier.weight(1f))
                    T("$pct%", 16, c.accent, FontWeight.Black, mono = true)
                }
                Spacer(Modifier.height(8.dp))
                XpBar(pct / 100f, height = 10.dp)
                Spacer(Modifier.height(8.dp))
                Row {
                    T("Completed: $done / ${tq.size} quests", 13, c.text, modifier = Modifier.weight(1f))
                    T("🔥 $streak ${if (streak == 1) "DAY" else "DAYS"}", 13, c.warn, FontWeight.Bold, mono = true)
                }
                if (streak == 0 && best > 0 && today !in d.activeDays) {
                    Spacer(Modifier.height(6.dp))
                    T("STREAK RESET. Progress continues. Begin again.", 12, c.sub)
                }
            }

            // Daily System
            SectionTitle("DAILY SYSTEM")
            Panel {
                if (d.dailyItems.isEmpty()) {
                    T("No daily objectives. Add some in Settings.", 13, c.sub)
                }
                d.dailyItems.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckBox(item.id in checkedNow, { vm.toggleDaily(item.id) })
                        T(item.text, 14, if (item.id in checkedNow) c.sub else c.text, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Progress: $dailyDone / ${d.dailyItems.size}", 12, c.sub, mono = true, modifier = Modifier.weight(1f))
                    if (d.dailyCompletedDay == today) T("COMPLETE", 12, c.good, FontWeight.Black, mono = true)
                    else T("Reward: +250 XP  +5 DIS", 12, c.accent, mono = true)
                }
            }

            // Today's quests
            SectionTitle("TODAY'S QUESTS")
            if (sorted.isEmpty()) {
                EmptyState("No quests yet. Create your first quest.")
                SysButton("CREATE QUEST", { nav.push(REdit(null)) }, Modifier.fillMaxWidth())
            } else {
                sorted.take(6).forEach { q ->
                    QuestRow(
                        q, today,
                        onOpen = { nav.push(RQuest(q.id)) },
                        onCheck = {
                            if (q.verification == Verification.NONE) vm.completeQuest(q.id)
                            else nav.push(RQuest(q.id))
                        }
                    )
                }
                if (sorted.size > 6) {
                    SysButton("VIEW ALL ${sorted.size} QUESTS", { nav.tab(Tab.QUESTS) }, Modifier.fillMaxWidth(), filled = false)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SysButton("ACHIEVEMENTS", { nav.push(RAchievements) }, Modifier.weight(1f), filled = false)
                SysButton("HISTORY", { nav.push(RHistory) }, Modifier.weight(1f), filled = false)
            }

            T(
                if (tq.isNotEmpty() && done >= tq.size) "All of today's objectives are complete. Well done."
                else "Today's system objectives are waiting.",
                13, c.sub, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }
}
