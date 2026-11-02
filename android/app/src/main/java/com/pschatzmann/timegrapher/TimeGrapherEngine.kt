package com.pschatzmann.timegrapher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.pschatzmann.timegrapher.audio.AudioCapture
import com.pschatzmann.timegrapher.dsp.KpiCalculator
import com.pschatzmann.timegrapher.dsp.TickDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Process-wide engine (same pattern as the Time-Services template): owns the capture loop and exposes StateFlows. */
class TimeGrapherEngine(private val context: Context) {
    private val store = ConfigStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _config = MutableStateFlow(store.load())
    val config: StateFlow<GrapherConfig> = _config.asStateFlow()
    private val _dark = MutableStateFlow(store.loadDark())
    val darkTheme: StateFlow<Boolean> = _dark.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _micGranted = MutableStateFlow(hasMic())
    val micGranted: StateFlow<Boolean> = _micGranted.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _kpis = MutableStateFlow(Kpis())
    val kpis: StateFlow<Kpis> = _kpis.asStateFlow()
    private val _level = MutableStateFlow(0.0)
    val level: StateFlow<Double> = _level.asStateFlow()
    /** Recent beats for the timegrapher chart. */
    private val _ticks = MutableStateFlow<List<Tick>>(emptyList())
    val ticks: StateFlow<List<Tick>> = _ticks.asStateFlow()
    /** KPIs over time, one point per second. */
    private val _history = MutableStateFlow<List<KpiPoint>>(emptyList())
    val history: StateFlow<List<KpiPoint>> = _history.asStateFlow()

    /** Volume history for the Live chart: filtered level and threshold, newest last (about 10 s). */
    private val _volume = MutableStateFlow<VolumeTrace>(VolumeTrace(FloatArray(0), 0f))
    val volume: StateFlow<VolumeTrace> = _volume.asStateFlow()
    private val volBuf = FloatArray(VOLUME_POINTS)
    private var volCount = 0

    private val tickBuf = ArrayDeque<Tick>()
    private val histBuf = ArrayDeque<KpiPoint>()

    companion object { const val VOLUME_POINTS = 500 }

    private fun hasMic() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun refreshPermissions() { _micGranted.value = hasMic() }

    fun updateConfig(f: (GrapherConfig) -> GrapherConfig) {
        val c = f(_config.value)
        _config.value = c
        store.save(c)
    }

    fun toggleTheme() { _dark.value = !_dark.value; store.saveDark(_dark.value) }

    fun resetSession() {
        synchronized(this) { tickBuf.clear(); histBuf.clear() }
        _ticks.value = emptyList(); _history.value = emptyList(); _kpis.value = Kpis()
    }

    fun start() {
        if (job?.isActive == true || !hasMic()) return
        _error.value = null
        _running.value = true
        job = scope.launch { capture() }
    }

    fun stop() {
        job?.cancel(); job = null
        _running.value = false
        _level.value = 0.0
    }

    private suspend fun capture() {
        val cap = AudioCapture()
        if (!cap.open()) { _error.value = "Microphone unavailable"; _running.value = false; return }
        val cfg = _config.value
        resetSession()
        volCount = 0
        val det = TickDetector(cap.sampleRate, cfg.lowHz, cfg.highHz, cfg.sensitivity) { onTick(it) }
        val buf = FloatArray(cap.sampleRate / 50)
        var samples = 0L
        var lastKpiSec = 0L
        try {
            while (scope.isActive && _running.value) {
                val n = cap.read(buf)
                if (n < 0) { _error.value = "Audio read error $n"; break }
                det.process(buf, n)
                samples += n
                _level.value = det.level
                pushVolume(det.takeBlockPeak().toFloat(), det.threshold.toFloat())
                val sec = samples / cap.sampleRate
                if (sec > lastKpiSec) {
                    lastKpiSec = sec
                    publishKpis(sec.toDouble(), det)
                }
            }
        } finally {
            cap.close()
            _running.value = false
        }
    }

    private fun pushVolume(peak: Float, threshold: Float) {
        if (volCount == VOLUME_POINTS) System.arraycopy(volBuf, 1, volBuf, 0, VOLUME_POINTS - 1) else volCount++
        volBuf[volCount - 1] = peak
        _volume.value = VolumeTrace(volBuf.copyOf(volCount), threshold)
    }

    private fun onTick(t: Tick) {
        synchronized(this) {
            tickBuf.addLast(t)
            while (tickBuf.size > 800) tickBuf.removeFirst()
            _ticks.value = tickBuf.toList()
        }
    }

    private fun publishKpis(sec: Double, det: TickDetector) {
        val cfg = _config.value
        val snapshot = synchronized(this) { tickBuf.toList() }
        val last = snapshot.lastOrNull()
        val window = snapshot.filter { it.timeSec >= sec - cfg.windowSec }
        val snr = last?.let { det.snrDb(it.peak) }
        val k = KpiCalculator.compute(window, cfg, snr, det.level)
        _kpis.value = k
        synchronized(this) {
            histBuf.addLast(KpiPoint(sec, k.rateSPerDay, k.beatErrorMs, k.amplitudeDeg))
            while (histBuf.size > 3600) histBuf.removeFirst()
            _history.value = histBuf.toList()
        }
    }
}
