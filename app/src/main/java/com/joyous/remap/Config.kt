package com.joyous.remap

import android.content.Context

enum class Gesture(val key: String, val label: Int) {
    SINGLE("single", R.string.g_single),
    DOUBLE("double", R.string.g_double),
    LONG("long", R.string.g_long),
    TRIPLE("triple", R.string.g_triple)
}

enum class Anim(val id: String, val label: Int) {
    SPREAD("spread", R.string.anim_spread),
    SWEEP("sweep", R.string.anim_sweep),
    PULSE("pulse", R.string.anim_pulse),
    FLASH("flash", R.string.anim_flash);

    companion object {
        fun from(id: String?): Anim = values().firstOrNull { it.id == id } ?: SPREAD
    }
}

enum class PanelShape(val id: String, val label: Int) {
    RECT("rect", R.string.shape_rect),
    SQUARE("square", R.string.shape_square);

    companion object {
        fun from(id: String?): PanelShape = values().firstOrNull { it.id == id } ?: RECT
    }
}

enum class PanelFx(val id: String, val label: Int) {
    SLIDE("slide", R.string.fx_slide),
    FADE("fade", R.string.fx_fade),
    POP("pop", R.string.fx_pop),
    DROP("drop", R.string.fx_drop);

    companion object {
        fun from(id: String?): PanelFx = values().firstOrNull { it.id == id } ?: SLIDE
    }
}

enum class PanelStyle(val id: String, val label: Int) {
    FRAME("frame", R.string.st_frame),
    PILL("pill", R.string.st_pill),
    SMOOTH("smooth", R.string.st_smooth),
    BARS("bars", R.string.st_bars),
    DOTS("dots", R.string.st_dots),
    STAIRS("stairs", R.string.st_stairs),
    LINE("line", R.string.st_line);

    companion object {
        fun from(id: String?): PanelStyle = values().firstOrNull { it.id == id } ?: FRAME
    }
}

enum class BarFx(val id: String, val label: Int) {
    CASCADE("cascade", R.string.bf_cascade),
    FADE("fade", R.string.bf_fade),
    GROW("grow", R.string.bf_grow);

    companion object {
        fun from(id: String?): BarFx = values().firstOrNull { it.id == id } ?: CASCADE
    }
}

enum class Act(val id: String, val label: Int) {
    NONE("none", R.string.act_none),
    APP("app", R.string.act_app),
    CAMERA("camera", R.string.act_camera),
    TORCH("torch", R.string.act_torch),
    QR("qr", R.string.act_qr),
    WALLET("wallet", R.string.act_wallet),
    RECORDER("recorder", R.string.act_recorder),
    SOUND("sound", R.string.act_sound),
    MUSIC("music", R.string.act_music),
    MUSIC_NEXT("music_next", R.string.act_music_next),
    ROTATION("rotation", R.string.act_rotation),
    DND("dnd", R.string.act_dnd),
    SCREENSHOT("screenshot", R.string.act_screenshot),
    WIFI_BT("wifi_bt", R.string.act_wifi_bt),
    WIFI_SHARE("wifi_share", R.string.act_wifi_share),
    ASSISTANT("assistant", R.string.act_assistant);

    companion object {
        fun from(id: String?): Act = values().firstOrNull { it.id == id } ?: NONE
    }
}

object Prefs {
    const val FILE = "prefs"
    const val KEY_LEVEL = "torch_level"
    const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()

    val COLORS = intArrayOf(
        0xFFFFFFFF.toInt(), // blanco
        0xFF00FF41.toInt(), // verde
        0xFF00E5FF.toInt(), // cian
        0xFF2979FF.toInt(), // azul
        0xFFD500F9.toInt(), // violeta
        0xFFFFEB3B.toInt(), // amarillo
        0xFFFF9100.toInt(), // naranja
        0xFFD71921.toInt()  // rojo
    )

    /** Colores del arcoíris para el borde (valor 0 en el selector). */
    val RAINBOW = intArrayOf(
        0xFFFF3B30.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
        0xFF00E5FF.toInt(), 0xFF2979FF.toInt(), 0xFFD500F9.toInt(), 0xFFFF3B30.toInt()
    )

    fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun action(c: Context, g: Gesture): Act {
        val sp = sp(c)
        val s = sp.getString("act_${g.key}", null)
        if (s != null) return Act.from(s)
        if (g == Gesture.SINGLE) { // ajustes de versiones anteriores
            return when (sp.getString("mode", null)) {
                "camera" -> Act.CAMERA
                "torch" -> Act.TORCH
                "app" -> if (sp.getString("package", null) != null) Act.APP else Act.NONE
                else -> Act.NONE
            }
        }
        return Act.NONE
    }

    fun setAction(c: Context, g: Gesture, a: Act) {
        sp(c).edit().putString("act_${g.key}", a.id).apply()
    }

    fun pkg(c: Context, g: Gesture): String? =
        sp(c).getString("app_${g.key}", null)
            ?: if (g == Gesture.SINGLE) sp(c).getString("package", null) else null

    fun setPkg(c: Context, g: Gesture, p: String) {
        sp(c).edit().putString("app_${g.key}", p).apply()
    }

    fun edge(c: Context) = sp(c).getBoolean("edge", true)
    fun setEdge(c: Context, v: Boolean) = sp(c).edit().putBoolean("edge", v).apply()

    fun haptic(c: Context) = sp(c).getBoolean("haptic", true)
    fun setHaptic(c: Context, v: Boolean) = sp(c).edit().putBoolean("haptic", v).apply()

    fun color(c: Context) = sp(c).getInt("color", DEFAULT_COLOR)
    fun setColor(c: Context, v: Int) = sp(c).edit().putInt("color", v).apply()

    fun tapMs(c: Context) = sp(c).getInt("tap_ms", 300)
    fun setTapMs(c: Context, v: Int) = sp(c).edit().putInt("tap_ms", v).apply()

    fun longMs(c: Context) = sp(c).getInt("long_ms", 500)
    fun setLongMs(c: Context, v: Int) = sp(c).edit().putInt("long_ms", v).apply()

    /** Altura de la tecla Essential en el lateral derecho, en % desde arriba. */
    fun keyY(c: Context) = sp(c).getInt("key_y", 30)
    fun setKeyY(c: Context, v: Int) = sp(c).edit().putInt("key_y", v).apply()

    /** Altura del botón de disparo (plan B), en % desde arriba. */
    fun shutterY(c: Context) = sp(c).getInt("shutter_y", 88)
    fun setShutterY(c: Context, v: Int) = sp(c).edit().putInt("shutter_y", v).apply()

    // Borde luminoso
    fun edgeThick(c: Context) = sp(c).getInt("edge_thick", 3)
    fun setEdgeThick(c: Context, v: Int) = sp(c).edit().putInt("edge_thick", v).apply()

    fun edgeSoft(c: Context) = sp(c).getInt("edge_soft", 8)
    fun setEdgeSoft(c: Context, v: Int) = sp(c).edit().putInt("edge_soft", v).apply()

    /** Color del borde tal y como se ve en la animación; 0 = arcoíris. */
    fun edgeColor(c: Context) = if (edgeRainbow(c)) 0 else edgeColorRaw(c)

    /** Color elegido, aunque el arcoíris esté activo. */
    fun edgeColorRaw(c: Context) = sp(c).getInt("edge_color", 0xFFFFFFFF.toInt())

    /** Al elegir un color se desactiva el arcoíris. */
    fun setEdgeColor(c: Context, v: Int) =
        sp(c).edit().putInt("edge_color", v).putBoolean("edge_rainbow", false).apply()

    fun edgeRainbow(c: Context) = sp(c).getBoolean("edge_rainbow", false)
    fun setEdgeRainbow(c: Context, v: Boolean) = sp(c).edit().putBoolean("edge_rainbow", v).apply()

    fun anim(c: Context): Anim = Anim.from(sp(c).getString("anim", null))
    fun setAnim(c: Context, a: Anim) = sp(c).edit().putString("anim", a.id).apply()

    // Panel lateral de la linterna
    fun panelShape(c: Context): PanelShape = PanelShape.from(sp(c).getString("panel_shape", null))
    fun setPanelShape(c: Context, v: PanelShape) = sp(c).edit().putString("panel_shape", v.id).apply()

