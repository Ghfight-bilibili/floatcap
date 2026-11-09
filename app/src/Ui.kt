package com.dsh.floatcap

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup

object Ui {
    const val BG = 0xFF0B0C0F.toInt()
    const val CARD = 0xFF14161B.toInt()
    const val STROKE = 0xFF23262E.toInt()
    const val TEXT = 0xFFF2F4F8.toInt()
    const val SUB = 0xFF8A9099.toInt()
    const val ACCENT = 0xFF5B8CFF.toInt()
    const val DANGER = 0xFFFF6B6B.toInt()

    fun dp(c: Context, v: Float): Int =
        (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun round(c: Context, color: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Float = 1f): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(c, radiusDp).toFloat()
        d.setColor(color)
        if (stroke != 0) d.setStroke(dp(c, strokeDp).coerceAtLeast(1), stroke)
        return d
    }

    fun card(c: Context): GradientDrawable = round(c, CARD, 20f, STROKE, 1f)

}
