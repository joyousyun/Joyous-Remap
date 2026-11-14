package com.joyous.remap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

class KeyService : AccessibilityService() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    // El servicio sigue vivo al cambiar el idioma en la app: se vuelve a leer en cada uso.
    private var resCode: String? = null
    private var resCache: android.content.res.Resources? = null

    override fun getResources(): android.content.res.Resources {
        val base = super.getResources()
        return try {
            val code = Lang.get(this)
            if (resCode == code && resCache != null) return resCache!!
            val cfg = android.content.res.Configuration(base.configuration)
            val loc = if (code.isEmpty()) android.content.res.Resources.getSystem().configuration.locales[0]
            else java.util.Locale(code)
            cfg.setLocale(loc)
            val r = createConfigurationContext(cfg).resources
            resCode = code
            resCache = r
            r
        } catch (e: Throwable) {
            base
        }
    }


    private lateinit var overlay: Overlay
    private lateinit var panel: TorchPanel
    private lateinit var cm: CameraManager
    private val handler = Handler(Looper.getMainLooper())

    private var torchId: String? = null
    private var maxLevel = 1
    private var torchOn = false
    private var fgPkg: String? = null

    // Perfil aplicable a la secuencia de pulsaciones en curso (se decide al empezar cada una).
    private var eff: Eff = Eff.NONE

    private fun act(g: Gesture): Act = eff.act(this, g)

    // Teclados: sus ventanas no cuentan como "app en primer plano".
    private var imePkgs: Set<String> = emptySet()
    private var imeAt = 0L

    private fun isIgnoredPkg(p: String): Boolean {
        if (p == packageName || p == "com.android.systemui") return true
        val now = System.currentTimeMillis()
        if (now - imeAt > 600_000L) {
            imeAt = now
            imePkgs = try {
                getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .inputMethodList.map { it.packageName }.toSet()
            } catch (e: Throwable) {
                emptySet()
            }
        }
        return p in imePkgs
    }

    /**
     * App en primer plano: la ventana activa en este momento; si es nuestra, el teclado o la barra
     * del sistema, la última app real vista. Solo se consulta al pulsar, no hay trabajo en reposo.
     */
    private fun foregroundPkg(): String? {
        val root = try { rootInActiveWindow?.packageName?.toString() } catch (e: Throwable) { null }
        if (root == packageName) return root
        if (root != null && !isIgnoredPkg(root)) return root
        return fgPkg
    }

    // Estado de pulsaciones
    private var taps = 0
    private var longFired = false
    private val resolveTaps = Runnable { fireTaps() }
    private val longRun = Runnable { onLong() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DeviceProfile.autoApply(this)
        overlay = Overlay(this)
        panel = TorchPanel(this)
        cm = getSystemService(CameraManager::class.java)
        initTorch()
    }

    // ---------- Detección de pulsaciones ----------

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // La tecla Essential llega como KEYCODE_UNKNOWN (0).
        if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN) return false
        // Al empezar una pulsación nueva se elige perfil según la app de delante.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && taps == 0) {
            eff = if (Profiles.any(this)) Profiles.resolve(this, foregroundPkg()) else Eff.NONE
        }
        // App excluida: Joyous Remap no hace nada y la tecla sigue su camino normal.
        if (eff.excluded) return false
        if (Gesture.values().all { act(it) == Act.NONE }) return false

        // Ya dentro de la cámara: cada pulsación hace una foto al instante y sin avisos.
        if (cameraMode()) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                handler.removeCallbacks(longRun)
                handler.removeCallbacks(resolveTaps)
                taps = 0
                if (Prefs.haptic(this)) buzz()
                shoot()
            }
            return true
        }

        val sp = Prefs.sp(this)
        val sawUp = sp.getBoolean("saw_up", false)

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return true
                longFired = false
                if (sawUp) {
                    if (act(Gesture.LONG) != Act.NONE) {
                        handler.postDelayed(longRun, Prefs.longMs(this).toLong())
                    }
                } else {
                    // Si el móvil no envía "soltar", se cuenta al pulsar.
                    registerTap()
                }
            }
            KeyEvent.ACTION_UP -> {
                if (!sawUp) {
                    sp.edit().putBoolean("saw_up", true).apply()
                } else {
                    handler.removeCallbacks(longRun)
                    if (!longFired) registerTap()
                }
            }
        }
        return true
    }

    private fun maxTaps(): Int = when {
        act(Gesture.TRIPLE) != Act.NONE -> 3
        act(Gesture.DOUBLE) != Act.NONE -> 2
        else -> 1
    }

    private fun registerTap() {
        taps++
        handler.removeCallbacks(resolveTaps)
        // Sin dobles ni triples configurados, la acción sale al instante.
        if (taps >= maxTaps()) fireTaps()
        else handler.postDelayed(resolveTaps, Prefs.tapMs(this).toLong())
    }

    private fun fireTaps() {
        handler.removeCallbacks(resolveTaps)
        val n = taps
        taps = 0
        val g = when (n) {
            1 -> Gesture.SINGLE
            2 -> Gesture.DOUBLE
            else -> Gesture.TRIPLE
        }
        run(g)
    }

    private fun onLong() {
        longFired = true
        taps = 0
        handler.removeCallbacks(resolveTaps)
        run(Gesture.LONG)
    }

    private fun run(g: Gesture) {
        val a = act(g)
        if (a == Act.NONE) return
        if (Prefs.haptic(this)) buzz()
        execute(a, eff.pkg(this, g))
    }

    private fun buzz() {
        try {
            getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } catch (e: Exception) {
        }
    }

    private fun flash(res: Int, vararg args: Any) {
        try {
            overlay.flash(getString(res, *args))
        } catch (e: Throwable) {
        }
    }

    /** Aviso de apagado: la animación del borde va al revés y más rápido. */
    private fun flashOff(res: Int) {
        try {
            overlay.flash(getString(res), reverse = true)
        } catch (e: Throwable) {
        }
    }

    // ---------- Acciones ----------

    private fun execute(act: Act, pkg: String?) {
        when (act) {
            Act.NONE -> {}
            Act.APP -> launchApp(pkg)
            Act.CAMERA -> cameraAction()
            Act.TORCH -> toggleTorch()
            Act.QR -> openQr()
            Act.WALLET -> launchPackage("com.google.android.apps.walletnfcrel", R.string.act_wallet)
            Act.RECORDER -> openRecorder()
            Act.SOUND -> cycleSound()
            Act.MUSIC -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, R.string.lbl_play_pause)
            Act.MUSIC_NEXT -> media(KeyEvent.KEYCODE_MEDIA_NEXT, R.string.lbl_next)
            Act.MUSIC_PREV -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, R.string.lbl_prev)
            Act.ROTATION -> toggleRotation()
            Act.DND -> toggleDnd()
            Act.SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            Act.WIFI_BT -> toggleWifiBt()
            Act.WIFI_SHARE -> {
                flash(R.string.lbl_wifi_settings)
                startLater(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            Act.ASSISTANT -> openAssistant()
        }
    }

    /** Deja que el aviso se dibuje unos fotogramas antes de lanzar la app. */
    private fun startLater(i: Intent) {
        val delay = if (Prefs.edge(this)) 120L else 0L
        handler.postDelayed({ startSafe(i) }, delay)
    }

    private fun startSafe(i: Intent): Boolean {
        return try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun launchApp(pkg: String?) {
        if (pkg == null) return
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            flash(R.string.lbl_not_found)
            return
        }
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        flash(R.string.lbl_opening, label)
        startLater(intent)
    }

    private fun launchPackage(pkg: String, nameRes: Int) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            flash(R.string.lbl_not_found)
            return
        }
        flash(R.string.lbl_opening, getString(nameRes))
        startLater(intent)
    }

    private fun openQr() {
        flash(R.string.lbl_qr)
        val gms = Intent().setClassName(
            "com.google.android.gms",
            "com.google.android.gms.mlkit.barcode.ui.PreloadActivity"
        )
        if (startSafe(gms)) return
        packageManager.getLaunchIntentForPackage("com.google.ar.lens")?.let {
            if (startSafe(it)) return
        }
        startSafe(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }

    private fun openRecorder() {
        flash(R.string.lbl_recorder)
        startLater(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION))
    }

    private fun openAssistant() {
        flash(R.string.lbl_assistant)
        if (!startSafe(Intent(Intent.ACTION_ASSIST))) startSafe(Intent(Intent.ACTION_VOICE_COMMAND))
    }

    private fun cycleSound() {
        val am = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        val next = when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE ->
                if (nm.isNotificationPolicyAccessGranted) AudioManager.RINGER_MODE_SILENT
                else AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        try {
            am.ringerMode = next
            flash(
                when (next) {
                    AudioManager.RINGER_MODE_VIBRATE -> R.string.lbl_vibrate
                    AudioManager.RINGER_MODE_SILENT -> R.string.lbl_silent
                    else -> R.string.lbl_sound
                }
            )
        } catch (e: SecurityException) {
            flash(R.string.lbl_need_policy)
        }
    }

    private fun media(code: Int, label: Int) {
        val am = getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        flash(label)
    }

    private fun toggleRotation() {
        if (!Settings.System.canWrite(this)) {
            flash(R.string.lbl_need_write)
            return
        }
        val cur = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (cur == 1) 0 else 1)
        if (cur == 1) flashOff(R.string.lbl_rot_off) else flash(R.string.lbl_rot_on)
    }

    private fun toggleDnd() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            flash(R.string.lbl_need_policy)
            return
        }
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
        if (on) flashOff(R.string.lbl_dnd_off) else flash(R.string.lbl_dnd_on)
    }

    private fun toggleWifiBt() {
        if (ShizukuHelper.running() && ShizukuHelper.granted()) {
            val wifi = applicationContext.getSystemService(WifiManager::class.java)
            val on = wifi.isWifiEnabled
            val s = if (on) "disable" else "enable"
            if (on) flashOff(R.string.lbl_conn_off) else flash(R.string.lbl_conn_on)
            Thread {
                try {
                    ShizukuHelper.run("svc wifi $s; svc bluetooth $s")
                } catch (e: Throwable) {
                }
            }.start()
        } else {
            // Sin Shizuku Android no deja cambiarlos: se abre el panel rápido.
            flash(R.string.lbl_conn_panel)
            startSafe(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        }
    }

    // ---------- Cámara ----------

    private fun cameraPackage(): String? {
        val i = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        val def = packageManager.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        if (def != null && def != "android") return def
        return packageManager.queryIntentActivities(i, 0).firstOrNull()?.activityInfo?.packageName
    }

    private fun isCameraForeground(): Boolean {
        val rootPkg = rootInActiveWindow?.packageName?.toString()
        val p = if (rootPkg != null && rootPkg != packageName) rootPkg else fgPkg
        return p != null && (p == cameraPackage() || p.contains("camera", ignoreCase = true))
    }

    private fun cameraMode(): Boolean =
        Gesture.values().any { act(it) == Act.CAMERA } && isCameraForeground()

    private fun cameraAction() {
        if (isCameraForeground()) {
            shoot()
        } else {
            flash(R.string.lbl_camera_open)
            startLater(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        }
    }

    /** Toca de verdad el botón de disparo; muchas cámaras ignoran el "click" accesible. */
    private fun shoot() {
        val node = rootInActiveWindow?.let { findShutter(it) }
        if (node != null) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) {
                tap(r.exactCenterX(), r.exactCenterY())
                return
            }
        }
        val b = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        tap(b.width() / 2f, b.height() * Prefs.shutterY(this) / 100f)
    }

    private fun findShutter(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val dm = resources.displayMetrics
        val idWords = listOf("shutter", "capture_button", "take_photo", "photo_button")
        val descWords = listOf(
            "shutter", "obturador", "disparador", "tomar foto", "hacer foto",
            "hacer una foto", "take photo", "take picture"
        )
        val r = Rect()
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0f
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            val desc = n.contentDescription?.toString()?.lowercase().orEmpty()
            if (n.isVisibleToUser && (idWords.any { it in id } || descWords.any { it in desc })) {
                return n
            }
            // Plan B: botón redondo grande, clicable y centrado en la parte baja.
            if (n.isClickable) {
                n.getBoundsInScreen(r)
                val w = r.width()
                val h = r.height()
                if (w > 0 && h > 0) {
                    val ratio = w.toFloat() / h
                    val dp = w / dm.density
                    val off = abs(r.exactCenterX() - dm.widthPixels / 2f)
                    if (r.centerY() > dm.heightPixels * 0.6f &&
                        off < dm.widthPixels * 0.18f &&
                        ratio in 0.8f..1.25f && dp in 48f..150f
                    ) {
                        val score = dp - off / dm.density
                        if (score > bestScore) {
                            bestScore = score
                            best = n
                        }
                    }
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return best
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ---------- Linterna ----------

    private fun initTorch() {
        try {
            for (id in cm.cameraIdList) {
                val c = cm.getCameraCharacteristics(id)
                val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val back = c.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
                if (hasFlash && back) {
                    torchId = id
                    maxLevel = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    Prefs.setTorchMax(this, maxLevel)
                    break
                }
            }
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) {
                        torchOn = enabled
                        if (!enabled) panel.hide()
                    }
                }
            }, handler)
        } catch (e: Exception) {
        }
    }

    private fun stepFlash(level: Int) {
        applyLevel(level)
        torchOn = true
    }

    private fun currentLevel(): Int =
        Prefs.sp(this).getInt(Prefs.KEY_LEVEL, maxLevel).coerceIn(1, maxLevel)

    private fun applyLevel(level: Int) {
        val id = torchId ?: return
        try {
            if (maxLevel > 1) {
                cm.turnOnTorchWithStrengthLevel(id, level.coerceIn(1, maxLevel))
            } else {
                cm.setTorchMode(id, true)
            }
            Prefs.sp(this).edit().putInt(Prefs.KEY_LEVEL, level).apply()
        } catch (e: Exception) {
        }
    }

    private fun turnOffTorch() {
        val id = torchId ?: return
        try {
            cm.setTorchMode(id, false)
            torchOn = false
        } catch (e: Exception) {
        }
        panel.hide()
        flashOff(R.string.lbl_torch_off)
    }

    private fun toggleTorch() {
        val id = torchId
        if (id == null) {
            flash(R.string.lbl_no_torch)
            return
        }
        try {
            if (torchOn) {
                turnOffTorch()
            } else {
                val idx = Prefs.panelIdx(this)
                stepFlash(Prefs.flashLevel(this, idx, maxLevel))
                flash(R.string.lbl_torch_on)
                panel.show(maxLevel, Prefs.panelColor(this), { stepFlash(it) }, { turnOffTorch() })
            }
        } catch (e: Exception) {
            flash(R.string.lbl_torch_err)
        }
    }

    // ---------- Ciclo de vida ----------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString() ?: return
            if (!isIgnoredPkg(p)) fgPkg = p
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.remove()
        if (::panel.isInitialized) panel.hide(false)
        super.onDestroy()
    }
}
