package com.kira.ascend.engine

import com.kira.ascend.model.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** XP and levelling. The formula is configurable through Settings.xpBase / xpExponent. */
object XpEngine {
    const val MAX_LEVEL = 999

    fun required(level: Int, s: Settings): Int =
        maxOf(10, (s.xpBase * level.toDouble().pow(s.xpExponent)).roundToInt())

    data class Result(val player: Player, val fromLevel: Int, val toLevel: Int)

    /** Adds (or removes, if negative) XP and resolves level ups / downs. */
    fun apply(p: Player, delta: Int, s: Settings): Result {
        var level = p.level
        var xp = p.xp.toLong() + delta
        while (level < MAX_LEVEL && xp >= required(level, s)) {
            xp -= required(level, s)
            level++
        }
        while (xp < 0 && level > 1) {
            level--
            xp += required(level, s)
        }
        if (xp < 0) xp = 0
        if (level >= MAX_LEVEL && xp >= required(level, s)) xp = (required(level, s) - 1).toLong()
        val total = maxOf(0L, p.totalXp + delta)
        return Result(p.copy(level = level, xp = xp.toInt(), totalXp = total), p.level, level)
    }
}

object RankEngine {
    fun rankFor(level: Int, s: Settings): Rank {
        var r = Rank.E
        Rank.values().forEachIndexed { i, rk ->
            if (i < s.rankMinLevels.size && level >= s.rankMinLevels[i]) r = rk
        }
        return r
    }

    /** Next rank and the level needed for it, or null at the top rank. */
    fun nextRank(level: Int, s: Settings): Pair<Rank, Int>? {
        val cur = rankFor(level, s)
        val idx = cur.ordinal + 1
        if (idx >= Rank.values().size || idx >= s.rankMinLevels.size) return null
        return Pair(Rank.values()[idx], s.rankMinLevels[idx])
    }
}

object QuestEngine {
    /** Weeks start on Monday. Epoch day 4 (5 Jan 1970) was a Monday. */
    fun weekOf(day: Long): Long = Math.floorDiv(day + 3, 7L)

    fun isDone(q: Quest, today: Long): Boolean = when (q.repeat) {
        Repeat.NONE -> q.completions > 0
        Repeat.DAILY -> q.lastCompletedDay == today
        Repeat.WEEKLY -> q.lastCompletedDay >= 0 && weekOf(q.lastCompletedDay) == weekOf(today)
    }

    fun isExpired(q: Quest, today: Long): Boolean {
        val dl = q.deadlineDay
        return q.repeat == Repeat.NONE && dl != null && dl < today && !isDone(q, today)
    }

    /** Quests that count towards "today": repeating ones, open one-offs, and one-offs finished today. */
    fun todayQuests(d: AppData, today: Long): List<Quest> = d.quests.filter { q ->
        when (q.repeat) {
            Repeat.DAILY, Repeat.WEEKLY -> true
            Repeat.NONE -> if (isDone(q, today)) q.lastCompletedDay == today else !isExpired(q, today)
        }
    }

    fun hasVerifiedSession(d: AppData, q: Quest, today: Long): Boolean =
        d.sessions.any { it.completed && it.questId == q.id && it.day == today && it.plannedMin >= q.timerMinutes }

    fun baseRewards(category: String): Map<Stat, Int> = when (category) {
        "Knowledge" -> mapOf(Stat.INT to 2, Stat.FOC to 1)
        "Fitness" -> mapOf(Stat.STR to 1, Stat.END to 2)
        "Discipline" -> mapOf(Stat.DIS to 2)
        "Productivity" -> mapOf(Stat.DIS to 1, Stat.FOC to 1)
        "Skills" -> mapOf(Stat.SKL to 2)
        "Creativity" -> mapOf(Stat.CRE to 2, Stat.SKL to 1)
        "Finance" -> mapOf(Stat.WIS to 1, Stat.DIS to 1)
        "Personal Growth" -> mapOf(Stat.WIS to 1, Stat.DIS to 1)
        else -> mapOf(Stat.DIS to 1)
    }

    /** Suggested stat rewards for a category + difficulty, limited to the enabled stats. */
    fun rewardsFor(category: String, diff: Difficulty, enabled: Set<Stat>): Map<Stat, Int> {
        val scale = 1 + diff.ordinal / 4
        val out = LinkedHashMap<Stat, Int>()
        for ((k, v) in baseRewards(category)) if (k in enabled) out[k] = v * scale
        if (out.isEmpty()) {
            val fallback = if (Stat.DIS in enabled) Stat.DIS else enabled.firstOrNull()
            if (fallback != null) out[fallback] = scale
        }
        return out
    }

