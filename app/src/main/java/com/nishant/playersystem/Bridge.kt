package com.nishant.playersystem

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface

/** Methods the web app calls as window.Android.* */
class Bridge(private val context: Context) {

    @JavascriptInterface
    fun load(): String = Store.read(context) ?: "null"

    @JavascriptInterface
    fun save(json: String) {
        Store.write(context, json)
        SystemWidget.refresh(context)
    }

    @JavascriptInterface
    fun vibrate(ms: Int) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(ms.coerceIn(5, 400).toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }
}
