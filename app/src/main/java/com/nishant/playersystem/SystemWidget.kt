package com.nishant.playersystem

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SystemWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Logic.rollover(context)
        refresh(context)
    }

    override fun onEnabled(context: Context) {
        scheduleMidnight(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_DONE -> {
                Logic.complete(context, intent.getStringExtra(EXTRA_ID))
                refresh(context)
            }
            ACTION_TICK -> {
                Logic.rollover(context)
                refresh(context)
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.nishant.playersystem.QUEST_DONE"
        const val ACTION_TICK = "com.nishant.playersystem.MIDNIGHT"
        const val EXTRA_ID = "quest_id"

        private val CELLS = intArrayOf(R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4, R.id.cell5)
        private val ICONS = intArrayOf(R.id.icon0, R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5)
        private val NAMES = intArrayOf(R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5)
        private val DETAILS = intArrayOf(R.id.detail0, R.id.detail1, R.id.detail2, R.id.detail3, R.id.detail4, R.id.detail5)
        private val CHECKS = intArrayOf(R.id.check0, R.id.check1, R.id.check2, R.id.check3, R.id.check4, R.id.check5)
        private val QUEST_ICON = mapOf(
            "train" to R.drawable.ic_q_train, "walk" to R.drawable.ic_q_walk, "read" to R.drawable.ic_q_read,
            "deep" to R.drawable.ic_q_deep, "water" to R.drawable.ic_q_water, "disc" to R.drawable.ic_q_disc
        )
        private val FALLBACK_NAMES = mapOf(
            "train" to "Physical Training", "walk" to "Steps", "read" to "Knowledge",
            "deep" to "Deep Work", "water" to "Hydration", "disc" to "Discipline"
        )

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SystemWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            for (id in ids) manager.updateAppWidget(id, views)
            scheduleMidnight(context)
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun build(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget)
            val open = openApp(context)
            v.setOnClickPendingIntent(R.id.header, open)
            v.setOnClickPendingIntent(R.id.footer, open)

            val s = Logic.load(context)
            if (s == null) {
                v.setTextViewText(R.id.level, "LV 1")
                v.setTextViewText(R.id.xptext, "The System is waiting for you.")
                v.setTextViewText(R.id.footer, "Open the app and accept to begin")
                for (i in 0 until 6) {
                    val qid = Logic.QUEST_IDS[i]
                    v.setImageViewResource(ICONS[i], QUEST_ICON[qid] ?: R.drawable.ic_q_train)
                    v.setTextViewText(NAMES[i], FALLBACK_NAMES[qid])
                    v.setTextViewText(DETAILS[i], "")
                    v.setOnClickPendingIntent(CELLS[i], open)
                }
                return v
            }

            Logic.rolloverIn(s)
            val today = Logic.today()
            val level = s.optInt("level", 1)
            val xp = s.optInt("xp")
            val need = Logic.need(level)
            val h = s.optJSONObject("history")?.optJSONObject(today)
            val q = h?.optJSONObject("q")
            val done = Logic.QUEST_IDS.count { q?.optBoolean(it) == true }
            val clearAt = s.optInt("clearAt", 4)

            v.setTextViewText(R.id.level, "LV $level")
            v.setTextViewText(R.id.rank, Logic.rankOf(level))
            v.setTextViewText(R.id.streak, s.optInt("streak").toString())
            v.setProgressBar(R.id.xp, 100, if (level >= 100) 100 else (xp * 100 / need), false)
            v.setTextViewText(R.id.xptext, (if (level >= 100) "MAX LEVEL" else "$xp / $need XP") + "  ·  $done/6 today")

            // Today's plan (written by the app for each weekday; index 0 = Sunday).
            val dow = LocalDate.now().dayOfWeek.value % 7
            val plan = s.optJSONArray("plan")?.optJSONArray(dow)
            for (i in 0 until 6) {
                val item = plan?.optJSONObject(i)
                val qid = item?.optString("id") ?: Logic.QUEST_IDS[i]
                val isDone = q?.optBoolean(qid) == true
                v.setImageViewResource(ICONS[i], QUEST_ICON[qid] ?: R.drawable.ic_q_train)
                v.setTextViewText(NAMES[i], item?.optString("name") ?: FALLBACK_NAMES[qid] ?: qid)
                v.setTextViewText(DETAILS[i], item?.optString("detail") ?: "")
                v.setImageViewResource(CHECKS[i], if (isDone) R.drawable.ic_check_on else R.drawable.ic_check_off)
                v.setInt(CELLS[i], "setBackgroundResource", if (isDone) R.drawable.cell_done_bg else R.drawable.cell_bg)
                val tap = Intent(context, SystemWidget::class.java)
                    .setAction(ACTION_DONE)
                    .putExtra(EXTRA_ID, qid)
                    .setData(Uri.parse("playersystem://quest/$qid"))
                v.setOnClickPendingIntent(
                    CELLS[i],
                    PendingIntent.getBroadcast(context, 100 + i, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
            }

            val penalty = s.optJSONObject("penalty") != null
            val cleared = h?.optBoolean("cleared") == true
            val lootWaiting = cleared && h?.optBoolean("lootOpened") != true
            val points = s.optInt("statPoints")
            val footer = when {
                penalty -> "PENALTY ACTIVE · XP halved · open the app"
                s.optJSONObject("pendingLevelUp") != null -> "LEVEL UP! Open the app to see it"
                lootWaiting -> "DAILY QUEST CLEARED · loot box ready"
                done >= 6 -> "PERFECT CLEAR · rest well, Player"
                cleared -> "CLEARED · ${6 - done} more for a perfect day"
                points > 0 -> "Clear ${clearAt - done} more · $points stat points to spend"
                else -> "Clear ${clearAt - done} more to avoid the penalty"
            }
            v.setTextViewText(R.id.footer, footer)
            v.setInt(R.id.footer, "setBackgroundResource", if (penalty) R.drawable.banner_red else R.drawable.banner_blue)
            v.setViewVisibility(R.id.footer, View.VISIBLE)
            return v
        }

        /** Wake the widget just after midnight so a new day's quests appear on time. */
        fun scheduleMidnight(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SystemWidget::class.java).setAction(ACTION_TICK)
            val pi = PendingIntent.getBroadcast(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val next = LocalDate.now().plusDays(1).atTime(LocalTime.of(0, 0, 30))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarm.set(AlarmManager.RTC, next, pi)
        }
    }
}
