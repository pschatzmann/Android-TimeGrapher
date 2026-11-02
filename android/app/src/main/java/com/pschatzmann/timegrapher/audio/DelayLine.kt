package com.pschatzmann.timegrapher.audio

/** Ring buffer that delays a stream by a changeable number of samples (zeros are played until it has filled). */
class DelayLine(private val maxSamples: Int) {
    private val ring = FloatArray(maxSamples + 1)
    private var pos = 0

    /** Replaces the first [count] samples of [buf] with the same stream [delay] samples earlier. */
    fun process(buf: FloatArray, count: Int, delay: Int) {
        val d = delay.coerceIn(0, maxSamples)
        for (i in 0 until count) {
            ring[pos] = buf[i]
            var r = pos - d
            if (r < 0) r += ring.size
            buf[i] = ring[r]
            pos = if (pos + 1 == ring.size) 0 else pos + 1
        }
    }
}