    private val unsafeWords = listOf(
        "starv", "self harm", "self-harm", "hurt myself", "harm myself", "no sleep", "skip sleep",
        "without sleep", "all-nighter", "all nighter", "stay awake", "no food", "don't eat", "dont eat",
        "skip meals", "skip a meal", "purge", "cut myself", "punish myself", "til i pass out",
        "until i pass out", "until exhaustion", "to exhaustion"
    )

    fun unsafeReason(vararg texts: String): String? {
        val joined = texts.joinToString(" ").lowercase()
        return if (unsafeWords.any { joined.contains(it) })
            "The System does not reward unsafe challenges. Choose something that builds you up."
        else null
    }
}

object StreakEngine {
    /** Current streak. A single missed day can be forgiven when [grace] is true. */
    fun current(days: Set<Long>, today: Long, grace: Boolean): Int {
        var d = if (today in days) today else today - 1
        var count = 0
        var graceLeft = if (grace) 1 else 0
        while (d >= 0) {
            if (d in days) {
                count++
                d--
            } else if (graceLeft > 0 && count > 0 && (d - 1) in days) {
                graceLeft--
                d--
            } else break
        }
        return count
    }

    fun best(days: Set<Long>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] == sorted[i - 1] + 1) run++ else run = 1
            if (run > best) best = run
        }
        return best
    }

    fun days(d: AppData, t: StreakType): Set<Long> = when (t) {
        StreakType.DAILY -> d.activeDays
        StreakType.STUDY -> d.history.filter { it.kind == HistoryKind.QUEST && it.category == "Knowledge" }.map { it.day }.toSet()
        StreakType.EXERCISE -> d.history.filter { it.kind == HistoryKind.QUEST && it.category == "Fitness" }.map { it.day }.toSet()
        StreakType.HABIT -> d.history.filter { it.kind == HistoryKind.QUEST && it.questType == QuestType.HABIT }.map { it.day }.toSet()
    }

    fun currentFor(d: AppData, t: StreakType, today: Long): Int =
        current(days(d, t), today, d.settings.streakGrace)

    fun bestFor(d: AppData, t: StreakType): Int = best(days(d, t))
}

data class Metrics(
    val level: Int, val rankIdx: Int, val quests: Int, val hardQuests: Int, val studyQuests: Int,
    val bestHabit: Int, val bestDaily: Int, val sessions: Int, val maxStat: Int, val minCore: Int,
    val dailySystems: Int, val created: Int, val console: Boolean, val early: Boolean, val totalXp: Long
)

data class AchDef(
    val id: String, val icon: String, val name: String, val desc: String,
    val xp: Int, val hidden: Boolean, val check: (Metrics) -> Boolean
)

object AchievementEngine {
    val defs: List<AchDef> = listOf(
        AchDef("first_quest", "🎯", "FIRST QUEST", "Complete your first quest.", 25, false) { it.quests >= 1 },
        AchDef("first_level", "▲", "FIRST LEVEL UP", "Reach Level 2.", 25, false) { it.level >= 2 },
        AchDef("first_day", "☀️", "FIRST DAY", "Complete the Daily System once.", 50, false) { it.dailySystems >= 1 },
        AchDef("iron_will", "⚔️", "IRON WILL", "Complete 10 difficult quests (rank B or higher).", 100, false) { it.hardQuests >= 10 },
        AchDef("scholar", "📚", "SCHOLAR", "Complete 50 study quests.", 200, false) { it.studyQuests >= 50 },
        AchDef("week_warrior", "🔥", "WEEK WARRIOR", "Reach a 7-day streak.", 100, false) { it.bestDaily >= 7 },
        AchDef("disciplined", "🧭", "DISCIPLINED", "Maintain a 30-day habit streak.", 300, false) { it.bestHabit >= 30 },
        AchDef("focus_initiate", "⏳", "FOCUS INITIATE", "Complete a focus session.", 40, false) { it.sessions >= 1 },
        AchDef("deep_work", "🧠", "DEEP WORK", "Complete 10 focus sessions.", 120, false) { it.sessions >= 10 },
        AchDef("rising_hunter", "🏅", "RISING HUNTER", "Reach Rank C.", 150, false) { it.rankIdx >= Rank.C.ordinal },
        AchDef("elite", "💎", "ELITE", "Reach Rank A.", 400, false) { it.rankIdx >= Rank.A.ordinal },
        AchDef("centurion", "💯", "CENTURION", "Complete 100 quests.", 300, false) { it.quests >= 100 },
        AchDef("master", "👑", "MASTER", "Reach Level 100.", 1000, false) { it.level >= 100 },
        AchDef("balanced", "⚖️", "TRUE BALANCE", "Raise all six core stats to 20.", 150, true) { it.minCore >= 20 },
        AchDef("early_riser", "🌅", "EARLY RISER", "Complete a quest before 7 AM.", 50, true) { it.early },
        AchDef("awakening", "🔓", "THE SYSTEM LISTENS", "Use the System Console.", 30, true) { it.console },
        AchDef("architect", "🛠️", "ARCHITECT", "Create 5 quests of your own.", 60, true) { it.created >= 5 },
        AchDef("legend", "✨", "LEGEND", "Earn 10,000 total XP.", 250, true) { it.totalXp >= 10000 }
    )

