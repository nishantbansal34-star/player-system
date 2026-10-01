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
import java.time.LocalDateTime
import java.time.ZoneId

class SystemWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Logic.rollover(context)
        refresh(context)
    }

    override fun onEnabled(context: Context) {
        scheduleDayReset(context)
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

        private val ROWS = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3)
        private val CELLS = intArrayOf(R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4, R.id.cell5, R.id.cell6, R.id.cell7)
        private val ICONS = intArrayOf(R.id.icon0, R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5, R.id.icon6, R.id.icon7)
        private val NAMES = intArrayOf(R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5, R.id.name6, R.id.name7)
        private val DETAILS = intArrayOf(R.id.detail0, R.id.detail1, R.id.detail2, R.id.detail3, R.id.detail4, R.id.detail5, R.id.detail6, R.id.detail7)
        private val CHECKS = intArrayOf(R.id.check0, R.id.check1, R.id.check2, R.id.check3, R.id.check4, R.id.check5, R.id.check6, R.id.check7)
        private val ICON_RES = mapOf(
            "dumbbell" to R.drawable.ic_q_train, "shoe" to R.drawable.ic_q_walk, "book" to R.drawable.ic_q_read,
            "laptop" to R.drawable.ic_q_deep, "drop" to R.drawable.ic_q_water, "moon" to R.drawable.ic_q_disc,
            "star" to R.drawable.ic_q_star, "bolt" to R.drawable.ic_q_bolt, "heart" to R.drawable.ic_q_heart,
            "target" to R.drawable.ic_q_target
        )
        private val DEFAULTS = listOf(
            Triple("train", "Training", "dumbbell"), Triple("walk", "Steps", "shoe"), Triple("read", "Knowledge", "book"),
            Triple("deep", "Deep Work", "laptop"), Triple("water", "Hydration", "drop"), Triple("disc", "Discipline", "moon")
        )

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SystemWidget::class.java))
            if (ids.isNotEmpty()) {
                val views = build(context)
                for (id in ids) manager.updateAppWidget(id, views)
            }
            scheduleDayReset(context)
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun showCells(v: RemoteViews, count: Int) {
            for (i in CELLS.indices) v.setViewVisibility(CELLS[i], if (i < count) View.VISIBLE else View.INVISIBLE)
            for (r in ROWS.indices) v.setViewVisibility(ROWS[r], if (r * 2 < count) View.VISIBLE else View.GONE)
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
                showCells(v, DEFAULTS.size)
                for (i in DEFAULTS.indices) {
                    v.setImageViewResource(ICONS[i], ICON_RES[DEFAULTS[i].third] ?: R.drawable.ic_q_star)
                    v.setTextViewText(NAMES[i], DEFAULTS[i].second)
                    v.setTextViewText(DETAILS[i], "")
                    v.setOnClickPendingIntent(CELLS[i], open)
                }
                return v
            }

            Logic.rolloverIn(s)
            val today = Logic.today(s)
            val level = s.optInt("level", 1)
            val xp = s.optInt("xp")
            val need = Logic.need(level)
            val h = s.optJSONObject("history")?.optJSONObject(today)
            val q = h?.optJSONObject("q")
            val plan = Logic.planFor(s, today)
            val count = minOf(plan?.length() ?: 0, CELLS.size)
            val ids = Logic.ids(plan)
            val done = ids.count { q?.optBoolean(it) == true }
            val clearAt = Logic.clearAt(s, ids.size)

            v.setTextViewText(R.id.level, "LV $level")
            v.setTextViewText(R.id.rank, Logic.rankOf(level))
            v.setTextViewText(R.id.streak, s.optInt("streak").toString())
            v.setProgressBar(R.id.xp, 100, (xp * 100 / need).coerceIn(0, 100), false)
            v.setTextViewText(R.id.xptext, "$xp / $need XP  ·  $done/${ids.size} today")

            showCells(v, count)
            for (i in 0 until count) {
                val item = plan!!.optJSONObject(i) ?: continue
                val qid = item.optString("id")
                val isDone = q?.optBoolean(qid) == true
                v.setImageViewResource(ICONS[i], ICON_RES[item.optString("icon")] ?: R.drawable.ic_q_star)
                v.setTextViewText(NAMES[i], item.optString("name"))
                v.setTextViewText(DETAILS[i], item.optString("detail"))
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
            val paused = s.optJSONObject("pause") != null
            val cleared = h?.optBoolean("cleared") == true
            val lootWaiting = cleared && h?.optBoolean("lootOpened") != true
            val points = s.optInt("statPoints")
            val urgent = Logic.urgentNow(s)
            val footer = when {
                count == 0 -> "Open the app to load today's quests"
                urgent != null -> "URGENT: ${urgent.first} · ${urgent.second / 60000} min left"
                penalty -> "PENALTY ACTIVE · XP halved · open the app"
                s.optJSONObject("pendingLevelUp") != null -> "LEVEL UP! Open the app to see it"
                lootWaiting -> "DAILY QUEST CLEARED · loot box ready"
                done >= ids.size -> "PERFECT CLEAR · rest well, Player"
                cleared -> "CLEARED · ${ids.size - done} more for a perfect day"
                paused -> "PAUSE MODE · no penalties today"
                points > 0 -> "Clear ${clearAt - done} more · $points stat points to spend"
                else -> "Clear ${clearAt - done} more to avoid the penalty"
            }
            v.setTextViewText(R.id.footer, footer)
            v.setInt(R.id.footer, "setBackgroundResource", if (penalty || urgent != null) R.drawable.banner_red else R.drawable.banner_blue)
            return v
        }

        /** Wake the widget just after the Player's day resets so new quests appear on time. */
        fun scheduleDayReset(context: Context) {
            val s = Logic.load(context)
            val hour = Logic.dayStart(s)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SystemWidget::class.java).setAction(ACTION_TICK)
            val pi = PendingIntent.getBroadcast(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour, 0, 30)
            if (!next.isAfter(now)) next = next.plusDays(1)
            alarm.set(AlarmManager.RTC, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
        }
    }
}
