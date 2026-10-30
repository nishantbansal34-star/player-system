package com.nishant.playersystem

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.Notification
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Morning "quests generated" and evening "you're behind" notifications. */
object Reminders {
    private const val CHANNEL = "daily_quests"
    const val EXTRA_KIND = "kind"

    fun schedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val am = pending(context, 201, "am")
        val pm = pending(context, 202, "pm")
        val urg = pending(context, 203, "urgent")
        val slot = pending(context, 205, "slot")
        alarm.cancel(am); alarm.cancel(pm); alarm.cancel(urg); alarm.cancel(slot)
        val s = Logic.load(context) ?: return
        Logic.nextUrgentAt(s)?.let { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it, urg) }
        if (s.optJSONObject("settings")?.optBoolean("remindOn", true) != false) {
            Logic.nextSlot(s)?.let { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.first, slot) }
        }
        val set = s.optJSONObject("settings")
        if (set != null && !set.optBoolean("remindOn", true)) return
        at(alarm, am, set?.optString("remindAM", "08:00") ?: "08:00")
        val pmTimes = mutableListOf(set?.optString("remindPM", "21:00") ?: "21:00")
        if (set?.optBoolean("nag", true) != false) pmTimes.addAll(listOf("13:00", "17:00", "22:30"))
        atEarliest(alarm, pm, pmTimes)
    }

    fun scheduleRunEnd(context: Context, at: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, 204, "run"))
    }

    fun scheduleSessionEnd(context: Context, at: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, 206, "session"))
    }

    fun cancelSessionEnd(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending(context, 206, "session"))
    }

    fun cancelRunEnd(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending(context, 204, "run"))
    }

    private fun pending(context: Context, code: Int, kind: String): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind).setAction("com.nishant.playersystem.REMIND_$kind")
        return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun atEarliest(alarm: AlarmManager, pi: PendingIntent, times: List<String>) {
        val now = LocalDateTime.now()
        var best: LocalDateTime? = null
        for (hhmm in times) {
            val time = try { LocalTime.parse(hhmm) } catch (e: Exception) { continue }
            var next = now.toLocalDate().atTime(time)
            if (!next.isAfter(now.plusSeconds(30))) next = next.plusDays(1)
            if (best == null || next.isBefore(best)) best = next
        }
        best?.let { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi) }
    }

    private fun at(alarm: AlarmManager, pi: PendingIntent, hhmm: String) {
        val time = try { LocalTime.parse(hhmm) } catch (e: Exception) { return }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now.plusSeconds(30))) next = next.plusDays(1)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    fun notify(context: Context, kind: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        Logic.rollover(context)
        val s = Logic.load(context) ?: return
        if (kind == "run") {
            if (s.optJSONObject("run") == null) return
            post(context, "dungeon", "Dungeons", 4, "[System] Dungeon cleared", "The boss has fallen. Return to the app to claim your rewards.")
            return
        }
        if (kind == "session") {
            val ss = s.optJSONObject("session") ?: return
            val name = if (ss.optString("id") == "train") "Training" else "Deep work"
            post(context, "session", "Quest sessions", 6, "[System] $name session complete", "Return to the app to claim your full XP.")
            return
        }
        if (kind == "slot") {
            if (s.optJSONObject("pause") != null) return
            val t = Logic.today(s)
            val slots = s.optJSONObject("settings")?.optJSONObject("slots")
            val q = s.optJSONObject("history")?.optJSONObject(t)?.optJSONObject("q")
            val plan = Logic.planFor(s, t) ?: return
            val nowHm = LocalTime.now()
            for (i in 0 until plan.length()) {
                val item = plan.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (q?.optBoolean(id) == true) continue
                val hhmm = slots?.optString(id, "") ?: ""
                val time = try { LocalTime.parse(hhmm) } catch (e: Exception) { continue }
                if (kotlin.math.abs(java.time.Duration.between(time, nowHm).toMinutes()) <= 15) {
                    val h = Logic.haste(s)
                    val anchor = item.optString("anchor", "")
                    val title = if (anchor.isNotEmpty()) "[System] $anchor → ${item.optString("name")}" else "[System] It's time: ${item.optString("name")}"
                    post(context, "plan", "Planned quests", 5, title,
                        item.optString("detail") + (if (h > 1) ". Tap to start for just 2 minutes · ×$h XP." else ". Tap to start for just 2 minutes."), id)
                    return
                }
            }
            return
        }
        if (kind == "urgent") {
            val u = Logic.urgentNow(s) ?: return
            post(context, "urgent", "Urgent quests", 3, "[System] URGENT QUEST", "${u.first}. You have ${u.second / 60000} minutes. Reward: +50 XP, +30 G.")
            return
        }
        if (s.optJSONObject("pause") != null) return
        val t = Logic.today(s)
        val ids = Logic.ids(Logic.planFor(s, t))
        if (ids.isEmpty()) return
        val q = s.optJSONObject("history")?.optJSONObject(t)?.optJSONObject("q")
        val done = ids.count { q?.optBoolean(it) == true }
        val clearAt = Logic.clearAt(s, ids.size)
        val penalty = s.optJSONObject("penalty") != null
        val title: String
        val body: String
        if (kind == "am") {
            title = "[System] Daily quests generated"
            body = "${ids.size} quests today. Clear $clearAt to stay safe." + (if (penalty) " A penalty is active: survive it to remove Weakened." else "")
        } else {
            if (done >= clearAt) return
            val left = clearAt - done
            val hr = LocalTime.now().hour
            val streak = s.optInt("streak")
            title = when {
                hr < 15 -> "[System] $left quest${if (left > 1) "s" else ""} left. Haste ends soon."
                hr < 20 -> "[System] Warning: $left quest${if (left > 1) "s" else ""} left"
                hr < 22 -> "[System] The gate boss is enraged"
                else -> "[System] FINAL WARNING: penalty incoming"
            }
            body = when {
                hr < 15 -> "Do one now while XP is still boosted. Tap here to start the easiest one for 2 minutes."
                hr < 20 -> "Complete $left more before the day resets, or face the Penalty Zone. Streak: $streak days."
                hr < 22 -> "$left quest${if (left > 1) "s" else ""} between you and a ${streak}-day streak. Use a minimum version if you're tired."
                else -> "Less than 2 hours. Do $left minimum version${if (left > 1) "s" else ""} now to save your ${streak}-day streak."
            }
        }
        post(context, CHANNEL, "Daily quests", if (kind == "am") 1 else 2, title, body, if (kind == "am") null else "easy")
    }

    private fun post(context: Context, channel: String, channelName: String, id: Int, title: String, body: String, start: String? = null) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val importance = if (channel == CHANNEL) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
        nm.createNotificationChannel(NotificationChannel(channel, channelName, importance))
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (start != null) launch.putExtra("start", start)
        val open = PendingIntent.getActivity(
            context, 30 + id, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setColor(0xFF3D7BFF.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try { Reminders.notify(context, intent.getStringExtra(Reminders.EXTRA_KIND) ?: "am") } catch (e: Exception) { }
        Reminders.schedule(context)
        SystemWidget.refresh(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.schedule(context)
        SystemWidget.refresh(context)
    }
}
