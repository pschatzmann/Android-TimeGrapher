package com.pschatzmann.timegrapher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pschatzmann.timegrapher.TimeGrapherEngine
import com.pschatzmann.timegrapher.VolumeTrace
import kotlin.math.log10

private const val MIN_DB = -80f

private fun db(v: Float): Float = if (v <= 0f) MIN_DB else (20 * log10(v)).coerceIn(MIN_DB, 0f)

/** Rolling level trace in dB (0 dB = full scale) with the detection threshold as a dashed line. */
@Composable
fun VolumeChart(trace: VolumeTrace, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outline
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val left = 34.dp.toPx()
        val w = size.width - left
        fun y(d: Float) = size.height * (1f - (d - MIN_DB) / -MIN_DB)
        for (d in intArrayOf(0, -20, -40, -60)) {
            drawLine(grid, Offset(left, y(d.toFloat())), Offset(size.width, y(d.toFloat())))
            drawText(measurer, "$d", Offset(0f, y(d.toFloat()) - 6.dp.toPx()), TextStyle(color = label, fontSize = 9.sp))
        }
        val n = trace.levels.size
        if (n < 2) return@Canvas
        val step = w / (VOLUME_VISIBLE - 1)
        val x0 = size.width - (n - 1) * step
        val line = Path()
        trace.levels.forEachIndexed { i, v ->
            val p = Offset(x0 + i * step, y(db(v)))
            if (i == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
        }
        drawPath(line, Cyan, style = Stroke(1.5.dp.toPx()))
        val ty = y(db(trace.threshold))
        drawLine(
            Amber, Offset(left, ty), Offset(size.width, ty), 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

private const val VOLUME_VISIBLE = TimeGrapherEngine.VOLUME_POINTS
