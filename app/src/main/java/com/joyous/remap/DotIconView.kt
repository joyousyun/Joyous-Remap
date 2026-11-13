package com.joyous.remap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Icono hecho de puntos en una rejilla 5x5, al estilo matriz de puntos de Nothing. */
class DotIconView(ctx: Context, private val rows: List<String>, var lit: Int) : View(ctx) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val cell = minOf(width, height) / 5f
        val ox = (width - cell * 5) / 2f
        val oy = (height - cell * 5) / 2f
        for (y in 0 until 5) for (x in 0 until 5) {
            val on = rows.getOrNull(y)?.getOrNull(x) == '1'
            p.color = if (on) lit else 0x26FFFFFF
            c.drawCircle(ox + cell * (x + 0.5f), oy + cell * (y + 0.5f), cell * (if (on) 0.34f else 0.17f), p)
        }
    }

    companion object {
        val SINGLE = listOf(".....", ".....", "..1..", ".....", ".....")
        val DOUBLE = listOf(".....", ".....", ".1.1.", ".....", ".....")
        val TRIPLE = listOf(".....", ".....", "1.1.1", ".....", ".....")
        val LONG = listOf(".....", ".....", "11111", ".....", ".....")
        val LOOK = listOf("11111", "1...1", "1.1.1", "1...1", "11111")
        val PANEL = listOf("1.1.1", ".111.", "11111", ".111.", "1.1.1")
        val TIMING = listOf(".111.", "1.1.1", "1.111", "1...1", ".111.")
        val MORE = listOf("..1..", ".....", "..1..", ".....", "..1..")
        val BACK = listOf("..1..", ".1...", "1....", ".1...", "..1..")
    }
}