    fun metrics(d: AppData): Metrics {
        val qh = d.history.filter { it.kind == HistoryKind.QUEST }
        val core = Stat.values().filter { it.core }
        val minCore = core.minOfOrNull { d.player.stats[it] ?: 10 } ?: 10
        val maxStat = d.player.stats.values.maxOrNull() ?: 10
        return Metrics(
            level = d.player.level,
            rankIdx = RankEngine.rankFor(d.player.level, d.settings).ordinal,
            quests = qh.size,
            hardQuests = qh.count { (it.difficulty?.ordinal ?: 0) >= Difficulty.B.ordinal },
            studyQuests = qh.count { it.category == "Knowledge" },
            bestHabit = StreakEngine.bestFor(d, StreakType.HABIT),
            bestDaily = StreakEngine.bestFor(d, StreakType.DAILY),
            sessions = d.sessions.count { it.completed },
            maxStat = maxStat,
            minCore = minCore,
            dailySystems = d.dailySystemsCompleted,
            created = d.questsCreated,
            console = d.consoleUsed,
            early = qh.any { hourOf(it.time) < 7 },
            totalXp = d.player.totalXp
        )
    }
}

object Analytics {
    fun xpByDay(h: List<HistoryEntry>, from: Long, to: Long): List<Int> =
        (from..to).map { day -> h.filter { it.day == day && it.xp > 0 }.sumOf { it.xp } }

    fun questsByDay(h: List<HistoryEntry>, from: Long, to: Long): List<Int> =
        (from..to).map { day -> h.count { it.day == day && it.kind == HistoryKind.QUEST } }

    fun gainsByStat(h: List<HistoryEntry>, from: Long, to: Long): Map<Stat, Int> {
        val r = LinkedHashMap<Stat, Int>()
        for (e in h) if (e.day in from..to) for ((k, v) in e.stats) r[k] = (r[k] ?: 0) + v
        return r
    }

    fun xpTotal(h: List<HistoryEntry>, from: Long, to: Long): Int =
        h.filter { it.day in from..to && it.xp > 0 }.sumOf { it.xp }

    /** Reconstructs a stat's value at the end of each of the last [days] days. */
    fun statSeries(d: AppData, stat: Stat, today: Long, days: Int): List<Float> {
        val current = d.player.stats[stat] ?: 10
        return (days - 1 downTo 0).map { back ->
            val day = today - back
            val later = d.history.filter { it.day > day }.sumOf { it.stats[stat] ?: 0 }
            (current - later).coerceAtLeast(0).toFloat()
        }
    }

    fun levelSeries(d: AppData, today: Long, days: Int): List<Float> {
        if (d.dayLog.isEmpty()) return emptyList()
        val sorted = d.dayLog.sortedBy { it.day }
        return (days - 1 downTo 0).map { back ->
            val day = today - back
            val upTo = sorted.lastOrNull { it.day <= day }
            (upTo?.level ?: sorted.first().level).toFloat()
        }
    }

