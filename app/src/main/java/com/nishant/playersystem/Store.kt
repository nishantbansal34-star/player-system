package com.nishant.playersystem

import android.content.Context

/** The whole Player state lives in one JSON string, shared by the app and the widget. */
object Store {
    private const val PREFS = "player_system"
    private const val KEY = "state"

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun write(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).commit()
    }
}
