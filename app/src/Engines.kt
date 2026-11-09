package com.dsh.floatcap

import android.content.Context
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

interface Engine : AutoCloseable {
    fun accept(samples: FloatArray, n: Int)
    fun finish()
}

class ParaformerEngine(
    ctx: Context,
    dir: String,
    endpointSilence: Float,
    threads: Int,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit
) : Engine {

    private val rec: OnlineRecognizer
    private val stream: OnlineStream

    init {
        val cfg = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80, dither = 0f),
            modelConfig = OnlineModelConfig(
                paraformer = OnlineParaformerModelConfig(
                    encoder = "$dir/encoder.int8.onnx",
                    decoder = "$dir/decoder.int8.onnx"
                ),
                tokens = "$dir/tokens.txt",
                numThreads = threads,
                provider = "cpu",
                modelType = "paraformer"
            ),
            enableEndpoint = true,
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, endpointSilence, 0f),
                rule2 = EndpointRule(true, 0.6f, 8f),
                rule3 = EndpointRule(false, 0f, 20f)
            )
        )
        rec = OnlineRecognizer(null, cfg)
        stream = rec.createStream()
    }

    override fun accept(samples: FloatArray, n: Int) {
        val chunk = if (n == samples.size) samples else samples.copyOf(n)
        stream.acceptWaveform(chunk, 16000)
        while (rec.isReady(stream)) rec.decode(stream)
        val text = rec.getResult(stream).text.trim()
        if (text.isNotEmpty()) onPartial(text)
        if (rec.isEndpoint(stream)) {
            if (text.isNotEmpty()) onFinal(text)
            rec.reset(stream)
        }
    }

    override fun finish() {
        try {
            stream.acceptWaveform(FloatArray(8000), 16000)
            while (rec.isReady(stream)) rec.decode(stream)
            val t = rec.getResult(stream).text.trim()
            if (t.isNotEmpty()) onFinal(t)
        } catch (_: Throwable) {
        }
    }

    override fun close() {
        try { stream.release() } catch (_: Throwable) {}
        try { rec.release() } catch (_: Throwable) {}
    }
}

class SenseVoiceEngine(
    ctx: Context,
    dir: String,
    vadPath: String,
    vadSilence: Float,
    threads: Int,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit
) : Engine {

    private val rec: OfflineRecognizer
    private val vad: Vad

    init {
        rec = OfflineRecognizer(
            null,
            OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80, dither = 0f),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = "$dir/model.int8.onnx",
                        language = "auto",
                        useInverseTextNormalization = true
                    ),
                    tokens = "$dir/tokens.txt",
                    numThreads = threads,
                    provider = "cpu",
                    modelType = "sensevoice"
                )
            )
        )
        vad = Vad(
            null,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadPath,
                    threshold = 0.45f,
                    minSilenceDuration = vadSilence,
                    minSpeechDuration = 0.25f,
                    maxSpeechDuration = 12f
                ),
                sampleRate = 16000,
                numThreads = 1,
                provider = "cpu"
            )
        )
    }

    override fun accept(samples: FloatArray, n: Int) {
        val chunk = if (n == samples.size) samples else samples.copyOf(n)
        vad.acceptWaveform(chunk)
        var spoke = false
        while (!vad.empty()) {
            val seg = vad.front()
            vad.pop()
            spoke = true
            val text = decode(seg.samples)
            if (text.isNotEmpty()) onFinal(text)
        }
        if (!spoke && vad.isSpeechDetected()) onPartial("⋯")
    }

    private fun decode(samples: FloatArray): String {
        val s = rec.createStream()
        return try {
            s.acceptWaveform(samples, 16000)
            rec.decode(s)
            rec.getResult(s).text.trim()
        } catch (t: Throwable) {
            ""
        } finally {
            try { s.release() } catch (_: Throwable) {}
        }
    }

    override fun finish() {
        try {
            vad.flush()
            while (!vad.empty()) {
                val seg = vad.front()
                vad.pop()
                val text = decode(seg.samples)
                if (text.isNotEmpty()) onFinal(text)
            }
        } catch (_: Throwable) {
        }
    }

    override fun close() {
        try { vad.release() } catch (_: Throwable) {}
        try { rec.release() } catch (_: Throwable) {}
    }
}
