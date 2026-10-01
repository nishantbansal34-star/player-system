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
        alarm.cancel(am); alarm.cancel(pm); alarm.cancel(urg)
        val s = Logic.load(context) ?: return
        Logic.nextUrgentAt(s)?.let { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it, urg) }
        val set = s.optJSONObject("settings")
        if (set != null && !set.optBoolean("remindOn", true)) return
        at(alarm, am, set?.optString("remindAM", "08:00") ?: "08:00")
        at(alarm, pm, set?.optString("remindPM", "21:00") ?: "21:00")
    }

    fun scheduleRunEnd(context: Context, at: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, 204, "run"))
    }

    fun cancelRunEnd(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending(context, 204, "run"))
    }

    private fun pending(context: Context, code: Int, kind: String): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind).setAction("com.nishant.playersystem.REMIND_$kind")
        return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
            title = "[System] Warning: $left quest${if (left > 1) "s" else ""} left"
            body = "Complete $left more before the day resets, or face the Penalty Zone. Streak: ${s.optInt("streak")} days."
        }
        post(context, CHANNEL, "Daily quests", if (kind == "am") 1 else 2, title, body)
    }

    private fun post(context: Context, channel: String, channelName: String, id: Int, title: String, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val importance = if (channel == CHANNEL) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
        nm.createNotificationChannel(NotificationChannel(channel, channelName, importance))
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
