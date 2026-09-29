package com.nishant.playersystem

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * The whole Player state lives in one JSON string, shared by the app and the widget.
 * A copy is also kept per day (last 14 days) so a bad day can be undone.
 */
object Store {
    private const val PREFS = "player_system"
    private const val KEY = "state"
    private const val KEEP = 14
    private val SAFE_NAME = Regex("^state-\\d{4}-\\d{2}-\\d{2}\\.json$")

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun write(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).commit()
        if (json != "null" && json.length > 20) snapshot(context, json)
    }

    private fun dir(context: Context): File = File(context.filesDir, "snapshots").also { it.mkdirs() }

    private fun snapshot(context: Context, json: String) {
        try {
            File(dir(context), "state-${LocalDate.now()}.json").writeText(json)
            val files = dir(context).listFiles()?.filter { SAFE_NAME.matches(it.name) }?.sortedByDescending { it.name } ?: return
            files.drop(KEEP).forEach { it.delete() }
        } catch (e: Exception) { }
    }

    fun listSnapshots(context: Context): List<String> =
        dir(context).listFiles()?.map { it.name }?.filter { SAFE_NAME.matches(it) }?.sortedDescending() ?: emptyList()

    fun readSnapshot(context: Context, name: String): String? {
        if (!SAFE_NAME.matches(name)) return null
        val f = File(dir(context), name)
        return if (f.exists()) f.readText() else null
    }
}
