package com.joyous.remap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Vista trasera dibujada a mano del móvil, con la posición de la tecla Essential marcada. */
class DeviceView(ctx: Context) : View(ctx) {

    var model: NothingModel? = null
    var left = false     // lateral de la tecla mirando la pantalla
    var y = 50           // altura en %
    var accent = 0xFFD71921.toInt()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        // Proporción 1 : 2,05, centrada y sin recortes.
        val ar = 2.05f
        val cap = height * 0.09f
        var w = width.toFloat() * 0.70f
        var h = w * ar
        if (h > (height - cap) * 0.96f) { h = (height - cap) * 0.96f; w = h / ar }
        val x0 = (width - w) / 2f
        val y0 = (height - cap - h) / 2f + 2f
        val u = w / 100f

        // Cuerpo
        r.set(x0, y0, x0 + w, y0 + h)
        p.style = Paint.Style.FILL; p.color = 0xFF0B0B0B.toInt()
        c.drawRoundRect(r, 16 * u, 16 * u, p)

        // Rejilla de puntos sutil
        p.color = 0x16FFFFFF
        var gy = y0 + 46 * u
        while (gy < y0 + h - 10 * u) {
            var gx = x0 + 12 * u
            while (gx < x0 + w - 8 * u) { c.drawCircle(gx, gy, 0.9f * u, p); gx += 8 * u }
            gy += 8 * u
        }

        // Tiras Glyph (puntos)
        p.color = 0x55FFFFFF
        val gl = x0 + 12 * u; val gr = x0 + w - 12 * u
        var t = y0 + 66 * u
        while (t < y0 + h - 22 * u) { c.drawCircle(gl, t, 1.3f * u, p); c.drawCircle(gr, t, 1.3f * u, p); t += 7 * u }
        var s = gl + 7 * u
        while (s < gr - 4 * u) { c.drawCircle(s, y0 + h - 14 * u, 1.3f * u, p); s += 7 * u }

        // Isla de cámaras
        val m = model
        val cams = m?.cams ?: 2
        val camH = 22 * u
        val camW = (12 + 14 * cams) * u
        r.set(x0 + 10 * u, y0 + 9 * u, x0 + 10 * u + camW, y0 + 9 * u + camH)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f * u; p.color = 0x99FFFFFF.toInt()
        c.drawRoundRect(r, camH / 2, camH / 2, p)
        p.style = Paint.Style.FILL
        for (i in 0 until cams) {
            val cx = r.left + camH / 2 + i * 14 * u
            p.color = 0xFF1C1C1C.toInt(); c.drawCircle(cx, r.centerY(), 5.2f * u, p)
            p.color = 0xFF3A4A66.toInt(); c.drawCircle(cx, r.centerY(), 2.2f * u, p)
        }

        // Matriz de puntos (Phone 3)
        if (m?.matrix == true) {
            val cx = x0 + w - 24 * u; val cy = y0 + 20 * u
            for (i in -2..2) for (j in -2..2) {
                if (i * i + j * j <= 5) {
                    p.color = if ((i + j) % 2 == 0) 0xCCFFFFFF.toInt() else 0x44FFFFFF
                    c.drawCircle(cx + i * 4.2f * u, cy + j * 4.2f * u, 1.3f * u, p)
                }
            }
        }

        // Contorno
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f * u; p.color = 0xAAFFFFFF.toInt()
        r.set(x0, y0, x0 + w, y0 + h)
        c.drawRoundRect(r, 16 * u, 16 * u, p)

        // Tecla Essential: vista trasera, así que el lateral izquierdo (de frente) queda a la derecha.
        val kh = 13 * u
        val cy = y0 + h * y.coerceIn(0, 100) / 100f
        val kx = if (left) x0 + w else x0
        p.style = Paint.Style.FILL
        p.color = (accent and 0x00FFFFFF) or 0x33000000; c.drawCircle(kx, cy, kh * 0.62f, p)
        p.color = accent
        r.set(kx - 2.2f * u, cy - kh / 2, kx + 2.2f * u, cy + kh / 2)
        c.drawRoundRect(r, 2.2f * u, 2.2f * u, p)

        // Rótulo
        p.color = 0xFF8A8A8A.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = cap * 0.62f
        p.typeface = android.graphics.Typeface.MONOSPACE
        p.letterSpacing = 0.1f
        c.drawText(context.getString(R.string.dev_back_view), width / 2f, height - cap * 0.28f, p)
    }
}
