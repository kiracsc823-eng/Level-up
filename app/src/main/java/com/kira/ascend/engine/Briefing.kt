package com.kira.ascend.engine

import com.kira.ascend.model.AppData
import com.kira.ascend.model.GameEvent
import com.kira.ascend.model.Stat
import com.kira.ascend.model.StreakType
import com.kira.ascend.model.todayEpoch
import java.util.Calendar

/** Builds the text the System speaks. Kept separate from the audio code. */
object Briefing {
    fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 5 -> "Good night"
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            h < 21 -> "Good evening"
            else -> "Good night"
        }
    }

    fun build(d: AppData): String {
        val today = todayEpoch()
        val p = d.player
        val s = d.settings
        val rank = RankEngine.rankFor(p.level, s)
        val need = XpEngine.required(p.level, s)
        val streak = StreakEngine.currentFor(d, StreakType.DAILY, today)
        val tq = QuestEngine.todayQuests(d, today)
        val pending = tq.filter { !QuestEngine.isDone(it, today) }
        val sb = StringBuilder()
        sb.append("System online. ${greeting()}, ${p.name}. ")
        sb.append("Player status. Level ${p.level}. Rank ${rank.name}. ${p.xp} of $need experience. ")
        if (streak > 0) sb.append("Current streak, $streak ${if (streak == 1) "day" else "days"}. ")
        if (tq.isEmpty()) {
            sb.append("You have no quests today. Create one to begin. ")
        } else if (pending.isEmpty()) {
            sb.append("All of today's quests are complete. Well done. ")
        } else {
            sb.append("You have ${pending.size} ${if (pending.size == 1) "quest" else "quests"} pending today. ")
            sb.append(pending.take(4).joinToString(", ") { it.name })
            if (pending.size > 4) sb.append(", and ${pending.size - 4} more")
            sb.append(". ")
        }
        val total = d.dailyItems.size
        if (total > 0) {
            val checked = if (d.dailyDay == today) d.dailyItems.count { it.id in d.dailyChecked } else 0
            sb.append("Daily system: $checked of $total objectives complete. ")
        }
        val top = Stat.values().filter { it in s.enabledStats }.maxByOrNull { p.stats[it] ?: 10 }
        if (top != null) sb.append("Your strongest attribute is ${top.label}. ")
        sb.append("Today's system objectives are waiting.")
        return sb.toString()
    }

    private fun statWords(stats: Map<Stat, Int>): String =
        stats.entries.joinToString(". ") { "${it.key.label} plus ${it.value}" }

    fun forEvent(ev: GameEvent): String = when (ev) {
        is GameEvent.QuestComplete -> {
            var t = "Quest complete. ${ev.xp} experience gained."
            if (ev.stats.isNotEmpty()) t += " " + statWords(ev.stats) + "."
            if (ev.bonus > 0) t += " Streak bonus, ${ev.bonus}."
            t
        }
        is GameEvent.DailyComplete -> "Daily system complete. ${ev.xp} experience gained."
        is GameEvent.LevelUp -> {
            var t = "Level up. You are now level ${ev.to}."
            val rt = ev.rankTo
            if (rt != null) t += " Rank advanced to ${rt.name}."
            t
        }
        is GameEvent.Achievement -> "Achievement unlocked. ${ev.name.lowercase()}."
    }
}
