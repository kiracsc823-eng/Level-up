package com.kira.ascend.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kira.ascend.GameViewModel
import com.kira.ascend.engine.QuestEngine
import com.kira.ascend.engine.XpEngine
import com.kira.ascend.model.AnimLevel
import com.kira.ascend.model.AppData
import com.kira.ascend.model.Rank
import com.kira.ascend.model.Stat
import com.kira.ascend.ui.Chip
import com.kira.ascend.ui.Flow
import com.kira.ascend.ui.LocalAnim
import com.kira.ascend.ui.LocalSys
import com.kira.ascend.ui.Panel
import com.kira.ascend.ui.Particles
import com.kira.ascend.ui.RankBadge
import com.kira.ascend.ui.SectionTitle
import com.kira.ascend.ui.SysButton
import com.kira.ascend.ui.SysField
import com.kira.ascend.ui.T
import com.kira.ascend.ui.XpBar
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val c = LocalSys.current
    val anim = LocalAnim.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val full = "SYSTEM INITIALIZING..."
        if (anim == AnimLevel.OFF) {
            text = full
            delay(500)
        } else {
            for (i in 1..full.length) {
                text = full.take(i)
                delay(45)
            }
            delay(600)
        }
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Particles(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T("ASCEND", 14, c.sub, FontWeight.Bold, mono = true, spacing = 6f)
            Spacer(Modifier.height(12.dp))
            T(text, 18, c.accent, FontWeight.Bold, mono = true, spacing = 1f)
        }
    }
}

private val goalCategories = listOf(
    "Knowledge", "Fitness", "Discipline", "Productivity", "Skills",
    "Creativity", "Finance", "Personal Growth", "Custom"
)

@Composable
fun OnboardingScreen(vm: GameViewModel, d: AppData) {
    val c = LocalSys.current
    val anim = LocalAnim.current
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var goalCat by remember { mutableStateOf("Knowledge") }
    var goalText by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(60) }
    var areas by remember { mutableStateOf(listOf("Knowledge")) }

    Box(Modifier.fillMaxSize()) {
        Particles(Modifier.fillMaxSize())
        when (step) {
            0 -> {
                var line1 by remember { mutableStateOf("") }
                var line2 by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    val a = "SYSTEM INITIALIZING..."
                    val b = "IDENTITY REQUIRED."
                    if (anim == AnimLevel.OFF) {
                        line1 = a
                        line2 = b
                    } else {
                        for (i in 1..a.length) { line1 = a.take(i); delay(35) }
                        delay(300)
                        for (i in 1..b.length) { line2 = b.take(i); delay(45) }
                    }
                }
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    T(line1, 16, c.sub, mono = true, spacing = 1f)
                    Spacer(Modifier.height(10.dp))
                    T(line2, 24, c.accent, FontWeight.Black, mono = true, spacing = 2f)
                    Spacer(Modifier.height(36.dp))
                    if (line2.length >= 18) SysButton("BEGIN", { step = 1 }, Modifier.fillMaxWidth())
                }
            }
            1 -> {
                val unsafe = QuestEngine.unsafeReason(goalText)
                val canGo = name.isNotBlank() && areas.isNotEmpty() && unsafe == null
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    T("IDENTITY", 22, c.accent, FontWeight.Black, mono = true, spacing = 3f)
                    T("Tell the System who you are.", 14, c.sub)
                    SysField(name, { name = it.take(30) }, "Name")
                    SysField(username, { username = it.take(20) }, "Preferred username")
                    SectionTitle("MAIN GOAL")
                    Flow {
                        goalCategories.forEach { g -> Chip(g, goalCat == g) { goalCat = g } }
                    }
                    SysField(
                        goalText, { goalText = it.take(60) },
                        if (goalCat == "Custom") "Describe your goal" else "Goal in your own words (optional)"
                    )
                    if (unsafe != null) T(unsafe, 13, c.bad)
                    SectionTitle("DAILY AVAILABLE TIME")
                    Flow {
                        listOf(30 to "30 min", 60 to "1 hour", 120 to "2 hours", 180 to "3 hours", 240 to "4+ hours")
                            .forEach { (m, l) -> Chip(l, minutes == m) { minutes = m } }
                    }
                    SectionTitle("AREAS TO IMPROVE")
                    Flow {
                        goalCategories.forEach { g ->
                            Chip(g, g in areas) {
                                areas = if (g in areas) areas - g else areas + g
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (!canGo && unsafe == null) T("Enter a name and pick at least one area.", 12, c.sub)
                    SysButton("GENERATE PROFILE", { step = 2 }, Modifier.fillMaxWidth(), enabled = canGo)
                }
            }
            else -> {
                val player = name.trim()
                val need = XpEngine.required(1, d.settings)
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    T("SYSTEM ONLINE.", 22, c.accent, FontWeight.Black, mono = true, spacing = 3f)
                    Panel(accent = true) {
                        T("PLAYER", 11, c.sub, mono = true, spacing = 2f)
                        T(player.uppercase(), 26, c.text, FontWeight.Black, mono = true)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                T("LEVEL", 11, c.sub, mono = true, spacing = 2f)
                                T("1", 44, c.text, FontWeight.Black, mono = true)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                T("RANK", 11, c.sub, mono = true, spacing = 2f)
                                RankBadge(Rank.E, 48.dp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        XpBar(0f)
                        Spacer(Modifier.height(4.dp))
                        T("XP 0 / $need", 12, c.sub, mono = true)
                        Spacer(Modifier.height(12.dp))
                        Stat.values().filter { it.core }.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { s ->
                                    T("${s.code}  10", 15, c.text, FontWeight.Bold, mono = true, modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    T("Your journey begins.", 16, c.sub, align = TextAlign.Center)
                    SysButton("ENTER THE SYSTEM", {
                        vm.finishOnboarding(name, username, goalCat, goalText, areas, minutes)
                    }, Modifier.fillMaxWidth())
                    SysButton("BACK", { step = 1 }, Modifier.fillMaxWidth(), filled = false)
                }
            }
        }
    }
}
