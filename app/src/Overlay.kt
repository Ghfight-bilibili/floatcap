package com.dsh.floatcap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

class Overlay(private val ctx: Context, private val onClose: () -> Unit) {

    private class FadeSpan(var progress: Float) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(tp: TextPaint) {
            val c = tp.color
            tp.color = Color.argb((Color.alpha(c) * progress).toInt(), Color.red(c), Color.green(c), Color.blue(c))
        }
    }

    private class Fade(val span: FadeSpan, val start: Long, val dur: Long)

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var front: TextView? = null
    private var back: TextView? = null
    private var bar: View? = null
    private var stack: FrameLayout? = null
    private var hideTask: Runnable? = null
    private var renderTask: Runnable? = null
    private var animator: ValueAnimator? = null
    private val active = ArrayList<Fade>()

    private val lines = ArrayDeque<String>()
    private var frozen = false
    private var displayed = ""
    private var lastKey = ""
    private val sb = SpannableStringBuilder()

    private fun dp(v: Float): Int = Ui.dp(ctx, v)

    private fun maxLines(): Int = Store.lines(ctx)
    private fun animMode(): Int = Store.anim(ctx)
    private fun lineH(): Float = (Store.font(ctx) * 1.45f * ctx.resources.displayMetrics.density)

    fun show() {
        main.post { showInternal() }
    }

    private fun styleText(tv: TextView) {
        val c = ctx
        val fg = Store.textColor(c)
        tv.setTextColor(fg)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, Store.font(c).toFloat())
        tv.includeFontPadding = false
        tv.typeface = if (Store.bold(c)) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tv.letterSpacing = Store.spacing(c) * 0.01f
        tv.maxLines = maxLines()
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.gravity = if (Store.alignCenter(c)) Gravity.CENTER_HORIZONTAL else Gravity.START
        tv.setLineSpacing(dp(4f).toFloat(), 1f)
        when (Store.shadow(c)) {
            0 -> tv.setShadowLayer(0f, 0f, 0f, 0)
            2 -> tv.setShadowLayer(14f, 0f, 3f, 0xE6000000.toInt())
            else -> tv.setShadowLayer(8f, 0f, 2f, 0xCC000000.toInt())
        }
    }

    private fun showInternal() {
        if (root != null) return
        val c = ctx
        val dm = c.resources.displayMetrics
        val fg = Store.textColor(c)

        bar = View(c).apply { setBackground(Ui.round(c, withAlpha(fg, 150), 2f)) }

        front = TextView(c).apply { styleText(this) }
        back = TextView(c).apply { styleText(this); alpha = 0f; visibility = View.GONE }

        stack = FrameLayout(c).apply {
            addView(front, FrameLayout.LayoutParams(-1, -2))
            addView(back, FrameLayout.LayoutParams(-1, -2))
        }

        val close = TextView(c).apply {
            text = "✕"
            setTextColor(withAlpha(fg, 140))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setBackground(Ui.round(c, withAlpha(fg, 36), 15f))
            alpha = 0.5f
            setOnClickListener { onClose() }
        }

        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(bar, LinearLayout.LayoutParams(dp(3f), dp(26f)).apply { rightMargin = dp(12f) })
        row.addView(stack, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(close, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply { leftMargin = dp(10f) })

        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(13f), dp(12f), dp(13f))
            background = panelBackground(c)
            addView(row, LinearLayout.LayoutParams(-1, -2))
            alpha = 0f
        }

        val lp = WindowManager.LayoutParams(
            barWidth(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.x = clampX(Store.posX(c).let { if (it == Int.MIN_VALUE) 0 else it }, barWidth())
        lp.y = Store.posY(c).let { if (it == Int.MIN_VALUE) dp(96f) else it }

        container.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var downX = 0f
            var downY = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                val p = params ?: return false
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = p.x
                        startY = p.y
                        downX = e.rawX
                        downY = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) moved = true
                        if (moved) {
                            p.x = clampX(startX + dx, p.width)
                            p.y = (startY - dy).coerceAtLeast(0)
                            try { wm.updateViewLayout(container, p) } catch (_: Throwable) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (moved) {
                            val half = p.width / 2
                            if (abs(p.x) < half / 4) p.x = 0
                            if (p.y < dp(48f)) p.y = dp(28f)
                            try { wm.updateViewLayout(container, p) } catch (_: Throwable) {}
                            Store.setPos(ctx, p.x, p.y)
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            wm.addView(container, lp)
            params = lp
            root = container
            container.animate().alpha(1f).setDuration(160).start()
            CrashLog.trace(ctx, "floatcap-trace.txt", "overlay added w=" + lp.width)
        } catch (t: Throwable) {
            root = null
            CrashLog.trace(ctx, "floatcap-trace.txt", "overlay failed: " + android.util.Log.getStackTraceString(t))
        }
    }

    private fun barWidth(): Int {
        val w = ctx.resources.displayMetrics.widthPixels * Store.widthPct(ctx) / 100
        return min(w, dp(1000f))
    }

    private fun clampX(x: Int, w: Int): Int {
        val half = ctx.resources.displayMetrics.widthPixels / 2 - w / 2
        return x.coerceIn(-half, half)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun shadowFor(textColor: Int): Int {
        val lum = (0.299 * Color.red(textColor) + 0.587 * Color.green(textColor) + 0.114 * Color.blue(textColor))
        return if (lum > 140) 0x55000000 else 0xCC000000.toInt()
    }

    private fun panelBackground(c: Context): android.graphics.drawable.GradientDrawable {
        val bg = Store.bgColor(c)
        val a = Store.alpha(c) * 255 / 100
        val lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg))
        val stroke = if (lum > 140) 0x1A000000 else 0x1FFFFFFF
        return Ui.round(c, Color.argb(a, Color.red(bg), Color.green(bg), Color.blue(bg)), Store.radius(c).toFloat(), stroke, 1f)
    }

    // ---------- text model ----------

    private fun compose(): String = lines.joinToString("\n")

    private fun structureKey(): String =
        lines.size.toString() + "|" + (lines.firstOrNull() ?: "")

    private fun scheduleRender() {
        renderTask?.let { main.removeCallbacks(it) }
        val delay = if (animMode() == 0) 90L else 0L
        val t = Runnable { render() }
        renderTask = t
        main.postDelayed(t, delay)
    }

    private fun render() {
        val fv = front ?: return
        val next = compose()
        if (next == displayed) {
            reveal()
            return
        }
        val now = SystemClock.uptimeMillis()
        val mode = animMode()
        val key = structureKey()

        if (mode == 2) {
            sb.clear(); sb.append(next); fv.text = sb
            displayed = next
            lastKey = key
            reveal()
            return
        }
        if (key != lastKey && displayed.isNotEmpty() && mode != 1) {
            slideTo(next)
            lastKey = key
            reveal()
            return
        }
        if (mode == 1) {
            smoothSwap(fv)
            sb.clear(); sb.append(next); fv.text = sb
            displayed = next
            lastKey = key
            reveal()
            return
        }

        val cp = commonPrefix(displayed, next)
        if (next.startsWith(displayed) && displayed.isNotEmpty()) {
            sb.append(next.substring(displayed.length))
            fadeRange(sb, displayed.length, next.length, now, 240, 14)
            startAnim()
            fv.text = sb
        } else if (cp >= 4 && cp >= displayed.length / 2 && next.length > cp) {
            sb.delete(cp, sb.length)
            sb.append(next.substring(cp))
            fadeRange(sb, cp, next.length, now, 150, 6)
            startAnim()
            fv.text = sb
        } else {
            smoothSwap(fv)
            sb.clear(); sb.append(next)
            fadeRange(sb, 0, next.length, now, 220, 0)
            startAnim()
            fv.text = sb
        }
        displayed = next
        lastKey = key
        reveal()
    }

    private fun slideTo(next: String) {
        val old = front ?: return
        val nw = back ?: return
        val h = if (old.height > 0) old.height.toFloat() else lineH()
        old.text = old.text?.toString() ?: ""
        nw.text = next
        nw.alpha = 0f
        nw.translationY = h / 2f
        nw.visibility = View.VISIBLE
        old.animate().cancel()
        old.animate().alpha(0f).translationY(-h / 2f).setDuration(200).withEndAction {
            old.visibility = View.GONE
            old.translationY = 0f
        }.start()
        nw.animate().alpha(1f).translationY(0f).setDuration(230).start()
        front = nw
        back = old
        sb.clear(); sb.append(next)
        displayed = next
        nw.text = sb
    }

    private fun fadeRange(text: SpannableStringBuilder, from: Int, to: Int, now: Long, dur: Long, stagger: Long) {
        var i = from
        while (i < to) {
            val span = FadeSpan(0f)
            val start = now + (i - from) * stagger
            text.setSpan(span, i, i + 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
            active.add(Fade(span, start, dur))
            i++
        }
    }

    private fun commonPrefix(a: String, b: String): Int {
        var i = 0
        val n = min(a.length, b.length)
        while (i < n && a[i] == b[i]) i++
        return i
    }

    private fun smoothSwap(cv: TextView) {
        cv.animate().cancel()
        cv.alpha = 0.35f
        cv.translationY = dp(16f).toFloat()
        cv.animate().alpha(1f).translationY(0f).setDuration(230).start()
    }

    private fun startAnim() {
        animator?.cancel()
        if (active.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        var end = now
        for (f in active) if (f.start + f.dur > end) end = f.start + f.dur
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (end - now).coerceAtLeast(16L)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = SystemClock.uptimeMillis()
                val it2 = active.iterator()
                while (it2.hasNext()) {
                    val f = it2.next()
                    val p = ((t - f.start).toFloat() / f.dur).coerceIn(0f, 1f)
                    f.span.progress = 1f - (1f - p) * (1f - p)
                    if (p >= 1f) it2.remove()
                }
                front?.invalidate()
            }
            start()
        }
    }

    fun status(text: String) {
        main.post {
            if (root == null) showInternal()
            lines.clear()
            frozen = false
            displayed = text
            lastKey = "0|"
            sb.clear(); sb.append(text)
            front?.let {
                it.visibility = View.VISIBLE
                it.alpha = 1f
                it.translationY = 0f
                it.text = sb
            }
            reveal()
        }
    }

    fun partial(text: String) {
        main.post {
            if (root == null) showInternal()
            if (text == "⋯" || text.isBlank()) return@post
            val cap = Store.maxChars(ctx)
            val t = if (text.length > cap) text.takeLast(cap) else text
            if (lines.isEmpty() || frozen) {
                lines.addLast(t)
                frozen = false
            } else {
                lines.removeLast()
                lines.addLast(t)
            }
            trimLines()
            scheduleRender()
        }
    }

    fun final(text: String) {
        main.post {
            if (root == null) showInternal()
            if (text.isBlank()) return@post
            val cap = Store.maxChars(ctx)
            val t = if (text.length > cap) text.takeLast(cap) else text
            if (lines.isEmpty() || frozen) {
                lines.addLast(t)
            } else {
                lines.removeLast()
                lines.addLast(t)
            }
            frozen = true
            trimLines()
            render()
            scheduleHide(8000)
        }
    }

    private fun trimLines() {
        while (lines.size > maxLines()) lines.removeFirst()
    }

    private fun reveal() {
        hideTask?.let { main.removeCallbacks(it) }
        hideTask = null
        val r = root ?: return
        if (r.alpha < 1f) r.animate().alpha(1f).setDuration(140).start()
    }

    private fun scheduleHide(delay: Long) {
        val r = root ?: return
        hideTask?.let { main.removeCallbacks(it) }
        val task = Runnable {
            r.animate().alpha(0f).setDuration(420).start()
            hideTask = null
        }
        hideTask = task
        main.postDelayed(task, delay)
    }

    fun applyStyle() {
        main.post {
            val c = ctx
            front?.let { styleText(it) }
            back?.let { styleText(it) }
            bar?.background = Ui.round(c, withAlpha(Store.textColor(c), 150), 2f)
            root?.background = panelBackground(c)
            val p = params ?: return@post
            p.width = barWidth()
            p.x = clampX(Store.posX(c).let { if (it == Int.MIN_VALUE) 0 else it }, p.width)
            p.y = Store.posY(c).let { if (it == Int.MIN_VALUE) dp(96f) else it }
            try { wm.updateViewLayout(root ?: return@post, p) } catch (_: Throwable) {}
            displayed = ""
            render()
        }
    }

    fun hide() {
        main.post {
            hideTask?.let { main.removeCallbacks(it) }
            hideTask = null
            animator?.cancel()
            val r = root ?: return@post
            try { wm.removeView(r) } catch (_: Throwable) {}
            root = null
            params = null
        }
    }
}
