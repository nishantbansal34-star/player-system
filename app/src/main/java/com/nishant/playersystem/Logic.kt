package com.nishant.playersystem

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The same rules as the app (index.html), for what the widget and reminders do on their own:
 * roll over to a new day and complete a quest. Extras (achievements, weekly dungeon) are
 * settled by the app the next time it opens.
 */
object Logic {
    fun need(level: Int) = 50 + 10 * level

    fun dayStart(s: JSONObject?): Int = s?.optJSONObject("settings")?.optInt("dayStart", 0) ?: 0

    /** The Player's current day, shifted by the "day resets at" setting. */
    fun today(s: JSONObject?): String = LocalDateTime.now().minusHours(dayStart(s).toLong()).toLocalDate().toString()

    fun load(context: Context): JSONObject? {
        val raw = Store.read(context) ?: return null
        if (raw == "null" || raw.isBlank()) return null
        return try { JSONObject(raw) } catch (e: Exception) { null }
    }

    private fun save(context: Context, s: JSONObject) = Store.write(context, s.toString())

    /** Today's quest list as the app planned it: [{id,name,detail,xp,icon}]. Index 0 of plan = Sunday. */
    fun planFor(s: JSONObject, day: String): JSONArray? {
        val dow = LocalDate.parse(day).dayOfWeek.value % 7
        return s.optJSONArray("plan")?.optJSONArray(dow)
    }

    fun ids(plan: JSONArray?): List<String> {
        val out = ArrayList<String>()
        if (plan == null) return out
        for (i in 0 until plan.length()) plan.optJSONObject(i)?.optString("id")?.let { if (it.isNotEmpty()) out.add(it) }
        return out
    }

    fun clearAt(s: JSONObject, n: Int): Int {
        val c = s.optInt("clearAt", 0)
        return if (c > 0) c else max(1, ceil(n * 2.0 / 3.0).toInt())
    }

    fun rollover(context: Context) {
        val s = load(context) ?: return
        if (rolloverIn(s)) save(context, s)
    }

    private fun rec(s: JSONObject, day: String): JSONObject {
        val hist = s.optJSONObject("history") ?: JSONObject().also { s.put("history", it) }
        return hist.optJSONObject(day) ?: JSONObject().put("q", JSONObject()).put("xp", 0).also { hist.put(day, it) }
    }

    /** Mirrors processDays() in the app. Returns true if anything changed. */
    fun rolloverIn(s: JSONObject): Boolean {
        val t = today(s)
        val last = s.optString("lastSeen", t)
        if (last == t) return false
        val start = s.optString("startDate", t)
        val hist = s.optJSONObject("history") ?: JSONObject()
        val pause = s.optJSONObject("pause")
        val pauseSince = pause?.optString("since")
        val prevStreak = s.optInt("streak")
        var d = try { LocalDate.parse(last) } catch (e: Exception) { LocalDate.parse(t) }
        val end = LocalDate.parse(t)
        var missed = 0
        var shielded = 0
        var lastMiss: String? = null
        var guard = 0
        while (d.isBefore(end) && guard < 400) {
            val k = d.toString()
            val h = hist.optJSONObject(k)
            if (k > start && (h == null || !h.optBoolean("cleared"))) {
                if ((pauseSince != null && k >= pauseSince) || (h != null && h.optBoolean("paused"))) {
                    rec(s, k).put("paused", true)
                } else if (s.optInt("freezes") > 0) {
                    s.put("freezes", s.optInt("freezes") - 1)
                    rec(s, k).put("frozen", true)
                    shielded++
                } else {
                    missed++
                    lastMiss = k
                }
            }
            d = d.plusDays(1)
            guard++
        }
        val trial = s.opt("jobTrial")
        if (trial is String && trial < t) {
            s.put("jobTrial", JSONObject.NULL)
            addNote(s, "shadow", "Trial failed", "The Job Change trial expired. Try again another day.", "red")
        }
        s.put("lastSeen", t)
        if (shielded > 0) {
            addNote(s, "loot", "Streak shield used",
                "$shielded missed day" + (if (shielded > 1) "s were" else " was") + " absorbed. Your streak is safe.", "gold")
        }
        if (missed > 0) {
            val existing = s.optJSONObject("penalty")
            if (existing != null) {
                existing.put("forDay", lastMiss); existing.put("multi", true)
            } else {
                s.put("penalty", JSONObject().put("since", t).put("forDay", lastMiss).put("prevStreak", prevStreak).put("multi", missed > 1))
            }
            s.put("streak", 0)
            val body = "You failed the daily quest" + (if (missed > 1) " on $missed days" else "") +
                ". Survive the penalty to remove Weakened." + (if (missed == 1) " Forgot to log? You can log yesterday until noon." else "")
            addNote(s, "penalty", "Penalty issued", body, "red")
        }
        addNote(s, "quest", "System notice",
            if (pause != null) "Pause mode is on. No penalties while you're away." else "Your daily quests have been generated.", "")
        return true
    }

