package com.kira.ascend.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kira.ascend.GameViewModel
import com.kira.ascend.model.AnimLevel
import com.kira.ascend.model.GameEvent
import com.kira.ascend.model.Settings
import com.kira.ascend.engine.Briefing
import com.kira.ascend.notify.Sfx
import com.kira.ascend.notify.Voice
import kotlinx.coroutines.delay

/** Full-screen cinematic feedback: quest complete, daily complete, achievement, level up. */
@Composable
fun EventHost(vm: GameViewModel, settings: Settings) {
    val events by vm.events.collectAsState()
    val ev = events.firstOrNull() ?: return
    val c = LocalSys.current
    val anim = LocalAnim.current
    val haptic = LocalHapticFeedback.current
    val a = if (c.accent.luminance() < 0.35f) lerp(c.accent, Color.White, 0.5f) else c.accent
    val white = Color(0xFFF4F6FF)
    val dim = Color(0xFF9AA4B8)

    var shown by remember(ev, events.size) { mutableStateOf(false) }
    LaunchedEffect(ev, events.size) {
        shown = true
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (settings.sound) {
            when (ev) {
                is GameEvent.LevelUp -> Sfx.levelUp()
                is GameEvent.QuestComplete -> Sfx.questDone()
                is GameEvent.DailyComplete -> Sfx.daily()
                is GameEvent.Achievement -> Sfx.achievement()
            }
        }
        if (settings.voice) Voice.speak(Briefing.forEvent(ev), false)
        if (ev !is GameEvent.LevelUp) {
            delay(2600)
            vm.consumeEvent()
        }
    }
    val spec: AnimationSpec<Float> = if (anim == AnimLevel.OFF) snap() else tween(320)
    val alphaV by animateFloatAsState(if (shown) 1f else 0f, spec, label = "evAlpha")
    val scaleV by animateFloatAsState(if (shown) 1f else 0.86f, spec, label = "evScale")
    val noRipple = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(interactionSource = noRipple, indication = null) {
                if (ev !is GameEvent.LevelUp) vm.consumeEvent()
            },
        contentAlignment = Alignment.Center
    ) {
        val mod = Modifier
            .graphicsLayer {
                this.alpha = alphaV
                scaleX = scaleV
                scaleY = scaleV
            }
            .padding(24.dp)
        when (ev) {
            is GameEvent.QuestComplete -> Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
                T("QUEST COMPLETE", 26, a, FontWeight.Black, mono = true, spacing = 3f, align = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                T(ev.title, 15, dim, align = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                T("+${ev.xp} XP", 44, white, FontWeight.Black, mono = true)
                if (ev.bonus > 0) T("+${ev.bonus} XP STREAK BONUS", 13, a, FontWeight.Bold, mono = true)
                Spacer(Modifier.height(12.dp))
                for ((k, v) in ev.stats) T("${k.code} +$v", 20, white, FontWeight.Bold, mono = true)
                Spacer(Modifier.height(18.dp))
                T("tap to continue", 11, dim)
            }
            is GameEvent.DailyComplete -> Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
                T("DAILY SYSTEM", 24, a, FontWeight.Black, mono = true, spacing = 3f)
                T("COMPLETE", 24, a, FontWeight.Black, mono = true, spacing = 3f)
                Spacer(Modifier.height(16.dp))
                T("+${ev.xp} XP", 44, white, FontWeight.Black, mono = true)
                for ((k, v) in ev.stats) T("${k.code} +$v", 20, white, FontWeight.Bold, mono = true)
                Spacer(Modifier.height(18.dp))
                T("tap to continue", 11, dim)
            }
            is GameEvent.Achievement -> Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
                T(ev.icon, 56, white)
                Spacer(Modifier.height(8.dp))
                T("ACHIEVEMENT UNLOCKED", 18, a, FontWeight.Black, mono = true, spacing = 2f, align = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                T(ev.name, 24, white, FontWeight.Black, mono = true, align = TextAlign.Center)
                if (ev.xp > 0) T("+${ev.xp} XP", 18, a, FontWeight.Bold, mono = true)
                Spacer(Modifier.height(18.dp))
                T("tap to continue", 11, dim)
            }
            is GameEvent.LevelUp -> {
                val entries = ev.stats.entries.toList()
                var visible by remember(ev, events.size) {
                    mutableStateOf(if (anim == AnimLevel.OFF) entries.size else 0)
                }
                LaunchedEffect(ev, events.size) {
                    if (anim != AnimLevel.OFF) {
                        for (i in 1..entries.size) {
                            delay(350)
                            visible = i
                        }
                    }
                }
                Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
                    T("LEVEL UP", 40, a, FontWeight.Black, mono = true, spacing = 4f)
                    Spacer(Modifier.height(14.dp))
                    T("LEVEL ${ev.from}", 20, dim, mono = true)
                    T("↓", 28, a, FontWeight.Bold)
                    T("LEVEL ${ev.to}", 56, white, FontWeight.Black, mono = true)
                    val rf = ev.rankFrom
                    val rt = ev.rankTo
                    if (rf != null && rt != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            RankBadge(rf, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            T("→", 22, a, FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            RankBadge(rt, 36.dp)
                        }
                        T("RANK UP", 13, a, FontWeight.Bold, mono = true, spacing = 2f)
                    }
                    Spacer(Modifier.height(12.dp))
                    entries.forEachIndexed { i, e ->
                        if (i < visible) T("${e.key.code} +${e.value}", 20, white, FontWeight.Bold, mono = true)
                    }
                    Spacer(Modifier.height(16.dp))
                    T("NEW LEVEL ACHIEVED.", 14, dim, mono = true, spacing = 2f)
                    Spacer(Modifier.height(18.dp))
                    SysButton("CONTINUE", { vm.consumeEvent() })
                }
            }
        }
    }
}
