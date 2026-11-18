package com.kira.ascend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kira.ascend.GameViewModel
import com.kira.ascend.engine.AchievementEngine
import com.kira.ascend.engine.XpEngine
import com.kira.ascend.model.AppData
import com.kira.ascend.model.Stat
import com.kira.ascend.ui.Chip
import com.kira.ascend.ui.Flow
import com.kira.ascend.ui.LocalSys
import com.kira.ascend.ui.Nav
import com.kira.ascend.ui.Panel
import com.kira.ascend.ui.SectionTitle
import com.kira.ascend.ui.SysButton
import com.kira.ascend.ui.SysField
import com.kira.ascend.ui.T

private val commandList = listOf("/help", "/status", "/quests", "/stats", "/history", "/level", "/achievements", "/profile")

@Composable
fun ConsoleScreen(vm: GameViewModel, d: AppData, nav: Nav) {
    val c = LocalSys.current
    val log = remember { mutableStateListOf<String>("SYSTEM CONSOLE ONLINE.", "Type /help for commands.") }
    var input by remember { mutableStateOf("") }

    fun exec(cmd: String) {
        val t = cmd.trim()
        if (t.isEmpty()) return
        log.add("> $t")
        log.addAll(vm.runCommand(t))
        while (log.size > 200) log.removeAt(0)
        input = ""
    }

    Page("SYSTEM CONSOLE", onBack = nav.back) {
        Flow(Modifier.fillMaxWidth()) {
            commandList.forEach { cmd -> Chip(cmd, false) { exec(cmd) } }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface)
                .padding(12.dp)
        ) {
            log.takeLast(60).forEach { line ->
                T(line, 13, if (line.startsWith(">")) c.accent else c.text, mono = true)
            }
        }
        SysField(input, { input = it.take(60) }, "Command, e.g. /status")
        SysButton("RUN", { exec(input) }, Modifier.fillMaxWidth())
        T("The console only displays information. It cannot change your progression.", 11, c.sub)
    }
}

@Composable
fun DevScreen(vm: GameViewModel, d: AppData, nav: Nav) {
    val c = LocalSys.current
    Page("DEVELOPER TOOLS", onBack = nav.back) {
        if (!d.devUnlocked) {
            T("Developer tools are locked.", 14, c.sub)
            return@Page
        }
        Panel(accent = true) {
            T(if (d.devMode) "TEST MODE: ON" else "TEST MODE: OFF", 16, if (d.devMode) c.warn else c.text, FontWeight.Black, mono = true)
            Spacer(Modifier.height(4.dp))
            T(
                "Test mode works on a temporary copy. Turning it off restores your real progression exactly as it was.",
                12, c.sub
            )
            Spacer(Modifier.height(8.dp))
            SysButton(
                if (d.devMode) "TURN OFF AND DISCARD CHANGES" else "ENTER TEST MODE",
                { vm.setDevMode(!d.devMode) },
                Modifier.fillMaxWidth(),
                danger = d.devMode
            )
        }

        if (d.devMode) {
            val p = d.player
            SectionTitle("XP")
            T("Level ${p.level}  ·  ${p.xp} / ${XpEngine.required(p.level, d.settings)} XP", 13, c.text, mono = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysButton("+100", { vm.devAddXp(100) }, Modifier.weight(1f), filled = false)
                SysButton("+1000", { vm.devAddXp(1000) }, Modifier.weight(1f), filled = false)
                SysButton("-100", { vm.devAddXp(-100) }, Modifier.weight(1f), filled = false)
            }
            SectionTitle("LEVEL")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysButton("-1", { vm.devSetLevel(p.level - 1) }, Modifier.weight(1f), filled = false)
                SysButton("+1", { vm.devSetLevel(p.level + 1) }, Modifier.weight(1f), filled = false)
                SysButton("+10", { vm.devSetLevel(p.level + 10) }, Modifier.weight(1f), filled = false)
            }
            SectionTitle("STATS")
            Stat.values().filter { it in d.settings.enabledStats }.forEach { st ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("${st.code}  ${p.stats[st] ?: 10}", 14, c.text, mono = true, modifier = Modifier.weight(1f))
                    SysButton("-5", { vm.devModifyStat(st, -5) }, Modifier.width(72.dp), filled = false)
                    Spacer(Modifier.width(8.dp))
                    SysButton("+5", { vm.devModifyStat(st, 5) }, Modifier.width(72.dp), filled = false)
                }
            }
            SectionTitle("QUESTS")
            SysButton("CREATE TEST QUEST", { vm.devCreateTestQuest() }, Modifier.fillMaxWidth(), filled = false)
            SectionTitle("UNLOCK ACHIEVEMENT")
            val locked = AchievementEngine.defs.filter { it.id !in d.unlocked }
            if (locked.isEmpty()) T("All achievements unlocked.", 12, c.sub)
            Flow(Modifier.fillMaxWidth()) {
                locked.forEach { a -> Chip(a.name, false) { vm.devUnlockAchievement(a.id) } }
            }
            SectionTitle("RESET")
            SysButton("RESET TEST DATA", { vm.devResetTestData() }, Modifier.fillMaxWidth(), filled = false, danger = true)
        }

        Spacer(Modifier.height(12.dp))
        SysButton("LOCK DEVELOPER TOOLS", { vm.lockDev(); nav.back() }, Modifier.fillMaxWidth(), filled = false)
    }
}
