package com.pschatzmann.timegrapher.dsp

import com.pschatzmann.timegrapher.Tick
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Second-order Butterworth section (RBJ cookbook), direct form I. */
class Biquad private constructor(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }

    companion object {
        private const val Q = 0.7071067811865476

        fun highPass(fs: Double, f: Double) = build(fs, f) { c, a -> doubleArrayOf((1 + c) / 2, -(1 + c), (1 + c) / 2) }
        fun lowPass(fs: Double, f: Double) = build(fs, f) { c, a -> doubleArrayOf((1 - c) / 2, 1 - c, (1 - c) / 2) }

        private fun build(fs: Double, f: Double, num: (Double, Double) -> DoubleArray): Biquad {
            val w = 2 * PI * f.coerceIn(10.0, fs * 0.45) / fs
            val c = cos(w)
            val alpha = sin(w) / (2 * Q)
            val a0 = 1 + alpha
            val n = num(c, alpha)
            return Biquad(n[0] / a0, n[1] / a0, n[2] / a0, -2 * c / a0, (1 - alpha) / a0)
        }
    }
}

/**
 * Streaming tick detector: band-pass, rectify, smooth, adaptive threshold above a slowly tracked noise floor.
 * After the first threshold crossing a short capture window records the beat's sub-peaks so the
 * lock-to-drop time (needed for the amplitude) can be measured.
 */
class TickDetector(
    private val sampleRate: Int,
    lowHz: Int,
    highHz: Int,
    private val sensitivity: Double,
    private val onTick: (Tick) -> Unit,
) {
    private val hp = Biquad.highPass(sampleRate.toDouble(), lowHz.toDouble())
    private val lp = Biquad.lowPass(sampleRate.toDouble(), highHz.toDouble())

    private val smoothAlpha = 1.0 - kotlin.math.exp(-1.0 / (0.0002 * sampleRate))   // ~0.2 ms
    private val floorAlpha = 1.0 - kotlin.math.exp(-1.0 / (1.0 * sampleRate))       // ~1 s
    private val captureLen = (0.030 * sampleRate).toInt()
    private val minSeparation = (0.0015 * sampleRate).toInt()
    private val warmup = (0.5 * sampleRate).toLong()
    private val minThreshold = 0.002

    private var index = 0L
    private var smooth = 0.0
    private var floor = 0.0
    private var capturing = false
    private var captureStart = 0L
    private val capture = DoubleArray(captureLen)

    /** Current smoothed noise floor and level for the UI meter. */
    var noiseFloor = 0.0; private set
    var level = 0.0; private set
    private var blockPeak = 0.0

    /** Detection threshold currently in force (same scale as the level). */
    val threshold: Double get() = max(floor * sensitivity, minThreshold)

    /** Largest filtered level since the last call, for the volume chart. */
    fun takeBlockPeak(): Double { val p = blockPeak; blockPeak = 0.0; return p }

    fun process(samples: FloatArray, count: Int) {
        for (i in 0 until count) step(samples[i].toDouble())
    }

    private fun step(x: Double) {
        val y = lp.process(hp.process(x))
        smooth += smoothAlpha * (abs(y) - smooth)
        level = max(smooth, level * 0.9999)
        if (smooth > blockPeak) blockPeak = smooth
        val threshold = max(floor * sensitivity, minThreshold)

        if (capturing) {
            val k = (index - captureStart).toInt()
            capture[k] = smooth
            if (k == captureLen - 1) finishCapture(threshold)
        } else {
            if (index < warmup) {
                floor += floorAlpha * 4 * (smooth - floor)
            } else if (smooth > threshold) {
                capturing = true
                captureStart = index
                capture[0] = smooth
            } else {
                floor += floorAlpha * (smooth - floor)
            }
            noiseFloor = floor
        }
        index++
    }

    private fun finishCapture(threshold: Double) {
        capturing = false
        var p1 = 0
        for (k in capture.indices) if (capture[k] > capture[p1]) p1 = k
        var p2 = -1
        for (k in capture.indices) {
            if (abs(k - p1) < minSeparation) continue
            if (p2 < 0 || capture[k] > capture[p2]) p2 = k
        }
        // the second sound must stand out as a real local peak above the threshold
        val second = p2 >= 0 && capture[p2] > threshold && isLocalPeak(p2)
        val ld = if (second) abs(p2 - p1) * 1000.0 / sampleRate else null
        // beat time = onset of the sound; the capture started at the first crossing
        val t = captureStart.toDouble() / sampleRate
        onTick(Tick(t, capture[p1], ld))
    }

    private fun isLocalPeak(k: Int): Boolean {
        val lo = max(0, k - 3)
        val hi = minOf(capture.size - 1, k + 3)
        for (j in lo..hi) if (capture[j] > capture[k]) return false
        return true
    }

    fun snrDb(peak: Double): Double = 20 * kotlin.math.log10(peak / max(floor, 1e-6))

    companion object {
        fun rms(samples: FloatArray, count: Int): Double {
            var s = 0.0
            for (i in 0 until count) s += samples[i] * samples[i]
            return sqrt(s / max(1, count))
        }
    }
}
