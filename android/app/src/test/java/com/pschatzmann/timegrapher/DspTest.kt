package com.pschatzmann.timegrapher

import com.pschatzmann.timegrapher.dsp.KpiCalculator
import com.pschatzmann.timegrapher.dsp.TickDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class DspTest {
    private val fs = 48000

    /** Synthetic watch: two decaying 4 kHz bursts (unlock, drop) per beat plus noise. */
    private fun synth(bph: Int, seconds: Int, periodScale: Double = 1.0, oddShiftMs: Double = 0.0, ldMs: Double = 7.0): FloatArray {
        val out = FloatArray(seconds * fs)
        val rnd = Random(1)
        for (i in out.indices) out[i] = (rnd.nextGaussian() * 0.002).toFloat()
        val period = 3600.0 / bph * periodScale
        var n = 0
        while (true) {
            val t = 0.5 + n * period + if (n % 2 == 1) oddShiftMs / 1000.0 else 0.0
            if (t + 0.05 >= seconds) break
            burst(out, t, 0.5f)
            burst(out, t + ldMs / 1000.0, 0.35f)
            n++
        }
        return out
    }

    private fun burst(buf: FloatArray, t: Double, amp: Float) {
        val s0 = (t * fs).toInt()
        for (k in 0 until (0.004 * fs).toInt()) {
            val tt = k.toDouble() / fs
            val idx = s0 + k
            if (idx < buf.size) buf[idx] += (amp * exp(-tt / 0.0008) * sin(2 * PI * 4000 * tt)).toFloat()
        }
    }

    private fun detect(sig: FloatArray, agc: Boolean = false, gainDb: Double = 0.0): List<Tick> {
        val ticks = ArrayList<Tick>()
        val det = TickDetector(fs, 2000, 8000, 4.0) { ticks += it }
        det.agc = agc
        det.manualGainDb = gainDb
        var pos = 0
        while (pos < sig.size) {
            val n = minOf(1024, sig.size - pos)
            det.process(sig.copyOfRange(pos, pos + n), n)
            pos += n
        }
        return ticks
    }

    private fun weak() = synth(28800, 20).also { for (i in it.indices) it[i] *= 0.004f }

    @Test fun weakSignalIsDetectedWithAndWithoutGain() {
        assertEquals(156.0, detect(weak()).size.toDouble(), 5.0)
        assertEquals(156.0, detect(weak(), gainDb = 30.0).size.toDouble(), 5.0)
    }

    @Test fun thresholdFollowsSensitivity() {
        fun threshold(sens: Double): Double {
            val det = TickDetector(fs, 2000, 8000, sens) { }
            val sig = synth(28800, 5)
            det.process(sig, sig.size)
            return det.threshold
        }
        assertEquals(2.0, threshold(8.0) / threshold(4.0), 0.1)
    }

    @Test fun agcAmplifiesWeakSignal() {
        assertEquals(156.0, detect(weak(), agc = true).size.toDouble(), 8.0)
    }

    @Test fun agcDoesNotAmplifyNoise() {
        val rnd = Random(2)
        val noise = FloatArray(30 * fs) { (rnd.nextGaussian() * 0.0005).toFloat() }
        assertEquals(0.0, detect(noise, agc = true).size.toDouble(), 3.0)
    }

    @Test fun detectsAllBeats() {
        val ticks = detect(synth(28800, 20))
        // 8 beats per second over ~19.5 s
        assertEquals(156.0, ticks.size.toDouble(), 3.0)
    }

    @Test fun zeroRateWhenNominal() {
        val k = KpiCalculator.compute(detect(synth(28800, 20)), GrapherConfig(bph = 28800), null, 0.0)
        assertEquals(0.0, k.rateSPerDay!!, 1.0)
        assertEquals(28800, k.nominalBph)
    }

    @Test fun slowWatchGivesNegativeRate() {
        val k = KpiCalculator.compute(detect(synth(28800, 20, periodScale = 1.001)), GrapherConfig(), null, 0.0)
        assertEquals(28800, k.nominalBph)
        assertEquals(-86.3, k.rateSPerDay!!, 1.5)
    }

    @Test fun beatError() {
        val k = KpiCalculator.compute(detect(synth(21600, 20, oddShiftMs = 1.5)), GrapherConfig(), null, 0.0)
        // odd beats shifted by 1.5 ms -> offset between even and odd of 1.5 ms
        assertEquals(1.5, k.beatErrorMs!!, 0.2)
    }

    @Test fun amplitudeFromLockDrop() {
        val k = KpiCalculator.compute(detect(synth(28800, 20, ldMs = 7.0)), GrapherConfig(liftAngleDeg = 52.0), null, 0.0)
        assertNotNull(k.amplitudeDeg)
        // 52 / (2 sin(pi * 0.007 / 0.25)) = ~296 deg
        assertEquals(296.0, k.amplitudeDeg!!, 10.0)
    }
}
