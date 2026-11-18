package com.kira.ascend.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kira.ascend.GameViewModel
import com.kira.ascend.engine.QuestEngine
import com.kira.ascend.model.AppData
import com.kira.ascend.model.Difficulty
import com.kira.ascend.model.Quest
import com.kira.ascend.model.QuestType
import com.kira.ascend.model.Repeat
import com.kira.ascend.model.Stat
import com.kira.ascend.model.Verification
import com.kira.ascend.model.dayText
import com.kira.ascend.model.newId
import com.kira.ascend.model.todayEpoch
import com.kira.ascend.ui.Chip
import com.kira.ascend.ui.EmptyState
import com.kira.ascend.ui.Flow
import com.kira.ascend.ui.LocalSys
import com.kira.ascend.ui.Nav
import com.kira.ascend.ui.Panel
import com.kira.ascend.ui.REdit
import com.kira.ascend.ui.RFocus
import com.kira.ascend.ui.RQuest
import com.kira.ascend.ui.SectionTitle
import com.kira.ascend.ui.SysButton
import com.kira.ascend.ui.SysField
import com.kira.ascend.ui.T

private enum class QFilter(val label: String) {
    ACTIVE("Active"), DONE("Done"), DAILY("Daily"), WEEKLY("Weekly"), MAIN("Main"),
    SIDE("Side"), CHALLENGE("Challenge"), HABIT("Habit"), CUSTOM("Custom")
}

