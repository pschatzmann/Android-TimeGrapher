package com.pschatzmann.timegrapher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pschatzmann.timegrapher.KpiPoint
import com.pschatzmann.timegrapher.TimeGrapherEngine

private val Windows = listOf("1 min" to 60, "5 min" to 300, "30 min" to 1800, "Session" to 3600)

@Composable
fun TrendsScreen(vm: TimeGrapherEngine) {
    val hist by vm.history.collectAsStateWithLifecycle()
    var sel by rememberSaveable { mutableIntStateOf(1) }
    val pts = hist.takeLast(Windows[sel].second)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Trends", "KPIs over time, one point per second")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Windows.forEachIndexed { i, (label, _) ->
                FilterChip(selected = sel == i, onClick = { sel = i }, label = { Text(label) })
            }
        }
        TrendCard("Rate (s/day)", pts, Cyan) { it.rateSPerDay }
        TrendCard("Beat error (ms)", pts, Amber) { it.beatErrorMs }
        TrendCard("Amplitude (deg)", pts, Emerald) { it.amplitudeDeg }
        OutlinedButton(onClick = { vm.resetSession() }) { Text("Reset session") }
    }
}

@Composable
private fun TrendCard(title: String, pts: List<KpiPoint>, color: androidx.compose.ui.graphics.Color, f: (KpiPoint) -> Double?) {
    val values = pts.map(f)
    val nums = values.filterNotNull()
    AppCard {
        Text(title, fontWeight = FontWeight.Bold)
        LineChart(listOf(ChartSeries(values, color)))
        ChartNote(
            if (nums.isEmpty()) "No data yet"
            else "min %.2f   avg %.2f   max %.2f   (%d s)".format(nums.min(), nums.average(), nums.max(), pts.size),
        )
    }
}
