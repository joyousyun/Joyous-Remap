package com.joyous.remap

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Nombre corto de un acceso (para el menú rápido y su editor). */
fun quickLabel(ctx: Context, pa: ProfileAct): String {
    val r = when (pa.act) {
        Act.CAMERA -> R.string.q_camera
        Act.TORCH -> R.string.q_torch
        Act.QR -> R.string.q_qr
        Act.WALLET -> R.string.q_wallet
        Act.RECORDER -> R.string.q_recorder
        Act.SOUND -> R.string.q_sound
        Act.MUSIC -> R.string.q_music
        Act.MUSIC_NEXT -> R.string.q_next
        Act.MUSIC_PREV -> R.string.q_prev
        Act.ROTATION -> R.string.q_rotation
        Act.DND -> R.string.q_dnd
        Act.SCREENSHOT -> R.string.q_screenshot
        Act.WIFI_BT -> R.string.q_wifi_bt
        Act.WIFI_SHARE -> R.string.q_wifi_share
        Act.ASSISTANT -> R.string.q_assistant
        else -> 0
    }
    if (r != 0) return ctx.getString(r)
    if (pa.act == Act.APP && pa.pkg != null) {
        return try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pa.pkg, 0)).toString()
        } catch (e: Exception) {
            pa.pkg
        }
    }
    return ctx.getString(pa.act.label)
}

/**
 * Menú rápido: panel flotante junto a la tecla con 2 a 6 accesos. Usa una ventana de accesibilidad
 * a pantalla completa (como el aviso del borde), así sigue la rotación y un toque fuera lo cierra.
 */