@Composable
fun QuestListScreen(vm: GameViewModel, d: AppData, nav: Nav) {
    val c = LocalSys.current
    val today = todayEpoch()
    var filter by remember { mutableStateOf(QFilter.ACTIVE) }
    val shown = d.quests.filter { q ->
        val done = QuestEngine.isDone(q, today)
        val expired = QuestEngine.isExpired(q, today)
        when (filter) {
            QFilter.ACTIVE -> !done && !expired
            QFilter.DONE -> done
            QFilter.DAILY -> q.type == QuestType.DAILY
            QFilter.WEEKLY -> q.type == QuestType.WEEKLY
            QFilter.MAIN -> q.type == QuestType.MAIN
            QFilter.SIDE -> q.type == QuestType.SIDE
            QFilter.CHALLENGE -> q.type == QuestType.CHALLENGE
            QFilter.HABIT -> q.type == QuestType.HABIT
            QFilter.CUSTOM -> q.type == QuestType.CUSTOM
        }
    }
    Page("QUESTS") {
        SysButton("+ CREATE QUEST", { nav.push(REdit(null)) }, Modifier.fillMaxWidth())
        Flow { QFilter.values().forEach { f -> Chip(f.label, filter == f) { filter = f } } }
        if (shown.isEmpty()) {
            EmptyState(
                if (d.quests.isEmpty()) "No quests yet. Create your first quest."
                else "Nothing here. Try another filter."
            )
        }
        shown.forEach { q ->
            QuestRow(
                q, today,
                onOpen = { nav.push(RQuest(q.id)) },
                onCheck = {
                    if (q.verification == Verification.NONE) vm.completeQuest(q.id)
                    else nav.push(RQuest(q.id))
                }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun QuestDetailScreen(vm: GameViewModel, d: AppData, nav: Nav, id: String) {
    val c = LocalSys.current
    val today = todayEpoch()
    val q = d.quests.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    if (q == null) {
        Page("QUEST", onBack = nav.back) { EmptyState("This quest no longer exists.") }
        return
    }
    val done = QuestEngine.isDone(q, today)
    val expired = QuestEngine.isExpired(q, today)
    val verified = QuestEngine.hasVerifiedSession(d, q, today)
    Page("QUEST DETAILS", onBack = nav.back) {
        Panel(accent = true) {
            T("RANK ${q.difficulty.name}  ·  ${q.type.label.uppercase()}", 12, c.accent, FontWeight.Bold, mono = true, spacing = 1.5f)
            Spacer(Modifier.height(6.dp))
            T(q.name, 22, c.text, FontWeight.Black)
            if (q.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                T(q.description, 14, c.sub)
            }
        }
        Panel {
            T("REQUIREMENT", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
            T(
                when {
                    q.timerMinutes > 0 && q.verification == Verification.TIMER -> "Complete a ${q.timerMinutes}-minute focus session."
                    q.verification == Verification.CONFIRM -> "Complete the objective and confirm honestly."
                    else -> "Complete the objective."
                }, 14, c.text
            )
            Spacer(Modifier.height(10.dp))
            T("REWARD", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
            T("+${q.xp} XP", 20, c.accent, FontWeight.Black, mono = true)
            if (q.rewards.isNotEmpty()) T(statLine(q.rewards), 14, c.text, mono = true)
            Spacer(Modifier.height(10.dp))
            T("IF ABANDONED", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
            T("No XP is awarded. Nothing is lost. Quest incomplete.", 13, c.sub)
        }
        Panel {
            T("DETAILS", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
            T("Category: ${q.category}", 13, c.text)
            T("Repeat: ${q.repeat.label}", 13, c.text)
            T("Verification: ${q.verification.label}", 13, c.text)
            q.deadlineDay?.let { T("Deadline: ${dayText(it)}", 13, if (expired) c.warn else c.text) }
            T("Times completed: ${q.completions}", 13, c.text)
            if (q.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                T("NOTES", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
                T(q.notes, 13, c.text)
            }
        }

        when {
            done -> T("COMPLETED${if (q.repeat != Repeat.NONE) " for now" else ""}.", 14, c.good, FontWeight.Black, mono = true)
            expired -> T("QUEST INCOMPLETE. The deadline passed. Edit the quest to set a new one.", 13, c.warn)
            q.verification == Verification.TIMER -> {
                if (verified) {
                    T("Focus session verified.", 13, c.good)
                    SysButton("CLAIM REWARD", { vm.completeQuest(q.id) }, Modifier.fillMaxWidth())
                } else {
                    SysButton("START FOCUS TIMER (${q.timerMinutes} MIN)", { nav.push(RFocus(q.id)) }, Modifier.fillMaxWidth())
                    T("This quest is verified by a finished focus session.", 12, c.sub)
                }
            }
            q.verification == Verification.CONFIRM -> {
                var confirmed by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("I really completed this", confirmed) { confirmed = !confirmed }
                }
                SysButton("COMPLETE QUEST", { vm.completeQuest(q.id, true) }, Modifier.fillMaxWidth(), enabled = confirmed)
                if (q.timerMinutes > 0) {
                    SysButton("USE FOCUS TIMER", { nav.push(RFocus(q.id)) }, Modifier.fillMaxWidth(), filled = false)
                }
            }
            else -> {
                SysButton("COMPLETE QUEST", { vm.completeQuest(q.id) }, Modifier.fillMaxWidth())
                if (q.timerMinutes > 0) {
                    SysButton("USE FOCUS TIMER", { nav.push(RFocus(q.id)) }, Modifier.fillMaxWidth(), filled = false)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SysButton("EDIT", { nav.push(REdit(q.id)) }, Modifier.weight(1f), filled = false)
            SysButton("DELETE", { confirmDelete = true }, Modifier.weight(1f), filled = false, danger = true)
        }
        if (confirmDelete) {
            Panel {
                T("Delete this quest? Its history entries stay.", 14, c.text)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SysButton("CANCEL", { confirmDelete = false }, Modifier.weight(1f), filled = false)
                    SysButton("DELETE", {
                        vm.deleteQuest(q.id)
                        nav.back()
                    }, Modifier.weight(1f), danger = true)
                }
            }
        }
    }
}

@Composable
fun QuestEditScreen(vm: GameViewModel, d: AppData, nav: Nav, id: String?) {
    val c = LocalSys.current
    val existing = if (id == null) null else d.quests.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: d.settings.categories.firstOrNull() ?: "Productivity") }
    var diff by remember { mutableStateOf(existing?.difficulty ?: Difficulty.D) }
    var type by remember { mutableStateOf(existing?.type ?: QuestType.CUSTOM) }
    var xpText by remember { mutableStateOf((existing?.xp ?: Difficulty.D.defaultXp).toString()) }
    var rewards by remember {
        mutableStateOf(existing?.rewards ?: QuestEngine.rewardsFor(category, diff, d.settings.enabledStats))
    }
    var repeat by remember { mutableStateOf(existing?.repeat ?: Repeat.NONE) }
    var timerText by remember { mutableStateOf((existing?.timerMinutes ?: 0).let { if (it == 0) "" else it.toString() }) }
    var ver by remember { mutableStateOf(existing?.verification ?: Verification.CONFIRM) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var deadline by remember { mutableStateOf(existing?.deadlineDay) }
    var error by remember { mutableStateOf<String?>(null) }

    val enabled = d.settings.enabledStats.toList().sortedBy { it.ordinal }

    Page(if (existing == null) "CREATE QUEST" else "EDIT QUEST", onBack = nav.back) {
        SysField(name, { name = it }, "Quest name")
        SysField(desc, { desc = it }, "Description", singleLine = false, minLines = 2)

        SectionTitle("TYPE")
        Flow {
            QuestType.values().forEach { t ->
                Chip(t.label, type == t) {
                    type = t
                    if (t == QuestType.DAILY) repeat = Repeat.DAILY
                    if (t == QuestType.WEEKLY) repeat = Repeat.WEEKLY
                    if (t == QuestType.HABIT && repeat == Repeat.NONE) repeat = Repeat.DAILY
                }
            }
        }

        SectionTitle("CATEGORY")
        Flow {
            val cats = (d.settings.categories + listOfNotNull(existing?.category)).distinct()
            cats.forEach { cat ->
                Chip(cat, category == cat) {
                    category = cat
                    rewards = QuestEngine.rewardsFor(cat, diff, d.settings.enabledStats)
                }
            }
        }

        SectionTitle("DIFFICULTY")
        Flow {
            Difficulty.values().forEach { df ->
                Chip(df.name, diff == df) {
                    diff = df
                    xpText = df.defaultXp.toString()
                    rewards = QuestEngine.rewardsFor(category, df, d.settings.enabledStats)
                }
            }
        }
        SysField(xpText, { xpText = it.filter { ch -> ch.isDigit() }.take(4) }, "XP reward (5-1000)", number = true)

        SectionTitle("STAT REWARDS")
        enabled.forEach { st ->
            val v = rewards[st] ?: 0
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                T("${st.code}  ${st.label}", 13, c.text, modifier = Modifier.weight(1f))
                SysButton("-", {
                    rewards = rewards.toMutableMap().also { m ->
                        if (v <= 1) m.remove(st) else m[st] = v - 1
                    }
                }, Modifier.width48(), filled = false, enabled = v > 0)
                T("$v", 16, c.accent, FontWeight.Black, mono = true, modifier = Modifier.width48())
                SysButton("+", {
                    rewards = rewards.toMutableMap().also { m -> m[st] = minOf(10, v + 1) }
                }, Modifier.width48(), filled = false, enabled = v < 10)
            }
        }

        SectionTitle("REPEAT")
        Flow { Repeat.values().forEach { r -> Chip(r.label, repeat == r) { repeat = r } } }

        SectionTitle("DEADLINE")
        Flow {
            val t = todayEpoch()
            Chip("None", deadline == null) { deadline = null }
            Chip("Today", deadline == t) { deadline = t }
            Chip("Tomorrow", deadline == t + 1) { deadline = t + 1 }
            Chip("+1 week", deadline == t + 7) { deadline = t + 7 }
            Chip("+1 month", deadline == t + 30) { deadline = t + 30 }
        }
        deadline?.let { T("Due: ${dayText(it)}", 12, c.sub) }

        SectionTitle("TIMER (OPTIONAL)")
        Flow {
            listOf(0 to "None", 25 to "25 min", 45 to "45 min", 60 to "60 min").forEach { (m, l) ->
                Chip(l, (timerText.toIntOrNull() ?: 0) == m) { timerText = if (m == 0) "" else m.toString() }
            }
        }
        SysField(timerText, { timerText = it.filter { ch -> ch.isDigit() }.take(3) }, "Custom minutes (max 240)", number = true)

        SectionTitle("VERIFICATION")
        Flow { Verification.values().forEach { v -> Chip(v.label, ver == v) { ver = v } } }
        if (ver == Verification.TIMER) T("Reward unlocks after a finished focus session.", 12, c.sub)

        SysField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)

        val err = error
        if (err != null) T(err, 13, c.bad, FontWeight.Bold)

        SysButton("SAVE QUEST", {
            val q = Quest(
                id = existing?.id ?: newId(),
                name = name, description = desc, category = category, difficulty = diff, type = type,
                xp = xpText.toIntOrNull() ?: 0, rewards = rewards.filter { it.value > 0 },
                deadlineDay = deadline, repeat = repeat,
                timerMinutes = timerText.toIntOrNull() ?: 0, verification = ver, notes = notes,
                createdAt = existing?.createdAt ?: 0L,
                completions = existing?.completions ?: 0,
                lastCompletedDay = existing?.lastCompletedDay ?: -1L
            )
            val r = vm.saveQuest(q)
            if (r == null) nav.back() else error = r
        }, Modifier.fillMaxWidth())
        SysButton("CANCEL", nav.back, Modifier.fillMaxWidth(), filled = false)
        Spacer(Modifier.height(8.dp))
    }
}

private fun Modifier.width48(): Modifier = this.then(Modifier.width(52.dp))
