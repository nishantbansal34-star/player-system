package com.nishant.playersystem

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The same rules as the app (index.html), for what the widget can do on its own:
 * roll over to a new day and complete a quest.
 */
object Logic {
    val QUEST_IDS = listOf("train", "walk", "read", "deep", "water", "disc")
    private val BASE_XP = mapOf("train" to 30, "walk" to 20, "read" to 20, "deep" to 30, "water" to 15, "disc" to 25)

    fun need(level: Int) = 50 + 10 * level
    fun today(): String = LocalDate.now().toString()

    fun load(context: Context): JSONObject? {
        val raw = Store.read(context) ?: return null
        if (raw == "null" || raw.isBlank()) return null
        return try { JSONObject(raw) } catch (e: Exception) { null }
    }

    private fun save(context: Context, s: JSONObject) = Store.write(context, s.toString())

    fun rollover(context: Context) {
        val s = load(context) ?: return
        if (rolloverIn(s)) save(context, s)
    }

    /** Mirrors processDays() in the app. Returns true if anything changed. */
    fun rolloverIn(s: JSONObject): Boolean {
        val t = today()
        val last = s.optString("lastSeen", t)
        if (last == t) return false
        val start = s.optString("startDate", t)
        val hist = s.optJSONObject("history") ?: JSONObject()
        var d = try { LocalDate.parse(last) } catch (e: Exception) { LocalDate.now() }
        val end = LocalDate.parse(t)
        var missed = 0
        var guard = 0
        while (d.isBefore(end) && guard < 400) {
            val k = d.toString()
            val h = hist.optJSONObject(k)
            if (k > start && (h == null || !h.optBoolean("cleared"))) missed++
            d = d.plusDays(1)
            guard++
        }
        val trial = s.opt("jobTrial")
        if (trial is String && trial < t) {
            s.put("jobTrial", JSONObject.NULL)
            addNote(s, "shadow", "Trial failed", "The Job Change trial expired. Try again another day.", "red")
        }
        s.put("lastSeen", t)
        if (missed > 0) {
            s.put("streak", 0)
            if (s.optJSONObject("penalty") == null) s.put("penalty", JSONObject().put("since", t))
            val body = "You failed the daily quest" + (if (missed > 1) " on $missed days" else "") +
                ". Survive the penalty to remove Weakened."
            addNote(s, "penalty", "Penalty issued", body, "red")
        }
        addNote(s, "quest", "System notice", "Your daily quests have been generated.", "")
        return true
    }

    /** Mirrors completeQuest() in the app. */
    fun complete(context: Context, id: String?) {
        if (id == null || id !in QUEST_IDS) return
        val s = load(context) ?: return
        rolloverIn(s)
        val t = today()
        val hist = s.optJSONObject("history") ?: JSONObject().also { s.put("history", it) }
        val h = hist.optJSONObject(t) ?: JSONObject().put("q", JSONObject()).put("xp", 0).also { hist.put(t, it) }
        val q = h.optJSONObject("q") ?: JSONObject().also { h.put("q", it) }
        if (q.optBoolean(id)) { save(context, s); return }

        q.put(id, true)
        val counts = s.optJSONObject("counts") ?: JSONObject().also { s.put("counts", it) }
        counts.put(id, counts.optInt(id) + 1)
        s.put("gold", s.optInt("gold") + 10)
        gain(s, h, BASE_XP[id] ?: 20)

        val done = QUEST_IDS.count { q.optBoolean(it) }
        val clearAt = s.optInt("clearAt", 4)
        if (done >= clearAt && !h.optBoolean("cleared")) {
            h.put("cleared", true)
            h.put("loot", true)
            val streak = s.optInt("streak") + 1
            s.put("streak", streak)
            s.put("totalDays", s.optInt("totalDays") + 1)
            s.put("bestStreak", max(s.optInt("bestStreak"), streak))
            s.put("statPoints", s.optInt("statPoints") + 3)
            gain(s, h, 40)
            addNote(s, "quest", "Daily quest cleared", "+3 stat points. A loot box is waiting in Quests.", "")
        }
        if (done >= QUEST_IDS.size && !h.optBoolean("perfect")) {
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
        val mult = shadowMult * (if (s.optJSONObject("penalty") != null) 0.5 else 1.0)
        val amt = (base * mult).roundToInt()
        var level = s.optInt("level", 1)
        var xp = s.optInt("xp") + amt
        val from = level
        var points = s.optInt("statPoints")
        h.put("xp", h.optInt("xp") + amt)
        while (level < 100 && xp >= need(level)) { xp -= need(level); level++; points += 5 }
        if (level >= 100) xp = 0
        s.put("level", level); s.put("xp", xp); s.put("statPoints", points)
        if (level > from) {
            val prev = s.optJSONObject("pendingLevelUp")
            val start = prev?.optInt("from", from) ?: from
            s.put("pendingLevelUp", JSONObject().put("from", start).put("to", level))
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

    fun rankOf(level: Int): String = when {
        level >= 100 -> "MONARCH"
        level >= 80 -> "S-RANK"
        level >= 60 -> "A-RANK"
        level >= 40 -> "B-RANK"
        level >= 25 -> "C-RANK"
        level >= 10 -> "D-RANK"
        else -> "E-RANK"
    }
}
