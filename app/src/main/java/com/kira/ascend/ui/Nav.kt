package com.kira.ascend.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kira.ascend.GameViewModel
import com.kira.ascend.model.AppData
import com.kira.ascend.ui.screens.AchievementsScreen
import com.kira.ascend.ui.screens.ConsoleScreen
import com.kira.ascend.ui.screens.DevScreen
import com.kira.ascend.ui.screens.FocusScreen
import com.kira.ascend.ui.screens.HistoryScreen
import com.kira.ascend.ui.screens.HomeScreen
import com.kira.ascend.ui.screens.OnboardingScreen
import com.kira.ascend.ui.screens.ProfileScreen
import com.kira.ascend.ui.screens.ProgressionScreen
import com.kira.ascend.ui.screens.QuestDetailScreen
import com.kira.ascend.ui.screens.QuestEditScreen
import com.kira.ascend.ui.screens.QuestListScreen
import com.kira.ascend.ui.screens.ResetScreen
import com.kira.ascend.ui.screens.SettingsScreen
import com.kira.ascend.ui.screens.SplashScreen
import com.kira.ascend.ui.screens.StatsScreen
import kotlinx.coroutines.delay

sealed interface Route

enum class Tab(val label: String, val glyph: String) : Route {
    HOME("SYSTEM", "◈"),
    QUESTS("QUESTS", "⚔"),
    STATS("STATS", "⬢"),
    PROGRESS("RISE", "▲"),
    PROFILE("YOU", "●")
}

object RAchievements : Route
object RHistory : Route
object RSettings : Route
object RReset : Route
object RConsole : Route
object RDev : Route
data class RQuest(val id: String) : Route
data class REdit(val id: String?) : Route
data class RFocus(val questId: String?) : Route

class Nav(
    val push: (Route) -> Unit,
    val back: () -> Unit,
    val tab: (Tab) -> Unit
)

@Composable
fun AppRoot(vm: GameViewModel) {
    val data by vm.data.collectAsState()
    val notice by vm.notice.collectAsState()
    var splashDone by rememberSaveable { mutableStateOf(false) }

    SystemTheme(data.settings) {
        val c = LocalSys.current
        Box(Modifier.fillMaxSize().background(c.bg)) {
            when {
                !splashDone -> SplashScreen { splashDone = true }
                !data.onboarded -> OnboardingScreen(vm, data)
                else -> MainShell(vm, data)
            }
            if (splashDone && data.onboarded) EventHost(vm, data.settings)
            LaunchedEffect(splashDone, data.onboarded) {
                if (splashDone && data.onboarded) {
                    delay(500)
                    vm.autoBriefing()
                }
            }

            val n = notice
            if (n != null) {
                LaunchedEffect(n) {
                    delay(2800)
                    vm.clearNotice()
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 20.dp, end = 20.dp, bottom = 84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface)
                        .border(1.dp, c.accent, RoundedCornerShape(12.dp))
                        .clickable { vm.clearNotice() }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    T(n, 13, c.text, FontWeight.Medium, align = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun MainShell(vm: GameViewModel, d: AppData) {
    val stack = remember { mutableStateListOf<Route>(Tab.HOME) }
    val current: Route = stack.last()
    val nav = remember {
        Nav(
            push = { stack.add(it) },
            back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else stack[0] = Tab.HOME },
            tab = { t ->
                stack.clear()
                stack.add(t)
            }
        )
    }
    BackHandler(enabled = stack.size > 1 || current != Tab.HOME) { nav.back() }

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshDay()
            delay(60_000)
        }
    }
    // Keep the timer ticking even when the Focus screen is not open.
    LaunchedEffect(d.timer?.running) {
        while (d.timer?.running == true) {
            vm.tickTimer()
            delay(1000)
        }
    }

    val c = LocalSys.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (current) {
                Tab.HOME -> HomeScreen(vm, d, nav)
                Tab.QUESTS -> QuestListScreen(vm, d, nav)
                Tab.STATS -> StatsScreen(vm, d, nav)
                Tab.PROGRESS -> ProgressionScreen(vm, d, nav)
                Tab.PROFILE -> ProfileScreen(vm, d, nav)
                is RQuest -> QuestDetailScreen(vm, d, nav, current.id)
                is REdit -> QuestEditScreen(vm, d, nav, current.id)
                is RFocus -> FocusScreen(vm, d, nav, current.questId)
                RAchievements -> AchievementsScreen(d, nav)
                RHistory -> HistoryScreen(d, nav)
                RSettings -> SettingsScreen(vm, d, nav)
                RReset -> ResetScreen(vm, d, nav)
                RConsole -> ConsoleScreen(vm, d, nav)
                RDev -> DevScreen(vm, d, nav)
            }
        }
        if (current is Tab) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .border(1.dp, c.border)
                    .height(64.dp)
            ) {
                for (t in Tab.values()) {
                    val sel = t == current
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { nav.tab(t) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.45f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) c.accent else c.accent.copy(alpha = 0f))
                        )
                        T(t.glyph, 20, if (sel) c.accent else c.sub, FontWeight.Bold)
                        T(t.label, 10, if (sel) c.accent else c.sub, if (sel) FontWeight.Bold else FontWeight.Normal, mono = true, spacing = 1f)
                    }
                }
            }
        }
    }
}
