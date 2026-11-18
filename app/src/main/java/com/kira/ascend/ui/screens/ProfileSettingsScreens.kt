package com.kira.ascend.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kira.ascend.GameViewModel
import com.kira.ascend.engine.RankEngine
import com.kira.ascend.engine.StreakEngine
import com.kira.ascend.engine.XpEngine
import com.kira.ascend.model.AnimLevel
import com.kira.ascend.model.AppData
import com.kira.ascend.model.FontScale
import com.kira.ascend.model.HistoryKind
import com.kira.ascend.model.ModeSetting
import com.kira.ascend.model.Stat
import com.kira.ascend.model.StreakType
import com.kira.ascend.model.ThemeName
import com.kira.ascend.model.todayEpoch
import com.kira.ascend.ui.Avatar
import com.kira.ascend.ui.Chip
import com.kira.ascend.ui.Flow
import com.kira.ascend.ui.LocalSys
import com.kira.ascend.ui.Nav
import com.kira.ascend.ui.Panel
import com.kira.ascend.ui.RAchievements
import com.kira.ascend.ui.RDev
import com.kira.ascend.ui.RHistory
import com.kira.ascend.ui.RReset
import com.kira.ascend.ui.RSettings
import com.kira.ascend.ui.RankBadge
import com.kira.ascend.ui.SectionTitle
import com.kira.ascend.ui.StatTile
import com.kira.ascend.ui.SysButton
import com.kira.ascend.ui.SysField
import com.kira.ascend.ui.T
import com.kira.ascend.ui.Tab
import com.kira.ascend.ui.accentChoices
import com.kira.ascend.ui.fmt
import java.io.File
import kotlin.math.roundToInt

private val avatarColors: List<Long> = accentChoices

/** Writes the save to a cache file and opens the share sheet (works for any save size). */
private fun shareSave(ctx: Context, json: String) {
    try {
        val dir = File(ctx.cacheDir, "exports")
        dir.mkdirs()
        val f = File(dir, "ascend-save.json")
        f.writeText(json)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Ascend save")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Export save"))
    } catch (e: Exception) {
        // fall through; nothing else to do
    }
}

