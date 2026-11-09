package com.dsh.floatcap

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Models {
    const val SENSEVOICE = "sensevoice"
    const val PARAFORMER = "paraformer"

    class Spec(val name: String, val path: String, val size: Long)

    private const val SV = "csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/"
    private const val PF = "csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/main/"

    val sense = listOf(
        Spec("model.int8.onnx", SV + "model.int8.onnx", 239233841L),
        Spec("tokens.txt", SV + "tokens.txt", 315894L)
    )
    val para = listOf(
        Spec("encoder.int8.onnx", PF + "encoder.int8.onnx", 165462184L),
        Spec("decoder.int8.onnx", PF + "decoder.int8.onnx", 71664561L),
        Spec("tokens.txt", PF + "tokens.txt", 75756L)
    )
    val vad = Spec("silero_vad.onnx", "csukuangfj/vad/resolve/main/silero_vad.onnx", 1_807_522L)

    private val mirrors = listOf("https://hf-mirror.com/", "https://huggingface.co/")

    fun root(c: Context): File = File(c.getExternalFilesDir(null) ?: c.filesDir, "models")
    fun dir(c: Context, engine: String): File = File(root(c), engine)
    fun vadFile(c: Context): File = File(File(root(c), "vad"), "silero_vad.onnx")

    fun specs(engine: String): List<Spec> = if (engine == PARAFORMER) para else sense

    fun downloadSize(engine: String): Long = specs(engine).sumOf { it.size } + vad.size

    fun isReady(c: Context, engine: String): Boolean {
        val v = vadFile(c)
        if (!v.exists() || v.length() < 1_000_000L) return false
        return specs(engine).all { s ->
            val f = File(dir(c, engine), s.name)
            f.exists() && (s.size <= 0L || f.length() == s.size)
        }
    }

    class Job(private val ctx: Context, private val engine: String) {
        @Volatile private var cancelled = false
        fun cancel() { cancelled = true }

        fun run(onProgress: (Int, Long) -> Unit, onDone: (Boolean, String) -> Unit) {
            val tasks = ArrayList<Pair<Spec, File>>()
            tasks.add(vad to vadFile(ctx))
            for (s in specs(engine)) tasks.add(s to File(dir(ctx, engine), s.name))
            val total = tasks.sumOf { it.first.size }
            var base = 0L
            try {
                for ((spec, target) in tasks) {
                    if (cancelled) { onDone(false, "已取消"); return }
                    target.parentFile?.mkdirs()
                    val expect = spec.size
                    if (target.exists() && target.length() == expect) { base += expect; continue }
                    var lastErr = "下载失败"
                    var ok = false
                    for (m in mirrors) {
                        if (cancelled) break
                        for (attempt in 1..3) {
                            if (cancelled) break
                            try {
                                fetch(m + spec.path, target, expect) { got ->
                                    onProgress((((base + got) * 100) / total).toInt(), base + got)
                                }
                                ok = true
                                break
                            } catch (e: Throwable) {
                                lastErr = (e.javaClass.simpleName + ": " + (e.message ?: ""))
                                try { Thread.sleep(700L * attempt) } catch (_: Throwable) {}
                            }
                        }
                        if (ok) break
                    }
                    if (!ok) { onDone(false, lastErr); return }
                    base += expect
                }
                onDone(true, "")
            } catch (e: Throwable) {
                onDone(false, e.message ?: "下载失败")
            }
        }

        private fun fetch(url: String, target: File, expect: Long, onBytes: (Long) -> Unit) {
            val part = File(target.path + ".part")
            var existing = 0L
            if (part.exists()) {
                if (part.length() == expect) { part.renameTo(target); return }
                existing = part.length()
            }
            var conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "FloatCap/1.0 (Android)")
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
            conn.connect()
            var code = conn.responseCode
            if (code != 200 && code != 206) {
                // retry once without range
                conn.disconnect()
                existing = 0L
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "FloatCap/1.0 (Android)")
                conn.setRequestProperty("Accept", "*/*")
                conn.setRequestProperty("Accept-Encoding", "identity")
                conn.connect()
                code = conn.responseCode
            }
            if (code != 200 && code != 206) throw RuntimeException("HTTP $code")
            if (code == 200) existing = 0L
            FileOutputStream(part, existing > 0).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(1 shl 16)
                    var got = existing
                    var lastTick = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 250) { lastTick = now; onBytes(got) }
                    }
                    out.flush()
                    try { out.fd.sync() } catch (_: Throwable) {}
                }
            }
            conn.disconnect()
            if (part.length() != expect) {
                throw RuntimeException("校验失败 " + part.length() + "/" + expect)
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, true)
                part.delete()
            }
        }
    }
}
