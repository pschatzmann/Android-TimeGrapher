package com.pschatzmann.timegrapher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pschatzmann.timegrapher.TimeGrapherEngine
import com.pschatzmann.timegrapher.TimeGrapherService
import kotlin.math.abs

private fun fmt(v: Double?, digits: Int, unit: String = ""): String =
    if (v == null) "--" else "%.${digits}f%s".format(v, unit)

@Composable
fun LiveScreen(vm: TimeGrapherEngine, requestPermissions: () -> Unit) {
    val k by vm.kpis.collectAsStateWithLifecycle()
    val ticks by vm.ticks.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val mic by vm.micGranted.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val cfg by vm.config.collectAsStateWithLifecycle()
    val vol by vm.volume.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Live", "Hold the microphone against the movement or case")
        AppCard(accent = if (running) Emerald else null) {
            if (!mic) {
                Text("Microphone permission is needed to hear the ticks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = requestPermissions) { Text("Grant permission") }
            } else {
                Button(
                    onClick = { if (running) TimeGrapherService.stop(ctx) else TimeGrapherService.start(ctx) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (running) "Stop" else "Start") }
                LinearProgressIndicator(progress = { (level * 20).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Rose) }
            }
        }

        val rateColor = k.rateSPerDay?.let { if (abs(it) < 15) Emerald else Amber } ?: Cyan
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Rate", fmt(k.rateSPerDay, 1), Modifier.weight(1f), rateColor, "s/day")
            StatTile("Beat error", fmt(k.beatErrorMs, 2), Modifier.weight(1f), beColor(k.beatErrorMs), "ms")
            StatTile("Amplitude", fmt(k.amplitudeDeg, 0), Modifier.weight(1f), Cyan, "deg")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Beat rate", k.nominalBph?.toString() ?: "--", Modifier.weight(1f), Purple, k.measuredBph?.let { "meas %.1f".format(it) } ?: "bph")
            StatTile("Tick/tock", fmt(k.tickTockRatio, 2), Modifier.weight(1f), Purple, "ratio")
            StatTile("Jitter", fmt(k.jitterMs, 2), Modifier.weight(1f), Blue, "ms")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("SNR", fmt(k.snrDb, 0), Modifier.weight(1f), Blue, "dB")
            StatTile("Window", "${cfg.windowSec}", Modifier.weight(1f), Blue, "s")
        }

        AppCard {
            Text("Volume", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            VolumeChart(vol)
            ChartNote("Filtered level of the last 10 s in dB. Peaks above the dashed threshold are counted as ticks.")
        }

        AppCard {
            Text("Timegrapher", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            TimegrapherChart(ticks, k.nominalBph ?: cfg.bph.takeIf { it > 0 })
            ChartNote("Tick (cyan) and tock (amber), newest at the bottom. Vertical = on rate; slope = rate error; line offset = beat error.")
        }
    }
}

private fun beColor(v: Double?): Color = if (v == null) Cyan else if (v < 0.4) Emerald else if (v < 1.0) Amber else Rose
