package com.dsh.floatcap

import android.content.Context

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("floatcap", Context.MODE_PRIVATE)

    class Preset(val name: String, val bg: Int, val fg: Int, val radius: Int, val alpha: Int)

    val presets = listOf(
        Preset("玻璃黑", 0xFF101216.toInt(), 0xFFFFFFFF.toInt(), 20, 72),
        Preset("纯黑", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 10, 100),
        Preset("暖白", 0xFFF3EDE2.toInt(), 0xFF1B1B1B.toInt(), 16, 94),
        Preset("霓虹", 0xFF08131A.toInt(), 0xFF6EF2D0.toInt(), 14, 82)
    )

    fun engine(c: Context): String = sp(c).getString("engine", Models.SENSEVOICE) ?: Models.SENSEVOICE
    fun setEngine(c: Context, v: String) = sp(c).edit().putString("engine", v).apply()

    fun font(c: Context): Int = sp(c).getInt("font", 26)
    fun setFont(c: Context, v: Int) = sp(c).edit().putInt("font", v).apply()

    fun alpha(c: Context): Int = sp(c).getInt("alpha", 72)
    fun setAlpha(c: Context, v: Int) = sp(c).edit().putInt("alpha", v).apply()

    fun radius(c: Context): Int = sp(c).getInt("radius", 20)
    fun setRadius(c: Context, v: Int) = sp(c).edit().putInt("radius", v).apply()

    fun widthPct(c: Context): Int = sp(c).getInt("width", 92)
    fun setWidth(c: Context, v: Int) = sp(c).edit().putInt("width", v).apply()

    fun lines(c: Context): Int = sp(c).getInt("lines", 2)
    fun setLines(c: Context, v: Int) = sp(c).edit().putInt("lines", v).apply()

    /** 0 = 逐字浮现, 1 = 整句淡入, 2 = 立即显示 */
    fun anim(c: Context): Int = sp(c).getInt("anim", 0)
    fun setAnim(c: Context, v: Int) = sp(c).edit().putInt("anim", v).apply()

    fun bgColor(c: Context): Int = sp(c).getInt("bg", 0xFF101216.toInt())
    fun textColor(c: Context): Int = sp(c).getInt("fg", 0xFFFFFFFF.toInt())
    fun presetName(c: Context): String = sp(c).getString("preset", "玻璃黑") ?: "玻璃黑"

    fun applyPreset(c: Context, p: Preset) {
        sp(c).edit()
            .putInt("bg", p.bg)
            .putInt("fg", p.fg)
            .putInt("radius", p.radius)
            .putInt("alpha", p.alpha)
            .putString("preset", p.name)
            .apply()
    }

    fun setTextColor(c: Context, color: Int) =
        sp(c).edit().putInt("fg", color).putString("preset", "自定义").apply()

    fun bold(c: Context): Boolean = sp(c).getBoolean("bold", true)
    fun setBold(c: Context, v: Boolean) = sp(c).edit().putBoolean("bold", v).apply()

    fun spacing(c: Context): Int = sp(c).getInt("spacing", 0)
    fun setSpacing(c: Context, v: Int) = sp(c).edit().putInt("spacing", v).apply()

    fun alignCenter(c: Context): Boolean = sp(c).getBoolean("align", false)
    fun setAlignCenter(c: Context, v: Boolean) = sp(c).edit().putBoolean("align", v).apply()

    /** 0 = 无, 1 = 轻, 2 = 强 */
    fun shadow(c: Context): Int = sp(c).getInt("shadow", 1)
    fun setShadow(c: Context, v: Int) = sp(c).edit().putInt("shadow", v).apply()

    fun maxChars(c: Context): Int = sp(c).getInt("maxchars", 60)
    fun setMaxChars(c: Context, v: Int) = sp(c).edit().putInt("maxchars", v).apply()

    fun vadSilence(c: Context): Int = sp(c).getInt("vad", 35)
    fun setVadSilence(c: Context, v: Int) = sp(c).edit().putInt("vad", v).apply()

    fun endpointSilence(c: Context): Int = sp(c).getInt("endpoint", 110)
    fun setEndpointSilence(c: Context, v: Int) = sp(c).edit().putInt("endpoint", v).apply()

    fun threads(c: Context): Int = sp(c).getInt("threads", 2)
    fun setThreads(c: Context, v: Int) = sp(c).edit().putInt("threads", v).apply()

    fun onboarded(c: Context): Boolean = sp(c).getBoolean("onboarded", false)
    fun setOnboarded(c: Context, v: Boolean) = sp(c).edit().putBoolean("onboarded", v).apply()

    fun posX(c: Context): Int = sp(c).getInt("px", Int.MIN_VALUE)
    fun posY(c: Context): Int = sp(c).getInt("py", Int.MIN_VALUE)
    fun setPos(c: Context, x: Int, y: Int) = sp(c).edit().putInt("px", x).putInt("py", y).apply()
    fun resetPos(c: Context) = sp(c).edit().remove("px").remove("py").apply()
}
