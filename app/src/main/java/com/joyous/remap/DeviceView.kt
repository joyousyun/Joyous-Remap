package com.joyous.remap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Vista trasera de cada Nothing Phone, dibujada con vectores (sin imágenes externas)
 * y con la posición de la tecla Essential marcada en el lateral.
 */
class DeviceView(ctx: Context) : View(ctx) {

    var model: NothingModel? = null
    var left = false     // lateral de la tecla mirando la pantalla
    var y = 50           // altura en %
    var accent = 0xFFD71921.toInt()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()

    // geometría del dibujo actual
    private var x0 = 0f; private var y0 = 0f; private var w = 0f; private var h = 0f

    private fun X(v: Float) = x0 + v * w
    private fun Y(v: Float) = y0 + v * h
    private fun W(v: Float) = v * w

    private fun fill(c: Int) { p.style = Paint.Style.FILL; p.color = c }
    private fun line(c: Int, wd: Float) { p.style = Paint.Style.STROKE; p.color = c; p.strokeWidth = W(wd); p.strokeCap = Paint.Cap.BUTT }
    private fun circle(c: Canvas, cx: Float, cy: Float, rad: Float) = c.drawCircle(X(cx), Y(cy), W(rad), p)
    private fun rrect(c: Canvas, l: Float, t: Float, rr: Float, b: Float, rad: Float) {
        r.set(X(l), Y(t), X(rr), Y(b)); c.drawRoundRect(r, W(rad), W(rad), p)
    }
    private fun square(c: Canvas, cx: Float, cy: Float, s: Float, col: Int) {
        fill(col); r.set(X(cx) - W(s) / 2, Y(cy) - W(s) / 2, X(cx) + W(s) / 2, Y(cy) + W(s) / 2); c.drawRect(r, p)
    }

    private fun lens(c: Canvas, cx: Float, cy: Float, rad: Float, ring: Int) {
        fill(ring); circle(c, cx, cy, rad)
        fill(0xFF050505.toInt()); circle(c, cx, cy, rad * 0.82f)
        fill(0xFF26324D.toInt()); circle(c, cx, cy, rad * 0.45f)
        fill(0x66FFFFFF); circle(c, cx - rad * 0.15f, cy - rad * 0.12f * w / h, rad * 0.14f)
    }