    fun weekdayName(day: Long): String =
        LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    fun mostProductiveWeekday(h: List<HistoryEntry>): String {
        val xp = h.filter { it.xp > 0 }.groupBy { LocalDate.ofEpochDay(it.day).dayOfWeek }
            .mapValues { e -> e.value.sumOf { it.xp } }
        val best = xp.maxByOrNull { it.value } ?: return "-"
        return best.key.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    fun completionRate(log: List<DayLog>, today: Long, days: Int): Int? {
        val recent = log.filter { it.day > today - days && it.due > 0 }
        val due = recent.sumOf { it.due }
        if (due == 0) return null
        val done = recent.sumOf { minOf(it.done, it.due) }
        return (done * 100.0 / due).roundToInt()
    }
}

/** Daily System reward scales with the number of objectives, so one tick-box cannot farm 250 XP. */
object DailyEngine {
    fun reward(items: Int): Pair<Int, Int> = Pair(minOf(250, 50 * items), minOf(5, items))
}

data class QuestTemplate(
    val name: String, val desc: String, val category: String, val difficulty: Difficulty,
    val type: QuestType, val timerMinutes: Int, val verification: Verification
)

object Templates {
    val all: List<QuestTemplate> = listOf(
        QuestTemplate("Study 60 minutes", "A focused study session.", "Knowledge", Difficulty.B, QuestType.DAILY, 60, Verification.TIMER),
        QuestTemplate("Read 20 pages", "Read a book or notes.", "Knowledge", Difficulty.D, QuestType.DAILY, 0, Verification.CONFIRM),
        QuestTemplate("Revise yesterday's notes", "Quick revision before new topics.", "Knowledge", Difficulty.D, QuestType.HABIT, 20, Verification.TIMER),
        QuestTemplate("Push-ups (3 sets)", "Good form. Rest between sets.", "Fitness", Difficulty.C, QuestType.DAILY, 0, Verification.CONFIRM),
        QuestTemplate("Squats (3 sets)", "Good form. Rest between sets.", "Fitness", Difficulty.C, QuestType.DAILY, 0, Verification.CONFIRM),
        QuestTemplate("Run or brisk walk 20 minutes", "Go at your own pace.", "Fitness", Difficulty.C, QuestType.DAILY, 0, Verification.CONFIRM),
        QuestTemplate("Stretch for 10 minutes", "Warm up or cool down.", "Fitness", Difficulty.E, QuestType.HABIT, 10, Verification.TIMER),
        QuestTemplate("Practice a skill 25 minutes", "Deliberate practice.", "Skills", Difficulty.C, QuestType.HABIT, 25, Verification.TIMER),
        QuestTemplate("Complete 1 priority task", "The most important task of the day.", "Productivity", Difficulty.D, QuestType.DAILY, 0, Verification.CONFIRM),
        QuestTemplate("Plan tomorrow's top 3 tasks", "Write them down tonight.", "Discipline", Difficulty.D, QuestType.HABIT, 0, Verification.CONFIRM),
        QuestTemplate("Drink 2 litres of water", "Stay hydrated.", "Discipline", Difficulty.E, QuestType.HABIT, 0, Verification.CONFIRM),
        QuestTemplate("Sleep before 11 PM", "Rest is part of progress.", "Discipline", Difficulty.D, QuestType.HABIT, 0, Verification.CONFIRM),
        QuestTemplate("Log today's spending", "Note what you spent and earned.", "Finance", Difficulty.E, QuestType.HABIT, 0, Verification.CONFIRM),
        QuestTemplate("Write a 5-minute reflection", "What went well? What to improve?", "Personal Growth", Difficulty.D, QuestType.HABIT, 0, Verification.CONFIRM),
        QuestTemplate("Create something small", "Draw, write, build or compose.", "Creativity", Difficulty.D, QuestType.HABIT, 0, Verification.CONFIRM)
    )

    fun build(t: QuestTemplate, s: Settings): Quest {
        val cat = if (t.category in s.categories) t.category else (s.categories.firstOrNull() ?: "Productivity")
        val repeat = if (t.type == QuestType.DAILY || t.type == QuestType.HABIT) Repeat.DAILY else Repeat.NONE
        return Quest(
            newId(), t.name, t.desc, cat, t.difficulty, t.type, t.difficulty.defaultXp,
            QuestEngine.rewardsFor(cat, t.difficulty, s.enabledStats), null, repeat,
            t.timerMinutes, t.verification, "", nowMs()
        )
    }

    private val keywords: List<Pair<String, List<String>>> = listOf(
        "Fitness" to listOf("squat", "push", "pull up", "pullup", "run", "jog", "walk", "gym", "workout", "exercise", "sit-up", "situp", "yoga", "stretch", "cycl", "swim", "plank", "cardio"),
        "Knowledge" to listOf("study", "read", "learn", "revis", "exam", "course", "homework", "gk", "notes", "lecture"),
        "Finance" to listOf("budget", "spend", "saving", "invest", "expense"),
        "Creativity" to listOf("draw", "paint", "sketch", "compose", "music", "design", "write a story", "poem"),
        "Skills" to listOf("practice", "typing", "coding", "code", "skill", "language"),
        "Discipline" to listOf("wake", "meditat", "routine", "plan ", "sleep", "water"),
        "Productivity" to listOf("task", "project", "work", "clean", "organi")
    )

