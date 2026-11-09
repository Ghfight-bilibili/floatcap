package com.dsh.floatcap

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra("trace") ?: "空"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0C0F.toInt())
            setPadding(32, 48, 32, 32)
        }
        val title = TextView(this).apply {
            setText("浮字出错了")
            setTextColor(0xFFFF8A8A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val body = TextView(this).apply {
            setText(text)
            setTextColor(0xFFDDE2EA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextIsSelectable(true)
        }
        root.addView(title)
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val close = TextView(this).apply {
            setText("关闭")
            setTextColor(0xFF0B0C0F.toInt())
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setBackground(Ui.round(this@CrashActivity, Ui.ACCENT, 24f))
            setPadding(0, 14, 0, 14)
            setOnClickListener { finish() }
        }
        root.addView(close, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    companion object {
        fun show(ctx: android.content.Context, trace: String) {
            try {
                val i = Intent(ctx, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("trace", trace)
                ctx.startActivity(i)
            } catch (t: Throwable) {
            }
        }
    }
}
