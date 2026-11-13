package com.joyous.remap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Fondo de matriz de puntos estilo Nothing, muy discreto: pocos puntos, estáticos
 * y apenas más claros que el fondo negro.
 */
class DotMatrixView(c: Context) : View(c) {

    /** Se mantiene por compatibilidad; el fondo ya no usa el color de acento. */
    var color: Int = Prefs.DEFAULT_COLOR

    private val d = resources.displayMetrics.density
    private val gap = 24 * d
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B1B1B.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.2f * d
    }
    private var pts = FloatArray(0)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cols = (w / gap).toInt() + 1
        val rows = (h / gap).toInt() + 1
        val ox = (w - (cols - 1) * gap) / 2f
        val oy = (h - (rows - 1) * gap) / 2f
        pts = FloatArray(cols * rows * 2)
        var k = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                pts[k++] = ox + c * gap
                pts[k++] = oy + r * gap
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        if (pts.isNotEmpty()) canvas.drawPoints(pts, dot)
    }
}
