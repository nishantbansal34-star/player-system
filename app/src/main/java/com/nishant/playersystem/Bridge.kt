package com.nishant.playersystem

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONArray

/** Methods the web app calls as window.Android.* */
class Bridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun load(): String = Store.read(activity) ?: "null"

    @JavascriptInterface
    fun save(json: String) {
        Store.write(activity, json)
        SystemWidget.refresh(activity)
        Reminders.schedule(activity)
    }

    @JavascriptInterface
    fun versionCode(): Int = try {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (e: Exception) { 0 }

    @JavascriptInterface
    fun requestNotifications() {
        activity.runOnUiThread { activity.askNotificationPermission() }
    }

    @JavascriptInterface
    fun exportBackup(json: String) {
        activity.runOnUiThread { activity.startExport(json) }
    }

    @JavascriptInterface
    fun importBackup() {
        activity.runOnUiThread { activity.startImport() }
    }

    @JavascriptInterface
    fun share(text: String) {
        activity.runOnUiThread {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_TEXT, text)
            try { activity.startActivity(android.content.Intent.createChooser(send, "Invite a friend")) } catch (e: Exception) { }
        }
    }

    @JavascriptInterface
    fun scheduleRunEnd(at: Double) { Reminders.scheduleRunEnd(activity, at.toLong()) }

    @JavascriptInterface
    fun cancelRunEnd() { Reminders.cancelRunEnd(activity) }

    @JavascriptInterface
    fun scheduleSessionEnd(at: Double) { Reminders.scheduleSessionEnd(activity, at.toLong()) }

    @JavascriptInterface
    fun cancelSessionEnd() { Reminders.cancelSessionEnd(activity) }

    @JavascriptInterface
    fun listSnapshots(): String = JSONArray(Store.listSnapshots(activity)).toString()

    @JavascriptInterface
    fun readSnapshot(name: String): String = Store.readSnapshot(activity, name) ?: "null"

    /** Which store installed the app ("com.android.vending" for Google Play). Play builds hide the GitHub self-update. */
    @JavascriptInterface
    fun installer(): String = try {
        if (Build.VERSION.SDK_INT >= 30) activity.packageManager.getInstallSourceInfo(activity.packageName).installingPackageName ?: ""
        else @Suppress("DEPRECATION") (activity.packageManager.getInstallerPackageName(activity.packageName) ?: "")
    } catch (e: Exception) { "" }

    @JavascriptInterface
    fun healthStatus(): String = Tracking.healthStatus(activity)

    @JavascriptInterface
    fun healthRequest() { activity.runOnUiThread { activity.askHealthPermission() } }

    @JavascriptInterface
    fun healthRead(fromMs: Double) {
        Tracking.healthRead(activity, fromMs.toLong()) { json -> activity.callJs("window.__health && window.__health(" + org.json.JSONObject.quote(json) + ")") }
    }

    @JavascriptInterface
    fun usageStatus(): String = if (Tracking.usageGranted(activity)) "ok" else "permission needed"

    @JavascriptInterface
    fun openUsageSettings() {
        activity.runOnUiThread { try { activity.startActivity(Tracking.usageSettingsIntent(activity)) } catch (e: Exception) { } }
    }

    @JavascriptInterface
    fun socialMinutes(from: Double, to: Double): String = Tracking.socialMinutes(activity, from.toLong(), to.toLong())

    @JavascriptInterface
    fun vibrate(ms: Int) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(ms.coerceIn(5, 400).toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }
}
