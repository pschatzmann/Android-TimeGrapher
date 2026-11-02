package com.pschatzmann.timegrapher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.pschatzmann.timegrapher.audio.AudioCapture
import com.pschatzmann.timegrapher.audio.AudioPlayer
import com.pschatzmann.timegrapher.audio.DelayLine
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
    private var monitorJob: Job? = null

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
    /** Same trace while only listening (Settings tab); kept apart so the Live tab freezes when stopped. */
    private val _monitorVolume = MutableStateFlow<VolumeTrace>(VolumeTrace(FloatArray(0), 0f))
    val monitorVolume: StateFlow<VolumeTrace> = _monitorVolume.asStateFlow()
    private val monBuf = FloatArray(VOLUME_POINTS)
    private var monCount = 0

    private val tickBuf = ArrayDeque<Tick>()
    private val histBuf = ArrayDeque<KpiPoint>()

    /** Newest tick of the session (survives trimming of [tickBuf]) and the learned beat interval, for beat numbering. */
    private var lastTick: Tick? = null
    private var beatUnit: Double? = null

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
        synchronized(this) { tickBuf.clear(); histBuf.clear(); lastTick = null; beatUnit = null }
        _ticks.value = emptyList(); _history.value = emptyList(); _kpis.value = Kpis()
    }

    /** Listens without measuring (no session, ticks or KPIs) so the Settings tab can show the live level. */
    fun startMonitor() {
        if (job?.isActive == true || monitorJob?.isActive == true || !hasMic()) return
        monitorJob = scope.launch { capture(monitor = true) }
    }

    fun stopMonitor() {
        monitorJob?.cancel(); monitorJob = null
    }

    fun start() {
        stopMonitor()
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

    private suspend fun capture(monitor: Boolean = false) {
        val cap = AudioCapture()
        if (!cap.open()) { _error.value = "Microphone unavailable"; if (!monitor) _running.value = false; return }
        val cfg = _config.value
        if (!monitor) resetSession()
        if (monitor) monCount = 0 else volCount = 0
        val det = TickDetector(cap.sampleRate, cfg.lowHz, cfg.highHz, cfg.sensitivity) { if (!monitor) onTick(it) }
        det.agc = cfg.agc
        det.manualGainDb = cfg.gainDb
        val buf = FloatArray(cap.sampleRate / 50)
        val heard = FloatArray(buf.size)
        var player: AudioPlayer? = null
        val delay = DelayLine(cap.sampleRate * 2)
        var samples = 0L
        var lastKpiSec = 0L
        try {
            while (scope.isActive && (monitor || _running.value)) {
                val n = cap.read(buf)
                if (n < 0) { _error.value = "Audio read error $n"; break }
                _config.value.let { det.agc = it.agc; det.manualGainDb = it.gainDb; det.sensitivity = it.sensitivity }
                val listen = _config.value.listen
                if (listen && player == null) player = AudioPlayer(cap.sampleRate)
                else if (!listen && player != null) { player.close(); player = null }
                det.process(buf, n, if (listen) heard else null)
                if (listen) {
                    delay.process(heard, n, (_config.value.listenDelayMs * cap.sampleRate / 1000))
                    player?.write(heard, n)
                }
                samples += n
                if (monitor) {
                    monCount = push(monBuf, monCount, det.takeBlockPeak().toFloat())
                    _monitorVolume.value = VolumeTrace(monBuf.copyOf(monCount), det.threshold.toFloat())
                } else {
                    _level.value = det.level
                    volCount = push(volBuf, volCount, det.takeBlockPeak().toFloat())
                    _volume.value = VolumeTrace(volBuf.copyOf(volCount), det.threshold.toFloat())
                }
                val sec = samples / cap.sampleRate
                if (!monitor && sec > lastKpiSec) {
                    lastKpiSec = sec
                    publishKpis(sec.toDouble(), det)
                }
            }
        } finally {
            cap.close()
            player?.close()
            if (!monitor) _running.value = false
        }
    }

    /** Appends to a rolling buffer and returns the new fill count. */
    private fun push(buf: FloatArray, count: Int, v: Float): Int {
        var n = count
        if (n == VOLUME_POINTS) System.arraycopy(buf, 1, buf, 0, VOLUME_POINTS - 1) else n++
        buf[n - 1] = v
        return n
    }

    private fun onTick(raw: Tick) {
        synchronized(this) {
            val prev = tickBuf.lastOrNull() ?: lastTick
            val bph = _kpis.value.nominalBph ?: _config.value.bph.takeIf { it > 0 }
            val unit = if (bph != null) 3600.0 / bph else beatUnit
            val steps = if (prev == null || unit == null) 1L else Math.round((raw.timeSec - prev.timeSec) / unit).coerceAtLeast(1)
            if (prev != null && steps == 1L) beatUnit = raw.timeSec - prev.timeSec
            val t = raw.copy(beat = (prev?.beat ?: -1L) + steps)
            lastTick = t
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
