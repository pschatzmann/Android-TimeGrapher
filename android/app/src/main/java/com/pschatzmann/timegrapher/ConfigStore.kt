package com.pschatzmann.timegrapher

import android.content.Context
import org.json.JSONObject

/** Persists [GrapherConfig] as one JSON string in SharedPreferences. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("timegrapher", Context.MODE_PRIVATE)

    fun load(): GrapherConfig = try {
        val d = GrapherConfig()
        val j = JSONObject(prefs.getString("config", null) ?: "{}")
        GrapherConfig(
            bph = j.optInt("bph", d.bph),
            liftAngleDeg = j.optDouble("lift", d.liftAngleDeg),
            windowSec = j.optInt("window", d.windowSec),
            lowHz = j.optInt("low", d.lowHz),
            highHz = j.optInt("high", d.highHz),
            sensitivity = j.optDouble("sens", d.sensitivity),
            calibrationSPerDay = j.optDouble("cal", d.calibrationSPerDay),
            keepScreenOn = j.optBoolean("awake", d.keepScreenOn),
        )
    } catch (_: Exception) { GrapherConfig() }

    fun save(c: GrapherConfig) {
        val j = JSONObject()
            .put("bph", c.bph).put("lift", c.liftAngleDeg).put("window", c.windowSec)
            .put("low", c.lowHz).put("high", c.highHz).put("sens", c.sensitivity)
            .put("cal", c.calibrationSPerDay).put("awake", c.keepScreenOn)
        prefs.edit().putString("config", j.toString()).apply()
    }

    fun loadDark(): Boolean = prefs.getBoolean("dark", true)
    fun saveDark(v: Boolean) = prefs.edit().putBoolean("dark", v).apply()
}