@Composable
fun ProfileScreen(vm: GameViewModel, d: AppData, nav: Nav) {
    val c = LocalSys.current
    val today = todayEpoch()
    val p = d.player
    val rank = RankEngine.rankFor(p.level, d.settings)
    val streak = StreakEngine.currentFor(d, StreakType.DAILY, today)
    val questsDone = d.history.count { it.kind == HistoryKind.QUEST }

    Page(
        "PROFILE",
        trailing = {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable { nav.push(RSettings) }
                    .semantics { contentDescription = "Settings" },
                contentAlignment = Alignment.Center
            ) { T("⚙", 24, c.sub) }
        }
    ) {
        Panel(accent = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.avatarStyle, Color(p.avatarColor), 80.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    T(p.name.uppercase(), 18, c.text, FontWeight.Black, mono = true, maxLines = 1)
                    T("@${p.username}", 13, c.sub, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    T("LEVEL ${p.level}", 14, c.accent, FontWeight.Bold, mono = true)
                }
                RankBadge(rank, 52.dp)
            }
            if (p.goal.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                T("GOAL", 11, c.sub, FontWeight.Bold, mono = true, spacing = 2f)
                T(p.goal, 14, c.text)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("TOTAL XP", fmt(p.totalXp), Modifier.weight(1f))
            StatTile("QUESTS", fmt(questsDone), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("STREAK", "🔥 $streak", Modifier.weight(1f))
            StatTile("ACHIEVEMENTS", "${d.unlocked.size}", Modifier.weight(1f))
        }

        SectionTitle("AVATAR")
        Panel {
            T("Style", 12, c.sub)
            Spacer(Modifier.height(6.dp))
            Flow(Modifier.fillMaxWidth()) {
                for (i in 0 until 6) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .border(2.dp, if (p.avatarStyle == i) c.accent else c.border, CircleShape)
                            .clickable { vm.setAvatar(i, p.avatarColor) }
                            .padding(4.dp)
                    ) { Avatar(i, Color(p.avatarColor), 48.dp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            T("Colour", 12, c.sub)
            Spacer(Modifier.height(6.dp))
            Flow(Modifier.fillMaxWidth()) {
                avatarColors.forEach { col ->
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(col))
                            .border(if (p.avatarColor == col) 3.dp else 1.dp, if (p.avatarColor == col) c.text else c.border, CircleShape)
                            .clickable { vm.setAvatar(p.avatarStyle, col) }
                            .semantics { contentDescription = "Avatar colour" }
                    )
                }
            }
        }

        SectionTitle("MORE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SysButton("ACHIEVEMENTS", { nav.push(RAchievements) }, Modifier.weight(1f), filled = false)
            SysButton("HISTORY", { nav.push(RHistory) }, Modifier.weight(1f), filled = false)
        }
        SysButton("SETTINGS", { nav.push(RSettings) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SwitchRow(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalSys.current
    Row(Modifier.fillMaxWidth().padding0(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(label, 14, c.text)
            if (sub.isNotEmpty()) T(sub, 11, c.sub)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun Modifier.padding0(): Modifier = this.heightIn(min = 52.dp)

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = LocalSys.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, 14, c.text, modifier = Modifier.weight(1f))
        SysButton("−", onMinus, Modifier.width(56.dp), filled = false)
        T(value, 15, c.text, FontWeight.Bold, mono = true, modifier = Modifier.width(72.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        SysButton("+", onPlus, Modifier.width(56.dp), filled = false)
    }
}

@Composable
fun SettingsScreen(vm: GameViewModel, d: AppData, nav: Nav) {
    val c = LocalSys.current
    val s = d.settings
    val ctx = LocalContext.current
    var versionTaps by remember { mutableStateOf(0) }
    var newCategory by remember { mutableStateOf("") }
    var newDaily by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(notifications = granted) }
    }

    Page("SETTINGS", onBack = nav.back) {
        SectionTitle("APPEARANCE")
        Panel {
            T("Theme", 12, c.sub)
            Flow(Modifier.fillMaxWidth()) {
                ThemeName.values().forEach { t -> Chip(t.label, s.theme == t) { vm.updateSettings { it.copy(theme = t) } } }
            }
            Spacer(Modifier.height(10.dp))
            T("Mode", 12, c.sub)
            Flow(Modifier.fillMaxWidth()) {
                ModeSetting.values().forEach { m -> Chip(m.label, s.mode == m) { vm.updateSettings { it.copy(mode = m) } } }
            }
            Spacer(Modifier.height(10.dp))
            T("Accent colour", 12, c.sub)
            Spacer(Modifier.height(6.dp))
            Flow(Modifier.fillMaxWidth()) {
                Chip("Theme default", s.accent == 0L) { vm.updateSettings { it.copy(accent = 0L) } }
                accentChoices.forEach { col ->
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(col))
                            .border(if (s.accent == col) 3.dp else 1.dp, if (s.accent == col) c.text else c.border, CircleShape)
                            .clickable { vm.updateSettings { it.copy(accent = col) } }
                            .semantics { contentDescription = "Accent colour" }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            T("Animation intensity", 12, c.sub)
            Flow(Modifier.fillMaxWidth()) {
                AnimLevel.values().forEach { a -> Chip(a.label, s.anim == a) { vm.updateSettings { it.copy(anim = a) } } }
            }
            Spacer(Modifier.height(10.dp))
            T("Text size", 12, c.sub)
            Flow(Modifier.fillMaxWidth()) {
                FontScale.values().forEach { f -> Chip(f.label, s.fontScale == f) { vm.updateSettings { it.copy(fontScale = f) } } }
            }
            SwitchRow("High contrast", "Stronger borders and text colours", s.highContrast) { v ->
                vm.updateSettings { it.copy(highContrast = v) }
            }
        }

        SectionTitle("FEEDBACK")
        Panel {
            SwitchRow("Sound effects", "Short tones on quest complete and level up", s.sound) { v ->
                vm.updateSettings { it.copy(sound = v) }
            }
            SwitchRow("Haptic feedback", "", s.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }
        }

        SectionTitle("NOTIFICATIONS")
        Panel {
            SwitchRow("Daily reminder", "One gentle reminder per day. Nothing else.", s.notifications) { v ->
                if (v && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.updateSettings { it.copy(notifications = v) }
                }
            }
            if (s.notifications) {
                Stepper(
                    "Reminder time", String.format("%02d:00", s.reminderHour),
                    { vm.updateSettings { it.copy(reminderHour = (it.reminderHour + 23) % 24) } },
                    { vm.updateSettings { it.copy(reminderHour = (it.reminderHour + 1) % 24) } }
                )
            }
        }

        SectionTitle("STATS")
        Panel {
            T("Choose which stats are tracked. At least one must stay on.", 12, c.sub)
            Spacer(Modifier.height(6.dp))
            Flow(Modifier.fillMaxWidth()) {
                Stat.values().forEach { st ->
                    val on = st in s.enabledStats
                    Chip("${st.code} ${st.label}", on) {
                        vm.updateSettings {
                            val next = if (on) it.enabledStats - st else it.enabledStats + st
                            if (next.isEmpty()) it else it.copy(enabledStats = next)
                        }
                    }
                }
            }
        }

        SectionTitle("XP BALANCING")
        Panel {
            Stepper(
                "Base XP", "${s.xpBase.roundToInt()}",
                { vm.updateSettings { it.copy(xpBase = (it.xpBase - 10).coerceAtLeast(50.0)) } },
                { vm.updateSettings { it.copy(xpBase = (it.xpBase + 10).coerceAtMost(300.0)) } }
            )
            Stepper(
                "Growth exponent", String.format("%.2f", s.xpExponent),
                { vm.updateSettings { it.copy(xpExponent = (((it.xpExponent - 0.05) * 100).roundToInt() / 100.0).coerceAtLeast(1.0)) } },
                { vm.updateSettings { it.copy(xpExponent = (((it.xpExponent + 0.05) * 100).roundToInt() / 100.0).coerceAtMost(2.0)) } }
            )
            Spacer(Modifier.height(6.dp))
            T(
                "XP for level N = base × N^exponent.  L1: ${XpEngine.required(1, s)}  L2: ${XpEngine.required(2, s)}  L3: ${XpEngine.required(3, s)}  L10: ${XpEngine.required(10, s)}",
                12, c.sub
            )
            Spacer(Modifier.height(8.dp))
            SysButton("RESTORE DEFAULTS", {
                vm.updateSettings { it.copy(xpBase = 100.0, xpExponent = 1.25) }
            }, Modifier.fillMaxWidth(), filled = false)
        }

        SectionTitle("STREAKS")
        Panel {
            SwitchRow("Streak protection", "Forgives a single missed day", s.streakGrace) { v ->
                vm.updateSettings { it.copy(streakGrace = v) }
            }
        }

        SectionTitle("QUEST CATEGORIES")
        Panel {
            Flow(Modifier.fillMaxWidth()) {
                s.categories.forEach { cat ->
                    Chip("$cat  ✕", false) {
                        if (s.categories.size > 1) vm.updateSettings { it.copy(categories = it.categories - cat) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SysField(newCategory, { newCategory = it.take(24) }, "New category")
            Spacer(Modifier.height(6.dp))
            SysButton("ADD CATEGORY", {
                val t = newCategory.trim()
                if (t.isNotEmpty() && s.categories.none { it.equals(t, true) }) {
                    vm.updateSettings { it.copy(categories = it.categories + t) }
                    newCategory = ""
                }
            }, Modifier.fillMaxWidth(), filled = false)
        }

        SectionTitle("DAILY SYSTEM OBJECTIVES")
        Panel {
            d.dailyItems.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(item.text, 14, c.text, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable { vm.removeDailyItem(item.id) }
                            .semantics { contentDescription = "Remove objective" },
                        contentAlignment = Alignment.Center
                    ) { T("✕", 16, c.sub) }
                }
            }
            SysField(newDaily, { newDaily = it; formError = null }, "New objective")
            val err = formError
            if (err != null) T(err, 12, c.warn)
            Spacer(Modifier.height(6.dp))
            SysButton("ADD OBJECTIVE", {
                val e = vm.addDailyItem(newDaily)
                formError = e
                if (e == null) newDaily = ""
            }, Modifier.fillMaxWidth(), filled = false)
        }

        SectionTitle("DATA")
        Panel {
            T("Your data stays on this device. Export a copy any time.", 12, c.sub)
            Spacer(Modifier.height(8.dp))
            SysButton("EXPORT SAVE", { shareSave(ctx, vm.exportJson()) }, Modifier.fillMaxWidth(), filled = false)
            Spacer(Modifier.height(8.dp))
            SysButton("RESET PROGRESSION", { nav.push(RReset) }, Modifier.fillMaxWidth(), filled = false, danger = true)
            Spacer(Modifier.height(8.dp))
            if (!confirmDelete) {
                SysButton("DELETE ALL DATA", { confirmDelete = true }, Modifier.fillMaxWidth(), filled = false, danger = true)
            } else {
                T("This permanently deletes your profile, quests and history from this device.", 13, c.bad)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SysButton("CANCEL", { confirmDelete = false }, Modifier.weight(1f), filled = false)
                    SysButton("DELETE", { vm.deleteAllData() }, Modifier.weight(1f), danger = true)
                }
            }
        }

        if (d.devUnlocked) {
            SectionTitle("DEVELOPER")
            Panel {
                T("Test tools are separate from normal progression.", 12, c.sub)
                Spacer(Modifier.height(8.dp))
                SysButton("OPEN DEVELOPER TOOLS", { nav.push(RDev) }, Modifier.fillMaxWidth(), filled = false)
            }
        }

        T(
            "Ascend 1.0.0",
            12, c.sub, align = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    versionTaps++
                    if (versionTaps >= 7) {
                        versionTaps = 0
                        vm.unlockDev()
                    }
                }
                .padding(vertical = 16.dp)
        )
    }
}

@Composable
fun ResetScreen(vm: GameViewModel, d: AppData, nav: Nav) {
    val c = LocalSys.current
    val ctx = LocalContext.current
    var typed by remember { mutableStateOf("") }
    Page("RESET PROGRESSION", onBack = nav.back) {
        Panel(accent = true) {
            T("WARNING", 18, c.bad, FontWeight.Black, mono = true, spacing = 3f)
            Spacer(Modifier.height(6.dp))
            T("This will permanently reset your current progression.", 15, c.text, FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            T(
                "Level, XP, stats, rank, history, achievements and streaks return to the start. " +
                    "Your profile, quests, daily objectives and settings are kept.",
                13, c.sub
            )
        }
        Panel {
            T("Optional: keep a copy first.", 13, c.text)
            Spacer(Modifier.height(8.dp))
            SysButton("EXPORT CURRENT SAVE", { shareSave(ctx, vm.exportJson()) }, Modifier.fillMaxWidth(), filled = false)
        }
        Panel {
            T("Type RESET to confirm.", 13, c.text)
            SysField(typed, { typed = it.take(10) }, "RESET")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SysButton("CANCEL", { nav.back() }, Modifier.weight(1f), filled = false)
            SysButton(
                "RESET",
                {
                    vm.resetProgression()
                    nav.tab(Tab.HOME)
                },
                Modifier.weight(1f),
                enabled = typed.trim().equals("RESET", ignoreCase = true),
                danger = true
            )
        }
    }
}
