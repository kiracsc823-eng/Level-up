package com.kira.ascend.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class Stat(val code: String, val label: String, val core: Boolean) {
    STR("STR", "Strength", true),
    INT("INT", "Intelligence", true),
    END("END", "Endurance", true),
    DIS("DIS", "Discipline", true),
    FOC("FOC", "Focus", true),
    SKL("SKL", "Skills", true),
    CRE("CRE", "Creativity", false),
    SOC("SOC", "Social", false),
    WIS("WIS", "Wisdom", false);

    companion object {
        fun fromCode(c: String): Stat? = values().firstOrNull { it.code == c }
        fun coreSet(): Set<Stat> = values().filter { it.core }.toSet()
    }
}

enum class Difficulty(val defaultXp: Int) { E(30), D(60), C(100), B(150), A(250), S(500), SS(800) }

enum class Rank { E, D, C, B, A, S, SS, SSS }

enum class QuestType(val label: String) {
    DAILY("Daily"), WEEKLY("Weekly"), MAIN("Main"), SIDE("Side"),
    CHALLENGE("Challenge"), HABIT("Habit"), CUSTOM("Custom")
}

enum class Repeat(val label: String) { NONE("Once"), DAILY("Daily"), WEEKLY("Weekly") }

enum class Verification(val label: String) { NONE("None"), TIMER("Timer"), CONFIRM("Self-confirm") }

enum class HistoryKind { QUEST, DAILY_SYSTEM, ACHIEVEMENT, BONUS }

enum class ThemeName(val label: String) {
    SYSTEM("System"), MOON("Moon"), VOID("Void"), CYBER("Cyber"), MINIMAL("Minimal")
}

enum class ModeSetting(val label: String) { DARK("Dark"), LIGHT("Light"), AUTO("Auto") }

enum class AnimLevel(val label: String) { OFF("Off"), REDUCED("Reduced"), FULL("Full") }

enum class FontScale(val label: String, val factor: Float) {
    NORMAL("Normal", 1f), LARGE("Large", 1.15f), XL("Extra large", 1.3f)
}

enum class StreakType(val label: String) {
    DAILY("Daily"), STUDY("Study"), EXERCISE("Exercise"), HABIT("Habit")
}

data class Settings(
    val theme: ThemeName = ThemeName.SYSTEM,
    val mode: ModeSetting = ModeSetting.DARK,
    val accent: Long = 0L,
    val anim: AnimLevel = AnimLevel.FULL,
    val sound: Boolean = false,
    val haptics: Boolean = true,
    val notifications: Boolean = false,
    val reminderHour: Int = 18,
    val enabledStats: Set<Stat> = Stat.coreSet(),
    val xpBase: Double = 100.0,
    val xpExponent: Double = 1.25,
    val rankMinLevels: List<Int> = listOf(1, 6, 11, 21, 36, 51, 76, 101),
    val categories: List<String> = listOf(
        "Knowledge", "Fitness", "Discipline", "Productivity",
        "Skills", "Creativity", "Finance", "Personal Growth"
    ),
    val streakGrace: Boolean = true,
    val highContrast: Boolean = false,
    val fontScale: FontScale = FontScale.NORMAL
)

data class Quest(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = "Productivity",
    val difficulty: Difficulty = Difficulty.E,
    val type: QuestType = QuestType.CUSTOM,
    val xp: Int = 30,
    val rewards: Map<Stat, Int> = emptyMap(),
    val deadlineDay: Long? = null,
    val repeat: Repeat = Repeat.NONE,
    val timerMinutes: Int = 0,
    val verification: Verification = Verification.NONE,
    val notes: String = "",
    val createdAt: Long = 0L,
    val completions: Int = 0,
    val lastCompletedDay: Long = -1L
)

data class HistoryEntry(
    val id: String,
    val time: Long,
    val day: Long,
    val title: String,
    val kind: HistoryKind,
    val xp: Int,
    val stats: Map<Stat, Int> = emptyMap(),
    val category: String = "",
    val difficulty: Difficulty? = null,
    val questType: QuestType? = null,
    val minutes: Int = 0
)

data class Player(
    val name: String = "",
    val username: String = "",
    val goal: String = "",
    val areas: List<String> = emptyList(),
    val dailyMinutes: Int = 60,
    val level: Int = 1,
    val xp: Int = 0,
    val totalXp: Long = 0L,
    val stats: Map<Stat, Int> = Stat.values().associateWith { 10 },
    val avatarStyle: Int = 0,
    val avatarColor: Long = 0xFF7C8CFFL
)

data class DailyItem(val id: String, val text: String)

data class TimerSession(
    val id: String,
    val questId: String?,
    val title: String,
    val plannedMin: Int,
    val completed: Boolean,
    val day: Long,
    val endedAt: Long
)

data class ActiveTimer(
    val questId: String?,
    val title: String,
    val plannedMin: Int,
    val durationMs: Long,
    val running: Boolean,
    val endAt: Long,
    val remainingMs: Long,
    val finished: Boolean = false
) {
    fun remaining(now: Long): Long =
        if (finished) 0L else if (running) maxOf(0L, endAt - now) else remainingMs
}

data class DayLog(val day: Long, val due: Int, val done: Int, val level: Int)

data class AppData(
    val onboarded: Boolean = false,
    val player: Player = Player(),
    val quests: List<Quest> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val unlocked: Map<String, Long> = emptyMap(),
    val dailyItems: List<DailyItem> = defaultDailyItems(),
    val dailyDay: Long = -1L,
    val dailyChecked: Set<String> = emptySet(),
    val dailyCompletedDay: Long = -1L,
    val dailySystemsCompleted: Int = 0,
    val activeDays: Set<Long> = emptySet(),
    val settings: Settings = Settings(),
    val sessions: List<TimerSession> = emptyList(),
    val timer: ActiveTimer? = null,
    val dayLog: List<DayLog> = emptyList(),
    val questsCreated: Int = 0,
    val consoleUsed: Boolean = false,
    val devUnlocked: Boolean = false,
    val devMode: Boolean = false,
    val devSnapshot: String? = null
)

sealed class GameEvent {
    data class QuestComplete(
        val title: String, val xp: Int, val bonus: Int, val stats: Map<Stat, Int>
    ) : GameEvent()

    data class DailyComplete(val xp: Int, val stats: Map<Stat, Int>) : GameEvent()

    data class LevelUp(
        val from: Int, val to: Int, val stats: Map<Stat, Int>,
        val rankFrom: Rank?, val rankTo: Rank?
    ) : GameEvent()

    data class Achievement(val icon: String, val name: String, val xp: Int) : GameEvent()
}

fun defaultDailyItems(): List<DailyItem> = listOf(
    DailyItem("d1", "Study 60 minutes"),
    DailyItem("d2", "Exercise 30 minutes"),
    DailyItem("d3", "Read 20 pages"),
    DailyItem("d4", "Complete 1 priority task"),
    DailyItem("d5", "Review today's progress")
)

fun newId(): String = UUID.randomUUID().toString().take(8)

fun todayEpoch(): Long = LocalDate.now().toEpochDay()

fun nowMs(): Long = System.currentTimeMillis()

fun dayText(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

fun shortDayText(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

fun timeText(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

fun hourOf(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).hour
