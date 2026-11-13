package com.joyous.remap

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.RoundedCorner
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Aviso en pantalla: una ventanita arriba y, si se activa, una línea en el borde real
 * del dispositivo con la animación elegida.
 */
class Overlay(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: FrameLayout? = null
    private var anim: ValueAnimator? = null

    fun flash(label: String, reverse: Boolean = false) {
        remove()
        val d = ctx.resources.displayMetrics.density
        val color = Prefs.color(ctx)

        val chip = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (9 * d).toInt(), (16 * d).toInt(), (9 * d).toInt())
            background = GradientDrawable().apply {
                setColor(0xE6101010.toInt())
                cornerRadius = 100 * d
                setStroke((1 * d).toInt().coerceAtLeast(1), 0x55FFFFFF)
            }
        }
        chip.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }, LinearLayout.LayoutParams((8 * d).toInt(), (8 * d).toInt()).apply {
            rightMargin = (10 * d).toInt()
        })
        chip.addView(TextView(ctx).apply {
            text = label.uppercase()
            setTextColor(Color.WHITE)
            textSize = 12f
            letterSpacing = 0.06f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        })

        val root = FrameLayout(ctx)
        var edgeView: EdgeView? = null
        if (Prefs.edge(ctx)) {
            edgeView = EdgeView(
                ctx, Prefs.edgeColor(ctx), Prefs.keyY(ctx) / 100f,
                Prefs.edgeThick(ctx).toFloat(), Prefs.edgeSoft(ctx).toFloat(),
                reverse, Prefs.anim(ctx)
            )
            root.addView(edgeView, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(
            chip,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = (44 * d).toInt()
            }
        )

        val b = wm.maximumWindowMetrics.bounds
        val lp = WindowManager.LayoutParams(
            b.width(), b.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                // Sin este flag la ventana se dibuja por software y va con tirones.
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
        }
        try {
            wm.addView(root, lp)
        } catch (e: Exception) {
            return
        }
        view = root
        chip.alpha = 0f
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (reverse) 700 else 1800 // al apagar, más rápido
            addUpdateListener {
                val t = it.animatedValue as Float
                edgeView?.t = t
                // La ventanita entra y sale con escala, desplazamiento y fundido.
                val p = when {
                    t < 0.15f -> t / 0.15f
                    t > 0.75f -> (1f - t) / 0.25f
                    else -> 1f
                }.coerceIn(0f, 1f)
                val sc = 0.8f + 0.2f * p
                chip.alpha = p
                chip.scaleX = sc
                chip.scaleY = sc
                chip.translationY = -24 * d * (1f - p)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    remove()
                }
            })
            start()
        }
    }

    fun remove() {
        anim?.removeAllListeners()
        anim?.cancel()
        anim = null
        view?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        view = null
    }
}

/**
 * Línea que sigue el borde de la pantalla (esquinas incluidas). Estilos: expandirse desde
 * la tecla, barrido, pulso y destello. Al apagar la animación va al revés.
 */