class QuickMenu(private val ctx: Context, private val onPick: (Act, String?) -> Unit) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val d = ctx.resources.displayMetrics.density

    private var root: FrameLayout? = null
    private var menu: View? = null
    private var closing = false
    private var shownAt = 0L
    private var fx = PanelFx.SLIDE
    private var dir = 1f // +1 pegado a la derecha, -1 a la izquierda
    private val autoHide = Runnable { hide() }

    val showing: Boolean get() = root != null && !closing

    /** Milisegundos desde que se abrió. */
    fun age(): Long = SystemClock.uptimeMillis() - shownAt

    fun show(items: List<ProfileAct>) {
        hide(false)
        val list = items.take(6)
        if (list.size < 2) return

        val scale = Prefs.quickSize(ctx) / 100f
        val accent = Prefs.quickColor(ctx)
        val asBar = Prefs.quickStyle(ctx) != "panel"
        val names = Prefs.quickNames(ctx)
        fx = Prefs.quickFx(ctx)

        // Lateral e inclinación según la rotación real de la pantalla.
        @Suppress("DEPRECATION")
        val rot = wm.defaultDisplay.rotation
        val keyLeft = Prefs.keyLeft(ctx)
        val atLeft = when (rot) { 0 -> keyLeft; 2 -> !keyLeft; else -> false }
        val yPct = when (rot) { 0 -> Prefs.quickY(ctx); 2 -> 100 - Prefs.quickY(ctx); else -> 50 }
        dir = if (atLeft) -1f else 1f

        val cell = (46 * scale * d).toInt()
        val cells = ArrayList<View>()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true // los huecos entre accesos no cierran el menú
            if (!asBar) {
                setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(0xF2101010.toInt())
                    cornerRadius = 24 * scale * d
                    setStroke(d.toInt().coerceAtLeast(1), 0x44FFFFFF)
                }
            }
        }

        list.forEachIndexed { i, pa ->
            if (!asBar && i > 0) {
                card.addView(View(ctx).apply { setBackgroundColor(0x22FFFFFF) },
                    LinearLayout.LayoutParams(-1, 1).apply {
                        leftMargin = (10 * d).toInt(); rightMargin = (10 * d).toInt()
                    })
            }
            val bg = GradientDrawable().apply {
                if (asBar) {
                    setColor(0xF2101010.toInt())
                    cornerRadius = 100 * d
                    setStroke(d.toInt().coerceAtLeast(1), 0x44FFFFFF)
                } else {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = 18 * scale * d
                }
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = bg
                minimumHeight = cell
                val pad = (if (asBar) 4 else 2) * d
                setPadding(pad.toInt(), (2 * d).toInt(), (if (names) 14 * d else pad).toInt(), (2 * d).toInt())
                isClickable = true
            }
            val icon = iconFor(pa, (cell * 0.64f).toInt(), accent)
            val iconBox = FrameLayout(ctx).apply { addView(icon, FrameLayout.LayoutParams(
                (cell * 0.64f).toInt(), (cell * 0.64f).toInt(), Gravity.CENTER)) }
            val label = TextView(ctx).apply {
                text = quickLabel(ctx, pa).uppercase()
                setTextColor(Color.WHITE)
                textSize = 11f * scale
                letterSpacing = 0.06f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = (150 * scale * d).toInt()
            }
            // Los iconos pegados al borde de la pantalla y los nombres hacia dentro.
            if (dir > 0) {
                if (names) row.addView(label, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = (10 * d).toInt(); leftMargin = (10 * d).toInt() })
                row.addView(iconBox, LinearLayout.LayoutParams(cell, cell))
            } else {
                row.addView(iconBox, LinearLayout.LayoutParams(cell, cell))
                if (names) row.addView(label, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = (4 * d).toInt() })
            }
            row.setOnClickListener { pick(i, cells, bg, asBar, accent, pa) }
            cells.add(row)
            card.addView(row, LinearLayout.LayoutParams(if (asBar) -2 else -1, -2).apply {
                if (asBar) {
                    if (i > 0) topMargin = (8 * scale * d).toInt()
                    gravity = if (dir > 0) Gravity.END else Gravity.START
                }
            })
        }

        val rootView = FrameLayout(ctx).apply { setOnClickListener { hide() } }
        val side = (14 * d).toInt()
        val lo = (40 * d).toInt()
        val mlp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or (if (dir > 0) Gravity.END else Gravity.START)).apply {
            if (dir > 0) rightMargin = side else leftMargin = side
        }
        rootView.addView(card, mlp)
        // Centra el menú en la altura elegida cuando ya se conoce su tamaño.
        card.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val h = rootView.height
            if (h > 0) {
                val want = (h * yPct / 100f - v.height / 2f).toInt()
                    .coerceIn(lo, (h - v.height - lo).coerceAtLeast(lo))
                val lp = v.layoutParams as FrameLayout.LayoutParams
                if (lp.topMargin != want) {
                    lp.topMargin = want
                    v.layoutParams = lp
                }
            }
        }

        val wlp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
        }

        card.alpha = 0f
        try {
            wm.addView(rootView, wlp)
        } catch (e: Exception) {
            return
        }
        root = rootView
        menu = card
        closing = false
        shownAt = SystemClock.uptimeMillis()

        // Efecto de entrada en cuanto la ventana tiene tamaño, y los accesos van entrando uno a uno.
        card.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                card.viewTreeObserver.removeOnPreDrawListener(this)
                card.pivotX = if (dir > 0) card.width.toFloat() else 0f
                card.pivotY = card.height / 2f
                when (fx) {
                    PanelFx.SLIDE -> { card.alpha = 1f; card.translationX = dir * (card.width + 28 * d) }
                    PanelFx.FADE -> card.alpha = 0f
                    PanelFx.POP -> { card.alpha = 0f; card.scaleX = 0.5f; card.scaleY = 0.5f }
                    PanelFx.DROP -> { card.alpha = 0f; card.translationY = -90 * d }
                }
                card.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(280)
                    .setInterpolator(if (fx == PanelFx.POP) OvershootInterpolator(1.4f) else DecelerateInterpolator())
                    .start()
                cells.forEachIndexed { i, c ->
                    c.alpha = 0f
                    c.translationX = dir * 22 * d
                    c.animate().alpha(1f).translationX(0f).setStartDelay(50L + i * 40L)
                        .setDuration(240).setInterpolator(DecelerateInterpolator()).start()
                }
                return true
            }
        })
        schedule()
    }

    private fun iconFor(pa: ProfileAct, size: Int, accent: Int): View {
        if (pa.act == Act.APP && pa.pkg != null) {
            try {
                return ImageView(ctx).apply {
                    setImageDrawable(ctx.packageManager.getApplicationIcon(pa.pkg))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            } catch (e: Exception) {
            }
        }
        return DotIconView(ctx, DotIconView.forAct(pa.act), accent)
    }

    /** Selección: el elegido crece y se marca, el resto se apaga; luego se cierra y se ejecuta. */
    private fun pick(i: Int, cells: List<View>, bg: GradientDrawable, asBar: Boolean, accent: Int, pa: ProfileAct) {
        if (closing || root == null) return
        closing = true
        handler.removeCallbacks(autoHide)
        if (asBar) bg.setStroke((2 * d).toInt(), accent) else bg.setColor((accent and 0x00FFFFFF) or 0x44000000)
        cells.forEachIndexed { j, c ->
            if (j == i) c.animate().scaleX(1.08f).scaleY(1.08f).setDuration(110)
                .setInterpolator(OvershootInterpolator(2f)).start()
            else c.animate().alpha(0.25f).setDuration(110).start()
        }
        handler.postDelayed({ animateOut { onPick(pa.act, pa.pkg) } }, 140)
    }

    private fun schedule() {
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, 10_000)
    }

    private fun animateOut(after: (() -> Unit)?) {
        val v = menu
        if (v == null) {
            detach()
            after?.invoke()
            return
        }
        val a = v.animate().setDuration(190).setInterpolator(AccelerateInterpolator())
        when (fx) {
            PanelFx.SLIDE -> a.translationX(dir * (v.width + 28 * d))
            PanelFx.FADE -> a.alpha(0f)
            PanelFx.POP -> a.alpha(0f).scaleX(0.5f).scaleY(0.5f)
            PanelFx.DROP -> a.alpha(0f).translationY(-90 * d)
        }
        a.withEndAction {
            detach()
            after?.invoke()
        }.start()
    }

    private fun detach() {
        handler.removeCallbacks(autoHide)
        menu?.animate()?.cancel()
        root?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        root = null
        menu = null
        closing = false
    }

    /** Se cierra con la animación de salida; sin animar se quita al instante. */
    fun hide(animated: Boolean = true) {
        handler.removeCallbacks(autoHide)
        if (root == null) return
        if (!animated) {
            handler.removeCallbacksAndMessages(null)
            detach()
            return
        }
        if (closing) return
        closing = true
        animateOut(null)
    }
}
