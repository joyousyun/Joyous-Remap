package com.joyous.remap

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Idioma elegido en la app: "" = el del sistema, "en" o "es". */
object Lang {
    private const val KEY = "lang"

    fun get(c: Context): String =
        c.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun set(c: Context, v: String) =
        c.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().putString(KEY, v).apply()

    fun wrap(base: Context): Context {
        val code = get(base)
        if (code.isEmpty()) return base
        val cfg = Configuration(base.resources.configuration)
        val loc = Locale(code)
        Locale.setDefault(loc)
        cfg.setLocale(loc)
        return base.createConfigurationContext(cfg)
    }
}
