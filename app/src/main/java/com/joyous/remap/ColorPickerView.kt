package com.joyous.remap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/** Selector de color: un cuadrado de saturación y brillo, y una barra fina para el tono. */
class ColorPickerView(
    c: Context,
    start: Int,
    private val onChange: (Int) -> Unit
) : View(c) {

    private val d = resources.displayMetrics.density
    private val hsv = FloatArray(3).also { Color.colorToHSV(start, it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clip = Path()
    private val sq = RectF()
    private val bar = RectF()
    private val barH = 26 * d
    private val gap = 16 * d
    private var drag = 0 // 1 = cuadrado, 2 = barra de tono

    val color: Int get() = Color.HSVToColor(hsv)

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        setMeasuredDimension(w, (w + gap + barH).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sq.set(0f, 0f, w.toFloat(), w.toFloat())
        bar.set(0f, w + gap, w.toFloat(), w + gap + barH)
    }

    override fun onDraw(canvas: Canvas) {
        val r = 14 * d
        // Cuadrado: blanco -> color puro (horizontal), transparente -> negro (vertical)
        clip.rewind()
        clip.addRoundRect(sq, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        val pure = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        paint.shader = LinearGradient(sq.left, 0f, sq.right, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        canvas.drawRect(sq, paint)
        paint.shader = LinearGradient(0f, sq.top, 0f, sq.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(sq, paint)
        paint.shader = null
        canvas.restore()

        // Barra de tono
        clip.rewind()
        clip.addRoundRect(bar, barH / 2f, barH / 2f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        paint.shader = LinearGradient(bar.left, 0f, bar.right, 0f, hues, null, Shader.TileMode.CLAMP)
        canvas.drawRect(bar, paint)
        paint.shader = null
        canvas.restore()

        // Marcadores
        val px = sq.left + hsv[1] * sq.width()
        val py = sq.top + (1f - hsv[2]) * sq.height()
        ring.strokeWidth = 3 * d
        ring.color = Color.WHITE
        canvas.drawCircle(px, py, 11 * d, ring)
        ring.strokeWidth = 1.5f * d
        ring.color = 0x88000000.toInt()
        canvas.drawCircle(px, py, 13 * d, ring)

        val hx = bar.left + hsv[0] / 360f * bar.width()
        paint.color = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        canvas.drawCircle(hx, bar.centerY(), barH / 2f - 1 * d, paint)
        ring.strokeWidth = 3 * d
        ring.color = Color.WHITE
        canvas.drawCircle(hx, bar.centerY(), barH / 2f - 1 * d, ring)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drag = if (e.y <= sq.bottom + gap / 2f) 1 else 2
                parent?.requestDisallowInterceptTouchEvent(true)
                apply(e)
            }
            MotionEvent.ACTION_MOVE -> apply(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = 0
        }
        return true
    }

    private fun apply(e: MotionEvent) {
        if (drag == 1) {
            hsv[1] = ((e.x - sq.left) / sq.width()).coerceIn(0f, 1f)
            hsv[2] = 1f - ((e.y - sq.top) / sq.height()).coerceIn(0f, 1f)
        } else if (drag == 2) {
            hsv[0] = (((e.x - bar.left) / bar.width()).coerceIn(0f, 1f) * 359.9f)
        }
        invalidate()
        onChange(color)
    }
}
