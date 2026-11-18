package com.kira.ascend.model

import org.json.JSONArray
import org.json.JSONObject

/** Manual JSON (de)serialisation so the save file is human-readable and exportable. */
object Json {

    private fun statsOut(m: Map<Stat, Int>): JSONObject {
        val o = JSONObject()
        for ((k, v) in m) o.put(k.code, v)
        return o
    }

    private fun statsIn(o: JSONObject?): Map<Stat, Int> {
        val r = LinkedHashMap<Stat, Int>()
        if (o != null) {
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val s = Stat.fromCode(k)
                if (s != null) r[s] = o.optInt(k)
            }
        }
        return r
    }

    private fun strArr(l: Collection<String>): JSONArray {
        val a = JSONArray()
        for (s in l) a.put(s)
        return a
    }

    private fun strList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val r = ArrayList<String>()
        for (i in 0 until a.length()) r.add(a.getString(i))
        return r
    }

    private fun longList(a: JSONArray?): List<Long> {
        if (a == null) return emptyList()
        val r = ArrayList<Long>()
        for (i in 0 until a.length()) r.add(a.getLong(i))
        return r
    }

    private fun objs(a: JSONArray?): List<JSONObject> {
        if (a == null) return emptyList()
        val r = ArrayList<JSONObject>()
        for (i in 0 until a.length()) r.add(a.getJSONObject(i))
        return r
    }

    private inline fun <reified T : Enum<T>> en(s: String?, def: T): T =
        enumValues<T>().firstOrNull { it.name == s } ?: def

    private inline fun <reified T : Enum<T>> enOrNull(s: String?): T? =
        if (s.isNullOrEmpty()) null else enumValues<T>().firstOrNull { it.name == s }

    // ---------- OUT ----------

    private fun questOut(q: Quest): JSONObject = JSONObject().apply {
        put("id", q.id); put("name", q.name); put("description", q.description)
        put("category", q.category); put("difficulty", q.difficulty.name); put("type", q.type.name)
        put("xp", q.xp); put("rewards", statsOut(q.rewards))
        put("deadlineDay", q.deadlineDay ?: -1L)
        put("repeat", q.repeat.name); put("timerMinutes", q.timerMinutes)
        put("verification", q.verification.name); put("notes", q.notes)
        put("createdAt", q.createdAt); put("completions", q.completions)
        put("lastCompletedDay", q.lastCompletedDay)
    }

    private fun historyOut(h: HistoryEntry): JSONObject = JSONObject().apply {
        put("id", h.id); put("time", h.time); put("day", h.day); put("title", h.title)
        put("kind", h.kind.name); put("xp", h.xp); put("stats", statsOut(h.stats))
        put("category", h.category); put("difficulty", h.difficulty?.name ?: "")
        put("questType", h.questType?.name ?: ""); put("minutes", h.minutes)
    }

    private fun playerOut(p: Player): JSONObject = JSONObject().apply {
        put("name", p.name); put("username", p.username); put("goal", p.goal)
        put("areas", strArr(p.areas)); put("dailyMinutes", p.dailyMinutes)
        put("level", p.level); put("xp", p.xp); put("totalXp", p.totalXp)
        put("stats", statsOut(p.stats)); put("avatarStyle", p.avatarStyle)
        put("avatarColor", p.avatarColor)
    }

    private fun settingsOut(s: Settings): JSONObject = JSONObject().apply {
        put("theme", s.theme.name); put("mode", s.mode.name); put("accent", s.accent)
        put("anim", s.anim.name); put("sound2", s.sound); put("voice", s.voice); put("haptics", s.haptics)
        put("notifications", s.notifications); put("reminderHour", s.reminderHour)
        put("enabledStats", strArr(s.enabledStats.map { it.code }))
        put("xpBase", s.xpBase); put("xpExponent", s.xpExponent)
        val ranks = JSONArray(); for (l in s.rankMinLevels) ranks.put(l)
        put("rankMinLevels", ranks)
        put("categories", strArr(s.categories))
        put("streakGrace", s.streakGrace); put("highContrast", s.highContrast)
        put("fontScale", s.fontScale.name)
    }

    fun toJson(d: AppData, includeSnapshot: Boolean = true): JSONObject {
        val o = JSONObject()
        o.put("version", 1)
        o.put("onboarded", d.onboarded)
        o.put("player", playerOut(d.player))
        val qa = JSONArray(); d.quests.forEach { qa.put(questOut(it)) }; o.put("quests", qa)
        val ha = JSONArray(); d.history.forEach { ha.put(historyOut(it)) }; o.put("history", ha)
        val un = JSONObject(); for ((k, v) in d.unlocked) un.put(k, v); o.put("unlocked", un)
        val di = JSONArray()
        d.dailyItems.forEach { di.put(JSONObject().put("id", it.id).put("text", it.text)) }
        o.put("dailyItems", di)
        o.put("dailyDay", d.dailyDay)
        o.put("dailyChecked", strArr(d.dailyChecked))
        o.put("dailyCompletedDay", d.dailyCompletedDay)
        o.put("dailySystemsCompleted", d.dailySystemsCompleted)
        val ad = JSONArray(); d.activeDays.forEach { ad.put(it) }; o.put("activeDays", ad)
        o.put("settings", settingsOut(d.settings))
        val sa = JSONArray()
        d.sessions.forEach {
            sa.put(
                JSONObject().put("id", it.id).put("questId", it.questId ?: "").put("title", it.title)
                    .put("plannedMin", it.plannedMin).put("completed", it.completed)
                    .put("day", it.day).put("endedAt", it.endedAt)
            )
        }
        o.put("sessions", sa)
        val t = d.timer
        if (t != null) {
            o.put(
                "timer",
                JSONObject().put("questId", t.questId ?: "").put("title", t.title)
                    .put("plannedMin", t.plannedMin).put("durationMs", t.durationMs)
                    .put("running", t.running).put("endAt", t.endAt)
                    .put("remainingMs", t.remainingMs).put("finished", t.finished)
            )
        }
        val dl = JSONArray()
        d.dayLog.forEach {
            dl.put(JSONObject().put("day", it.day).put("due", it.due).put("done", it.done).put("level", it.level))
        }
        o.put("dayLog", dl)
        o.put("questsCreated", d.questsCreated)
        o.put("consoleUsed", d.consoleUsed)
        o.put("devUnlocked", d.devUnlocked)
        o.put("devMode", d.devMode)
        if (includeSnapshot && d.devSnapshot != null) o.put("devSnapshot", d.devSnapshot)
        return o
    }

    // ---------- IN ----------

    private fun questIn(o: JSONObject): Quest {
        val dl = o.optLong("deadlineDay", -1L)
        return Quest(
            id = o.optString("id", newId()),
            name = o.optString("name", "Quest"),
            description = o.optString("description", ""),
            category = o.optString("category", "Productivity"),
            difficulty = en(o.optString("difficulty"), Difficulty.E),
            type = en(o.optString("type"), QuestType.CUSTOM),
            xp = o.optInt("xp", 30),
            rewards = statsIn(o.optJSONObject("rewards")),
            deadlineDay = if (dl < 0) null else dl,
            repeat = en(o.optString("repeat"), Repeat.NONE),
            timerMinutes = o.optInt("timerMinutes", 0),
            verification = en(o.optString("verification"), Verification.NONE),
            notes = o.optString("notes", ""),
            createdAt = o.optLong("createdAt", 0L),
            completions = o.optInt("completions", 0),
            lastCompletedDay = o.optLong("lastCompletedDay", -1L)
        )
    }

    private fun historyIn(o: JSONObject): HistoryEntry = HistoryEntry(
        id = o.optString("id", newId()),
        time = o.optLong("time", 0L),
        day = o.optLong("day", 0L),
        title = o.optString("title", ""),
        kind = en(o.optString("kind"), HistoryKind.QUEST),
        xp = o.optInt("xp", 0),
        stats = statsIn(o.optJSONObject("stats")),
        category = o.optString("category", ""),
        difficulty = enOrNull<Difficulty>(o.optString("difficulty")),
        questType = enOrNull<QuestType>(o.optString("questType")),
        minutes = o.optInt("minutes", 0)
    )

    private fun playerIn(o: JSONObject?): Player {
        if (o == null) return Player()
        val st = statsIn(o.optJSONObject("stats"))
        val full = Stat.values().associateWith { st[it] ?: 10 }
        return Player(
            name = o.optString("name", ""),
            username = o.optString("username", ""),
            goal = o.optString("goal", ""),
            areas = strList(o.optJSONArray("areas")),
            dailyMinutes = o.optInt("dailyMinutes", 60),
            level = maxOf(1, o.optInt("level", 1)),
            xp = maxOf(0, o.optInt("xp", 0)),
            totalXp = o.optLong("totalXp", 0L),
            stats = full,
            avatarStyle = o.optInt("avatarStyle", 0),
            avatarColor = o.optLong("avatarColor", 0xFF7C8CFFL)
        )
    }

    private fun settingsIn(o: JSONObject?): Settings {
        if (o == null) return Settings()
        val def = Settings()
        val enabled = strList(o.optJSONArray("enabledStats")).mapNotNull { Stat.fromCode(it) }.toSet()
        val ranksArr = o.optJSONArray("rankMinLevels")
        val ranks = ArrayList<Int>()
        if (ranksArr != null) for (i in 0 until ranksArr.length()) ranks.add(ranksArr.getInt(i))
        val cats = strList(o.optJSONArray("categories"))
        return Settings(
            theme = en(o.optString("theme"), def.theme),
            mode = en(o.optString("mode"), def.mode),
            accent = o.optLong("accent", 0L),
            anim = en(o.optString("anim"), def.anim),
            sound = o.optBoolean("sound2", def.sound),
            voice = o.optBoolean("voice", def.voice),
            haptics = o.optBoolean("haptics", def.haptics),
            notifications = o.optBoolean("notifications", def.notifications),
            reminderHour = o.optInt("reminderHour", def.reminderHour).coerceIn(0, 23),
            enabledStats = if (enabled.isEmpty()) def.enabledStats else enabled,
            xpBase = o.optDouble("xpBase", def.xpBase),
            xpExponent = o.optDouble("xpExponent", def.xpExponent),
            rankMinLevels = if (ranks.size == 8) ranks else def.rankMinLevels,
            categories = if (cats.isEmpty()) def.categories else cats,
            streakGrace = o.optBoolean("streakGrace", def.streakGrace),
            highContrast = o.optBoolean("highContrast", def.highContrast),
            fontScale = en(o.optString("fontScale"), def.fontScale)
        )
    }

    fun fromJson(o: JSONObject): AppData {
        val unlocked = LinkedHashMap<String, Long>()
        val uo = o.optJSONObject("unlocked")
        if (uo != null) {
            val it = uo.keys()
            while (it.hasNext()) { val k = it.next(); unlocked[k] = uo.optLong(k) }
        }
        val items = objs(o.optJSONArray("dailyItems")).map {
            DailyItem(it.optString("id", newId()), it.optString("text", ""))
        }
        val sessions = objs(o.optJSONArray("sessions")).map {
            val qid = it.optString("questId", "")
            TimerSession(
                it.optString("id", newId()), if (qid.isEmpty()) null else qid,
                it.optString("title", ""), it.optInt("plannedMin", 0),
                it.optBoolean("completed", false), it.optLong("day", 0L), it.optLong("endedAt", 0L)
            )
        }
        val to = o.optJSONObject("timer")
        val timer = if (to == null) null else {
            val qid = to.optString("questId", "")
            ActiveTimer(
                if (qid.isEmpty()) null else qid, to.optString("title", ""),
                to.optInt("plannedMin", 25), to.optLong("durationMs", 0L),
                to.optBoolean("running", false), to.optLong("endAt", 0L),
                to.optLong("remainingMs", 0L), to.optBoolean("finished", false)
            )
        }
        val dayLog = objs(o.optJSONArray("dayLog")).map {
            DayLog(it.optLong("day"), it.optInt("due"), it.optInt("done"), it.optInt("level", 1))
        }
        return AppData(
            onboarded = o.optBoolean("onboarded", false),
            player = playerIn(o.optJSONObject("player")),
            quests = objs(o.optJSONArray("quests")).map { questIn(it) },
            history = objs(o.optJSONArray("history")).map { historyIn(it) },
            unlocked = unlocked,
            dailyItems = if (items.isEmpty() && !o.has("dailyItems")) defaultDailyItems() else items,
            dailyDay = o.optLong("dailyDay", -1L),
            dailyChecked = strList(o.optJSONArray("dailyChecked")).toSet(),
            dailyCompletedDay = o.optLong("dailyCompletedDay", -1L),
            dailySystemsCompleted = o.optInt("dailySystemsCompleted", 0),
            activeDays = longList(o.optJSONArray("activeDays")).toSet(),
            settings = settingsIn(o.optJSONObject("settings")),
            sessions = sessions,
            timer = timer,
            dayLog = dayLog,
            questsCreated = o.optInt("questsCreated", 0),
            consoleUsed = o.optBoolean("consoleUsed", false),
            devUnlocked = o.optBoolean("devUnlocked", false),
            devMode = o.optBoolean("devMode", false),
            devSnapshot = if (o.has("devSnapshot")) o.optString("devSnapshot") else null
        )
    }

    fun parse(text: String): AppData? = try {
        fromJson(JSONObject(text))
    } catch (e: Exception) {
        null
    }
}