    private fun arcGlyph(c: Canvas, cx: Float, cy: Float, rad: Float, a0: Float, a1: Float) {
        r.set(X(cx) - W(rad), Y(cy) - W(rad), X(cx) + W(rad), Y(cy) + W(rad))
        line(0xFFFFFFFF.toInt(), 0.034f); p.strokeCap = Paint.Cap.ROUND
        c.drawArc(r, a0, a1 - a0, false, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun matrixDisc(c: Canvas, cx: Float, cy: Float, rad: Float) {
        fill(0xFF000000.toInt()); circle(c, cx, cy, rad)
        val hs = intArrayOf(1, 2, 4, 3, 6, 4, 3, 5, 2, 1)
        val st = rad * 0.17f
        fill(0xFFFFFFFF.toInt())
        for (i in hs.indices) for (j in -hs[i] / 2..(hs[i] - 1) / 2) {
            circle(c, cx + (i - 4.5f) * st, cy + j * st * w / h * (h / w), st * 0.36f)
        }
    }

    private fun label(c: Canvas, rx: Float, ry: Float, col: Int) {
        c.save()
        c.rotate(-90f, X(rx), Y(ry))
        p.style = Paint.Style.FILL; p.color = col
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = W(0.075f); p.letterSpacing = 0.12f; p.textAlign = Paint.Align.CENTER
        c.drawText("NOTHING", X(rx), Y(ry), p)
        c.restore()
        p.letterSpacing = 0f
    }

    override fun onDraw(c: Canvas) {
        val ar = 2.12f
        val cap = height * 0.09f
        w = width * 0.70f
        h = w * ar
        if (h > (height - cap) * 0.96f) { h = (height - cap) * 0.96f; w = h / ar }
        x0 = (width - w) / 2f
        y0 = (height - cap - h) / 2f + 2f

        val m = model
        val body: Int; val edge: Int; val dim: Int
        when (m) {
            NothingModel.P3A_PRO -> { body = 0xFF8C9096.toInt(); edge = 0xFFC4C7CB.toInt(); dim = 0xFF777B82.toInt() }
            NothingModel.P3A_LITE -> { body = 0xFFEEEEEE.toInt(); edge = 0xFFFFFFFF.toInt(); dim = 0xFFD2D2D2.toInt() }
            NothingModel.P4A -> { body = 0xFF121821.toInt(); edge = 0xFF4A5566.toInt(); dim = 0xFF1E2632.toInt() }
            NothingModel.P4A_PRO -> { body = 0xFF17191D.toInt(); edge = 0xFF555A63.toInt(); dim = 0xFF22252B.toInt() }
            else -> { body = 0xFF141519.toInt(); edge = 0xFF50555E.toInt(); dim = 0xFF262A30.toInt() }
        }
        val lite = m == NothingModel.P3A_LITE

        fill(body); rrect(c, 0f, 0f, 1f, 1f, 0.14f)

        when (m) {
            NothingModel.P3A, NothingModel.P3A_PRO -> drawA(c, m == NothingModel.P3A_PRO, dim)
            NothingModel.P3A_LITE -> drawLite(c, dim)
            NothingModel.P3 -> draw3(c, dim)
            NothingModel.P4A -> draw4a(c, dim)
            NothingModel.P4A_PRO -> draw4aPro(c, dim)
            null -> { // genérico
                fill(dim); rrect(c, 0.1f, 0.05f, 0.6f, 0.15f, 0.05f)
                lens(c, 0.24f, 0.10f, 0.06f, 0xFF333333.toInt()); lens(c, 0.42f, 0.10f, 0.06f, 0xFF333333.toInt())
            }
        }

        if (m != NothingModel.P4A_PRO) label(c, 0.10f, 0.80f, if (lite) 0xFF9A9A9A.toInt() else 0xFF6E727A.toInt())

        // Contorno
        line(edge, 0.016f); rrect(c, 0f, 0f, 1f, 1f, 0.14f)

        // Tecla Essential: vista trasera, así que el lateral izquierdo (de frente) queda a la derecha.
        val kh = W(0.13f)
        val cy = y0 + h * y.coerceIn(0, 100) / 100f
        val kx = if (left) x0 + w else x0
        fill((accent and 0x00FFFFFF) or 0x33000000); c.drawCircle(kx, cy, kh * 0.62f, p)
        fill(accent)
        r.set(kx - W(0.024f), cy - kh / 2, kx + W(0.024f), cy + kh / 2)
        c.drawRoundRect(r, W(0.024f), W(0.024f), p)

        // Rótulo
        p.style = Paint.Style.FILL; p.color = 0xFF8A8A8A.toInt()
        p.textAlign = Paint.Align.CENTER; p.textSize = cap * 0.62f
        p.typeface = Typeface.MONOSPACE; p.letterSpacing = 0.1f
        c.drawText(context.getString(R.string.dev_back_view), width / 2f, height - cap * 0.28f, p)
        p.letterSpacing = 0f
    }

    /** Phone (3a) y (3a) Pro: isla circular grande con tres arcos Glyph. */
    private fun drawA(c: Canvas, pro: Boolean, dim: Int) {
        val cx = 0.5f; val cy = 0.225f
        // interior transparente (bobina y cintas)
        line(dim, 0.012f); rrect(c, 0.06f, 0.43f, 0.94f, 0.96f, 0.05f)
        fill(dim); circle(c, 0.14f, 0.47f, 0.07f)
        rrect(c, 0.36f, 0.50f, 0.52f, 0.90f, 0.02f); rrect(c, 0.54f, 0.64f, 0.90f, 0.93f, 0.02f)
        // isla
        fill(if (pro) 0xFF9A9EA4.toInt() else 0xFF0E0F12.toInt()); circle(c, cx, cy, 0.385f)
        line(if (pro) 0xFF6F737A.toInt() else 0xFF2A2D33.toInt(), 0.012f); circle(c, cx, cy, 0.34f)
        if (!pro) {
            fill(0xFF050505.toInt()); rrect(c, 0.245f, 0.17f, 0.755f, 0.27f, 0.085f)
            line(0xFF3A3D44.toInt(), 0.01f); rrect(c, 0.245f, 0.17f, 0.755f, 0.27f, 0.085f)
            lens(c, 0.33f, 0.222f, 0.052f, 0xFF1A1A1A.toInt()); lens(c, 0.50f, 0.222f, 0.052f, 0xFF1A1A1A.toInt())
            lens(c, 0.67f, 0.222f, 0.052f, 0xFF1A1A1A.toInt())
            fill(0xFFE8E8E8.toInt()); circle(c, 0.42f, 0.105f, 0.026f)
        } else {
            lens(c, 0.327f, 0.139f, 0.062f, 0xFF1A1A1A.toInt()); lens(c, 0.327f, 0.225f, 0.062f, 0xFF1A1A1A.toInt())
            fill(0xFFB9BDC2.toInt()); rrect(c, 0.46f, 0.17f, 0.81f, 0.285f, 0.05f)
            fill(0xFF050505.toInt()); rrect(c, 0.50f, 0.185f, 0.60f, 0.265f, 0.04f)
            fill(0xFF777B82.toInt()); circle(c, 0.49f, 0.10f, 0.04f)
        }
        // Arcos Glyph
        arcGlyph(c, cx, cy, 0.41f, 199f, 240f)
        arcGlyph(c, cx, cy, 0.41f, -15f, 30f)
        arcGlyph(c, cx, cy, 0.41f, 133f, 153f)
        square(c, 0.91f, 0.41f, 0.055f, if (pro) 0xFFE6E6E6.toInt() else 0xFFC0242B.toInt())
    }

    /** Phone (3a) Lite: tres lentes sueltas, cuadrado rojo y un único LED. */
    private fun drawLite(c: Canvas, dim: Int) {
        line(0xFFC9C9C9.toInt(), 0.012f); rrect(c, 0.27f, 0.165f, 0.955f, 0.84f, 0.07f)
        line(0xFFD8D8D8.toInt(), 0.01f); rrect(c, 0.33f, 0.178f, 0.77f, 0.20f, 0.01f)
        fill(0xFF2A2A2A.toInt())
        listOf(0.82f to 0.07f, 0.9f to 0.05f, 0.55f to 0.46f, 0.55f to 0.92f, 0.9f to 0.92f).forEach { circle(c, it.first, it.second, 0.012f) }
        lens(c, 0.135f, 0.058f, 0.08f, 0xFF1A1A1A.toInt())
        lens(c, 0.347f, 0.096f, 0.07f, 0xFF1A1A1A.toInt())
        lens(c, 0.135f, 0.193f, 0.08f, 0xFF1A1A1A.toInt())
        square(c, 0.91f, 0.135f, 0.06f, 0xFFC0242B.toInt())
        fill(0xFFFFFFFF.toInt()); circle(c, 0.90f, 0.91f, 0.018f)
    }

    /** Phone (3): cámara triple, disco Glyph Matrix y cuadrado rojo. */
    private fun draw3(c: Canvas, dim: Int) {
        fill(dim); rrect(c, 0.05f, 0.05f, 0.95f, 0.32f, 0.04f)
        rrect(c, 0.05f, 0.33f, 0.40f, 0.72f, 0.02f); rrect(c, 0.42f, 0.33f, 0.74f, 0.72f, 0.02f)
        // arco tipo vinilo
        line(0xFF3A3E45.toInt(), 0.008f)
        for (i in 0..3) {
            val rad = 0.30f - i * 0.05f
            r.set(X(1.0f) - W(rad), Y(0.30f) - W(rad), X(1.0f) + W(rad), Y(0.30f) + W(rad))
            c.drawArc(r, 90f, 180f, false, p)
        }
        fill(0xFF2E3238.toInt()); circle(c, 0.85f, 0.465f, 0.08f); circle(c, 0.85f, 0.565f, 0.08f)
        // fila superior
        fill(0xFF0A0A0C.toInt()); rrect(c, 0.04f, 0.05f, 0.24f, 0.15f, 0.03f)
        lens(c, 0.12f, 0.10f, 0.05f, 0xFF1A1A1A.toInt())
        fill(0xFF2A2D33.toInt()); circle(c, 0.50f, 0.094f, 0.125f)
        line(0xFF50555E.toInt(), 0.01f); circle(c, 0.50f, 0.094f, 0.10f)
        fill(0xFFC8C8C8.toInt()); circle(c, 0.50f, 0.094f, 0.022f)
        matrixDisc(c, 0.80f, 0.094f, 0.135f)
        // lentes grandes
        lens(c, 0.19f, 0.236f, 0.105f, 0xFF1A1A1A.toInt()); lens(c, 0.50f, 0.236f, 0.105f, 0xFF1A1A1A.toInt())
        square(c, 0.37f, 0.30f, 0.05f, 0xFFC0242B.toInt())
    }

    /** Phone (4a): isla con tres lentes y Glyph Bar vertical de seis cuadrados. */
    private fun draw4a(c: Canvas, dim: Int) {
        line(dim, 0.012f); rrect(c, 0.08f, 0.355f, 0.92f, 0.985f, 0.07f)
        fill(dim); rrect(c, 0.40f, 0.44f, 0.73f, 0.50f, 0.03f); rrect(c, 0.31f, 0.60f, 0.52f, 0.82f, 0.02f)
        rrect(c, 0.10f, 0.935f, 0.30f, 0.965f, 0.01f)
        // franja de ranuras
        fill(0xFF2C3440.toInt()); rrect(c, 0.28f, 0.29f, 0.43f, 0.32f, 0.012f)
        rrect(c, 0.50f, 0.29f, 0.64f, 0.32f, 0.012f); rrect(c, 0.66f, 0.29f, 0.71f, 0.32f, 0.012f)
        // isla
        fill(0xFF2A3038.toInt()); rrect(c, 0.175f, 0.096f, 0.85f, 0.255f, 0.08f)
        fill(0xFF05070A.toInt()); rrect(c, 0.215f, 0.115f, 0.81f, 0.235f, 0.06f)
        lens(c, 0.34f, 0.175f, 0.062f, 0xFF1A1A1A.toInt()); lens(c, 0.48f, 0.175f, 0.062f, 0xFF1A1A1A.toInt())
        lens(c, 0.648f, 0.175f, 0.062f, 0xFF1A1A1A.toInt())
        fill(0xFF3B424C.toInt()); circle(c, 0.563f, 0.068f, 0.045f)
        fill(0xFFE6E6E6.toInt()); circle(c, 0.563f, 0.068f, 0.025f)
        // Glyph Bar
        val bx = 0.925f; val bh = 0.168f / 6f
        for (i in 0 until 6) square(c, bx, 0.106f + bh * (i + 0.5f) , 0.05f, if (i == 5) 0xFFC0242B.toInt() else 0xFFF2F2F2.toInt())
    }

    /** Phone (4a) Pro: bloque de cámaras cuadrado con Glyph Matrix grande. */
    private fun draw4aPro(c: Canvas, dim: Int) {
        fill(0xFF0B0C0E.toInt()); rrect(c, 0.05f, 0.024f, 0.95f, 0.325f, 0.10f)
        line(0xFF3A3D44.toInt(), 0.012f); rrect(c, 0.05f, 0.024f, 0.95f, 0.325f, 0.10f)
        lens(c, 0.224f, 0.108f, 0.117f, 0xFF1E1F23.toInt())
        fill(0xFF1A1B1E.toInt()); rrect(c, 0.125f, 0.19f, 0.515f, 0.295f, 0.09f)
        lens(c, 0.224f, 0.245f, 0.075f, 0xFF1A1A1A.toInt()); lens(c, 0.41f, 0.245f, 0.075f, 0xFF1A1A1A.toInt())
        fill(0xFFD8D0C0.toInt()); circle(c, 0.42f, 0.14f, 0.02f)
        fill(0xFF444850.toInt()); circle(c, 0.42f, 0.096f, 0.014f)
        matrixDisc(c, 0.70f, 0.13f, 0.18f)
        square(c, 0.86f, 0.235f, 0.04f, 0xFFC0242B.toInt())
        fill(0xFF2A2D33.toInt()); rrect(c, 0.64f, 0.285f, 0.76f, 0.30f, 0.01f)
        line(0xFF22252B.toInt(), 0.012f); circle(c, 0.14f, 0.955f, 0.085f)
    }
}