    fun panelFx(c: Context): PanelFx = PanelFx.from(sp(c).getString("panel_fx", null))
    fun setPanelFx(c: Context, v: PanelFx) = sp(c).edit().putString("panel_fx", v.id).apply()

    fun panelRadius(c: Context) = sp(c).getInt("panel_radius", 22)
    fun setPanelRadius(c: Context, v: Int) = sp(c).edit().putInt("panel_radius", v).apply()

    fun panelSize(c: Context) = sp(c).getInt("panel_size", 100)
    fun setPanelSize(c: Context, v: Int) = sp(c).edit().putInt("panel_size", v).apply()

    fun panelColor(c: Context) = sp(c).getInt("panel_color", color(c))
    fun setPanelColor(c: Context, v: Int) = sp(c).edit().putInt("panel_color", v).apply()

    fun panelY(c: Context) = sp(c).getInt("panel_y", keyY(c))
    fun setPanelY(c: Context, v: Int) = sp(c).edit().putInt("panel_y", v).apply()

    fun panelText(c: Context) = sp(c).getInt("panel_text", 0xFFFFFFFF.toInt())
    fun setPanelText(c: Context, v: Int) = sp(c).edit().putInt("panel_text", v).apply()

    fun panelSteps(c: Context) = sp(c).getInt("panel_steps", 8)
    fun setPanelSteps(c: Context, v: Int) = sp(c).edit().putInt("panel_steps", v).apply()

    fun panelStyle(c: Context): PanelStyle = PanelStyle.from(sp(c).getString("panel_style", null))
    fun setPanelStyle(c: Context, v: PanelStyle) = sp(c).edit().putString("panel_style", v.id).apply()

    fun panelBorder(c: Context) = sp(c).getInt("panel_border", 2)
    fun setPanelBorder(c: Context, v: Int) = sp(c).edit().putInt("panel_border", v).apply()

    fun panelBorderColor(c: Context) = sp(c).getInt("panel_border_color", 0xFFFFFFFF.toInt())
    fun setPanelBorderColor(c: Context, v: Int) = sp(c).edit().putInt("panel_border_color", v).apply()

    fun panelAnim(c: Context) = sp(c).getBoolean("panel_anim", false)
    fun setPanelAnim(c: Context, v: Boolean) = sp(c).edit().putBoolean("panel_anim", v).apply()

    fun panelBarFx(c: Context): BarFx = BarFx.from(sp(c).getString("panel_bar_fx", null))
    fun setPanelBarFx(c: Context, v: BarFx) = sp(c).edit().putString("panel_bar_fx", v.id).apply()

    /** Curva de brillo en décimas: 10 = lineal, más alto = más pasos con poca luz. */
    fun panelCurve(c: Context) = sp(c).getInt("panel_curve", 25)
    fun setPanelCurve(c: Context, v: Int) = sp(c).edit().putInt("panel_curve", v).apply()

    fun torchMax(c: Context) = sp(c).getInt("torch_max", 0)
    fun setTorchMax(c: Context, v: Int) = sp(c).edit().putInt("torch_max", v).apply()

    /** No más pasos que niveles reales de brillo tiene el flash del móvil. */
    fun stepsCount(c: Context) = panelSteps(c).coerceIn(2, maxOf(2, torchMax(c).takeIf { it > 0 } ?: 20))

    fun panelIdx(c: Context): Int =
        sp(c).getInt("panel_idx", stepsCount(c) - 1).coerceIn(0, stepsCount(c) - 1)
    fun setPanelIdx(c: Context, v: Int) = sp(c).edit().putInt("panel_idx", v).apply()

    /** Nivel del flash para un paso: reparto lineal, sin repetir niveles. */
    fun flashLevel(c: Context, idx: Int, maxLvl: Int): Int {
        val n = stepsCount(c) - 1
        val f = if (n > 0) (idx.toFloat() / n).coerceIn(0f, 1f) else 1f
        return 1 + Math.round(f * (maxLvl - 1).coerceAtLeast(0))
    }

    /** El botón está en el lateral izquierdo (algunos móviles) en lugar del derecho. */
    fun keyLeft(c: Context) = sp(c).getBoolean("key_left", false)
    fun setKeyLeft(c: Context, v: Boolean) = sp(c).edit().putBoolean("key_left", v).apply()
}
