package com.dsh.floatcap

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQ_MIC = 11
    private val REQ_NOTIF = 12
    private val REQ_PROJ = 13

    private val dots = HashMap<String, View>()
    private val titles = HashMap<String, TextView>()
    private val swatches = ArrayList<View>()
    private lateinit var statusView: TextView
    private lateinit var dlButton: TextView
    private lateinit var startButton: TextView
    private var downloading = false
    private var preview: Overlay? = null
    private var histText: TextView? = null

    private fun dp(v: Int): Int = Ui.dp(this, v.toFloat())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(applicationContext)
        CrashLog.boot(applicationContext)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        setContentView(buildUi())
        CaptureService.onState = { on -> runOnUiThread { refreshRunState() } }
    }

    override fun onResume() {
        super.onResume()
        refreshEngine()
        refreshRunState()
        histText?.text = historySummary()
    }

    override fun onDestroy() {
        CaptureService.onState = null
        preview?.hide()
        preview = null
        super.onDestroy()
    }

    // ---------- helpers ----------

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            includeFontPadding = false
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.card(this@MainActivity)
        setPadding(dp(16), dp(14), dp(16), dp(16))
    }

    private fun sectionTitle(s: String): TextView =
        label(s, 12f, Ui.SUB).apply { setPadding(0, 0, 0, 0) }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(0xFF1E222B.toInt())
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(2); bottomMargin = dp(2) }
    }

    private fun slider(
        title: String,
        min: Int,
        max: Int,
        value: Int,
        fmt: (Int) -> String,
        onChange: (Int) -> Unit
    ): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val v = label(fmt(value), 14f, Ui.TEXT)
        head.addView(label(title, 14f, Ui.TEXT), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(v)
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val real = min + p
                    v.text = fmt(real)
                    onChange(real)
                    CaptureService.instance?.refreshStyle()
                    preview?.applyStyle()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(head)
        box.addView(seek, LinearLayout.LayoutParams(-1, dp(32)))
        return box
    }

    private fun chips(options: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val views = ArrayList<TextView>()
        fun paint(sel: Int) {
            views.forEachIndexed { i, tv ->
                if (i == sel) {
                    tv.background = Ui.round(this@MainActivity, 0x265B8CFF, 11f, Ui.ACCENT, 1f)
                    tv.setTextColor(Ui.ACCENT)
                } else {
                    tv.background = Ui.round(this@MainActivity, 0x0AFFFFFF, 11f, 0xFF2B303A.toInt(), 1f)
                    tv.setTextColor(0xFFAEB4BE.toInt())
                }
            }
        }
        options.forEachIndexed { i, s ->
            val tv = label(s, 13f, 0xFFAEB4BE.toInt()).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(8), dp(16), dp(8))
                isClickable = true
                setOnClickListener {
                    paint(i)
                    onPick(i)
                    CaptureService.instance?.refreshStyle()
                    preview?.applyStyle()
                }
            }
            views.add(tv)
            row.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        paint(selected)
        return row
    }

    private fun swatchRow(): LinearLayout {
        val colors = listOf(
            0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF6EF2D0.toInt(),
            0xFFFFD166.toInt(), 0xFFFF8FB1.toInt()
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        val current = Store.textColor(this)
        for (c in colors) {
            val v = View(this).apply {
                background = Ui.round(this@MainActivity, c, 14f, 0xFF3A404B.toInt(), 1.5f)
                isClickable = true
                setOnClickListener {
                    Store.setTextColor(this@MainActivity, c)
                    paintSwatches()
                    CaptureService.instance?.refreshStyle()
                    preview?.applyStyle()
                }
            }
            row.addView(v, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) })
            swatches.add(v)
        }
        paintSwatches()
        return row
    }

    private fun paintSwatches() {
        val current = Store.textColor(this)
        for ((i, v) in swatches.withIndex()) {
            val colors = listOf(
                0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF6EF2D0.toInt(),
                0xFFFFD166.toInt(), 0xFFFF8FB1.toInt()
            )
            val sel = colors.getOrNull(i) == current
            (v.layoutParams as? LinearLayout.LayoutParams)?.let {
                v.layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) }
            }
            v.background = Ui.round(
                this, colors[i], 14f,
                if (sel) Ui.ACCENT else 0xFF3A404B.toInt(),
                if (sel) 2.5f else 1.5f
            )
        }
    }

    // ---------- UI ----------

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            isFillViewport = true
            clipToPadding = false
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(34))
        }
        val frame = FrameLayout(this)
        val contentW = minOf(resources.displayMetrics.widthPixels - dp(24), dp(760))
        frame.addView(col, FrameLayout.LayoutParams(contentW, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        scroll.addView(frame, FrameLayout.LayoutParams(-1, -2))

        col.addView(label("浮字", 34f, Ui.TEXT, true))
        col.addView(label("内录系统声音 · 端侧实时字幕", 14f, Ui.SUB).apply { setPadding(0, dp(6), 0, 0) })

        if (!Store.onboarded(this)) {
            val ob = card()
            col.addView(ob, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
            ob.addView(sectionTitle("三步就能用"))
            val steps = listOf(
                "1  允许「显示在其他应用上层」，字幕才能飘在视频上",
                "2  开始字幕时同意「录制或投放屏幕」，选整个屏幕",
                "3  首次会下载约 230MB 模型，下完就完全离线"
            )
            for (t in steps) {
                ob.addView(label(t, 12.5f, 0xFFC6CBD4.toInt()).apply { setPadding(0, dp(9), 0, 0) })
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, 0)
            }
            row.addView(label("去开悬浮窗", 13f, Ui.ACCENT).apply {
                isClickable = true
                setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } catch (_: Throwable) {
                    }
                }
            })
            row.addView(label("知道了", 13f, 0xFF6E7681.toInt()).apply {
                setPadding(dp(24), 0, 0, 0)
                isClickable = true
                setOnClickListener {
                    Store.setOnboarded(this@MainActivity, true)
                    recreate()
                }
            })
            ob.addView(row)
        }

        // engine
        val engineCard = card()
        col.addView(engineCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        engineCard.addView(sectionTitle("识别引擎"))
        engineCard.addView(engineRow(Models.SENSEVOICE, "SenseVoice", "中文最准 · 带标点 · 停顿后上屏"))
        engineCard.addView(divider())
        engineCard.addView(engineRow(Models.PARAFORMER, "Paraformer", "逐字上屏 · 延迟最低 · 适合直播长课"))

        statusView = label("", 12f, Ui.SUB).apply { setPadding(0, dp(10), 0, 0) }
        engineCard.addView(statusView)
        dlButton = TextView(this).apply {
            text = "下载模型"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Ui.ACCENT)
            gravity = Gravity.CENTER
            setPadding(0, dp(11), 0, dp(11))
            background = Ui.round(this@MainActivity, 0x1A5B8CFF, 12f, 0x665B8CFF, 1f)
            setOnClickListener { startDownload() }
        }
        engineCard.addView(dlButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // appearance
        val look = card()
        col.addView(look, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        look.addView(sectionTitle("外观"))
        val presetNames = Store.presets.map { it.name }
        val currentPreset = presetNames.indexOf(Store.presetName(this))
        look.addView(chips(presetNames, if (currentPreset >= 0) currentPreset else 0) { i ->
            Store.applyPreset(this, Store.presets[i])
            recreate()
        })
        look.addView(slider("字号", 16, 44, Store.font(this), { "$it" }) { Store.setFont(this, it) })
        look.addView(slider("不透明度", 0, 100, Store.alpha(this), { "$it%" }) { Store.setAlpha(this, it) })
        look.addView(slider("圆角", 0, 28, Store.radius(this), { "$it" }) { Store.setRadius(this, it) })
        look.addView(slider("宽度", 60, 100, Store.widthPct(this), { "$it%" }) { Store.setWidth(this, it) })
        look.addView(label("行数", 14f, Ui.TEXT).apply { setPadding(0, dp(14), 0, 0) })
        look.addView(chips(listOf("1 行", "2 行", "3 行"), Store.lines(this) - 1) { Store.setLines(this, it + 1) })
        look.addView(label("字色", 14f, Ui.TEXT).apply { setPadding(0, dp(14), 0, 0) })
        look.addView(swatchRow())
        look.addView(label("字重", 14f, Ui.TEXT).apply { setPadding(0, dp(14), 0, 0) })
        look.addView(chips(listOf("常规", "加粗"), if (Store.bold(this)) 1 else 0) {
            Store.setBold(this, it == 1)
            CaptureService.instance?.refreshStyle()
            preview?.applyStyle()
        })
        look.addView(label("对齐", 14f, Ui.TEXT).apply { setPadding(0, dp(14), 0, 0) })
        look.addView(chips(listOf("左对齐", "居中"), if (Store.alignCenter(this)) 1 else 0) {
            Store.setAlignCenter(this, it == 1)
            CaptureService.instance?.refreshStyle()
            preview?.applyStyle()
        })
        look.addView(label("阴影", 14f, Ui.TEXT).apply { setPadding(0, dp(14), 0, 0) })
        look.addView(chips(listOf("无", "轻", "强"), Store.shadow(this)) {
            Store.setShadow(this, it)
            CaptureService.instance?.refreshStyle()
            preview?.applyStyle()
        })
        look.addView(slider("字间距", -2, 6, Store.spacing(this), { "$it" }) {
            Store.setSpacing(this, it)
        })

        // motion
        val motion = card()
        col.addView(motion, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        motion.addView(sectionTitle("动效"))
        motion.addView(chips(listOf("逐字浮现", "整句淡入", "立即显示"), Store.anim(this)) { Store.setAnim(this, it) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, dp(2))
        }
        actions.addView(label("预览字幕条", 13f, Ui.ACCENT).apply {
            isClickable = true
            setOnClickListener { previewOverlay() }
        })
        actions.addView(label("复位位置", 13f, 0xFF6E7681.toInt()).apply {
            setPadding(dp(24), 0, 0, 0)
            isClickable = true
            setOnClickListener {
                Store.resetPos(this@MainActivity)
                CaptureService.instance?.refreshStyle()
                preview?.applyStyle()
                toast("字幕位置已复位")
            }
        })
        motion.addView(actions)

        val tuning = card()
        col.addView(tuning, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        tuning.addView(sectionTitle("识别"))
        tuning.addView(slider("每行字数", 30, 90, Store.maxChars(this), { "$it" }) { Store.setMaxChars(this, it) })
        tuning.addView(slider("断句静音", 20, 120, Store.vadSilence(this), { "${it / 100f} 秒" }) { Store.setVadSilence(this, it) })
        tuning.addView(slider("端点静音", 60, 200, Store.endpointSilence(this), { "${it / 100f} 秒" }) { Store.setEndpointSilence(this, it) })
        tuning.addView(slider("线程数", 1, 4, Store.threads(this), { "$it 核" }) { Store.setThreads(this, it) })
        tuning.addView(label("改这几项要停止再开始字幕才生效", 11f, 0xFF6E7681.toInt()).apply { setPadding(0, dp(8), 0, 0) })

        val history = card()
        col.addView(history, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        history.addView(sectionTitle("字幕记录"))
        val histText = label("本次还没记录", 12f, 0xFFC6CBD4.toInt()).apply { setPadding(0, dp(9), 0, 0) }
        this.histText = histText
        history.addView(histText)
        histText.text = historySummary()
        val hrow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        hrow.addView(label("导出 TXT", 13f, Ui.ACCENT).apply {
            isClickable = true
            setOnClickListener {
                val name = History.export(this@MainActivity)
                if (name == null) toast("还没有字幕可导出") else toast("已保存到 Download/DSH-Go/$name")
            }
        })
        hrow.addView(label("清空", 13f, 0xFF6E7681.toInt()).apply {
            setPadding(dp(24), 0, 0, 0)
            isClickable = true
            setOnClickListener {
                History.clear()
                histText.text = "已清空"
            }
        })
        history.addView(hrow)

        // start
        startButton = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                if (CaptureService.running) {
                    startService(Intent(this@MainActivity, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
                } else {
                    beginStart()
                }
            }
        }
        col.addView(startButton, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(22) })

        col.addView(label(
            "开始时会请求「录制或投放屏幕」，请选『整个屏幕』。\n受版权保护的内容（如会员影视）无法被采集。",
            11.5f, Ui.SUB
        ).apply {
            setPadding(0, dp(14), 0, 0)
            gravity = Gravity.CENTER
        })
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (t: Throwable) {
            "?"
        }
        col.addView(label("浮字 v$ver · 本地识别", 11f, 0xFF5A6068.toInt()).apply {
            setPadding(0, dp(10), 0, 0)
            gravity = Gravity.CENTER
        })

        paintDots()
        return scroll
    }

    private fun engineRow(id: String, title: String, desc: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(11), 0, dp(11))
            isClickable = true
            setOnClickListener { selectEngine(id) }
        }
        val dot = View(this)
        row.addView(dot, LinearLayout.LayoutParams(dp(17), dp(17)).apply { rightMargin = dp(13) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val t = label(title, 16f, Ui.TEXT, true)
        texts.addView(t)
        texts.addView(label(desc, 11.5f, Ui.SUB).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        dots[id] = dot
        titles[id] = t
        return row
    }

    private fun paintDots() {
        val id = Store.engine(this)
        for ((k, v) in dots) {
            if (k == id) {
                v.background = Ui.round(this, Ui.ACCENT, 9f)
                titles[k]?.setTextColor(Ui.TEXT)
            } else {
                v.background = Ui.round(this, 0, 9f, 0xFF4A505C.toInt(), 1.5f)
                titles[k]?.setTextColor(0xFFC6CBD4.toInt())
            }
        }
    }

    private fun selectEngine(id: String) {
        if (CaptureService.running) {
            toast("先停止字幕再切换引擎")
            return
        }
        Store.setEngine(this, id)
        paintDots()
        refreshEngine()
    }

    private fun refreshEngine() {
        val id = Store.engine(this)
        val ready = Models.isReady(this, id)
        if (ready) {
            statusView.text = "模型已就绪 · 约 ${Models.downloadSize(id) / 1024 / 1024} MB 存在本机"
            dlButton.visibility = View.GONE
        } else {
            statusView.text = "首次使用需下载约 ${Models.downloadSize(id) / 1024 / 1024} MB 模型（只下一次）"
            dlButton.visibility = View.VISIBLE
            if (!downloading) dlButton.text = "下载模型"
        }
        dlButton.isEnabled = !downloading
    }

    private fun refreshRunState() {
        if (CaptureService.running) {
            startButton.text = "停止字幕"
            startButton.setTextColor(0xFFFF8A8A.toInt())
            startButton.background = Ui.round(this, 0xFF1B1E25.toInt(), 28f, 0x66FF8A8A, 1f)
        } else {
            startButton.text = "开始字幕"
            startButton.setTextColor(0xFF0B0C0F.toInt())
            startButton.background = Ui.round(this, Ui.ACCENT, 28f)
        }
    }

    private fun startDownload() {
        if (downloading) return
        val id = Store.engine(this)
        downloading = true
        dlButton.isEnabled = false
        dlButton.text = "0%"
        Thread {
            Models.Job(this, id).run(
                { p, _ ->
                    runOnUiThread {
                        dlButton.text = "$p%"
                        statusView.text = "下载中 $p% · 可锁屏等待"
                    }
                },
                { ok, err ->
                    runOnUiThread {
                        downloading = false
                        if (ok) {
                            toast("模型下载完成")
                            refreshEngine()
                        } else {
                            statusView.text = "下载失败：$err"
                            dlButton.visibility = View.VISIBLE
                            dlButton.isEnabled = true
                            dlButton.text = "重试下载"
                        }
                    }
                }
            )
        }.start()
    }

    private fun historySummary(): String {
        val n = History.count()
        if (n == 0) return "本次还没记录 · 开始字幕后会自动存在这里"
        val tail = History.snapshot().takeLast(2)
        return "共 $n 句\n" + tail.joinToString("\n")
    }

    private fun previewOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            toast("请先允许「显示在其他应用上层」")
            return
        }
        val ov = preview ?: Overlay(this) {
            preview?.hide()
            preview = null
        }.also { preview = it }
        ov.show()
        ov.final("这是字幕预览，拖动可调整位置")
        startButton.postDelayed({ ov.final("新的一行会自动接上，超出部分自动消失") }, 2000)
        startButton.postDelayed({
            preview?.hide()
            preview = null
        }, 7000)
    }

    // ---------- permissions ----------

    private fun beginStart() {
        val id = Store.engine(this)
        if (!Models.isReady(this, id)) {
            toast("先把模型下载好再开始")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("请允许「显示在其他应用上层」，回来再点开始")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Throwable) {
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        askProjection()
    }

    private fun askProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJ)
        } catch (t: Throwable) {
            toast("无法发起屏幕录制授权")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                askProjection()
            } else {
                toast("需要录音权限才能采集系统声音")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PROJ) return
        if (resultCode == RESULT_OK && data != null) {
            val i = Intent(this, CaptureService::class.java)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_RESULT, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data)
            startForegroundService(i)
            startButton.postDelayed({ refreshRunState() }, 400)
        } else {
            toast("没有授权，字幕采集不到声音")
        }
    }

    private fun toast(s: String) {
        runOnUiThread { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    }
}
