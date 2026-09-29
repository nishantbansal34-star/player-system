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
        alarm.cancel(am); alarm.cancel(pm)
        val s = Logic.load(context) ?: return
        val set = s.optJSONObject("settings")
        if (set != null && !set.optBoolean("remindOn", true)) return
        at(alarm, am, set?.optString("remindAM", "08:00") ?: "08:00")
        at(alarm, pm, set?.optString("remindPM", "21:00") ?: "21:00")
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
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily quests", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setColor(0xFF3D7BFF.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(if (kind == "am") 1 else 2, n)
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
