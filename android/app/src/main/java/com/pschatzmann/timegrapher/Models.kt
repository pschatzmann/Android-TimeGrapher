package com.pschatzmann.timegrapher

/** One detected beat. [timeSec] is derived from the audio sample counter, not the wall clock. */
data class Tick(
    val timeSec: Double,
    val peak: Double,
    /** Time between the two main sounds of the beat (unlock to drop) in ms, or null if not resolvable. */
    val lockDropMs: Double?,
)

data class Kpis(
    val rateSPerDay: Double? = null,
    val beatErrorMs: Double? = null,
    val amplitudeDeg: Double? = null,
    val measuredBph: Double? = null,
    val nominalBph: Int? = null,
    val tickTockRatio: Double? = null,
    val jitterMs: Double? = null,
    val snrDb: Double? = null,
    val level: Double = 0.0,
) {
    val locked: Boolean get() = rateSPerDay != null
}

/** One sample of the KPIs over time (one per second while running). */
data class KpiPoint(
    val timeSec: Double,
    val rateSPerDay: Double?,
    val beatErrorMs: Double?,
    val amplitudeDeg: Double?,
)

data class GrapherConfig(
    /** 0 = auto-detect from the tick interval. */
    val bph: Int = 0,
    val liftAngleDeg: Double = 52.0,
    val windowSec: Int = 10,
    val lowHz: Int = 2000,
    val highHz: Int = 8000,
    val sensitivity: Double = 4.0,
    val calibrationSPerDay: Double = 0.0,
    val keepScreenOn: Boolean = true,
)

/** Recent filtered signal levels (linear, 0..1) at 50 Hz and the detection threshold. */
class VolumeTrace(val levels: FloatArray, val threshold: Float)

val StandardBph = listOf(14400, 18000, 19800, 21600, 25200, 28800, 36000)