private class EdgeView(
    c: Context,
    private val edgeColor: Int, // 0 = arcoíris
    private val keyFrac: Float,
    private val thickDp: Float,
    private val softDp: Float,
    private val reverse: Boolean,
    private val animStyle: Anim
) : View(c) {

    var t = 0f
        set(v) {
            field = v
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var shape = Path()
    private var measure: PathMeasure? = null
    private var rainbow: SweepGradient? = null
    private var len = 0f
    private var start = 0f
    private val seg = Path()
    private val tmp = Path()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        shape = buildShape(w, h)
        val m = PathMeasure(shape, true)
        measure = m
        len = m.length
        rainbow = SweepGradient(w / 2f, h / 2f, Prefs.RAINBOW, null)
        // Punto del contorno más cercano a la tecla (lateral derecho).
        val tx = w.toFloat()
        val ty = h * keyFrac
        val pos = FloatArray(2)
        var best = Float.MAX_VALUE
        var bestDist = 0f
        var dist = 0f
        while (dist < len) {
            m.getPosTan(dist, pos, null)
            val dx = pos[0] - tx
            val dy = pos[1] - ty
            val dd = dx * dx + dy * dy
            if (dd < best) {
                best = dd
                bestDist = dist
            }
            dist += 4f
        }
        start = bestDist
    }

    private fun buildShape(w: Int, h: Int): Path {
        val ins = rootWindowInsets
        fun r(pos: Int) = ins?.getRoundedCorner(pos)?.radius?.toFloat() ?: (44 * d)
        val tl = r(RoundedCorner.POSITION_TOP_LEFT)
        val tr = r(RoundedCorner.POSITION_TOP_RIGHT)
        val br = r(RoundedCorner.POSITION_BOTTOM_RIGHT)
        val bl = r(RoundedCorner.POSITION_BOTTOM_LEFT)
        return Path().apply {
            addRoundRect(
                RectF(0f, 0f, w.toFloat(), h.toFloat()),
                floatArrayOf(tl, tl, tr, tr, br, br, bl, bl),
                Path.Direction.CW
            )
        }
    }

    private fun ease(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

    /** Construye una sola vez por fotograma el tramo del contorno a dibujar. */
    private fun buildSeg(m: PathMeasure, a: Float, b: Float) {
        var s = a
        while (s < 0f) s += len
        while (s >= len) s -= len
        val e = s + (b - a)
        seg.rewind()
        m.getSegment(s, minOf(e, len), seg, true)
        if (e > len) {
            tmp.rewind()
            m.getSegment(0f, e - len, tmp, true)
            seg.addPath(tmp)
        }
    }

    private fun tint(alpha: Float) {
        paint.color = if (edgeColor == 0) Color.WHITE else edgeColor
        paint.alpha = alpha.toInt().coerceIn(0, 255)
    }

    override fun onDraw(canvas: Canvas) {
        try {
            draw2(canvas)
        } catch (e: Exception) {
        }
    }

    private fun draw2(canvas: Canvas) {
        val m = measure ?: return
        if (len <= 0f) return

        var fade = 1f
        var full = false
        var a = 0f
        var b = 0f
        when (animStyle) {
            Anim.SPREAD -> {
                // Al apagar es la misma animación al revés: se recoge hacia la tecla.
                val spread = if (reverse) ease(1f - t) else ease((t / 0.5f).coerceAtMost(1f))
                fade = if (reverse) 1f else if (t > 0.7f) 1f - (t - 0.7f) / 0.3f else 1f
                val half = len / 2f * spread
                a = start - half
                b = start + half
            }
            Anim.SWEEP -> {
                val tail = len * 0.3f
                if (reverse) {
                    val head = start - t * len
                    a = head
                    b = head + tail
                } else {
                    val head = start + t * len
                    a = head - tail
                    b = head
                }
                fade = if (t < 0.1f) t / 0.1f else if (t > 0.85f) (1f - t) / 0.15f else 1f
            }
            Anim.PULSE -> {
                full = true
                fade = Math.sin(Math.PI * t).toFloat()
            }
            Anim.FLASH -> {
                full = true
                fade = (1f - t) * (1f - t)
            }
        }
        if (fade <= 0.01f) return

        val target = if (full) shape else {
            buildSeg(m, a, b)
            seg
        }

        canvas.save()
        canvas.clipPath(shape) // solo se ve la mitad de dentro del trazo
        paint.shader = if (edgeColor == 0) rainbow else null
        // Halo difuminado: capas translúcidas cada vez más estrechas.
        if (softDp > 0f) {
            val n = 5
            for (k in 0 until n) {
                val e = (thickDp + softDp * (n - k) / n) * d
                paint.strokeWidth = e * 2
                tint(34 * fade)
                canvas.drawPath(target, paint)
            }
        }
        paint.strokeWidth = thickDp * d * 2
        tint(255 * fade)
        canvas.drawPath(target, paint)
        canvas.restore()
    }
}

/** Slider vertical rectangular o cuadrado (esquinas configurables) que se rellena desde abajo. */
private class SideSlider(
    c: Context,
    private val accent: Int,
    private val radius: Float,
    private val steps: Int,
    private val textColor: Int,
    private val style: PanelStyle,
    private val borderW: Float,
    private val borderColor: Int,
    private val animOn: Boolean,
    private val barFx: BarFx
) : View(c) {

    var fraction = 1f
        set(v) {
            field = v.coerceIn(0f, 1f)
            retarget()
            invalidate()
        }

    // ---- Animación de llenado / vaciado ----
    private val animMs = 220L
    private var inited = false
    private var oldLit = 0
    private var shown = 1f
    private val aFrom = FloatArray(32)
    private val aTo = FloatArray(32)
    private val aT0 = LongArray(32)

    private fun prog(i: Int): Float {
        if (!animOn) return aTo[i]
        val k = ((SystemClock.uptimeMillis() - aT0[i]).toFloat() / animMs).coerceIn(0f, 1f)
        val e = 1f - (1f - k) * (1f - k)
        return aFrom[i] + (aTo[i] - aFrom[i]) * e
    }

    private fun retarget() {
        val lit = seg()
        val now = SystemClock.uptimeMillis()
        for (i in 0 until steps) {
            val tgt = if (i <= lit) 1f else 0f
            if (!inited || !animOn) {
                aFrom[i] = tgt; aTo[i] = tgt; aT0[i] = 0
            } else if (tgt != aTo[i]) {
                aFrom[i] = prog(i)
                aTo[i] = tgt
                val order = if (tgt == 1f) i - oldLit - 1 else oldLit - i
                val delay = if (barFx == BarFx.CASCADE) order.coerceAtLeast(0) * 40L else 0L
                aT0[i] = now + delay
            }
        }
        oldLit = lit
    }

    private fun drawCell(canvas: Canvas, i: Int, rad: Float) {
        canvas.drawRoundRect(cell, rad, rad, offPaint)
        val p = prog(i)
        if (p <= 0.01f) return
        if (barFx == BarFx.GROW) {
            val cx = cell.centerX()
            val cy = cell.centerY()
            val hw = cell.width() / 2f * p
            val hh = cell.height() / 2f * p
            val keep = RectF(cell)
            cell.set(cx - hw, cy - hh, cx + hw, cy + hh)
            canvas.drawRoundRect(cell, rad * p, rad * p, onPaint)
            cell.set(keep)
        } else {
            onPaint.alpha = (255 * p).toInt()
            canvas.drawRoundRect(cell, rad, rad, onPaint)
            onPaint.alpha = 255
        }
    }
    var interactive = true
    var onFraction: ((Float) -> Unit)? = null
    var onDrag: ((Boolean) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2101010.toInt() }
    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A2A.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
        color = 0x44FFFFFF
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = 15 * d
        color = textColor
        setShadowLayer(3 * d, 0f, 0f, Color.BLACK)
    }
    private val rect = RectF()
    private val cell = RectF()

    private fun seg() = Math.round(fraction * (steps - 1)).coerceIn(0, steps - 1)

    private val clip = Path()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderW
        color = borderColor
    }

    private fun drawBorder(canvas: Canvas, w: Float, h: Float, r: Float) {
        if (borderW <= 0f) return
        val half = borderW / 2f
        rect.set(half, half, w - half, h - half)
        canvas.drawRoundRect(rect, r, r, borderPaint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (!inited) {
            inited = true
            retarget()
            shown = fraction
        }
        shown = if (animOn) shown + (fraction - shown) * 0.3f else fraction
        val lit = seg()
        when (style) {
            PanelStyle.PILL -> {
                val r = if (h > w * 1.2f) w / 2f else minOf(radius, w / 2f, h / 2f)
                rect.set(0f, 0f, w, h)
                clip.rewind()
                clip.addRoundRect(rect, r, r, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawRect(rect, bgPaint)
                val gap = 3 * d
                val ch = (h - gap * (steps - 1)) / steps
                for (i in 0 until steps) {
                    val bottom = h - i * (ch + gap)
                    cell.set(0f, bottom - ch, w, bottom)
                    drawCell(canvas, i, 0f)
                }
                canvas.restore()
                drawBorder(canvas, w, h, r)
            }
            PanelStyle.FRAME -> {
                // Píldora segmentada dentro de otra píldora, con margen entre ambas
                val r = if (h > w * 1.2f) w / 2f else minOf(radius, w / 2f, h / 2f)
                rect.set(0f, 0f, w, h)
                canvas.drawRoundRect(rect, r, r, bgPaint)
                val pad = borderW + 6 * d
                val ir = (r - pad).coerceAtLeast(0f)
                rect.set(pad, pad, w - pad, h - pad)
                clip.rewind()
                clip.addRoundRect(rect, ir, ir, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clip)
                val gap = 4 * d
                val ih = h - pad * 2
                val ch = (ih - gap * (steps - 1)) / steps
                for (i in 0 until steps) {
                    val bottom = h - pad - i * (ch + gap)
                    cell.set(pad, bottom - ch, w - pad, bottom)
                    drawCell(canvas, i, 0f)
                }
                canvas.restore()
                drawBorder(canvas, w, h, r)
            }
            PanelStyle.SMOOTH -> {
                val r = minOf(radius, w / 2f, h / 2f)
                rect.set(0f, 0f, w, h)
                clip.rewind()
                clip.addRoundRect(rect, r, r, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawRect(rect, bgPaint)
                canvas.drawRect(0f, h * (1f - shown), w, h, onPaint)
                canvas.restore()
                drawBorder(canvas, w, h, r)
            }
            PanelStyle.BARS -> {
                val r = minOf(radius, w / 2f, h / 2f)
                rect.set(0f, 0f, w, h)
                canvas.drawRoundRect(rect, r, r, bgPaint)
                val padX = borderW + 12 * d
                val padY = borderW + 10 * d
                val slot = (h - padY * 2) / steps
                val bh = minOf(slot * 0.4f, 9 * d)
                for (i in 0 until steps) {
                    val cy = h - padY - slot * (i + 0.5f)
                    cell.set(padX, cy - bh / 2f, w - padX, cy + bh / 2f)
                    drawCell(canvas, i, bh / 2f)
                }
                drawBorder(canvas, w, h, r)
            }
        }
        if (animOn) {
            val now = SystemClock.uptimeMillis()
            var running = Math.abs(fraction - shown) > 0.002f
            for (i in 0 until steps) if (now - aT0[i] < animMs) running = true
            if (running) postInvalidateOnAnimation() else shown = fraction
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (e.actionMasked == MotionEvent.ACTION_DOWN) onDrag?.invoke(true)
                if (height > 0) {
                    val raw = (1f - e.y / height).coerceIn(0f, 1f)
                    val f = if (style == PanelStyle.SMOOTH) raw else {
                        val idx = (raw * steps).toInt().coerceIn(0, steps - 1)
                        if (steps > 1) idx / (steps - 1f) else 1f
                    }
                    if (f != fraction) {
                        fraction = f
                        onFraction?.invoke(f)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onDrag?.invoke(false)
        }
        return true
    }
}

/** Panel lateral junto a la tecla: slider vertical y botón de apagar, con efectos de entrada y salida. */
class TorchPanel(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val d = ctx.resources.displayMetrics.density
    private var view: View? = null
    private var curFx = PanelFx.SLIDE
    private var curW = 0
    private val autoHide = Runnable { hide() }

    fun show(maxLvl: Int, color: Int, onLevel: (Int) -> Unit, onOff: () -> Unit) {
        hide(false)
        val shape = Prefs.panelShape(ctx)
        val fx = Prefs.panelFx(ctx)
        val scale = Prefs.panelSize(ctx) / 100f
        val w = ((if (shape == PanelShape.RECT) 64 else 140) * scale * d).toInt()
        val h = ((if (shape == PanelShape.RECT) 220 else 140) * scale * d).toInt()
        val offH = (36 * d).toInt()
        val gap = (8 * d).toInt()
        val total = h + gap + offH

        val steps = Prefs.stepsCount(ctx)
        var lastIdx = Prefs.panelIdx(ctx)
        val slider = SideSlider(ctx, color, Prefs.panelRadius(ctx) * d, steps, Prefs.panelText(ctx),
            Prefs.panelStyle(ctx), Prefs.panelBorder(ctx) * d, Prefs.panelBorderColor(ctx),
            Prefs.panelAnim(ctx), Prefs.panelBarFx(ctx)).apply {
            fraction = lastIdx / (steps - 1f)
            interactive = true
            onFraction = { f ->
                val idx = Math.round(f * (steps - 1))
                if (idx != lastIdx) {
                    lastIdx = idx
                    Prefs.setPanelIdx(ctx, idx)
                    onLevel(Prefs.flashLevel(ctx, idx, maxLvl))
                }
                schedule()
            }
            onDrag = { dragging ->
                if (dragging) handler.removeCallbacks(autoHide) else schedule()
            }
        }
        val off = TextView(ctx).apply {
            text = ctx.getString(R.string.btn_off).uppercase()
            setTextColor(Prefs.panelText(ctx))
            textSize = 11f
            letterSpacing = 0.06f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xF2101010.toInt())
                cornerRadius = minOf(Prefs.panelRadius(ctx) * d, offH / 2f)
                setStroke((Prefs.panelBorder(ctx) * d).toInt(), Prefs.panelBorderColor(ctx))
            }
            setOnClickListener { onOff() }
        }
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        box.addView(slider, LinearLayout.LayoutParams(w, h))
        box.addView(off, LinearLayout.LayoutParams(w, offH).apply { topMargin = gap })

        val dm = ctx.resources.displayMetrics
        val lo = (40 * d).toInt()
        val hi = (dm.heightPixels - total - 40 * d).toInt().coerceAtLeast(lo)
        val lp = WindowManager.LayoutParams(
            w, total,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (14 * d).toInt() // pegado al lateral donde está la tecla
            y = (dm.heightPixels * Prefs.panelY(ctx) / 100f - total / 2f).toInt().coerceIn(lo, hi)
        }

        curFx = fx
        curW = w
        prepare(box, fx, w, total)
        try {
            wm.addView(box, lp)
            view = box
            animateIn(box, fx)
            schedule()
        } catch (e: Exception) {
        }
    }

    // ---------- Efectos ----------

    private fun prepare(v: View, fx: PanelFx, w: Int, total: Int) {
        v.pivotX = w.toFloat()
        v.pivotY = total / 2f
        when (fx) {
            PanelFx.SLIDE -> v.translationX = w + 28 * d
            PanelFx.FADE -> v.alpha = 0f
            PanelFx.POP -> {
                v.alpha = 0f
                v.scaleX = 0.5f
                v.scaleY = 0.5f
            }
            PanelFx.DROP -> {
                v.alpha = 0f
                v.translationY = -90 * d
            }
        }
    }

    private fun animateIn(v: View, fx: PanelFx) {
        v.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(280)
            .setInterpolator(if (fx == PanelFx.POP) OvershootInterpolator(1.4f) else DecelerateInterpolator())
            .start()
    }

    private fun animateOut(v: View, fx: PanelFx, w: Int, end: () -> Unit) {
        val a = v.animate().setDuration(190).setInterpolator(AccelerateInterpolator())
        when (fx) {
            PanelFx.SLIDE -> a.translationX(w + 28 * d)
            PanelFx.FADE -> a.alpha(0f)
            PanelFx.POP -> a.alpha(0f).scaleX(0.5f).scaleY(0.5f)
            PanelFx.DROP -> a.alpha(0f).translationY(-90 * d)
        }
        a.withEndAction { end() }.start()
    }

    private fun schedule() {
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, 6000)
    }

    private fun detach(v: View) {
        try {
            wm.removeView(v)
        } catch (e: Exception) {
        }
    }

    /** Se va con el efecto de salida; sin animar se quita al instante. */
    fun hide(animated: Boolean = true) {
        handler.removeCallbacks(autoHide)
        val v = view ?: return
        view = null
        v.animate().cancel()
        if (!animated) {
            detach(v)
            return
        }
        animateOut(v, curFx, curW) { detach(v) }
    }
}
