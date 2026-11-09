package com.dsh.floatcap

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class CaptureService : Service() {

    companion object {
        const val ACTION_START = "com.dsh.floatcap.START"
        const val ACTION_STOP = "com.dsh.floatcap.STOP"
        const val EXTRA_RESULT = "resultCode"
        const val EXTRA_DATA = "data"
        const val CHANNEL = "capture"

        @Volatile var running = false
        @Volatile var instance: CaptureService? = null
        var onState: ((Boolean) -> Unit)? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private var engine: Engine? = null
    private var overlay: Overlay? = null
    @Volatile private var stopFlag = false
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopAll()
            ACTION_START -> startCapture(intent)
            else -> {
                if (!running) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "字幕采集", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            ch.enableVibration(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL)
            .setContentTitle("浮字正在生成字幕")
            .setContentText("声音只在本机识别，不会上传")
            .setSmallIcon(resources.getIdentifier("ic_notify", "drawable", packageName))
            .setOngoing(true)
            .setContentIntent(open)
        try {
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "停止", stop
                ).build()
            )
        } catch (_: Throwable) {
        }
        return b.build()
    }

    private fun startCapture(intent: Intent) {
        try {
            startCaptureInner(intent)
        } catch (t: Throwable) {
            trace("start failed: " + android.util.Log.getStackTraceString(t))
            overlay?.status("启动失败：" + (t.message ?: t.javaClass.simpleName))
            main.postDelayed({ stopAll() }, 2500)
        }
    }

    private fun startCaptureInner(intent: Intent) {
        if (running) return
        trace("enter")
        running = true
        stopping = false
        stopFlag = false

        trace("before fg")
        try {
            startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            trace("after fg")
        } catch (t: Throwable) {
            trace("fg failed: " + android.util.Log.getStackTraceString(t))
            throw t
        }

        overlay = Overlay(this) { stopAll() }
        overlay?.status("准备中…")

        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
        val code = intent.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED)
        if (data == null) {
            overlay?.status("没有拿到屏幕录制授权")
            main.postDelayed({ stopAll() }, 1500)
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = try {
            mpm.getMediaProjection(code, data)
        } catch (t: Throwable) {
            null
        }
        if (projection == null) {
            overlay?.status("屏幕录制授权已失效，请重新开始")
            main.postDelayed({ stopAll() }, 1800)
            return
        }
        trace("projection ok")
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (!stopping) stopAll()
            }
        }, main)

        History.begin()
        val engineId = Store.engine(this)
        trace("starting worker")
        worker = Thread({ loop(engineId) }, "floatcap-audio").also { it.start() }
        onState?.invoke(true)
    }

    private fun loop(engineId: String) {
        try {
            if (!Models.isReady(this, engineId)) {
                status("模型还没下载好，回到「浮字」里点下载")
                Thread.sleep(2600)
                return
            }
            status("加载模型…")
            trace("worker: loading engine " + engineId)
            val dir = Models.dir(this, engineId).absolutePath
            val vadPath = Models.vadFile(this).absolutePath
            val threads = Store.threads(this)
            val endpoint = Store.endpointSilence(this) / 100f
            val vadSil = Store.vadSilence(this) / 100f
            val onPartial: (String) -> Unit = { p -> overlay?.partial(p) }
            val onFinal: (String) -> Unit = { f ->
                overlay?.final(f)
                History.add(f)
                trace("final n=" + History.count() + " " + f.take(24))
            }
            engine = if (engineId == Models.PARAFORMER) {
                ParaformerEngine(this, dir, endpoint, threads, onPartial, onFinal)
            } else {
                SenseVoiceEngine(this, dir, vadPath, vadSil, threads, onPartial, onFinal)
            }
            trace("worker: engine ready")
            if (stopFlag) return

            val proj = projection ?: return
            val capCfg = AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBuf = AudioRecord.getMinBufferSize(
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(minBuf * 3, 32000)
            val rec = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(16000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setAudioPlaybackCaptureConfig(capCfg)
                .build()
            record = rec
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                status("无法采集系统声音（可能被系统限制）")
                return
            }
            status("开始识别，播点有声的内容吧")
            trace("worker: recording")
            rec.startRecording()

            val shorts = ShortArray(1600)
            val floats = FloatArray(1600)
            var idle = 0
            while (!stopFlag) {
                val n = try { rec.read(shorts, 0, shorts.size) } catch (t: Throwable) { -1 }
                if (n < 0) break
                if (n == 0) continue
                var peak = 0
                for (i in 0 until n) {
                    val v = shorts[i]
                    val a = if (v < 0) -v.toInt() else v.toInt()
                    if (a > peak) peak = a
                    floats[i] = v / 32768f
                }
                if (peak < 60) {
                    idle++
                    if (idle == 300) status("在等声音…记得把视频音量打开")
                } else {
                    idle = 0
                }
                engine?.accept(floats, n)
            }
        } catch (t: Throwable) {
            status("出错了：" + (t.message ?: t.javaClass.simpleName))
        } finally {
            try { record?.stop() } catch (_: Throwable) {}
            try { record?.release() } catch (_: Throwable) {}
            record = null
            try { engine?.close() } catch (_: Throwable) {}
            engine = null
        }
    }

    private fun trace(s: String) {
        CrashLog.trace(this, "floatcap-trace.txt", System.currentTimeMillis().toString() + " " + s)
    }

    private fun status(text: String) {
        main.post { overlay?.status(text) }
    }

    fun refreshStyle() {
        overlay?.applyStyle()
    }

    fun stopAll() {
        if (stopping) return
        stopping = true
        stopFlag = true
        val w = worker
        worker = null
        if (w != null && w !== Thread.currentThread()) {
            try { w.join(500) } catch (_: Throwable) {}
        }
        try { projection?.stop() } catch (_: Throwable) {}
        projection = null
        overlay?.hide()
        overlay = null
        running = false
        try { stopForeground(true) } catch (_: Throwable) {}
        onState?.invoke(false)
        stopSelf()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.applyStyle()
    }

    override fun onDestroy() {
        instance = null
        running = false
        super.onDestroy()
    }
}
