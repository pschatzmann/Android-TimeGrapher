package com.pschatzmann.timegrapher.dsp

import com.pschatzmann.timegrapher.Tick
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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
    sensitivity: Double,
    private val onTick: (Tick) -> Unit,
) {
    private val hp = Biquad.highPass(sampleRate.toDouble(), lowHz.toDouble())
    private val lp = Biquad.lowPass(sampleRate.toDouble(), highHz.toDouble())

    private val smoothAlpha = 1.0 - kotlin.math.exp(-1.0 / (0.0002 * sampleRate))   // ~0.2 ms
    private val floorAlpha = 1.0 - kotlin.math.exp(-1.0 / (1.0 * sampleRate))       // ~1 s
    private val captureLen = (0.030 * sampleRate).toInt()
    private val minSeparation = (0.0015 * sampleRate).toInt()
    private val warmup = (0.5 * sampleRate).toLong()
    private val minThreshold = 0.0001
    private val agcRelease = kotlin.math.exp(-1.0 / (3.0 * sampleRate))             // ~3 s peak hold decay
    private val agcRise = 1.0 - kotlin.math.exp(-1.0 / (1.0 * sampleRate))          // ~1 s gain increase
    private val agcTarget = 0.5
    private val agcMaxGain = 1000.0                                                  // 60 dB
    private val agcGate = 8.0   // only raise the gain while peaks stand this far above the noise floor

    /** Threshold as a multiple of the noise floor; may be changed while running. */
    @Volatile var sensitivity = sensitivity

    /** Automatic gain control keeps the filtered peaks near a fixed level; otherwise [manualGainDb] applies. */
    @Volatile var agc = false
    @Volatile var manualGainDb = 0.0

    /** Gain currently applied to the filtered signal, in dB. */
    val gainDb: Double get() = 20 * kotlin.math.log10(gain)
    private var gain = 1.0
    private var agcEnv = 0.0

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

    /** Processes [count] samples; when [out] is given it receives the filtered, amplified signal (clipped to +-1). */
    fun process(samples: FloatArray, count: Int, out: FloatArray? = null) {
        for (i in 0 until count) {
            step(samples[i].toDouble())
            if (out != null) out[i] = lastOut.toFloat().coerceIn(-1f, 1f)
        }
    }
    private var lastOut = 0.0

    private fun step(x: Double) {
        val f = lp.process(hp.process(x))
        val y = f * applyGain(abs(f))
        lastOut = y
        smooth += smoothAlpha * (abs(y) - smooth)
        level = max(smooth, level * 0.9999)
        if (smooth > blockPeak) blockPeak = smooth
        val threshold = max(floor * sensitivity, minThreshold)

        if (capturing) {
            val k = (index - captureStart).toInt()
            capture[k] = smooth
            trackFloorClamped()
            if (k == captureLen - 1) finishCapture(threshold)
        } else {
            if (index < warmup) {
                floor += floorAlpha * 4 * (smooth - floor)
            } else if (smooth > threshold) {
                capturing = true
                captureStart = index
                capture[0] = smooth
                trackFloorClamped()
            } else {
                floor += floorAlpha * (smooth - floor)
            }
            noiseFloor = floor
        }
        index++
    }

    /**
     * Keeps the floor following the noise while a beat is being captured. Without it a floor that falls behind a
     * rising level (e.g. AGC gain increasing) would never recover, because every capture ends in a new trigger.
     * The input is clamped so the beat itself hardly moves the floor.
     */
    private fun trackFloorClamped() {
        floor += floorAlpha * (min(smooth, max(3 * floor, 1e-6)) - floor)
    }

    private fun applyGain(a: Double): Double {
        if (!agc) {
            gain = Math.pow(10.0, manualGainDb / 20)
            agcEnv = 0.0
            return gain
        }
        agcEnv = max(a, agcEnv * agcRelease)
        val wanted = (agcTarget / max(agcEnv, 1e-9)).coerceIn(1.0, agcMaxGain)
        // pure noise must not be amplified up to the target, it would then trigger the detector at random
        val signal = index >= warmup && agcEnv * gain > agcGate * floor
        val before = gain
        gain = if (wanted < gain) wanted else if (signal) gain + agcRise * (wanted - gain) else gain
        floor *= gain / before   // the floor is in amplified units; a gain change must not look like a noise change
        return gain
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
