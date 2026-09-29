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
    fun listSnapshots(): String = JSONArray(Store.listSnapshots(activity)).toString()

    @JavascriptInterface
    fun readSnapshot(name: String): String = Store.readSnapshot(activity, name) ?: "null"

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
