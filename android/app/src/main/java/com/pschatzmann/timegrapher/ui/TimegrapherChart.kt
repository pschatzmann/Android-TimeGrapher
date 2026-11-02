package com.pschatzmann.timegrapher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.pschatzmann.timegrapher.Tick
import kotlin.math.floor

/**
 * Standard timegrapher trace. One row = one balance oscillation (two beats). Beats are placed by their phase in
 * the row, so a steady watch gives two vertical dotted lines (tick, tock); a slope shows the rate error and a
 * shifted tock line shows beat error. The newest beat is at the bottom, anchored at 25 % of the width.
 */
@Composable
fun TimegrapherChart(ticks: List<Tick>, bph: Int?, modifier: Modifier = Modifier, rows: Int = 40) {
    val grid = MaterialTheme.colorScheme.outline
    val ref = bph ?: 28800
    Canvas(modifier.fillMaxWidth().height(260.dp)) {
        val rowLen = 2 * 3600.0 / ref
        val newest = ticks.lastOrNull()?.timeSec ?: return@Canvas
        drawLine(grid, Offset(size.width * 0.25f, 0f), Offset(size.width * 0.25f, size.height))
        drawLine(grid, Offset(size.width * 0.75f, 0f), Offset(size.width * 0.75f, size.height))
        for (i in 0..4) {
            val y = size.height * i / 4f
            drawLine(grid.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y))
        }
        for (t in ticks) {
            val u = (t.timeSec - newest) / rowLen
            val k = floor(u + 0.25)
            val row = -k
            if (row >= rows) continue
            val x = (u - k + 0.25).toFloat()
            val y = size.height * (1f - (row.toFloat() + 0.5f) / rows)
            drawCircle(if (x < 0.5f) Cyan else Amber, 3.dp.toPx(), Offset(x * size.width, y))
        }
    }
}
