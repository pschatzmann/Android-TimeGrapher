package com.pschatzmann.timegrapher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pschatzmann.timegrapher.StandardBph
import com.pschatzmann.timegrapher.TimeGrapherEngine

@Composable
fun SettingsScreen(vm: TimeGrapherEngine) {
    val c by vm.config.collectAsStateWithLifecycle()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Settings", "Changes to the filter apply on the next start")
        AppCard {
            Text("Beat rate (bph)")
            val options = listOf(0) + StandardBph
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.chunked(4).forEach { rowOpts ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowOpts.forEach { b ->
                            FilterChip(
                                selected = c.bph == b,
                                onClick = { vm.updateConfig { it.copy(bph = b) } },
                                label = { Text(if (b == 0) "Auto" else b.toString()) },
                            )
                        }
                    }
                }
            }
        }
        AppCard {
            Labeled("Lift angle", "%.0f deg".format(c.liftAngleDeg))
            Slider(c.liftAngleDeg.toFloat(), { v -> vm.updateConfig { it.copy(liftAngleDeg = v.toDouble().let { x -> Math.round(x).toDouble() }) } }, valueRange = 30f..60f)
            Labeled("Analysis window", "${c.windowSec} s")
            Slider(c.windowSec.toFloat(), { v -> vm.updateConfig { it.copy(windowSec = v.toInt()) } }, valueRange = 5f..30f)
            Labeled("Sensitivity (threshold x noise)", "%.1f".format(c.sensitivity))
            Slider(c.sensitivity.toFloat(), { v -> vm.updateConfig { it.copy(sensitivity = v.toDouble()) } }, valueRange = 2f..12f)
            Labeled("Filter low cut", "${c.lowHz} Hz")
            Slider(c.lowHz.toFloat(), { v -> vm.updateConfig { it.copy(lowHz = (v / 100).toInt() * 100) } }, valueRange = 500f..6000f)
            Labeled("Filter high cut", "${c.highHz} Hz")
            Slider(c.highHz.toFloat(), { v -> vm.updateConfig { it.copy(highHz = (v / 100).toInt() * 100) } }, valueRange = 4000f..16000f)
        }
        AppCard {
            Labeled("Calibration offset", "%.0f s/day".format(c.calibrationSPerDay))
            Slider(c.calibrationSPerDay.toFloat(), { v -> vm.updateConfig { it.copy(calibrationSPerDay = Math.round(v).toDouble()) } }, valueRange = -60f..60f)
            ChartNote("Added to the measured rate to compensate the microphone clock of this phone. Calibrate against a reference watch.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Keep screen on")
                Switch(c.keepScreenOn, { v -> vm.updateConfig { it.copy(keepScreenOn = v) } })
            }
        }
    }
}

@Composable
private fun Labeled(label: String, value: String) = KeyValue(label, value)