    fun guessCategory(name: String): String? {
        val n = name.lowercase()
        for ((cat, words) in keywords) if (words.any { n.contains(it) }) return cat
        return null
    }
}

data class Estimate(val difficulty: Difficulty, val xp: Int, val capped: Boolean, val note: String)

/** Works out a quest's rank (difficulty) and XP from what the quest asks for. */
object Estimator {
    private val opt = setOf(RegexOption.IGNORE_CASE)
    private val setsRe = Regex("(\\d+)\\s*sets?\\s*(?:of|x)\\s*(\\d+)", opt)
    private val minRe = Regex("(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m)\\b", opt)
    private val kmRe = Regex("(\\d+(?:\\.\\d+)?)\\s*(km|kms|kilometers?|kilometres?|k)\\b", opt)
    private val repsRe = Regex("(\\d+)\\s*(?:x\\s*)?(?:push|pull|squat|sit|crunch|burpee|jump|rep|dip|lunge|plank)", opt)
    private val pagesRe = Regex("(\\d+)\\s*(?:pages?|pgs?)\\b", opt)
    private val questionsRe = Regex("(\\d+)\\s*(?:questions?|mcqs?|problems?)\\b", opt)
    private val heavyWords = listOf("exam", "test", "project", "assignment", "interview", "presentation", "priority")

    fun estimate(name: String, desc: String, timerMin: Int, type: QuestType): Estimate {
        var text = "$name $desc"
        val parts = ArrayList<String>()

        var reps = 0.0
        for (m in setsRe.findAll(text)) {
            reps += (m.groupValues[1].toIntOrNull() ?: 0).coerceAtMost(50) * (m.groupValues[2].toIntOrNull() ?: 0).coerceAtMost(500)
        }
        text = setsRe.replace(text, " ")
        for (m in repsRe.findAll(text)) reps += (m.groupValues[1].toIntOrNull() ?: 0).coerceAtMost(2000)

        var minutes = 0.0
        for (m in minRe.findAll(text)) {
            val v = m.groupValues[1].toDoubleOrNull() ?: 0.0
            minutes += if (m.groupValues[2].lowercase().startsWith("h")) v * 60 else v
        }
        if (minutes == 0.0 && timerMin > 0) minutes = timerMin.toDouble()

        var km = 0.0
        for (m in kmRe.findAll(text)) km += m.groupValues[1].toDoubleOrNull() ?: 0.0
        var pages = 0.0
        for (m in pagesRe.findAll(text)) pages += m.groupValues[1].toIntOrNull() ?: 0
        var questions = 0.0
        for (m in questionsRe.findAll(text)) questions += m.groupValues[1].toIntOrNull() ?: 0

        if (minutes > 0) parts.add("${minutes.roundToInt()} min")
        if (reps > 0) parts.add("${reps.roundToInt()} reps")
        if (km > 0) parts.add("$km km")
        if (pages > 0) parts.add("${pages.roundToInt()} pages")
        if (questions > 0) parts.add("${questions.roundToInt()} questions")

        var effort = minutes + reps * 0.4 + km * 7 + pages * 1.5 + questions * 0.5
        var note: String
        if (effort <= 0.0) {
            val lower = text.lowercase()
            effort = if (heavyWords.any { lower.contains(it) }) 40.0 else 15.0
            note = "No amount found in the name, so the System assumed a normal task. Add minutes, reps, km or pages for a better estimate."
        } else {
            note = "Estimated from: " + parts.joinToString(", ") + "."
        }
        var capped = false
        if (effort > 120.0) {
            effort = 120.0
            capped = true
            note += " Very large amounts are capped. Train smart and rest."
        }
        val mult = when (type) {
            QuestType.MAIN -> 2.5
            QuestType.WEEKLY -> 2.0
            QuestType.CHALLENGE -> 1.5
            else -> 1.0
        }
        val e = effort * mult
        val diff = when {
            e < 10 -> Difficulty.E
            e < 25 -> Difficulty.D
            e < 45 -> Difficulty.C
            e < 75 -> Difficulty.B
            e < 120 -> Difficulty.A
            e < 200 -> Difficulty.S
            else -> Difficulty.SS
        }
        return Estimate(diff, diff.defaultXp, capped, note)
    }
}