    /** Mirrors completeQuest() in the app for today. */
    fun complete(context: Context, id: String?) {
        if (id.isNullOrEmpty()) return
        val s = load(context) ?: return
        rolloverIn(s)
        val t = today(s)
        val plan = planFor(s, t)
        val ids = ids(plan)
        if (id !in ids) { save(context, s); return }
        var baseXp = 20
        for (i in 0 until (plan?.length() ?: 0)) {
            val item = plan!!.optJSONObject(i)
            if (item != null && item.optString("id") == id) baseXp = item.optInt("xp", 20)
        }
        val h = rec(s, t)
        val q = h.optJSONObject("q") ?: JSONObject().also { h.put("q", it) }
        if (q.optBoolean(id)) { save(context, s); return }

        q.put(id, true)
        val fx = h.optJSONObject("fx")
        if (fx != null && fx.optDouble("bloodlust", 0.0) > 0 && !fx.optBoolean("bloodUsed")) {
            baseXp = (baseXp * fx.optDouble("bloodlust", 1.0)).roundToInt()
            fx.put("bloodUsed", true)
        }
        val mpMax = s.optInt("mpMax", 100)
        s.put("mp", minOf(mpMax, s.optInt("mp") + s.optInt("mpq", 10)))
        val counts = s.optJSONObject("counts") ?: JSONObject().also { s.put("counts", it) }
        counts.put(id, counts.optInt(id) + 1)
        s.put("gold", s.optInt("gold") + 10)
        gain(s, h, baseXp)

        val done = ids.count { q.optBoolean(it) }
        if (done >= clearAt(s, ids.size) && !h.optBoolean("cleared")) {
            h.put("cleared", true)
            h.put("loot", true)
            s.put("mp", s.optInt("mpMax", 100))
            val streak = s.optInt("streak") + 1
            s.put("streak", streak)
            s.put("totalDays", s.optInt("totalDays") + 1)
            s.put("bestStreak", max(s.optInt("bestStreak"), streak))
            s.put("statPoints", s.optInt("statPoints") + 3)
            if (streak % 7 == 0 && s.optInt("freezes") < s.optInt("maxShields", 2)) {
                s.put("freezes", s.optInt("freezes") + 1)
                addNote(s, "loot", "Streak shield earned", "A shield will absorb one missed day. You hold ${s.optInt("freezes")}.", "gold")
            }
            gain(s, h, 40)
            addNote(s, "quest", "Daily quest cleared", "+3 stat points. A loot box is waiting in Quests.", "")
        }
        if (done >= ids.size && ids.isNotEmpty() && !h.optBoolean("perfect")) {
            h.put("perfect", true)
            s.put("perfectDays", s.optInt("perfectDays") + 1)
            gain(s, h, 30)
            s.put("gold", s.optInt("gold") + 20)
            val trial = s.opt("jobTrial")
            if (trial is String && trial == t) {
                s.put("jobTrial", JSONObject.NULL)
                s.put("job", "Necromancer")
                addNote(s, "shadow", "Job change complete", "You are now a Necromancer. ARISE is unlocked.", "violet")
            }
        }
        save(context, s)
    }

