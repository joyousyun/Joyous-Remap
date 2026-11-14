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
    private fun circleS(c: Canvas, cx: Float, cy: Float, rad: Float) {
        val st = p.style; p.style = Paint.Style.STROKE; c.drawCircle(X(cx), Y(cy), W(rad), p); p.style = st
    }
    private fun rrect(c: Canvas, l: Float, t: Float, rr: Float, b: Float, rad: Float, stroke: Boolean = false) {
        r.set(X(l), Y(t), X(rr), Y(b))
        if (stroke) { p.style = Paint.Style.STROKE; c.drawRoundRect(r, W(rad), W(rad), p) } else { p.style = Paint.Style.FILL; c.drawRoundRect(r, W(rad), W(rad), p) }
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
        line(0xFFFFFFFF.toInt(), 0.042f); p.strokeCap = Paint.Cap.ROUND
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

    private fun label(c: Canvas, rx: Float, ry: Float, col: Int, text: String = "NOTHING") {
        c.save()
        c.rotate(-90f, X(rx), Y(ry))
        p.style = Paint.Style.FILL; p.color = col
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = W(0.075f); p.letterSpacing = 0.12f; p.textAlign = Paint.Align.CENTER
        c.drawText(text, X(rx), Y(ry), p)
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
        // Todos los modelos con el mismo estilo oscuro para integrarse con la app.
        val body = 0xFF141519.toInt(); val edge = 0xFF50555E.toInt(); val dim = 0xFF262A30.toInt()
        val lite = false

        val cr = if (m == NothingModel.CMF2PRO) 0.075f else 0.14f
        fill(body); rrect(c, 0f, 0f, 1f, 1f, cr)

        when (m) {
            NothingModel.P3A, NothingModel.P3A_PRO -> drawA(c, m == NothingModel.P3A_PRO, dim)
            NothingModel.P3A_LITE -> drawLite(c, dim)
            NothingModel.P3 -> draw3(c, dim)
            NothingModel.P4A -> draw4a(c, dim)
            NothingModel.P4A_PRO -> draw4aPro(c, dim)
            NothingModel.CMF2PRO -> drawCmf(c)
            null -> { // genérico
                fill(dim); rrect(c, 0.1f, 0.05f, 0.6f, 0.15f, 0.05f)
                lens(c, 0.24f, 0.10f, 0.06f, 0xFF333333.toInt()); lens(c, 0.42f, 0.10f, 0.06f, 0xFF333333.toInt())
            }
        }

        // Contorno
        line(edge, 0.016f); rrect(c, 0f, 0f, 1f, 1f, cr, true)

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

    private val L = 0xFF1A1A1A.toInt()
    private val RED = 0xFFC0242B.toInt()

    private fun screws(c: Canvas, pts: List<Pair<Float, Float>>, rad: Float = 0.014f) {
        fill(0xFF0A0B0D.toInt()); pts.forEach { circle(c, it.first, it.second, rad) }
        fill(0xFF3A3E45.toInt()); pts.forEach { circle(c, it.first, it.second, rad * 0.45f) }
    }

    /** Phone (3a) y (3a) Pro: isla circular grande con tres arcos Glyph. */
    private fun drawA(c: Canvas, pro: Boolean, dim: Int) {
        val cx = if (pro) 0.505f else 0.49f; val cy = 0.225f
        // interior transparente: cinta de la bobina y módulos
        fill(0xFF181A1E.toInt()); rrect(c, 0.04f, 0.435f, 0.96f, 0.975f, 0.05f)
        fill(0xFF23262C.toInt()); rrect(c, 0.05f, 0.44f, 0.93f, 0.55f, 0.03f); rrect(c, 0.72f, 0.44f, 0.93f, 0.76f, 0.07f)
        rrect(c, 0.35f, 0.57f, 0.56f, 0.82f, 0.02f)
        fill(0xFF0F1013.toInt()); rrect(c, 0.05f, 0.57f, 0.33f, 0.74f, 0.01f); rrect(c, 0.56f, 0.57f, 0.70f, 0.75f, 0.01f)
        fill(0xFF2A2D33.toInt()); circle(c, 0.12f, 0.465f, 0.07f)
        rrect(c, 0.04f, 0.76f, 0.34f, 0.97f, 0.01f); rrect(c, 0.36f, 0.86f, 0.70f, 0.97f, 0.01f)
        screws(c, listOf(0.09f to 0.03f, 0.35f to 0.03f, 0.9f to 0.03f, 0.09f to 0.12f, 0.9f to 0.12f, 0.09f to 0.29f,
            0.9f to 0.33f, 0.62f to 0.33f))
        // isla
        fill(0xFF1B1D21.toInt()); circle(c, cx, cy, 0.355f)
        line(if (pro) 0xFF4A4E56.toInt() else 0xFF2E3238.toInt(), if (pro) 0.03f else 0.012f); circleS(c, cx, cy, if (pro) 0.335f else 0.34f)
        if (!pro) {
            fill(0xFF0A0B0D.toInt()); rrect(c, 0.18f, 0.16f, 0.825f, 0.295f, 0.14f)
            fill(0xFF050505.toInt()); rrect(c, 0.23f, 0.185f, 0.77f, 0.265f, 0.11f)
            lens(c, 0.33f, 0.225f, 0.062f, L); lens(c, 0.50f, 0.225f, 0.045f, L); lens(c, 0.666f, 0.225f, 0.062f, L)
            fill(0xFF0A0A0A.toInt()); circle(c, 0.41f, 0.103f, 0.047f); lens(c, 0.41f, 0.103f, 0.032f, 0xFF333333.toInt())
        } else {
            lens(c, 0.326f, 0.138f, 0.062f, L); lens(c, 0.326f, 0.222f, 0.074f, L)
            fill(0xFF0A0A0A.toInt()); circle(c, 0.50f, 0.105f, 0.057f); lens(c, 0.50f, 0.105f, 0.038f, 0xFF4A4E56.toInt())
            fill(0xFF2E3138.toInt()); rrect(c, 0.453f, 0.17f, 0.825f, 0.282f, 0.05f)
            fill(0xFF050505.toInt()); rrect(c, 0.48f, 0.18f, 0.617f, 0.268f, 0.06f)
            fill(0xFF3A3D44.toInt()); circle(c, 0.549f, 0.224f, 0.022f)
        }
        // Arcos Glyph
        arcGlyph(c, cx, cy, 0.40f, 198f, 241f)
        arcGlyph(c, cx, cy, 0.40f, -14f, 31f)
        arcGlyph(c, cx, cy, 0.40f, 130f, 151f)
        square(c, 0.913f, 0.41f, 0.06f, if (pro) 0xFFD6D6D6.toInt() else RED)
        label(c, 0.127f, 0.865f, 0xFF6E727A.toInt())
    }

    /** Phone (3a) Lite: tres lentes sueltas, cuadrado rojo y un único LED. */
    private fun drawLite(c: Canvas, dim: Int) {
        fill(0xFF181A1E.toInt()); rrect(c, 0.286f, 0.155f, 0.985f, 0.83f, 0.06f)
        line(0xFF3A3E45.toInt(), 0.01f); rrect(c, 0.286f, 0.155f, 0.985f, 0.83f, 0.06f, true)
        line(0xFF50555E.toInt(), 0.012f); rrect(c, 0.56f, 0.183f, 0.75f, 0.203f, 0.01f, true)
        fill(0xFF50555E.toInt()); circle(c, 0.369f, 0.193f, 0.03f)
        screws(c, listOf(0.81f to 0.07f, 0.42f to 0.30f, 0.985f to 0.29f, 0.055f to 0.29f, 0.055f to 0.89f,
            0.436f to 0.958f, 0.93f to 0.965f, 0.95f to 0.04f), 0.016f)
        lens(c, 0.139f, 0.062f, 0.094f, L); lens(c, 0.369f, 0.095f, 0.078f, L); lens(c, 0.139f, 0.193f, 0.094f, L)
        square(c, 0.955f, 0.138f, 0.067f, RED)
        fill(0xFFFFFFFF.toInt()); circle(c, 0.946f, 0.912f, 0.026f)
        label(c, 0.14f, 0.74f, 0xFF6E727A.toInt())
    }

    /** Phone (3): cámara triple, disco Glyph Matrix y cuadrado rojo. */
    private fun draw3(c: Canvas, dim: Int) {
        fill(0xFF1B1D21.toInt()); rrect(c, 0.04f, 0.05f, 0.96f, 0.31f, 0.03f)
        fill(0xFF101114.toInt()); rrect(c, 0.04f, 0.175f, 0.34f, 0.31f, 0.01f); rrect(c, 0.34f, 0.175f, 0.65f, 0.31f, 0.01f)
        fill(0xFF1F2227.toInt()); rrect(c, 0.04f, 0.31f, 0.34f, 0.87f, 0.01f); rrect(c, 0.34f, 0.31f, 0.65f, 0.73f, 0.01f)
        fill(0xFF17191D.toInt()); rrect(c, 0.34f, 0.76f, 0.65f, 0.87f, 0.01f)
        // disco tipo vinilo
        line(0xFF3A3E45.toInt(), 0.006f)
        for (i in 0..5) {
            val rad = 0.295f - i * 0.045f
            r.set(X(0.95f) - W(rad), Y(0.312f) - W(rad), X(0.95f) + W(rad), Y(0.312f) + W(rad))
            c.drawArc(r, 90f, 180f, false, p)
        }
        fill(0xFF33363C.toInt()); circle(c, 0.855f, 0.466f, 0.082f)
        fill(0xFF222529.toInt()); circle(c, 0.857f, 0.55f, 0.086f)
        // fila superior
        fill(0xFF2A2D33.toInt()); circle(c, 0.233f, 0.062f, 0.085f)
        fill(0xFF0A0A0C.toInt()); rrect(c, 0.04f, 0.07f, 0.23f, 0.15f, 0.03f)
        lens(c, 0.134f, 0.108f, 0.082f, L)
        fill(0xFF2A2D33.toInt()); circle(c, 0.50f, 0.092f, 0.14f)
        line(0xFF3A3E45.toInt(), 0.008f); circleS(c, 0.50f, 0.092f, 0.115f)
        fill(0xFF9A9EA4.toInt()); circle(c, 0.50f, 0.095f, 0.026f)
        matrixDisc(c, 0.80f, 0.09f, 0.134f)
        // lentes grandes
        lens(c, 0.188f, 0.235f, 0.104f, L); lens(c, 0.497f, 0.235f, 0.104f, L)
        square(c, 0.372f, 0.298f, 0.06f, RED)
        screws(c, listOf(0.09f to 0.035f, 0.35f to 0.035f, 0.93f to 0.035f, 0.07f to 0.965f, 0.55f to 0.92f, 0.93f to 0.965f))
        label(c, 0.09f, 0.83f, 0xFF6E727A.toInt())
    }

    /** Phone (4a): isla con tres lentes y Glyph Bar vertical de seis cuadrados. */
    private fun draw4a(c: Canvas, dim: Int) {
        fill(0xFF1A2029.toInt()); rrect(c, 0.07f, 0.355f, 0.92f, 0.955f, 0.08f)
        line(0xFF232B36.toInt(), 0.014f); rrect(c, 0.07f, 0.355f, 0.92f, 0.955f, 0.08f, true)
        fill(0xFF222A35.toInt()); rrect(c, 0.45f, 0.45f, 0.70f, 0.495f, 0.05f); rrect(c, 0.51f, 0.595f, 0.73f, 0.87f, 0.04f)
        rrect(c, 0.10f, 0.88f, 0.28f, 0.935f, 0.015f)
        fill(0xFF0E1218.toInt()); rrect(c, 0.283f, 0.29f, 0.426f, 0.32f, 0.012f)
        rrect(c, 0.543f, 0.29f, 0.67f, 0.32f, 0.012f); rrect(c, 0.69f, 0.29f, 0.77f, 0.32f, 0.012f)
        // isla
        fill(0xFF2A3038.toInt()); rrect(c, 0.095f, 0.055f, 0.875f, 0.27f, 0.12f)
        line(0xFF3A424D.toInt(), 0.012f); rrect(c, 0.115f, 0.075f, 0.855f, 0.25f, 0.105f, true)
        fill(0xFF05070A.toInt()); rrect(c, 0.19f, 0.10f, 0.81f, 0.235f, 0.07f)
        lens(c, 0.334f, 0.165f, 0.062f, L); lens(c, 0.479f, 0.165f, 0.05f, L); lens(c, 0.638f, 0.165f, 0.062f, L)
        fill(0xFF2A3038.toInt()); circle(c, 0.564f, 0.065f, 0.05f)
        fill(0xFFC8C8C8.toInt()); circle(c, 0.564f, 0.065f, 0.03f)
        // Glyph Bar
        val bh = 0.172f / 6f
        for (i in 0 until 6) square(c, 0.913f, 0.10f + bh * (i + 0.5f), 0.046f, if (i == 5) RED else 0xFFF2F2F2.toInt())
        screws(c, listOf(0.09f to 0.045f, 0.91f to 0.04f, 0.07f to 0.315f, 0.85f to 0.31f), 0.012f)
        label(c, 0.106f, 0.81f, 0xFF4E5560.toInt())
    }

    /** Phone (4a) Pro: bloque de cámaras cuadrado con Glyph Matrix grande. */
    private fun draw4aPro(c: Canvas, dim: Int) {
        fill(0xFF0B0C0E.toInt()); rrect(c, 0.043f, 0.03f, 0.948f, 0.33f, 0.12f)
        line(0xFF3A3D44.toInt(), 0.02f); rrect(c, 0.043f, 0.03f, 0.948f, 0.33f, 0.12f, true)
        lens(c, 0.226f, 0.11f, 0.12f, 0xFF2A2C31.toInt())
        fill(0xFF0E0F11.toInt()); rrect(c, 0.108f, 0.195f, 0.528f, 0.295f, 0.1f)
        line(0xFF2A2C31.toInt(), 0.014f); rrect(c, 0.108f, 0.195f, 0.528f, 0.295f, 0.1f, true)
        lens(c, 0.226f, 0.243f, 0.07f, L); lens(c, 0.405f, 0.243f, 0.052f, L)
        fill(0xFFD8D0C0.toInt()); circle(c, 0.427f, 0.135f, 0.02f)
        fill(0xFF444850.toInt()); circle(c, 0.43f, 0.085f, 0.022f)
        matrixDisc(c, 0.70f, 0.146f, 0.183f)
        square(c, 0.86f, 0.235f, 0.047f, RED)
        fill(0xFF2A2D33.toInt()); rrect(c, 0.60f, 0.282f, 0.80f, 0.302f, 0.01f)
        line(0xFF2A2D33.toInt(), 0.012f); circleS(c, 0.134f, 0.935f, 0.082f)
    }

    /** CMF Phone 2 Pro: dos lentes grandes con aro metálico, módulo plateado con flash y panel inferior con el dial rojo. */
    private fun drawCmf(c: Canvas) {
        val metal = 0xFFB4B8BD.toInt()
        // panel inferior
        fill(0xFF1B1D21.toInt()); r.set(X(0f), Y(0.5f), X(1f), Y(0.6f)); c.drawRect(r, p)
        rrect(c, 0f, 0.5f, 1f, 1f, 0.075f)
        line(0xFF2E3238.toInt(), 0.012f); p.style = Paint.Style.STROKE
        c.drawLine(X(0f), Y(0.5f), X(1f), Y(0.5f), p)
        // módulo plateado (lente pequeña + flash)
        fill(0xFF8E9298.toInt()); rrect(c, 0.296f, 0.069f, 0.432f, 0.193f, 0.068f)
        ringLens(c, 0.364f, 0.096f, 0.073f, metal)
        fill(0xFFE8E0C8.toInt()); circle(c, 0.361f, 0.164f, 0.034f)
        fill(0xFFB4A78A.toInt()); circle(c, 0.361f, 0.164f, 0.02f)
        // lentes grandes
        ringLens(c, 0.145f, 0.064f, 0.112f, metal)
        ringLens(c, 0.145f, 0.195f, 0.112f, metal)
        screws(c, listOf(0.359f to 0.021f, 0.055f to 0.288f, 0.945f to 0.29f, 0.05f to 0.89f), 0.018f)
        // dial rojo, pegado a la esquina inferior derecha
        fill(RED); circle(c, 0.864f, 0.939f, 0.126f)
        fill(0xFF9A1C22.toInt()); circleS(c, 0.864f, 0.939f, 0.11f)
        // marca: vertical, se lee de abajo hacia arriba como en el móvil real
        p.style = Paint.Style.FILL; p.color = 0xFF8A8E96.toInt(); p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        c.save()
        c.rotate(-90f, X(0.157f), Y(0.80f))
        p.textSize = W(0.1f); c.drawText("cmf", X(0.157f), Y(0.80f), p)
        c.restore()
        c.save()
        c.rotate(-90f, X(0.20f), Y(0.80f))
        p.textSize = W(0.032f); c.drawText("by NOTHING", X(0.20f), Y(0.80f), p)
        c.restore()
    }

    private fun ringLens(c: Canvas, cx: Float, cy: Float, rad: Float, ring: Int) {
        fill(ring); circle(c, cx, cy, rad)
        fill(0xFF050505.toInt()); circle(c, cx, cy, rad * 0.66f)
        fill(0xFF26324D.toInt()); circle(c, cx, cy, rad * 0.36f)
        fill(0x66FFFFFF); circle(c, cx - rad * 0.12f, cy - rad * 0.1f * w / h, rad * 0.1f)
    }
}
