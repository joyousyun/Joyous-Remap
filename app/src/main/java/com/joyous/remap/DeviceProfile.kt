package com.joyous.remap

import android.content.Context
import android.os.Build

/** Modelos Nothing conocidos con su lateral y altura de la tecla Essential (provisionales, ajustables). */
enum class NothingModel(
    val id: String,
    val title: String,
    val left: Boolean,
    val y: Int,
    val cams: Int,
    val matrix: Boolean
) {
    P3A("3a", "Phone (3a)", false, 59, 3, false),
    P3A_PRO("3a_pro", "Phone (3a) Pro", false, 59, 3, false),
    P3A_LITE("3a_lite", "Phone (3a) Lite", false, 59, 2, false),
    P3("3", "Phone (3)", false, 59, 3, true),
    P4A("4a", "Phone (4a)", true, 25, 2, false),
    P4A_PRO("4a_pro", "Phone (4a) Pro", true, 29, 3, false),
    // Lateral derecho, justo debajo del botón de encendido (altura provisional).
    CMF2PRO("cmf_2_pro", "CMF Phone 2 Pro", false, 47, 3, false);

    companion object {
        fun from(id: String?) = values().firstOrNull { it.id == id }
    }
}

object DeviceProfile {

    /** Detección por Build.MANUFACTURER, Build.MODEL y Build.DEVICE. No necesita permisos. */
    fun detect(): NothingModel? {
        val norm = { s: String -> s.lowercase().replace(Regex("[^a-z0-9]"), "") }
        val maker = norm(Build.MANUFACTURER)
        if (maker != "nothing" && maker != "cmf" && !norm(Build.MODEL).contains("cmf")) return null
        val m = norm(Build.MODEL)
        val dev = norm(Build.DEVICE)
        return when {
            m.contains("cmf") && m.contains("2pro") || m == "a001" -> NothingModel.CMF2PRO
            m.contains("4apro") -> NothingModel.P4A_PRO
            m.contains("4a") -> NothingModel.P4A
            m.contains("3alite") || m == "a001t" -> NothingModel.P3A_LITE
            m.contains("3apro") || m == "a059p" -> NothingModel.P3A_PRO
            m.contains("3a") || m == "a059" -> NothingModel.P3A
            m.contains("phone3") || m == "a024" || dev.contains("metroid") -> NothingModel.P3
            dev.contains("asteroids") -> NothingModel.P3A
            else -> null
        }
    }

    private fun sp(c: Context) = Prefs.sp(c)

    /** Modelo elegido a mano, o null si es automático. */
    fun manual(c: Context): NothingModel? = NothingModel.from(sp(c).getString("model_sel", null))
    fun current(c: Context): NothingModel? = manual(c) ?: detect()

    fun isCustom(c: Context) = sp(c).getBoolean("key_custom", false)
    fun markCustom(c: Context) = sp(c).edit().putBoolean("key_custom", true).apply()

    /** Aplica lateral y altura del perfil (acción explícita del usuario). */
    fun apply(c: Context, m: NothingModel) {
        sp(c).edit()
            .putBoolean("key_left", m.left)
            .putInt("key_y", m.y)
            .putInt("panel_y", m.y)
            .putString("model_applied", m.id)
            .putBoolean("key_custom", false)
            .apply()
    }

    fun setManual(c: Context, m: NothingModel?) {
        sp(c).edit().putString("model_sel", m?.id ?: "").apply()
        (m ?: detect())?.let { apply(c, it) }
    }

    /** Solo en la variante Testing: configura sola la tecla la primera vez y al cambiar de modelo. */
    fun autoApply(c: Context) {
        if (!BuildConfig.TESTING) return
        val m = current(c) ?: return
        val s = sp(c)
        val applied = s.getString("model_applied", "") ?: ""
        if (applied == m.id) return
        val hadSettings = s.contains("key_y") || s.contains("key_left")
        if (applied.isEmpty() && hadSettings) {
            // Ya había ajustes anteriores: se respetan como personalizados.
            s.edit().putString("model_applied", m.id).putBoolean("key_custom", true).apply()
        } else if (isCustom(c)) {
            s.edit().putString("model_applied", m.id).apply()
        } else {
            apply(c, m)
        }
    }
}