    private fun gain(s: JSONObject, h: JSONObject, base: Int) {
        val shadowMult = s.optDouble("smult", 1.0).let { if (it.isNaN()) 1.0 else it }
        val authority = h.optJSONObject("fx")?.optBoolean("authority") == true
        val mult = shadowMult * (if (s.optJSONObject("penalty") != null && !authority) 0.5 else 1.0)
        val amt = (base * mult).roundToInt()
        var level = s.optInt("level", 1)
        var xp = s.optInt("xp") + amt
        val from = level
        var points = s.optInt("statPoints")
        h.put("xp", h.optInt("xp") + amt)
        s.put("totalXp", s.optLong("totalXp") + amt)
        var guard = 0
        while (xp >= need(level) && guard < 1000) { xp -= need(level); level++; points += 5; guard++ }
        s.put("level", level); s.put("xp", xp); s.put("statPoints", points)
        if (level >= 100 && s.optString("job") != "Shadow Monarch") {
            s.put("job", "Shadow Monarch")
            addNote(s, "level", "Job changed", "You have become the Shadow Monarch.", "violet")
        }
        if (level > from) {
            val prev = s.optJSONObject("pendingLevelUp")
            val startLv = prev?.optInt("from", from) ?: from
            s.put("pendingLevelUp", JSONObject().put("from", startLv).put("to", level))
            addNote(s, "level", "Level up", "You reached Level $level. +${5 * (level - from)} stat points.", "")
        }
    }

    private fun addNote(s: JSONObject, type: String, title: String, body: String, kind: String) {
        val old = s.optJSONArray("notes") ?: JSONArray()
        val arr = JSONArray()
        arr.put(JSONObject().put("t", System.currentTimeMillis()).put("type", type).put("title", title).put("body", body).put("kind", kind))
        for (i in 0 until minOf(old.length(), 49)) arr.put(old.get(i))
        s.put("notes", arr)
    }

    /** Today's urgent quest if it is live right now: (text, millis left). */
    fun urgentNow(s: JSONObject): Pair<String, Long>? {
        if (s.optJSONObject("pause") != null) return null
        val u = s.optJSONObject("urgent")?.optJSONObject(today(s)) ?: return null
        if (u.optBoolean("done") || u.optBoolean("missed")) return null
        val at = u.optLong("at"); val dur = u.optLong("dur", 3600000L); val now = System.currentTimeMillis()
        return if (now >= at - 120000 && now < at + dur) Pair(u.optString("text"), at + dur - now) else null
    }

    /** The next urgent quest start time in the future, or null. */
    fun nextUrgentAt(s: JSONObject): Long? {
        val urg = s.optJSONObject("urgent") ?: return null
        val now = System.currentTimeMillis()
        var best: Long? = null
        val keys = urg.keys()
        while (keys.hasNext()) {
            val u = urg.optJSONObject(keys.next()) ?: continue
            if (u.optBoolean("done") || u.optBoolean("missed")) continue
            val at = u.optLong("at")
            if (at > now && (best == null || at < best)) best = at
        }
        return best
    }

    fun rankOf(level: Int): String = when {
        level >= 200 -> "SSS-RANK"
        level >= 150 -> "S+ RANK"
        level >= 100 -> "SS-RANK"
        level >= 80 -> "S-RANK"
        level >= 60 -> "A-RANK"
        level >= 40 -> "B-RANK"
        level >= 25 -> "C-RANK"
        level >= 10 -> "D-RANK"
        else -> "E-RANK"
    }
}
