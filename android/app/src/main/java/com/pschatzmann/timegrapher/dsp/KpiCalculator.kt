package com.pschatzmann.timegrapher.dsp

import com.pschatzmann.timegrapher.GrapherConfig
import com.pschatzmann.timegrapher.Kpis
import com.pschatzmann.timegrapher.StandardBph
import com.pschatzmann.timegrapher.Tick
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object KpiCalculator {
    private const val MIN_TICKS = 8

    /** Snap a measured beat rate to the nearest standard rate if it is within 5 %, otherwise round it. */
    fun snapBph(measured: Double): Int {
        val best = StandardBph.minByOrNull { abs(it - measured) }!!
        return if (abs(best - measured) / best < 0.05) best else measured.roundToInt()
    }

    fun medianInterval(ticks: List<Tick>): Double? {
        if (ticks.size < 3) return null
        val d = ticks.zipWithNext { a, b -> b.timeSec - a.timeSec }.sorted()
        return d[d.size / 2]
    }

    /**
     * KPIs from the ticks inside the analysis window. Rate comes from a linear fit of the tick times against
     * the (miss-tolerant) beat index, beat error from the offset between even and odd beats.
     */
    fun compute(ticks: List<Tick>, config: GrapherConfig, snrDb: Double?, level: Double): Kpis {
        val base = Kpis(snrDb = snrDb, level = level)
        if (ticks.size < MIN_TICKS) return base
        val median = medianInterval(ticks) ?: return base
        val bph = if (config.bph > 0) config.bph else snapBph(3600.0 / median)
        val nominal = 3600.0 / bph

        // beat index relative to the first tick, tolerant against missed beats
        val t0 = ticks.first().timeSec
        val idx = IntArray(ticks.size) { ((ticks[it].timeSec - t0) / nominal).roundToInt() }
        val n = ticks.size
        val xs = DoubleArray(n) { idx[it].toDouble() }
        val ys = DoubleArray(n) { ticks[it].timeSec - t0 }
        val mx = xs.average(); val my = ys.average()
        var sxx = 0.0; var sxy = 0.0
        for (i in 0 until n) { sxx += (xs[i] - mx) * (xs[i] - mx); sxy += (xs[i] - mx) * (ys[i] - my) }
        if (sxx <= 0) return base
        val slope = sxy / sxx
        val intercept = my - slope * mx

        val measuredBph = 3600.0 / slope
        val rate = (nominal / slope - 1.0) * 86400.0 + config.calibrationSPerDay

        // residuals split by parity
        var sumE = 0.0; var nE = 0; var sumO = 0.0; var nO = 0
        val res = DoubleArray(n) { ys[it] - (intercept + slope * xs[it]) }
        for (i in 0 until n) if (idx[i] % 2 == 0) { sumE += res[i]; nE++ } else { sumO += res[i]; nO++ }
        var beatError: Double? = null
        var jitter: Double? = null
        if (nE > 0 && nO > 0) {
            val mE = sumE / nE; val mO = sumO / nO
            beatError = abs(mO - mE) * 1000.0
            var ss = 0.0
            for (i in 0 until n) { val d = res[i] - if (idx[i] % 2 == 0) mE else mO; ss += d * d }
            jitter = sqrt(ss / n) * 1000.0
        }

        var peakE = 0.0; var peakO = 0.0
        for (i in 0 until n) if (idx[i] % 2 == 0) peakE += ticks[i].peak else peakO += ticks[i].peak
        val ratio = if (nE > 0 && nO > 0 && peakO > 0) (peakE / nE) / (peakO / nO) else null

        val lds = ticks.mapNotNull { it.lockDropMs }.sorted()
        val amplitude = if (lds.size >= MIN_TICKS / 2) amplitudeDeg(lds[lds.size / 2], bph, config.liftAngleDeg) else null

        return base.copy(
            rateSPerDay = rate, beatErrorMs = beatError, amplitudeDeg = amplitude, measuredBph = measuredBph,
            nominalBph = bph, tickTockRatio = ratio, jitterMs = jitter,
        )
    }

    /** A = L / (2 sin(pi t / T)), T = full balance period = 7200 / bph seconds, t = unlock to drop time. */
    fun amplitudeDeg(lockDropMs: Double, bph: Int, liftDeg: Double): Double? {
        val period = 7200.0 / bph
        val s = sin(PI * (lockDropMs / 1000.0) / period)
        if (s <= 0.0) return null
        val a = liftDeg / (2 * s)
        return if (a in 90.0..360.0) a else null
    }
}
