package com.nishant.playersystem

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

/** Phone tracking: Health Connect (steps, exercise, sleep) and screen time for social apps. All reads stay on the phone. */
object Tracking {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val HEALTH_PERMS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    private val SOCIAL = linkedMapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.facebook.katana" to "Facebook",
        "com.twitter.android" to "X",
        "com.snapchat.android" to "Snapchat",
        "com.reddit.frontpage" to "Reddit"
    )

    private fun sdk(ctx: Context): Int = try { HealthConnectClient.getSdkStatus(ctx) } catch (e: Throwable) { HealthConnectClient.SDK_UNAVAILABLE }

    /** "ok", "needs permission", "not installed" or "unavailable". Blocking check is fine: called rarely from settings. */
    fun healthStatus(ctx: Context): String {
        return when (sdk(ctx)) {
            HealthConnectClient.SDK_AVAILABLE -> try {
                val granted = kotlinx.coroutines.runBlocking { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions() }
                if (granted.containsAll(HEALTH_PERMS)) "connected" else "permission needed"
            } catch (e: Throwable) { "permission needed" }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update Health Connect"
            else -> "not available on this phone"
        }
    }

    fun permissionIntent(ctx: Context): Intent? {
        if (sdk(ctx) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            return Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata"))
        }
        if (sdk(ctx) != HealthConnectClient.SDK_AVAILABLE) return null
        return try { PermissionController.createRequestPermissionResultContract().createIntent(ctx, HEALTH_PERMS) } catch (e: Throwable) { null }
    }

    /** Reads today's numbers and returns them to JS through window.__health(json). */
    fun healthRead(ctx: Context, fromMs: Long, done: (String) -> Unit) {
        if (sdk(ctx) != HealthConnectClient.SDK_AVAILABLE) { done(JSONObject().put("error", "unavailable").toString()); return }
        scope.launch {
            val out = JSONObject()
            try {
                val client = HealthConnectClient.getOrCreate(ctx)
                val granted = client.permissionController.getGrantedPermissions()
                val now = Instant.now()
                val from = Instant.ofEpochMilli(fromMs)
                if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
                    val agg = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from, now)))
                    out.put("steps", agg[StepsRecord.COUNT_TOTAL] ?: 0L)
                }
                if (granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
                    val r = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(from, now)))
                    val mins = r.records.sumOf { (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()) / 60000L }
                    out.put("exerciseMin", mins)
                }
                if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                    // Last night: sleep that ended after 6 PM yesterday (relative to the day start).
                    val r = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from.minusSeconds(14 * 3600), now)))
                    val mins = r.records.filter { it.endTime.isAfter(from.minusSeconds(6 * 3600)) }
                        .sumOf { (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()) / 60000L }
                    out.put("sleepMin", mins)
                }
                if (!out.has("steps") && !out.has("exerciseMin") && !out.has("sleepMin")) out.put("error", "permission")
            } catch (e: Throwable) {
                out.put("error", e.javaClass.simpleName)
            }
            done(out.toString())
        }
    }

    fun usageGranted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageSettingsIntent(ctx: Context): Intent {
        val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        if (Build.VERSION.SDK_INT >= 29) i.data = android.net.Uri.parse("package:" + ctx.packageName)
        return i
    }

    /** Foreground minutes per social app between two times, from resume/pause events. */
    fun socialMinutes(ctx: Context, from: Long, to: Long): String {
        val out = JSONObject(); val apps = JSONObject(); var total = 0L
        if (!usageGranted(ctx)) return out.put("total", 0).put("apps", apps).put("error", "permission").toString()
        try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(from, to)
            val started = HashMap<String, Long>(); val ms = HashMap<String, Long>()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                if (!SOCIAL.containsKey(pkg)) continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> if (!started.containsKey(pkg)) started[pkg] = e.timeStamp
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> started.remove(pkg)?.let { ms[pkg] = (ms[pkg] ?: 0L) + (e.timeStamp - it) }
                }
            }
            for ((pkg, st) in started) ms[pkg] = (ms[pkg] ?: 0L) + (to - st)
            for ((pkg, v) in ms) { val m = (v + 30000L) / 60000L; if (m > 0) { apps.put(SOCIAL[pkg], m); total += m } }
        } catch (e: Throwable) { out.put("error", e.javaClass.simpleName) }
        return out.put("total", total).put("apps", apps).toString()
    }
}
