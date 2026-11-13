package com.joyous.remap

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * d).toInt()

    private val grey = 0xFFA0A0A0.toInt()
    private val cardColor = 0xD9090909.toInt()
    private val lineColor = 0x33FFFFFF
    private var accent = Prefs.DEFAULT_COLOR

    private lateinit var root: FrameLayout
    private lateinit var matrix: DotMatrixView
    private var scroll: ScrollView? = null

    private val essCandidates = listOf(
        "com.nothing.ntessentialrecorder",
        "com.nothing.ntessentialspace"
    )

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        runOnUiThread {
            if (result == PackageManager.PERMISSION_GRANTED) applyEssentialToggle()
            else toast(getString(R.string.toast_shizuku_denied))
        }
    }

    private val apps: List<Pair<String, String>> by lazy {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener)
        } catch (e: Throwable) {
        }

        root = FrameLayout(this)
        matrix = DotMatrixView(this)
        root.addView(matrix, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
        } catch (e: Throwable) {
        }
        super.onDestroy()
    }

    private fun rebuild() {
        accent = Prefs.color(this)
        matrix.color = accent
        val sy = scroll?.scrollY ?: 0
        scroll?.let { root.removeView(it) }
        val sv = ScrollView(this).apply {
            isFillViewport = true
            setOverScrollMode(View.OVER_SCROLL_NEVER)
            addView(buildContent())
        }
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))
        // Se restaura el scroll antes de pintar el primer fotograma: sin saltos.
        sv.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                sv.viewTreeObserver.removeOnPreDrawListener(this)
                sv.scrollTo(0, sy)
                return true
            }
        })
        scroll = sv
    }

    // ---------- Contenido ----------

    private fun buildContent(): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(44), px(20), px(32))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
        }, LinearLayout.LayoutParams(px(12), px(12)).apply { rightMargin = px(12) })
        header.addView(mono(TextView(this), 24f, Color.WHITE, true).apply { text = "JOYOUS REMAP" })
        c.addView(header)
        c.addView(mono(TextView(this), 12f, grey).apply {
            text = getString(R.string.subtitle) + "  ·  b" + (try { packageManager.getPackageInfo(packageName, 0).longVersionCode } catch (e: Exception) { 0 })
            setPadding(px(24), px(2), 0, px(20))
        })

        c.addView(statusCard())
        c.addView(sectionTitle(R.string.sec_gestures)); c.addView(gesturesCard())
        c.addView(sectionTitle(R.string.sec_timing)); c.addView(timingCard())
        c.addView(sectionTitle(R.string.sec_look)); c.addView(lookCard())
        c.addView(sectionTitle(R.string.sec_panel)); c.addView(panelCard())
        c.addView(sectionTitle(R.string.sec_lang)); c.addView(langCard())
        c.addView(sectionTitle(R.string.sec_backup)); c.addView(backupCard())
        c.addView(sectionTitle(R.string.sec_perms)); c.addView(permsCard())
        c.addView(sectionTitle(R.string.sec_essential)); c.addView(essentialCard())
        return c
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName) && enabled.contains("KeyService")
    }

    private fun statusCard(): View {
        val box = cardBox()
        val active = isServiceEnabled()
        box.addView(mono(TextView(this), 11f, grey).apply { text = getString(R.string.svc_title) })
        box.addView(mono(TextView(this), 20f, if (active) accent else Color.WHITE, true).apply {
            text = getString(if (active) R.string.svc_on else R.string.svc_off)
        })
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(12), 0, 0)
        }
        buttons.addView(pill(getString(R.string.btn_accessibility), true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, LinearLayout.LayoutParams(0, px(44), 1f).apply { rightMargin = px(8) })
        buttons.addView(pill(getString(R.string.btn_appinfo), false) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }, LinearLayout.LayoutParams(0, px(44), 1f))
        box.addView(buttons)
        return box
    }

    // ----- Pulsaciones -----

    private fun appLabel(pkg: String?): String {
        if (pkg == null) return "?"
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun describe(g: Gesture): String {
        val a = Prefs.action(this, g)
        return if (a == Act.APP) getString(R.string.act_app_named, appLabel(Prefs.pkg(this, g)))
        else getString(a.label)
    }

    private fun gesturesCard(): View {
        val box = cardBox()
        val all = Gesture.values()
        all.forEachIndexed { i, g ->
            val off = Prefs.action(this, g) == Act.NONE
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, px(10), 0, px(10))
                setOnClickListener { pickAction(g) }
            }
            row.addView(mono(TextView(this), 10f, grey).apply { text = getString(g.label) })
            row.addView(mono(TextView(this), 14f, if (off) grey else Color.WHITE).apply {
                text = describe(g) + "  ›"
            })
            box.addView(row)
            if (i < all.size - 1) box.addView(divider())
        }
        return box
    }

    private fun darkDialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun pickAction(g: Gesture) {
        val acts = Act.values()
        val labels = acts.map { getString(it.label) }.toTypedArray()
        darkDialog()
            .setTitle(R.string.pick_action)
            .setItems(labels) { _, i ->
                val a = acts[i]
                if (a == Act.APP) pickApp(g)
                else {
                    Prefs.setAction(this, g, a)
                    rebuild()
                }
            }.show()
    }

    private fun pickApp(g: Gesture) {
        val labels = apps.map { it.first }.toTypedArray()
        darkDialog()
            .setTitle(R.string.pick_app)
            .setItems(labels) { _, i ->
                Prefs.setAction(this, g, Act.APP)
                Prefs.setPkg(this, g, apps[i].second)
                rebuild()
            }.show()
    }

    // ----- Ajustes de tiempo -----

    private fun timingCard(): View {
        val box = cardBox()
        box.addView(slider(150, 500, Prefs.tapMs(this), { getString(R.string.tap_interval, it) }) {
            Prefs.setTapMs(this, it)
        })
        box.addView(slider(300, 1000, Prefs.longMs(this), { getString(R.string.long_time, it) }) {
            Prefs.setLongMs(this, it)
        })
        box.addView(divider().apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = px(10)
        })
        box.addView(toggle(R.string.haptic, Prefs.haptic(this)) { Prefs.setHaptic(this, it) })
        box.addView(divider())
        box.addView(slider(60, 95, Prefs.shutterY(this), { getString(R.string.shutter_y, it) }) {
            Prefs.setShutterY(this, it)
        })
        return box
    }

    // ----- Aspecto -----

    private fun lookCard(): View {
        val box = cardBox()
        box.addView(toggle(R.string.edge_line, Prefs.edge(this)) { Prefs.setEdge(this, it) })
        box.addView(colorRow(R.string.color, Prefs.color(this)) { Prefs.setColor(this, it) })
        box.addView(colorRow(R.string.edge_color, Prefs.edgeColorRaw(this)) { Prefs.setEdgeColor(this, it) })
        box.addView(toggle(R.string.rainbow, Prefs.edgeRainbow(this)) { Prefs.setEdgeRainbow(this, it) })
        box.addView(divider())
        val animRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(10), 0, px(10))
            setOnClickListener { pickAnim() }
        }
        animRow.addView(mono(TextView(this), 10f, grey).apply { text = getString(R.string.anim_title) })
        animRow.addView(mono(TextView(this), 14f, Color.WHITE).apply {
            text = getString(Prefs.anim(this@MainActivity).label) + "  ›"
        })
        box.addView(animRow)
        box.addView(slider(1, 10, Prefs.edgeThick(this), { getString(R.string.edge_thick, it) }) {
            Prefs.setEdgeThick(this, it)
        })
        box.addView(slider(0, 24, Prefs.edgeSoft(this), { getString(R.string.edge_soft, it) }) {
            Prefs.setEdgeSoft(this, it)
        })
        box.addView(toggle(R.string.key_left, Prefs.keyLeft(this)) { Prefs.setKeyLeft(this, it) })
        box.addView(slider(5, 95, Prefs.keyY(this), { getString(R.string.key_pos, it) }) {
            Prefs.setKeyY(this, it)
        })
        return box
    }

    private fun panelCard(): View {
        val box = cardBox()
        box.addView(pickerRow(R.string.panel_style, getString(Prefs.panelStyle(this).label)) { pickStyle() })
        box.addView(divider())
        box.addView(pickerRow(R.string.panel_shape, getString(Prefs.panelShape(this).label)) { pickShape() })
        box.addView(divider())
        box.addView(colorRow(R.string.panel_color, Prefs.panelColor(this)) { Prefs.setPanelColor(this, it) })
        box.addView(colorRow(R.string.panel_border_color, Prefs.panelBorderColor(this)) { Prefs.setPanelBorderColor(this, it) })
        box.addView(slider(0, 6, Prefs.panelBorder(this), { getString(R.string.panel_border, it) }) {
            Prefs.setPanelBorder(this, it)
        })
        box.addView(colorRow(R.string.panel_text, Prefs.panelText(this)) { Prefs.setPanelText(this, it) })
        box.addView(slider(2, maxOf(2, Prefs.torchMax(this).takeIf { it > 0 } ?: 20), Prefs.panelSteps(this).coerceAtMost(maxOf(2, Prefs.torchMax(this).takeIf { it > 0 } ?: 20)), { getString(R.string.panel_steps, it) }) {
            Prefs.setPanelSteps(this, it)
        })
        box.addView(divider())
        box.addView(toggle(R.string.panel_anim, Prefs.panelAnim(this)) { Prefs.setPanelAnim(this, it) })
        if (Prefs.panelAnim(this)) {
            box.addView(pickerRow(R.string.panel_bar_fx, getString(Prefs.panelBarFx(this).label)) { pickBarFx() })
            box.addView(divider())
        }
        box.addView(pickerRow(R.string.panel_fx, getString(Prefs.panelFx(this).label)) { pickFx() })
        box.addView(slider(5, 95, Prefs.panelY(this), { getString(R.string.panel_y, it) }) {
            Prefs.setPanelY(this, it)
        })
        box.addView(slider(0, 40, Prefs.panelRadius(this), { getString(R.string.panel_radius, it) }) {
            Prefs.setPanelRadius(this, it)
        })
        box.addView(slider(60, 150, Prefs.panelSize(this), { getString(R.string.panel_size, it) }) {
            Prefs.setPanelSize(this, it)
        })
        return box
    }

    private fun pickerRow(title: Int, value: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(10), 0, px(10))
            setOnClickListener { onClick() }
        }
        row.addView(mono(TextView(this), 10f, grey).apply { text = getString(title) })
        row.addView(mono(TextView(this), 14f, Color.WHITE).apply { text = "$value  ›" })
        return row
    }

    private fun pickBarFx() {
        val all = BarFx.values()
        darkDialog()
            .setTitle(R.string.pick_bar_fx)
            .setItems(all.map { getString(it.label) }.toTypedArray()) { _, i ->
                Prefs.setPanelBarFx(this, all[i])
                rebuild()
            }.show()
    }

    private fun pickStyle() {
        val all = PanelStyle.values()
        darkDialog()
            .setTitle(R.string.pick_style)
            .setItems(all.map { getString(it.label) }.toTypedArray()) { _, i ->
                Prefs.setPanelStyle(this, all[i])
                rebuild()
            }.show()
    }

    private fun pickShape() {
        val all = PanelShape.values()
        darkDialog()
            .setTitle(R.string.pick_shape)
            .setItems(all.map { getString(it.label) }.toTypedArray()) { _, i ->
                Prefs.setPanelShape(this, all[i])
                rebuild()
            }.show()
    }

    private fun pickFx() {
        val all = PanelFx.values()
        darkDialog()
            .setTitle(R.string.pick_fx)
            .setItems(all.map { getString(it.label) }.toTypedArray()) { _, i ->
                Prefs.setPanelFx(this, all[i])
                rebuild()
            }.show()
    }

    private fun pickAnim() {
        val all = Anim.values()
        darkDialog()
            .setTitle(R.string.pick_anim)
            .setItems(all.map { getString(it.label) }.toTypedArray()) { _, i ->
                Prefs.setAnim(this, all[i])
                rebuild()
            }.show()
    }

    private fun swatch(col: Int, selected: Boolean, onClick: () -> Unit) = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(col)
            if (selected) setStroke(px(3), Color.WHITE)
        }
        setOnClickListener { onClick() }
    }

    /** Fila de colores; el último círculo "+" abre el selector personalizado. */
    private fun colorRow(title: Int, current: Int, onPick: (Int) -> Unit): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(mono(TextView(this), 11f, grey).apply {
            setPadding(0, px(8), 0, px(8))
            text = getString(title)
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun lp() = LinearLayout.LayoutParams(0, px(30), 1f).apply {
            rightMargin = px(4)
            leftMargin = px(2)
        }
        for (col in Prefs.COLORS) {
            row.addView(swatch(col, col == current) {
                onPick(col)
                rebuild()
            }, lp())
        }
        val custom = current != 0 && current !in Prefs.COLORS
        row.addView(TextView(this).apply {
            text = "+"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (custom) current else 0xFF2A2A2A.toInt())
                setStroke(px(if (custom) 3 else 1), if (custom) Color.WHITE else 0x88FFFFFF.toInt())
            }
            setOnClickListener {
                pickCustom(if (custom) current else Color.WHITE) {
                    onPick(it)
                    rebuild()
                }
            }
        }, lp())
        wrap.addView(row)
        return wrap
    }

    /** Selector de color libre: tono, saturación y brillo con vista previa. */
    private fun pickCustom(start: Int, onPick: (Int) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(16), px(24), px(8))
        }
        val preview = View(this)
        fun paintPreview(c: Int) {
            preview.background = GradientDrawable().apply {
                cornerRadius = px(16).toFloat()
                setColor(c)
            }
        }
        paintPreview(start)
        val picker = ColorPickerView(this, start) { paintPreview(it) }
        box.addView(preview, LinearLayout.LayoutParams(-1, px(48)).apply { bottomMargin = px(14) })
        box.addView(picker, LinearLayout.LayoutParams(-1, -2))
        darkDialog()
            .setTitle(R.string.custom_color)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> onPick(picker.color) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ----- Permisos -----

    private fun langCard(): View {
        val box = cardBox()
        val cur = when (Lang.get(this)) {
            "en" -> getString(R.string.lang_en)
            "es" -> getString(R.string.lang_es)
            else -> getString(R.string.lang_auto)
        }
        box.addView(pickerRow(R.string.sec_lang, cur) {
            val codes = arrayOf("", "en", "es")
            val names = arrayOf(getString(R.string.lang_auto), getString(R.string.lang_en), getString(R.string.lang_es))
            darkDialog()
                .setTitle(R.string.sec_lang)
                .setItems(names) { _, i ->
                    Lang.set(this, codes[i])
                    recreate()
                }.show()
        })
        return box
    }

    private fun backupCard(): View {
        val box = cardBox()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(pill(getString(R.string.btn_export), false) {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, "joyous-remap.json"), REQ_EXPORT
            )
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = px(8) })
        row.addView(pill(getString(R.string.btn_import), false) {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT
            )
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)
        return box
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try {
            val sp = Prefs.sp(this)
            if (requestCode == REQ_EXPORT) {
                val o = org.json.JSONObject()
                for ((k, v) in sp.all) {
                    val t = when (v) {
                        is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; else -> "s"
                    }
                    o.put(k, org.json.JSONObject().put("t", t).put("v", v))
                }
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(o.toString(2).toByteArray()) }
                Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
            } else if (requestCode == REQ_IMPORT) {
                val txt = contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
                val o = org.json.JSONObject(txt)
                val e = sp.edit()
                for (k in o.keys()) {
                    val x = o.getJSONObject(k)
                    when (x.getString("t")) {
                        "b" -> e.putBoolean(k, x.getBoolean("v"))
                        "i" -> e.putInt(k, x.getInt("v"))
                        "l" -> e.putLong(k, x.getLong("v"))
                        "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                        else -> e.putString(k, x.getString("v"))
                    }
                }
                e.apply()
                Toast.makeText(this, R.string.toast_restored, Toast.LENGTH_SHORT).show()
                rebuild()
            }
        } catch (ex: Exception) {
            Toast.makeText(this, getString(R.string.toast_error, ex.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val REQ_EXPORT = 41
        const val REQ_IMPORT = 42
    }

    private fun permsCard(): View {
        val box = cardBox()
        val nm = getSystemService(NotificationManager::class.java)
        box.addView(permRow(
            R.string.perm_dnd, nm.isNotificationPolicyAccessGranted,
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        ))
        box.addView(divider())
        box.addView(permRow(
            R.string.perm_write, Settings.System.canWrite(this),
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
        ))
        return box
    }

    private fun permRow(label: Int, granted: Boolean, intent: Intent): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(8), 0, px(8))
        }
        row.addView(mono(TextView(this), 12f, Color.WHITE).apply { text = getString(label) },
            LinearLayout.LayoutParams(0, -2, 1f))
        if (granted) {
            row.addView(mono(TextView(this), 12f, accent, true).apply {
                text = "✓ " + getString(R.string.perm_granted)
            })
        } else {
            row.addView(pill(getString(R.string.perm_grant), false) { startActivity(intent) },
                LinearLayout.LayoutParams(px(110), px(36)))
        }
        return row
    }

    // ----- Essential Space -----

    private fun essentialPkg(): String? = essCandidates.firstOrNull {
        try {
            packageManager.getApplicationInfo(it, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun essentialCard(): View {
        val box = cardBox()
        val pkg = essentialPkg()
        val enabled = pkg?.let {
            try {
                packageManager.getApplicationInfo(it, 0).enabled
            } catch (e: Exception) {
                true
            }
        }
        box.addView(mono(TextView(this), 16f, if (pkg != null && enabled == true) accent else Color.WHITE, true).apply {
            text = getString(
                when {
                    pkg == null -> R.string.ess_notfound
                    enabled == true -> R.string.ess_active
                    else -> R.string.ess_disabled
                }
            )
        })
        box.addView(
            pill(
                getString(if (pkg != null && enabled == false) R.string.btn_enable_again else R.string.btn_disable_nopc),
                true
            ) { shizukuToggle() },
            LinearLayout.LayoutParams(-1, px(48)).apply { topMargin = px(8) }
        )
        return box
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun shizukuToggle() {
        val installed = try {
            packageManager.getPackageInfo(ShizukuHelper.PKG, 0)
            true
        } catch (e: Exception) {
            false
        }
        if (!installed) {
            toast(getString(R.string.toast_install_shizuku))
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        } else if (!ShizukuHelper.running()) {
            toast(getString(R.string.toast_start_shizuku))
            packageManager.getLaunchIntentForPackage(ShizukuHelper.PKG)?.let { startActivity(it) }
        } else if (!ShizukuHelper.granted()) {
            ShizukuHelper.request()
        } else {
            applyEssentialToggle()
        }
    }

    private fun applyEssentialToggle() {
        val pkg = essentialPkg() ?: essCandidates[0]
        val enabledNow = try {
            packageManager.getApplicationInfo(pkg, 0).enabled
        } catch (e: Exception) {
            true
        }
        val cmd = if (enabledNow) "pm disable-user --user 0 $pkg" else "pm enable $pkg"
        Thread {
            val r = try {
                ShizukuHelper.run(cmd)
            } catch (e: Throwable) {
                -1 to (e.message ?: "error")
            }
            runOnUiThread {
                toast(
                    if (r.first == 0) getString(
                        if (enabledNow) R.string.toast_ess_disabled else R.string.toast_ess_enabled
                    ) else getString(R.string.toast_error, r.second)
                )
                rebuild()
            }
        }.start()
    }

    // ---------- Helpers de estilo ----------

    private fun mono(t: TextView, size: Float, color: Int, bold: Boolean = false): TextView {
        t.typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        t.textSize = size
        t.setTextColor(color)
        t.letterSpacing = 0.05f
        return t
    }

    private fun cardBox() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(18), px(14), px(18), px(14))
        background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = px(24).toFloat()
            setStroke(px(1), lineColor)
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun sectionTitle(res: Int) = mono(TextView(this), 11f, grey).apply {
        text = getString(res)
        setPadding(px(6), px(24), 0, px(8))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(lineColor)
        layoutParams = LinearLayout.LayoutParams(-1, 1)
    }

    private fun toggle(label: Int, on: Boolean, set: (Boolean) -> Unit): View =
        mono(TextView(this), 14f, if (on) Color.WHITE else grey).apply {
            text = (if (on) "● " else "○ ") + getString(label)
            setPadding(0, px(12), 0, px(12))
            setOnClickListener {
                set(!on)
                rebuild()
            }
        }

    private fun slider(
        minV: Int, maxV: Int, value: Int,
        title: (Int) -> String, onSet: (Int) -> Unit
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(10), 0, px(2))
        }
        val t = mono(TextView(this), 12f, Color.WHITE).apply { text = title(value) }
        box.addView(t)
        box.addView(SeekBar(this).apply {
            max = maxV - minV
            progress = value - minV
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + minV
                    t.text = title(v)
                    if (fromUser) onSet(v)
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        return box
    }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = true
        stateListAnimator = null
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 11f
        setTextColor(if (filled) Color.BLACK else Color.WHITE)
        setPadding(px(8), 0, px(8), 0)
        background = GradientDrawable().apply {
            cornerRadius = px(100).toFloat()
            if (filled) setColor(Color.WHITE) else {
                setColor(Color.TRANSPARENT)
                setStroke(px(1), Color.WHITE)
            }
        }
        setOnClickListener { onClick() }
    }
}
