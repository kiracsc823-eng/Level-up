package com.kira.ascend

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kira.ascend.data.Store
import com.kira.ascend.engine.AchievementEngine
import com.kira.ascend.engine.QuestEngine
import com.kira.ascend.engine.RankEngine
import com.kira.ascend.engine.StreakEngine
import com.kira.ascend.engine.XpEngine
import com.kira.ascend.model.*
import com.kira.ascend.notify.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private fun hist(
    kind: HistoryKind, title: String, xp: Int, stats: Map<Stat, Int>,
    cat: String = "", diff: Difficulty? = null, type: QuestType? = null, minutes: Int = 0
): HistoryEntry = HistoryEntry(newId(), nowMs(), todayEpoch(), title, kind, xp, stats, cat, diff, type, minutes)

/** Working copy of the data while one player action is being resolved. */
private class Ctx(var d: AppData) {
    val events = mutableListOf<GameEvent>()
    val gained = LinkedHashMap<Stat, Int>()

    fun give(xp: Int, stats: Map<Stat, Int>): Map<Stat, Int> {
        val en = d.settings.enabledStats
        val applied = LinkedHashMap<Stat, Int>()
        for ((k, v) in stats) if (k in en && v > 0) applied[k] = v
        val ns = d.player.stats.toMutableMap()
        for ((k, v) in applied) {
            ns[k] = (ns[k] ?: 10) + v
            gained[k] = (gained[k] ?: 0) + v
        }
        val r = XpEngine.apply(d.player.copy(stats = ns), xp, d.settings)
        d = d.copy(player = r.player)
        return applied
    }

    fun addHistory(e: HistoryEntry) {
        d = d.copy(history = d.history + e)
    }

    fun checkAchievements() {
        var guard = 0
        while (guard < 10) {
            guard++
            val m = AchievementEngine.metrics(d)
            val fresh = AchievementEngine.defs.filter { it.id !in d.unlocked && it.check(m) }
            if (fresh.isEmpty()) break
            for (a in fresh) {
                d = d.copy(unlocked = d.unlocked + (a.id to nowMs()))
                addHistory(hist(HistoryKind.ACHIEVEMENT, "Achievement: ${a.name}", a.xp, emptyMap()))
                events.add(GameEvent.Achievement(a.icon, a.name, a.xp))
                give(a.xp, emptyMap())
            }
        }
    }

    fun finish(oldLevel: Int, oldRank: Rank) {
        checkAchievements()
        val nl = d.player.level
        if (nl > oldLevel) {
            val nr = RankEngine.rankFor(nl, d.settings)
            val up = nr != oldRank
            events.add(
                GameEvent.LevelUp(oldLevel, nl, LinkedHashMap(gained), if (up) oldRank else null, if (up) nr else null)
            )
        }
    }
}

class GameViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _events = MutableStateFlow<List<GameEvent>>(emptyList())
    val events: StateFlow<List<GameEvent>> = _events.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        Notifier.ensureChannel(app)
        refreshDay()
        val s = _data.value.settings
        Notifier.applyDaily(app, s.notifications, s.reminderHour)
    }

    // ---------------------------------------------------------------- core plumbing

    private fun withLog(d: AppData): AppData {
        if (!d.onboarded) return d
        val today = todayEpoch()
        val tq = QuestEngine.todayQuests(d, today)
        val due = tq.size
        val done = tq.count { QuestEngine.isDone(it, today) }
        val existing = d.dayLog.firstOrNull { it.day == today }
        val entry = DayLog(today, maxOf(due, existing?.due ?: 0), maxOf(done, existing?.done ?: 0), d.player.level)
        val log = (d.dayLog.filter { it.day != today } + entry).takeLast(400)
        return d.copy(dayLog = log)
    }

    private fun commit(d: AppData) {
        val n = withLog(d)
        _data.value = n
        store.save(Json.toJson(n).toString())
    }

    private fun push(c: Ctx) {
        commit(c.d)
        if (c.events.isNotEmpty()) _events.value = _events.value + c.events
    }

    private fun say(msg: String) {
        _notice.value = msg
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun consumeEvent() {
        _events.value = _events.value.drop(1)
    }

    /** Called on resume and once a minute: daily rollover + finishing timers that ended in the background. */
    fun refreshDay() {
        var d = _data.value
        if (!d.onboarded) return
        val today = todayEpoch()
        if (d.dailyDay != today) {
            d = d.copy(dailyDay = today, dailyChecked = emptySet())
            commit(d)
        }
        tickTimer()
    }

    // ---------------------------------------------------------------- onboarding

    private fun starterQuests(areas: List<String>, minutes: Int, goalText: String, s: Settings): List<Quest> {
        val out = mutableListOf<Quest>()
        val now = nowMs()
        fun mk(
            name: String, desc: String, cat: String, diff: Difficulty, type: QuestType,
            repeat: Repeat, timer: Int, ver: Verification
        ): Quest = Quest(
            newId(), name, desc, cat, diff, type, diff.defaultXp,
            QuestEngine.rewardsFor(cat, diff, s.enabledStats), null, repeat, timer, ver, "", now
        )

        val study = if (minutes <= 30) 25 else if (minutes <= 60) 45 else 60
        for (a in areas.take(4)) {
            when (a) {
                "Knowledge" -> out.add(
                    mk("Study $study minutes", "A focused study session.", "Knowledge",
                        if (study >= 60) Difficulty.B else Difficulty.C, QuestType.DAILY, Repeat.DAILY, study, Verification.TIMER)
                )
                "Fitness" -> out.add(
                    mk("Exercise 30 minutes", "Any healthy movement: walk, run, bodyweight. Rest when you need to.",
                        "Fitness", Difficulty.C, QuestType.DAILY, Repeat.DAILY, 0, Verification.CONFIRM)
                )
                "Discipline" -> out.add(
                    mk("Plan tomorrow's top 3 tasks", "Write them down before you sleep.", "Discipline",
                        Difficulty.D, QuestType.HABIT, Repeat.DAILY, 0, Verification.CONFIRM)
                )
                "Productivity" -> out.add(
                    mk("Complete 1 priority task", "Pick the most important task and finish it.", "Productivity",
                        Difficulty.D, QuestType.DAILY, Repeat.DAILY, 0, Verification.CONFIRM)
                )
                "Skills" -> out.add(
                    mk("Practice a skill for 25 minutes", "Deliberate practice, no distractions.", "Skills",
                        Difficulty.C, QuestType.HABIT, Repeat.DAILY, 25, Verification.TIMER)
                )
                "Creativity" -> out.add(
                    mk("Create something small", "Draw, write, build or compose for a while.", "Creativity",
                        Difficulty.D, QuestType.HABIT, Repeat.DAILY, 0, Verification.CONFIRM)
                )
                "Finance" -> out.add(
                    mk("Log today's spending", "Note what you spent and earned today.", "Finance",
                        Difficulty.E, QuestType.HABIT, Repeat.DAILY, 0, Verification.CONFIRM)
                )
                "Personal Growth" -> out.add(
                    mk("Write a 5-minute reflection", "What went well? What will you improve?", "Personal Growth",
                        Difficulty.D, QuestType.HABIT, Repeat.DAILY, 0, Verification.CONFIRM)
                )
                else -> out.add(
                    mk("Work on your custom goal", "One honest step towards what matters to you.", "Productivity",
                        Difficulty.D, QuestType.DAILY, Repeat.DAILY, 0, Verification.CONFIRM)
                )
            }
        }
        if (goalText.isNotBlank()) {
            out.add(
                mk("Main goal: ${goalText.trim().take(40)}", "Make visible progress on your main goal.", "Productivity",
                    Difficulty.A, QuestType.MAIN, Repeat.NONE, 0, Verification.CONFIRM)
            )
        }
        out.add(
            mk("Weekly review", "Look back on the week and plan the next one.", "Personal Growth",
                Difficulty.C, QuestType.WEEKLY, Repeat.WEEKLY, 0, Verification.CONFIRM)
        )
        return out
    }

    fun finishOnboarding(name: String, username: String, goalCat: String, goalText: String, areas: List<String>, minutes: Int) {
        val cur = _data.value
        val goal = if (goalText.isNotBlank()) goalText.trim() else goalCat
        val player = Player(
            name = name.trim(), username = username.trim().ifBlank { name.trim() },
            goal = goal, areas = areas, dailyMinutes = minutes
        )
        val quests = starterQuests(areas, minutes, goalText, cur.settings)
        commit(AppData(onboarded = true, player = player, quests = quests, dailyDay = todayEpoch(), settings = cur.settings))
    }

    // ---------------------------------------------------------------- quests

    fun completeQuest(id: String, confirmed: Boolean = false): Boolean {
        val d0 = _data.value
        val today = todayEpoch()
        val q = d0.quests.firstOrNull { it.id == id } ?: return false
        if (QuestEngine.isDone(q, today)) { say("Already completed."); return false }
        if (QuestEngine.isExpired(q, today)) { say("Quest incomplete. Extend the deadline to try again."); return false }
        when (q.verification) {
            Verification.TIMER -> if (!QuestEngine.hasVerifiedSession(d0, q, today)) {
                say("Finish a focus session for this quest first.")
                return false
            }
            Verification.CONFIRM -> if (!confirmed) {
                say("Confirm that you really completed the quest.")
                return false
            }
            Verification.NONE -> {}
        }
        val c = Ctx(d0)
        val oldLevel = d0.player.level
        val oldRank = RankEngine.rankFor(oldLevel, d0.settings)
        val streakBefore = StreakEngine.current(d0.activeDays, today, d0.settings.streakGrace)
        val first = today !in d0.activeDays
        val bonus = if (first && streakBefore >= 3) minOf(streakBefore, 30) * 2 else 0

        val applied = c.give(q.xp, q.rewards)
        val updated = q.copy(completions = q.completions + 1, lastCompletedDay = today)
        c.d = c.d.copy(
            quests = c.d.quests.map { if (it.id == q.id) updated else it },
            activeDays = c.d.activeDays + today
        )
        c.addHistory(hist(HistoryKind.QUEST, q.name, q.xp, applied, q.category, q.difficulty, q.type, q.timerMinutes))
        if (bonus > 0) {
            c.give(bonus, emptyMap())
            c.addHistory(hist(HistoryKind.BONUS, "Streak bonus ($streakBefore days)", bonus, emptyMap()))
        }
        c.events.add(GameEvent.QuestComplete(q.name, q.xp, bonus, applied))
        c.finish(oldLevel, oldRank)
        push(c)
        return true
    }

    /** Returns an error message, or null when saved. */
    fun saveQuest(q: Quest): String? {
        val d = _data.value
        val name = q.name.trim()
        if (name.isEmpty()) return "Quest name is required."
        if (name.length > 60) return "Quest name is too long (max 60 characters)."
        val unsafe = QuestEngine.unsafeReason(name, q.description, q.notes)
        if (unsafe != null) return unsafe
        if (q.timerMinutes > 240) return "A timer can be at most 240 minutes."
        if (q.verification == Verification.TIMER && q.timerMinutes <= 0) return "Timer verification needs a timer length."
        if (q.xp < 5 || q.xp > 1000) return "XP must be between 5 and 1000."
        val clean = q.copy(name = name, description = q.description.trim(), notes = q.notes.trim())
        val exists = d.quests.any { it.id == clean.id }
        val c = Ctx(d)
        val ol = d.player.level
        val orank = RankEngine.rankFor(ol, d.settings)
        if (exists) {
            c.d = c.d.copy(quests = c.d.quests.map { if (it.id == clean.id) clean else it })
        } else {
            c.d = c.d.copy(
                quests = c.d.quests + clean.copy(createdAt = nowMs()),
                questsCreated = c.d.questsCreated + 1
            )
        }
        c.finish(ol, orank)
        push(c)
        return null
    }

    fun deleteQuest(id: String) {
        val d = _data.value
        commit(d.copy(quests = d.quests.filter { it.id != id }))
    }

    // ---------------------------------------------------------------- daily system

    fun toggleDaily(id: String) {
        var d = _data.value
        val today = todayEpoch()
        if (d.dailyDay != today) d = d.copy(dailyDay = today, dailyChecked = emptySet())
        if (d.dailyItems.none { it.id == id }) return
        val checked = d.dailyChecked.toMutableSet()
        val added = checked.add(id)
        if (!added) checked.remove(id)
        d = d.copy(dailyChecked = checked, activeDays = if (added) d.activeDays + today else d.activeDays)
        val c = Ctx(d)
        val ol = d.player.level
        val orank = RankEngine.rankFor(ol, d.settings)
        if (added && d.dailyItems.isNotEmpty() && d.dailyItems.all { it.id in checked } && d.dailyCompletedDay != today) {
            val applied = c.give(250, mapOf(Stat.DIS to 5))
            c.addHistory(hist(HistoryKind.DAILY_SYSTEM, "Daily System complete", 250, applied))
            c.d = c.d.copy(dailyCompletedDay = today, dailySystemsCompleted = c.d.dailySystemsCompleted + 1)
            c.events.add(GameEvent.DailyComplete(250, applied))
        }
        c.finish(ol, orank)
        push(c)
    }

    fun addDailyItem(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return "Write something first."
        if (t.length > 50) return "Keep it under 50 characters."
        val unsafe = QuestEngine.unsafeReason(t)
        if (unsafe != null) return unsafe
        val d = _data.value
        if (d.dailyItems.size >= 10) return "Maximum 10 daily objectives."
        commit(d.copy(dailyItems = d.dailyItems + DailyItem(newId(), t)))
        return null
    }

    fun removeDailyItem(id: String) {
        val d = _data.value
        commit(d.copy(dailyItems = d.dailyItems.filter { it.id != id }, dailyChecked = d.dailyChecked - id))
    }

    // ---------------------------------------------------------------- timer / focus

    fun startTimer(questId: String?, minutes: Int) {
        val d = _data.value
        val existing = d.timer
        if (existing != null && !existing.finished) { say("A focus session is already running."); return }
        val q = if (questId == null) null else d.quests.firstOrNull { it.id == questId }
        val m = minutes.coerceIn(1, 240)
        val ms = m * 60_000L
        val t = ActiveTimer(q?.id, q?.name ?: "Free focus", m, ms, true, nowMs() + ms, ms, false)
        commit(d.copy(timer = t))
        if (d.settings.notifications) {
            Notifier.scheduleOnce(app, Notifier.REQ_TIMER, t.endAt, "FOCUS SESSION COMPLETE", "Return to Ascend to claim your reward.")
        }
    }

    fun pauseTimer() {
        val d = _data.value
        val t = d.timer ?: return
        if (!t.running || t.finished) return
        val rem = maxOf(0L, t.endAt - nowMs())
        commit(d.copy(timer = t.copy(running = false, remainingMs = rem)))
        Notifier.cancel(app, Notifier.REQ_TIMER)
    }

    fun resumeTimer() {
        val d = _data.value
        val t = d.timer ?: return
        if (t.running || t.finished) return
        val end = nowMs() + t.remainingMs
        commit(d.copy(timer = t.copy(running = true, endAt = end)))
        if (d.settings.notifications) {
            Notifier.scheduleOnce(app, Notifier.REQ_TIMER, end, "FOCUS SESSION COMPLETE", "Return to Ascend to claim your reward.")
        }
    }

    /** Called by the UI every second while a timer exists; finishes it when time is up. */
    fun tickTimer() {
        val d = _data.value
        val t = d.timer ?: return
        if (!t.running || t.finished || nowMs() < t.endAt) return
        val today = todayEpoch()
        val session = TimerSession(newId(), t.questId, t.title, t.plannedMin, true, today, nowMs())
        val c = Ctx(
            d.copy(
                sessions = d.sessions + session,
                timer = t.copy(running = false, remainingMs = 0L, finished = true),
                activeDays = d.activeDays + today
            )
        )
        val ol = d.player.level
        c.finish(ol, RankEngine.rankFor(ol, d.settings))
        push(c)
        Notifier.cancel(app, Notifier.REQ_TIMER)
    }

    fun endTimerEarly() {
        val d = _data.value
        val t = d.timer ?: return
        val session = TimerSession(newId(), t.questId, t.title, t.plannedMin, false, todayEpoch(), nowMs())
        commit(d.copy(timer = null, sessions = d.sessions + session))
        Notifier.cancel(app, Notifier.REQ_TIMER)
    }

    fun dismissTimer() {
        val d = _data.value
        val t = d.timer ?: return
        if (t.finished) commit(d.copy(timer = null))
    }

    fun claimTimerReward() {
        val d = _data.value
        val t = d.timer ?: return
        if (!t.finished) return
        val qid = t.questId
        commit(d.copy(timer = null))
        if (qid != null) completeQuest(qid, true)
    }

    // ---------------------------------------------------------------- settings / profile

    fun updateSettings(f: (Settings) -> Settings) {
        val d = _data.value
        val ns = f(d.settings)
        var nd = d.copy(settings = ns)
        if (ns.xpBase != d.settings.xpBase || ns.xpExponent != d.settings.xpExponent ||
            ns.rankMinLevels != d.settings.rankMinLevels
        ) {
            nd = nd.copy(player = XpEngine.apply(nd.player, 0, ns).player)
        }
        commit(nd)
        if (ns.notifications != d.settings.notifications || ns.reminderHour != d.settings.reminderHour) {
            Notifier.applyDaily(app, ns.notifications, ns.reminderHour)
        }
    }

    fun setAvatar(style: Int, color: Long) {
        val d = _data.value
        commit(d.copy(player = d.player.copy(avatarStyle = style, avatarColor = color)))
    }

    // ---------------------------------------------------------------- data safety

    fun exportJson(): String = Json.toJson(_data.value).toString(2)

    fun resetProgression() {
        val d = _data.value
        val p = d.player
        val fresh = Player(
            name = p.name, username = p.username, goal = p.goal, areas = p.areas,
            dailyMinutes = p.dailyMinutes, avatarStyle = p.avatarStyle, avatarColor = p.avatarColor
        )
        commit(
            d.copy(
                player = fresh,
                quests = d.quests.map { it.copy(completions = 0, lastCompletedDay = -1L) },
                history = emptyList(), unlocked = emptyMap(),
                dailyChecked = emptySet(), dailyDay = todayEpoch(), dailyCompletedDay = -1L,
                dailySystemsCompleted = 0, activeDays = emptySet(), sessions = emptyList(),
                timer = null, dayLog = emptyList(), questsCreated = 0, consoleUsed = false,
                devMode = false, devSnapshot = null
            )
        )
        _events.value = emptyList()
        Notifier.cancel(app, Notifier.REQ_TIMER)
    }

    fun deleteAllData() {
        store.wipe()
        _data.value = AppData()
        _events.value = emptyList()
        Notifier.applyDaily(app, false, 18)
        Notifier.cancel(app, Notifier.REQ_TIMER)
    }

    // ---------------------------------------------------------------- console

    private fun markConsole() {
        val d = _data.value
        if (d.consoleUsed) return
        val c = Ctx(d.copy(consoleUsed = true))
        val ol = d.player.level
        c.finish(ol, RankEngine.rankFor(ol, d.settings))
        push(c)
    }

    fun runCommand(raw: String): List<String> {
        val input = raw.trim()
        if (input.isEmpty()) return emptyList()
        markConsole()
        val d = _data.value
        val today = todayEpoch()
        val p = d.player
        val rank = RankEngine.rankFor(p.level, d.settings)
        val need = XpEngine.required(p.level, d.settings)
        val tq = QuestEngine.todayQuests(d, today)
        val done = tq.count { QuestEngine.isDone(it, today) }
        val streak = StreakEngine.current(d.activeDays, today, d.settings.streakGrace)
        val cmd = input.lowercase().split(" ").first()
        return when (cmd) {
            "/help" -> listOf(
                "AVAILABLE COMMANDS",
                "/status  /quests  /stats  /history",
                "/level  /achievements  /profile",
                "The console is read-only. It never changes progression."
            )
            "/status" -> listOf(
                "PLAYER: ${p.name}",
                "LEVEL: ${p.level}   RANK: ${rank.name}",
                "XP: ${p.xp} / $need",
                "STREAK: $streak days",
                "TODAY: $done / ${tq.size} quests"
            )
            "/quests" -> if (tq.isEmpty()) listOf("No quests today.") else
                tq.mapIndexed { i, q ->
                    val mark = if (QuestEngine.isDone(q, today)) "[x]" else "[ ]"
                    "${i + 1}. $mark ${q.name}  (+${q.xp} XP)"
                }
            "/stats" -> d.settings.enabledStats.map { "${it.code}  ${p.stats[it] ?: 10}   ${it.label}" }
            "/history" -> {
                val last = d.history.takeLast(10).reversed()
                if (last.isEmpty()) listOf("History is empty.")
                else last.map { "${shortDayText(it.day)}  ${it.title}  ${if (it.xp >= 0) "+" else ""}${it.xp} XP" }
            }
            "/level" -> {
                val nr = RankEngine.nextRank(p.level, d.settings)
                listOf(
                    "LEVEL ${p.level}  (${p.xp} / $need XP)",
                    "NEXT LEVEL IN: ${need - p.xp} XP",
                    if (nr == null) "TOP RANK REACHED" else "NEXT RANK: ${nr.first.name} at level ${nr.second}"
                )
            }
            "/achievements" -> {
                val names = AchievementEngine.defs.filter { it.id in d.unlocked }.map { "* ${it.name}" }
                listOf("UNLOCKED: ${d.unlocked.size} / ${AchievementEngine.defs.size}") + names
            }
            "/profile" -> listOf(
                "NAME: ${p.name}", "USERNAME: ${p.username}", "GOAL: ${p.goal}",
                "TOTAL XP: ${p.totalXp}", "QUESTS COMPLETED: ${d.history.count { it.kind == HistoryKind.QUEST }}"
            )
            "/createquest", "/complete" ->
                listOf("Read-only console. Quests can only be created or completed from the Quests screen.")
            else -> listOf("Unknown command. Type /help.")
        }
    }

    // ---------------------------------------------------------------- developer / test mode

    fun unlockDev() {
        val d = _data.value
        if (d.devUnlocked) return
        commit(d.copy(devUnlocked = true))
        say("Developer tools unlocked (Settings > Developer).")
    }

    fun setDevMode(on: Boolean) {
        val d = _data.value
        if (!d.devUnlocked) return
        if (on && !d.devMode) {
            val snap = Json.toJson(d, includeSnapshot = false).toString()
            commit(d.copy(devMode = true, devSnapshot = snap))
            say("TEST MODE ON. Changes are discarded when you turn it off.")
        } else if (!on && d.devMode) {
            restoreSnapshot(false)
            say("Test mode off. Real progression restored.")
        }
    }

    fun lockDev() {
        if (_data.value.devMode) restoreSnapshot(false)
        val d = _data.value
        commit(d.copy(devUnlocked = false, devMode = false, devSnapshot = null))
    }

    private fun restoreSnapshot(stayInDev: Boolean) {
        val d = _data.value
        val snap = d.devSnapshot
        if (snap == null) {
            commit(d.copy(devMode = stayInDev))
            return
        }
        val old = Json.parse(snap) ?: return
        commit(
            old.copy(
                settings = d.settings, devUnlocked = d.devUnlocked, devMode = stayInDev,
                devSnapshot = if (stayInDev) snap else null
            )
        )
        _events.value = emptyList()
    }

    fun devResetTestData() {
        if (!_data.value.devMode) return
        restoreSnapshot(true)
        say("Test data reset.")
    }

    fun devAddXp(n: Int) {
        val d = _data.value
        if (!d.devMode) return
        val c = Ctx(d)
        val ol = d.player.level
        val orank = RankEngine.rankFor(ol, d.settings)
        c.give(n, emptyMap())
        c.finish(ol, orank)
        push(c)
    }

    fun devSetLevel(level: Int) {
        val d = _data.value
        if (!d.devMode) return
        commit(d.copy(player = d.player.copy(level = level.coerceIn(1, XpEngine.MAX_LEVEL), xp = 0)))
    }

    fun devModifyStat(stat: Stat, delta: Int) {
        val d = _data.value
        if (!d.devMode) return
        val ns = d.player.stats.toMutableMap()
        ns[stat] = ((ns[stat] ?: 10) + delta).coerceAtLeast(0)
        commit(d.copy(player = d.player.copy(stats = ns)))
    }

    fun devCreateTestQuest() {
        val d = _data.value
        if (!d.devMode) return
        val n = d.quests.count { it.name.startsWith("TEST QUEST") } + 1
        val q = Quest(
            newId(), "TEST QUEST $n", "Created in test mode.", "Productivity", Difficulty.D,
            QuestType.SIDE, 60, mapOf(Stat.DIS to 1), null, Repeat.NONE, 0, Verification.NONE, "", nowMs()
        )
        commit(d.copy(quests = d.quests + q))
    }

    fun devUnlockAchievement(id: String) {
        val d = _data.value
        if (!d.devMode || id in d.unlocked) return
        commit(d.copy(unlocked = d.unlocked + (id to nowMs())))
    }
}
