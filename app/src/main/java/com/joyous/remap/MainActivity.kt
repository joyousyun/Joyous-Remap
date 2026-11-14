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
        DeviceProfile.autoApply(this)
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

        when (page) {
            null -> {
                c.addView(statusCard())
                if (BuildConfig.TESTING) {
                    c.addView(deviceCard().apply {
                        (layoutParams as LinearLayout.LayoutParams).topMargin = px(14)
                    })
                }
                c.addView(sectionTitle(R.string.sec_gestures))
                c.addView(gesturesCard())
                c.addView(sectionTitle(R.string.sec_profiles))
                c.addView(profilesCard())
                c.addView(menuCard())
            }
            else -> if (page!!.startsWith("prof:") || page == "excl") buildProfilePage(c) else {
                c.addView(pageHeader(when (page) {
                    "quick" -> DotIconView.QUICK
                    "media" -> DotIconView.forAct(Act.MUSIC)
                    "look" -> DotIconView.LOOK
                    "panel" -> DotIconView.PANEL
                    "timing" -> DotIconView.TIMING
                    else -> DotIconView.MORE
                }, when (page) {
                    "quick" -> R.string.menu_quick
                    "media" -> R.string.menu_media
                    "look" -> R.string.menu_look
                    "panel" -> R.string.menu_panel
                    "timing" -> R.string.menu_timing
                    else -> R.string.menu_more
                }))
                when (page) {
                    "media" -> {
                        c.addView(sectionTitle(R.string.sec_media)); c.addView(mediaCard())
                        c.addView(sectionTitle(R.string.sec_media_status)); c.addView(mediaStatusCard())
                    }
                    "quick" -> {
                        c.addView(sectionTitle(R.string.sec_quick_items)); c.addView(quickItemsCard())
                        c.addView(sectionTitle(R.string.sec_quick_look)); c.addView(quickLookCard())
                    }
                    "look" -> {
                        c.addView(sectionTitle(R.string.sec_notice)); c.addView(noticeCard())
                        c.addView(sectionTitle(R.string.sec_edge)); c.addView(lookCard())
                    }
                    "panel" -> { c.addView(sectionTitle(R.string.sec_panel)); c.addView(panelCard()) }
                    "timing" -> { c.addView(sectionTitle(R.string.sec_timing)); c.addView(timingCard()) }
                    else -> {
                        c.addView(sectionTitle(R.string.sec_lang)); c.addView(langCard())
                        c.addView(sectionTitle(R.string.sec_backup)); c.addView(backupCard())
                        c.addView(sectionTitle(R.string.sec_perms)); c.addView(permsCard())
                        c.addView(sectionTitle(R.string.sec_essential)); c.addView(essentialCard())
                    }
                }
            }
        }
        return c
    }

    private var page: String? = null
    private var adv = false

    private fun go(p: String?) {
        page = p
        adv = false
        scroll?.let { root.removeView(it) }
        scroll = null
        rebuild()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page != null) go(null) else super.onBackPressed()
    }

    private fun icon(rows: List<String>, size: Int = 36, lit: Int = accent): View =
        DotIconView(this, rows, lit).apply {
            layoutParams = LinearLayout.LayoutParams(px(size), px(size)).apply { rightMargin = px(14) }
        }

    private fun gestureIcon(g: Gesture) = when (g) {
        Gesture.SINGLE -> DotIconView.SINGLE
        Gesture.DOUBLE -> DotIconView.DOUBLE
        Gesture.LONG -> DotIconView.LONG
        Gesture.TRIPLE -> DotIconView.TRIPLE
    }

    private fun pageHeader(rows: List<String>, title: Int): View = pageHeader(rows, getString(title))

    private fun pageHeader(rows: List<String>, title: String): View {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(6), 0, px(6))
        }
        h.addView(icon(rows, 40))
        h.addView(mono(TextView(this), 20f, Color.WHITE, true).apply { text = title.uppercase() },
            LinearLayout.LayoutParams(0, -2, 1f))
        h.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(10), px(8), 0, px(8))
            setOnClickListener { go(null) }
            addView(DotIconView(this@MainActivity, DotIconView.BACK, grey),
                LinearLayout.LayoutParams(px(20), px(20)).apply { rightMargin = px(4) })
            addView(mono(TextView(this@MainActivity), 11f, grey).apply {
                text = getString(R.string.back).removePrefix("‹ ")
            })
        })
        return h
    }

    private fun menuRow(rows: List<String>, title: Int, sub: Int, target: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(14), 0, px(14))
            setOnClickListener { go(target) }
        }
        row.addView(icon(rows))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(mono(TextView(this), 14f, Color.WHITE).apply { text = getString(title) })
        col.addView(mono(TextView(this), 10f, grey).apply { text = getString(sub) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(mono(TextView(this), 18f, grey).apply { text = "›" })
        return row
    }

    private fun menuCard(): View {
        val box = cardBox()
        (box.layoutParams as LinearLayout.LayoutParams).topMargin = px(20)
        box.addView(menuRow(DotIconView.QUICK, R.string.menu_quick, R.string.menu_quick_sub, "quick"))
        box.addView(divider())
        box.addView(menuRow(DotIconView.forAct(Act.MUSIC), R.string.menu_media, R.string.menu_media_sub, "media"))
        box.addView(divider())
        box.addView(menuRow(DotIconView.LOOK, R.string.menu_look, R.string.menu_look_sub, "look"))
        box.addView(divider())
        box.addView(menuRow(DotIconView.PANEL, R.string.menu_panel, R.string.menu_panel_sub, "panel"))
        box.addView(divider())
        box.addView(menuRow(DotIconView.TIMING, R.string.menu_timing, R.string.menu_timing_sub, "timing"))
        box.addView(divider())
        box.addView(menuRow(DotIconView.MORE, R.string.menu_more, R.string.menu_more_sub, "more"))
        return box
    }

    private fun advToggle(): View = mono(TextView(this), 11f, accent, true).apply {
        text = getString(if (adv) R.string.adv_hide else R.string.adv_show)
        setPadding(0, px(14), 0, px(6))
        setOnClickListener { adv = !adv; rebuild() }
    }

    private fun deviceCard(): View {
        val box = cardBox()
        val manual = DeviceProfile.manual(this)
        val model = DeviceProfile.current(this)
        val dv = DeviceView(this).apply {
            this.model = model
            left = Prefs.keyLeft(this@MainActivity)
            y = Prefs.keyY(this@MainActivity)
            accent = 0xFFD71921.toInt()
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(dv, LinearLayout.LayoutParams(px(120), px(236)).apply { rightMargin = px(14) })

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (model != null) info.addView(mono(TextView(this), 10f, 0xFFD71921.toInt()).apply {
            text = getString(if (manual != null) R.string.dev_manual else R.string.dev_auto)
        })
        info.addView(mono(TextView(this), 18f, Color.WHITE, true).apply {
            text = model?.title ?: getString(R.string.dev_unknown)
        })
        if (model == null) info.addView(mono(TextView(this), 10f, grey).apply { text = getString(R.string.dev_unknown_sub) })
        info.addView(pickerRow(R.string.dev_model, manual?.title ?: getString(R.string.dev_auto_opt)) { pickModel() }
            .apply { setPadding(0, px(18), 0, px(10)) })
        info.addView(mono(TextView(this), 10f, grey).apply { text = getString(R.string.dev_side) })
        info.addView(segmented(
            listOf(getString(R.string.dev_left), getString(R.string.dev_right)),
            if (Prefs.keyLeft(this)) 0 else 1
        ) { i ->
            Prefs.setKeyLeft(this, i == 0); DeviceProfile.markCustom(this); rebuild()
        }, LinearLayout.LayoutParams(-1, px(38)).apply { topMargin = px(6) })
        if (model != null) {
            info.addView(mono(TextView(this), 10f, 0xFFD71921.toInt(), true).apply {
                text = "↺  " + getString(R.string.dev_reset)
                setPadding(0, px(16), 0, px(4))
                setOnClickListener { DeviceProfile.apply(this@MainActivity, model); rebuild() }
            })
        }
        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(top)
        box.addView(slider(5, 95, Prefs.keyY(this), { getString(R.string.dev_height, it) }) {
            Prefs.setKeyY(this, it); Prefs.setPanelY(this, it); DeviceProfile.markCustom(this)
            dv.y = it; dv.invalidate()
        })
        return box
    }

    /** Selector de 2+ opciones en cápsula; la elegida va rellena. */
    private fun segmented(opts: List<String>, sel: Int, onSel: (Int) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = px(100).toFloat()
                setStroke(px(1), 0x88FFFFFF.toInt())
            }
            setPadding(px(3), px(3), px(3), px(3))
        }
        opts.forEachIndexed { i, t ->
            row.addView(mono(TextView(this), 10f, if (i == sel) Color.BLACK else Color.WHITE, true).apply {
                text = t
                gravity = Gravity.CENTER
                if (i == sel) background = GradientDrawable().apply {
                    cornerRadius = px(100).toFloat(); setColor(Color.WHITE)
                }
                setOnClickListener { if (i != sel) onSel(i) }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        return row
    }

    private fun pickModel() {
        val all = NothingModel.values()
        val names = arrayOf(getString(R.string.dev_auto_opt)) + all.map { it.title }.toTypedArray()
        darkDialog()
            .setTitle(R.string.dev_pick)
            .setItems(names) { _, i ->
                DeviceProfile.setManual(this, if (i == 0) null else all[i - 1])
                rebuild()
            }.show()
    }

    private fun noticeCard(): View {
        val box = cardBox()
        box.addView(toggle(R.string.chip_show, Prefs.chipOn(this)) { Prefs.setChipOn(this, it) })
        if (Prefs.chipOn(this)) {
            box.addView(divider())
            box.addView(pickerRow(R.string.chip_fx, getString(Prefs.chipFx(this).label)) { pickChipFx() })
            box.addView(slider(2, 30, Prefs.chipY(this), { getString(R.string.chip_y, it) }) {
                Prefs.setChipY(this, it)
            })
        }
        return box
    }

    private fun pickChipFx() {
        val all = ChipFx.values()
        darkDialog()
            .setTitle(R.string.pick_chip_fx)
            .setItems(all.map { getString(it.label) }.toTypedArray()) { _, i ->
                Prefs.setChipFx(this, all[i])
                rebuild()
            }.show()
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName) && enabled.contains("KeyService")
    }

    private fun statusCard(): View {
        val active = isServiceEnabled()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(mono(TextView(this), 12f, if (active) accent else Color.WHITE, true).apply {
            text = getString(if (active) R.string.svc_on else R.string.svc_off)
            setPadding(px(24), 0, 0, 0)
        })
        if (!active) {
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
        }
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

    private fun describe(g: Gesture): String = describeAct(Prefs.action(this, g), Prefs.pkg(this, g))

    private fun describeAct(a: Act, pkg: String?): String =
        if (a == Act.APP) getString(R.string.act_app_named, appLabel(pkg)) else getString(a.label)

    private fun gesturesCard(): View {
        val box = cardBox()
        val all = Gesture.values()
        all.forEachIndexed { i, g ->
            val off = Prefs.action(this, g) == Act.NONE
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(10), 0, px(10))
                setOnClickListener { pickAction(g) }
            }
            row.addView(icon(gestureIcon(g), 32, if (off) grey else accent))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(mono(TextView(this), 10f, grey).apply { text = getString(g.label) })
            col.addView(mono(TextView(this), 14f, if (off) grey else Color.WHITE).apply {
                text = describe(g)
            })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(mono(TextView(this), 18f, grey).apply { text = "›" })
            box.addView(row)
            if (i < all.size - 1) box.addView(divider())
        }
        return box
    }

    private fun darkDialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun pickAction(g: Gesture) = chooseAction(R.string.pick_action, false) { a, pkg ->
        if (a != null) {
            Prefs.setAction(this, g, a)
            if (pkg != null) Prefs.setPkg(this, g, pkg)
            rebuild()
        }
    }

    /**
     * Selector de acciones único: lo usan las pulsaciones generales y los perfiles.
     * Con [inherit] se añade arriba "Ajuste general" y devuelve (null, null).
     */
    private fun chooseAction(
        title: Int, inherit: Boolean, exclude: Set<Act> = emptySet(), onChosen: (Act?, String?) -> Unit
    ) {
        val acts = Act.values().filter { it !in exclude }.toTypedArray()
        val labels = (if (inherit) listOf(getString(R.string.prof_inherit)) else emptyList()) +
            acts.map { getString(it.label) }
        darkDialog()
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, i ->
                val k = if (inherit) i - 1 else i
                when {
                    k < 0 -> onChosen(null, null)
                    acts[k] == Act.APP -> chooseApp { onChosen(Act.APP, it) }
                    else -> onChosen(acts[k], null)
                }
            }.show()
    }

    private fun chooseApp(onPick: (String) -> Unit) {
        val labels = apps.map { it.first }.toTypedArray()
        darkDialog()
            .setTitle(R.string.pick_app)
            .setItems(labels) { _, i -> onPick(apps[i].second) }.show()
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
        box.addView(divider())
        box.addView(pickerRow(R.string.anim_title, getString(Prefs.anim(this).label)) { pickAnim() })
        box.addView(divider())
        box.addView(toggle(R.string.key_left, Prefs.keyLeft(this)) { Prefs.setKeyLeft(this, it); DeviceProfile.markCustom(this) })
        box.addView(advToggle())
        if (adv) {
            box.addView(colorRow(R.string.edge_color, Prefs.edgeColorRaw(this)) { Prefs.setEdgeColor(this, it) })
            box.addView(toggle(R.string.rainbow, Prefs.edgeRainbow(this)) { Prefs.setEdgeRainbow(this, it) })
            box.addView(slider(1, 10, Prefs.edgeThick(this), { getString(R.string.edge_thick, it) }) {
                Prefs.setEdgeThick(this, it)
            })
            box.addView(slider(0, 24, Prefs.edgeSoft(this), { getString(R.string.edge_soft, it) }) {
                Prefs.setEdgeSoft(this, it)
            })
            box.addView(slider(5, 95, Prefs.keyY(this), { getString(R.string.key_pos, it) }) {
                Prefs.setKeyY(this, it); Prefs.setPanelY(this, it); DeviceProfile.markCustom(this)
            })
        }
        return box
    }

    private fun panelCard(): View {
        val box = cardBox()
        val tm = maxOf(2, Prefs.torchMax(this).takeIf { it > 0 } ?: 20)
        box.addView(pickerRow(R.string.panel_style, getString(Prefs.panelStyle(this).label)) { pickStyle() })
        box.addView(divider())
        box.addView(colorRow(R.string.panel_color, Prefs.panelColor(this)) { Prefs.setPanelColor(this, it) })
        box.addView(slider(2, tm, Prefs.panelSteps(this).coerceAtMost(tm), { getString(R.string.panel_steps, it) }) {
            Prefs.setPanelSteps(this, it)
        })
        box.addView(slider(5, 95, Prefs.panelY(this), { getString(R.string.panel_y, it) }) {
            Prefs.setPanelY(this, it)
        })
        box.addView(advToggle())
        if (adv) {
            box.addView(pickerRow(R.string.panel_shape, getString(Prefs.panelShape(this).label)) { pickShape() })
            box.addView(divider())
            box.addView(colorRow(R.string.panel_border_color, Prefs.panelBorderColor(this)) { Prefs.setPanelBorderColor(this, it) })
            box.addView(slider(0, 6, Prefs.panelBorder(this), { getString(R.string.panel_border, it) }) {
                Prefs.setPanelBorder(this, it)
            })
            box.addView(colorRow(R.string.panel_text, Prefs.panelText(this)) { Prefs.setPanelText(this, it) })
            box.addView(divider())
            box.addView(toggle(R.string.panel_anim, Prefs.panelAnim(this)) { Prefs.setPanelAnim(this, it) })
            if (Prefs.panelAnim(this)) {
                box.addView(pickerRow(R.string.panel_bar_fx, getString(Prefs.panelBarFx(this).label)) { pickBarFx() })
                box.addView(divider())
            }
            box.addView(pickerRow(R.string.panel_fx, getString(Prefs.panelFx(this).label)) { pickFx() })
            box.addView(slider(0, 40, Prefs.panelRadius(this), { getString(R.string.panel_radius, it) }) {
                Prefs.setPanelRadius(this, it)
            })
            box.addView(slider(60, 150, Prefs.panelSize(this), { getString(R.string.panel_size, it) }) {
                Prefs.setPanelSize(this, it)
            })
        }
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
        } catch (ex: org.json.JSONException) {
            Toast.makeText(this, R.string.toast_bad_file, Toast.LENGTH_LONG).show()
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

    // ---------- Controles multimedia con pantalla apagada ----------

    private fun mediaCard(): View {
        val box = cardBox()
        box.addView(mono(TextView(this), 10f, grey).apply {
            text = getString(R.string.media_desc)
            setPadding(0, px(4), 0, px(6))
        })
        box.addView(toggle(R.string.media_toggle, Prefs.mediaOff(this)) { Prefs.setMediaOff(this, it) })
        if (Prefs.mediaOff(this)) {
            box.addView(divider())
            val all = Gesture.values()
            all.forEachIndexed { i, g ->
                val m = Prefs.mediaAct(this, g)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, px(10), 0, px(10))
                    setOnClickListener { pickMediaAct(g) }
                }
                row.addView(icon(gestureIcon(g), 32, if (m == MediaAct.NONE) grey else accent))
                val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                col.addView(mono(TextView(this), 10f, grey).apply { text = getString(g.label) })
                col.addView(mono(TextView(this), 14f, if (m == MediaAct.NONE) grey else Color.WHITE).apply {
                    text = getString(m.label)
                })
                row.addView(col, lp(0, -2, 1f))
                row.addView(mono(TextView(this), 18f, grey).apply { text = "›" })
                box.addView(row)
                if (i < all.size - 1) box.addView(divider())
            }
        }
        return box
    }

    private fun pickMediaAct(g: Gesture) {
        val all = MediaAct.values()
        darkDialog()
            .setTitle(R.string.pick_media)
            .setItems(all.map { getString(it.label) }.toTypedArray()) { _, i ->
                Prefs.setMediaAct(this, g, all[i]); rebuild()
            }.show()
    }

    private fun mediaStatusCard(): View {
        val box = cardBox()
        // Acceso a notificaciones: lo exige Android para localizar la sesión que suena.
        val granted = MediaRemote.listenerEnabled(this)
        box.addView(permRow(R.string.media_perm, granted, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
        if (!granted) {
            box.addView(mono(TextView(this), 10f, grey).apply {
                text = getString(R.string.media_perm_hint)
                setPadding(0, px(2), 0, px(6))
            })
        }
        box.addView(divider())
        // Sesión que se controlaría ahora mismo
        box.addView(mono(TextView(this), 10f, grey).apply {
            text = getString(R.string.media_session)
            setPadding(0, px(10), 0, 0)
        })
        val s = if (granted) MediaRemote.describe(this) else null
        box.addView(mono(TextView(this), 13f, if (s != null) Color.WHITE else grey).apply {
            text = if (s == null) getString(R.string.media_session_none)
            else getString(R.string.media_session_val, s.first,
                getString(if (s.second) R.string.media_playing else R.string.media_paused))
            setPadding(0, px(2), 0, px(10))
        })
        box.addView(divider())
        // Prueba real: ¿llega la tecla a la app con la pantalla apagada?
        val seen = Prefs.sp(this).getLong("off_seen_ess", 0L)
        box.addView(mono(TextView(this), 10f, grey).apply {
            text = getString(R.string.media_detect)
            setPadding(0, px(10), 0, 0)
        })
        box.addView(mono(TextView(this), 11f, if (seen > 0) accent else Color.WHITE).apply {
            text = if (seen > 0) getString(R.string.media_detect_ok,
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                    .format(java.util.Date(seen)))
            else getString(R.string.media_detect_no)
            setPadding(0, px(4), 0, px(6))
        })
        if (seen > 0) {
            box.addView(mono(TextView(this), 10f, accent, true).apply {
                text = "↺  " + getString(R.string.media_detect_reset)
                setPadding(0, px(6), 0, px(4))
                setOnClickListener {
                    Prefs.sp(this@MainActivity).edit().remove("off_seen_ess").apply(); rebuild()
                }
            })
        }
        return box
    }

    // ---------- Menú rápido ----------

    private fun quickItemsCard(): View {
        val box = cardBox()
        box.addView(mono(TextView(this), 10f, grey).apply {
            text = getString(R.string.quick_desc)
            setPadding(0, px(4), 0, px(8))
        })
        quickEditor(box, Prefs.quickItems(this)) { Prefs.setQuickItems(this, it); rebuild() }
        return box
    }

    /** Icono de un acceso: el de la app si es "abrir app", si no el de puntos. */
    private fun accessIcon(pa: ProfileAct, size: Int): View {
        if (pa.act == Act.APP && pa.pkg != null) {
            val dr = appIcon(pa.pkg)
            if (dr != null) return android.widget.ImageView(this).apply {
                setImageDrawable(dr)
                layoutParams = LinearLayout.LayoutParams(px(size), px(size)).apply { rightMargin = px(14) }
            }
        }
        return icon(DotIconView.forAct(pa.act), size, Prefs.quickColor(this))
    }

    /** Lista editable de accesos (2 a 6): reordenar, quitar y añadir con el selector de acciones de siempre. */
    private fun quickEditor(box: LinearLayout, items: List<ProfileAct>, onChange: (List<ProfileAct>) -> Unit) {
        items.forEachIndexed { i, pa ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(6), 0, px(6))
            }
            row.addView(accessIcon(pa, 28))
            row.addView(mono(TextView(this), 13f, Color.WHITE).apply {
                text = quickLabel(this@MainActivity, pa)
                setSingleLine()
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, lp(0, -2, 1f))
            fun btn(t: String, on: Boolean, f: () -> Unit) = mono(TextView(this), 15f, grey, true).apply {
                text = t
                alpha = if (on) 1f else 0.25f
                setPadding(px(12), px(6), px(6), px(6))
                if (on) setOnClickListener { f() }
            }
            row.addView(btn("▲", i > 0) {
                onChange(items.toMutableList().also { val t = it[i]; it[i] = it[i - 1]; it[i - 1] = t })
            })
            row.addView(btn("▼", i < items.size - 1) {
                onChange(items.toMutableList().also { val t = it[i]; it[i] = it[i + 1]; it[i + 1] = t })
            })
            row.addView(btn("✕", items.size > 2) { onChange(items.filterIndexed { j, _ -> j != i }) })
            box.addView(row)
        }
        if (items.size < 6) {
            box.addView(pill(getString(R.string.quick_add), true) {
                chooseAction(R.string.quick_pick, false, setOf(Act.NONE, Act.QUICK)) { a, pkg ->
                    if (a != null) onChange(items + ProfileAct(a, pkg))
                }
            }, lp(-1, px(44)).apply { topMargin = px(10) })
        }
    }

    private fun quickLookCard(): View {
        val box = cardBox()
        box.addView(segmented(
            listOf(getString(R.string.quick_bar), getString(R.string.quick_panel)),
            if (Prefs.quickStyle(this) == "panel") 1 else 0
        ) { i -> Prefs.setQuickStyle(this, if (i == 1) "panel" else "bar"); rebuild() }, lp(-1, px(38)))
        box.addView(toggle(R.string.quick_names, Prefs.quickNames(this)) { Prefs.setQuickNames(this, it) })
        box.addView(divider())
        box.addView(colorRow(R.string.quick_color, Prefs.quickColor(this)) { Prefs.setQuickColor(this, it) })
        box.addView(divider())
        box.addView(pickerRow(R.string.quick_fx, getString(Prefs.quickFx(this).label)) {
            val all = PanelFx.values()
            darkDialog()
                .setTitle(R.string.pick_fx)
                .setItems(all.map { getString(it.label) }.toTypedArray()) { _, i ->
                    Prefs.setQuickFx(this, all[i]); rebuild()
                }.show()
        })
        box.addView(slider(70, 150, Prefs.quickSize(this), { getString(R.string.quick_size, it) }) {
            Prefs.setQuickSize(this, it)
        })
        box.addView(slider(-30, 30, Prefs.quickDy(this), { getString(R.string.quick_y, it) }) {
            Prefs.setQuickDy(this, it)
        })
        if (Prefs.quickDy(this) != 0) {
            box.addView(mono(TextView(this), 10f, accent, true).apply {
                text = getString(R.string.quick_y_follow)
                setPadding(0, px(12), 0, px(4))
                setOnClickListener { Prefs.setQuickDy(this@MainActivity, 0); rebuild() }
            })
        }
        return box
    }

    // ---------- Perfiles por aplicación ----------

    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.drawable.Drawable>()

    private fun appIcon(pkg: String): android.graphics.drawable.Drawable? {
        iconCache[pkg]?.let { return it }
        return try {
            packageManager.getApplicationIcon(pkg).also { iconCache[pkg] = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun isInstalled(pkg: String) = try {
        packageManager.getApplicationInfo(pkg, 0); true
    } catch (e: Exception) {
        false
    }

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    /** Hilera de iconos de apps (hasta [max]) con "+N" si hay más. */
    private fun appStrip(pkgs: List<String>, max: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(6), 0, 0)
        }
        val shown = pkgs.filter { isInstalled(it) }
        shown.take(max).forEach { pk ->
            row.addView(android.widget.ImageView(this).apply { setImageDrawable(appIcon(pk)) },
                lp(px(22), px(22)).apply { rightMargin = px(6) })
        }
        if (pkgs.size > max) row.addView(mono(TextView(this), 10f, grey).apply { text = "+${pkgs.size - max}" })
        return row
    }

    private fun profileSub(p: Profile): String {
        val names = if (p.pkgs.isEmpty()) getString(R.string.prof_no_apps)
        else p.pkgs.take(3).joinToString(", ") { appLabel(it) } +
            (if (p.pkgs.size > 3) " +${p.pkgs.size - 3}" else "")
        return getString(if (p.except) R.string.prof_sub_except else R.string.prof_sub_only, names)
    }

    private fun profilesCard(): View {
        val box = cardBox()
        val list = Profiles.all(this)
        if (list.isEmpty()) {
            box.addView(mono(TextView(this), 11f, grey).apply {
                text = getString(R.string.prof_none)
                setPadding(0, px(6), 0, px(10))
            })
        }
        list.forEachIndexed { i, p ->
            box.addView(profileRow(p, i))
            box.addView(divider())
        }
        box.addView(simpleRow(DotIconView.PLUS, getString(R.string.prof_new), null) {
            val id = Profiles.newId()
            Profiles.add(this, Profile(id, getString(R.string.prof_default_name, list.size + 1)))
            go("prof:$id")
        })
        box.addView(divider())
        val ex = Profiles.excluded(this)
        box.addView(simpleRow(
            DotIconView.EXCLUDE, getString(R.string.excl_title),
            if (ex.isEmpty()) getString(R.string.excl_sub)
            else resources.getQuantityString(R.plurals.n_apps, ex.size, ex.size)
        ) { go("excl") })
        if (list.size > 1) {
            box.addView(mono(TextView(this), 10f, grey).apply {
                text = getString(R.string.prof_hint)
                setPadding(0, px(8), 0, px(4))
            })
        }
        return box
    }

    private fun simpleRow(rows: List<String>, title: String, sub: String?, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(12), 0, px(12))
            setOnClickListener { onClick() }
        }
        row.addView(icon(rows, 32))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(mono(TextView(this), 14f, Color.WHITE).apply { text = title })
        if (sub != null) col.addView(mono(TextView(this), 10f, grey).apply { text = sub })
        row.addView(col, lp(0, -2, 1f))
        row.addView(mono(TextView(this), 18f, grey).apply { text = "›" })
        return row
    }

    private fun profileRow(p: Profile, idx: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(12), 0, px(12))
            setOnClickListener { go("prof:" + p.id) }
        }
        row.addView(icon(DotIconView.PROFILE, 32, if (p.enabled) accent else grey))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(mono(TextView(this), 14f, if (p.enabled) Color.WHITE else grey).apply {
            text = "#${idx + 1}  ${p.name}"
            setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        col.addView(mono(TextView(this), 10f, grey).apply {
            text = profileSub(p)
            setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (p.pkgs.isNotEmpty()) col.addView(appStrip(p.pkgs, 7))
        row.addView(col, lp(0, -2, 1f))
        row.addView(mono(TextView(this), 18f, if (p.enabled) accent else grey, true).apply {
            text = if (p.enabled) "●" else "○"
            setPadding(px(14), px(10), px(4), px(10))
            setOnClickListener {
                Profiles.update(this@MainActivity, p.id) { it.copy(enabled = !it.enabled) }
                rebuild()
            }
        })
        return row
    }

    /** Pantalla de un perfil o de las exclusiones. */
    private fun buildProfilePage(c: LinearLayout) {
        val pg = page ?: return
        if (pg == "excl") {
            c.addView(pageHeader(DotIconView.EXCLUDE, R.string.excl_title))
            val box = cardBox()
            box.addView(mono(TextView(this), 11f, grey).apply {
                text = getString(R.string.excl_desc)
                setPadding(0, px(6), 0, px(10))
            })
            val ex = Profiles.excluded(this).toList()
            appRows(box, ex) { pk -> Profiles.setExcluded(this, Profiles.excluded(this) - pk); rebuild() }
            box.addView(pill(getString(R.string.prof_pick_apps), true) {
                pickApps(R.string.excl_pick, Profiles.excluded(this)) { Profiles.setExcluded(this, it); rebuild() }
            }, lp(-1, px(44)).apply { topMargin = px(10) })
            c.addView(box)
            return
        }
        val p = Profiles.get(this, pg.substring(5))
        if (p == null) {
            c.addView(pageHeader(DotIconView.PROFILE, "?"))
            return
        }
        val all = Profiles.all(this)
        val idx = all.indexOfFirst { it.id == p.id }
        c.addView(pageHeader(DotIconView.PROFILE, p.name))

        // Datos generales
        val top = cardBox()
        top.addView(pickerRow(R.string.prof_name, p.name) { renameProfile(p) })
        top.addView(divider())
        top.addView(toggle(R.string.prof_enabled, p.enabled) {
            Profiles.update(this, p.id) { q -> q.copy(enabled = it) }
        })
        top.addView(divider())
        val pr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(10), 0, px(6))
        }
        val pc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pc.addView(mono(TextView(this), 10f, grey).apply { text = getString(R.string.prof_priority) })
        pc.addView(mono(TextView(this), 14f, Color.WHITE).apply {
            text = getString(R.string.prof_prio_pos, idx + 1, all.size)
        })
        pr.addView(pc, lp(0, -2, 1f))
        pr.addView(pill(getString(R.string.prof_up), false) {
            Profiles.move(this, p.id, -1); rebuild()
        }.apply { alpha = if (idx > 0) 1f else 0.3f }, lp(px(92), px(38)).apply { rightMargin = px(6) })
        pr.addView(pill(getString(R.string.prof_down), false) {
            Profiles.move(this, p.id, 1); rebuild()
        }.apply { alpha = if (idx < all.size - 1) 1f else 0.3f }, lp(px(92), px(38)))
        top.addView(pr)
        c.addView(top)

        // Dónde se aplica
        c.addView(sectionTitle(R.string.prof_mode))
        val where = cardBox()
        where.addView(segmented(
            listOf(getString(R.string.prof_mode_only), getString(R.string.prof_mode_except)),
            if (p.except) 1 else 0
        ) { i -> Profiles.update(this, p.id) { it.copy(except = i == 1) }; rebuild() }, lp(-1, px(38)))
        where.addView(mono(TextView(this), 10f, grey).apply {
            text = getString(if (p.except) R.string.prof_mode_except_d else R.string.prof_mode_only_d)
            setPadding(0, px(10), 0, px(6))
        })
        where.addView(divider())
        appRows(where, p.pkgs) { pk ->
            Profiles.update(this, p.id) { it.copy(pkgs = it.pkgs - pk) }; rebuild()
        }
        where.addView(pill(getString(R.string.prof_pick_apps), true) {
            pickApps(R.string.prof_pick_apps, p.pkgs.toSet()) { s ->
                Profiles.update(this, p.id) { it.copy(pkgs = s.toList()) }; rebuild()
            }
        }, lp(-1, px(44)).apply { topMargin = px(10) })
        c.addView(where)

        // Pulsaciones propias o heredadas
        c.addView(sectionTitle(R.string.prof_presses))
        val gs = cardBox()
        val gAll = Gesture.values()
        gAll.forEachIndexed { i, g ->
            val own = p.acts[g]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(10), 0, px(10))
                setOnClickListener {
                    chooseAction(R.string.prof_pick_action, true) { a, pkg ->
                        Profiles.update(this@MainActivity, p.id) { q ->
                            q.copy(acts = if (a == null) q.acts - g else q.acts + (g to ProfileAct(a, pkg)))
                        }
                        rebuild()
                    }
                }
            }
            row.addView(icon(gestureIcon(g), 32, if (own != null) accent else grey))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(mono(TextView(this), 10f, grey).apply { text = getString(g.label) })
            col.addView(mono(TextView(this), 14f, if (own != null) Color.WHITE else grey).apply {
                text = if (own != null) describeAct(own.act, own.pkg)
                else getString(R.string.prof_inherit_val, describe(g))
            })
            row.addView(col, lp(0, -2, 1f))
            row.addView(mono(TextView(this), 18f, grey).apply { text = "›" })
            gs.addView(row)
            if (i < gAll.size - 1) gs.addView(divider())
        }
        c.addView(gs)

        // Menú rápido propio del perfil (solo si lo usa alguna pulsación o ya tiene accesos propios)
        val usesQuick = gAll.any { (p.acts[it]?.act ?: Prefs.action(this, it)) == Act.QUICK }
        if (usesQuick || p.quick != null) {
            c.addView(sectionTitle(R.string.menu_quick))
            val qb = cardBox()
            qb.addView(toggle(R.string.quick_own, p.quick != null) { on ->
                Profiles.update(this, p.id) { it.copy(quick = if (on) Prefs.quickItems(this) else null) }
            })
            val own = p.quick
            if (own != null) {
                qb.addView(divider())
                quickEditor(qb, own) { l -> Profiles.update(this, p.id) { it.copy(quick = l) }; rebuild() }
            } else {
                qb.addView(mono(TextView(this), 10f, grey).apply {
                    text = getString(R.string.quick_own_hint)
                    setPadding(0, px(2), 0, px(6))
                })
            }
            c.addView(qb)
        }

        c.addView(pill(getString(R.string.prof_delete), false) {
            darkDialog()
                .setMessage(getString(R.string.prof_delete_q, p.name))
                .setPositiveButton(android.R.string.ok) { _, _ -> Profiles.delete(this, p.id); go(null) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }, lp(-1, px(44)).apply { topMargin = px(24) })
    }

    /** Lista de apps con icono y nombre; la ✕ quita una. */
    private fun appRows(box: LinearLayout, pkgs: List<String>, onRemove: (String) -> Unit) {
        if (pkgs.isEmpty()) {
            box.addView(mono(TextView(this), 12f, grey).apply {
                text = getString(R.string.prof_apps_tap).removeSuffix("  ›")
                setPadding(0, px(8), 0, px(4))
            })
            return
        }
        pkgs.sortedBy { appLabel(it).lowercase() }.forEach { pk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(6), 0, px(6))
            }
            row.addView(android.widget.ImageView(this).apply { setImageDrawable(appIcon(pk)) },
                lp(px(30), px(30)).apply { rightMargin = px(12) })
            val installed = isInstalled(pk)
            row.addView(mono(TextView(this), 13f, if (installed) Color.WHITE else grey).apply {
                text = appLabel(pk)
                setSingleLine()
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, lp(0, -2, 1f))
            row.addView(mono(TextView(this), 14f, grey, true).apply {
                text = "✕"
                setPadding(px(14), px(6), px(4), px(6))
                setOnClickListener { onRemove(pk) }
            })
            box.addView(row)
        }
    }

    private fun renameProfile(p: Profile) {
        val et = android.widget.EditText(this).apply {
            setText(p.name)
            setSelectAllOnFocus(true)
            setSingleLine()
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val wrap = FrameLayout(this).apply {
            setPadding(px(24), px(8), px(24), 0)
            addView(et)
        }
        darkDialog()
            .setTitle(R.string.prof_name_title)
            .setView(wrap)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) Profiles.update(this, p.id) { it.copy(name = n.take(40)) }
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Selector de varias apps a pantalla completa, con buscador, iconos y nombres. */
    private fun pickApps(title: Int, selected: Set<String>, onDone: (Set<String>) -> Unit) {
        val sel = selected.toMutableSet()
        val known = apps.filter { it.second != packageName }
        val extra = selected.filter { s -> known.none { it.second == s } }.map { appLabel(it) to it }
        // Las ya elegidas arriba (orden fijo mientras está abierto), luego el resto A-Z.
        val base = (extra + known).sortedWith(
            compareBy<Pair<String, String>>({ it.second !in selected }, { it.first.lowercase() })
        )
        var shown = base

        val dlg = android.app.Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(px(16), px(40), px(16), px(8))
        }
        val count = mono(TextView(this), 10f, grey)
        fun refreshCount() { count.text = getString(R.string.picker_count, sel.size) }
        refreshCount()

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(mono(TextView(this), 18f, Color.WHITE, true).apply { text = getString(title).uppercase() },
            lp(0, -2, 1f))
        head.addView(pill(getString(R.string.picker_done), true) {
            onDone(sel.toSet()); dlg.dismiss()
        }, lp(px(96), px(40)))
        box.addView(head)

        val search = android.widget.EditText(this).apply {
            hint = getString(R.string.picker_search)
            setHintTextColor(grey)
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 13f
            setSingleLine()
            setPadding(px(16), px(10), px(16), px(10))
            background = GradientDrawable().apply {
                cornerRadius = px(100).toFloat()
                setStroke(px(1), 0x88FFFFFF.toInt())
            }
        }
        box.addView(search, lp(-1, px(44)).apply { topMargin = px(14); bottomMargin = px(8) })
        box.addView(count, lp(-2, -2).apply { leftMargin = px(6); bottomMargin = px(4) })

        val list = android.widget.ListView(this).apply {
            divider = null
            cacheColorHint = 0
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
        }
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, parent: android.view.ViewGroup?): View {
                val (name, pk) = shown[i]
                val row = (v as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(px(4), px(8), px(4), px(8))
                    addView(android.widget.ImageView(this@MainActivity), lp(px(36), px(36)).apply { rightMargin = px(14) })
                    val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                    col.addView(mono(TextView(this@MainActivity), 14f, Color.WHITE).apply {
                        setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    col.addView(mono(TextView(this@MainActivity), 9f, grey).apply {
                        setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    addView(col, lp(0, -2, 1f))
                    addView(mono(TextView(this@MainActivity), 18f, grey, true).apply {
                        setPadding(px(12), 0, px(4), 0)
                    })
                }
                (row.getChildAt(0) as android.widget.ImageView).setImageDrawable(iconCache[pk])
                val col = row.getChildAt(1) as LinearLayout
                (col.getChildAt(0) as TextView).text = name
                (col.getChildAt(1) as TextView).text = pk
                val on = pk in sel
                (row.getChildAt(2) as TextView).apply {
                    text = if (on) "●" else "○"
                    setTextColor(if (on) accent else grey)
                }
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, i, _ ->
            val pk = shown[i].second
            if (!sel.add(pk)) sel.remove(pk)
            refreshCount()
            adapter.notifyDataSetChanged()
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                shown = if (q.isEmpty()) base
                else base.filter { it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }
                adapter.notifyDataSetChanged()
            }
        })
        box.addView(list, lp(-1, 0, 1f))
        dlg.setContentView(box)
        dlg.window?.setLayout(-1, -1)
        dlg.show()

        // Los iconos se cargan fuera del hilo principal y la lista se va rellenando.
        Thread {
            var n = 0
            for ((_, pk) in base) {
                appIcon(pk)
                if (++n % 25 == 0) runOnUiThread { adapter.notifyDataSetChanged() }
            }
            runOnUiThread { adapter.notifyDataSetChanged() }
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
