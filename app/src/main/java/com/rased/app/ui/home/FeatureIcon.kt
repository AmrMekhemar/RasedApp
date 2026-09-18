package com.rased.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

internal enum class FeatureSymbol { Sorting, Unloading, Checking, Radar }

/** Decorative icons; the adjacent card text supplies the accessible label. */
@Composable
internal fun FeatureIcon(symbol: FeatureSymbol, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(tint, point(x, y), point(x2, y2), 1.7f * unit, StrokeCap.Round)
        when (symbol) {
            FeatureSymbol.Sorting -> {
                line(4f, 6f, 20f, 6f)
                line(7f, 12f, 17f, 12f)
                line(10f, 18f, 14f, 18f)
                drawCircle(tint, 2f * unit, point(8f, 6f))
                drawCircle(tint, 2f * unit, point(15f, 12f))
            }
            FeatureSymbol.Unloading -> {
                line(12f, 3f, 12f, 14f)
                line(8f, 10f, 12f, 14f)
                line(12f, 14f, 16f, 10f)
                val tray = Path().apply {
                    moveTo(4f * unit, 14f * unit)
                    lineTo(4f * unit, 20f * unit)
                    lineTo(20f * unit, 20f * unit)
                    lineTo(20f * unit, 14f * unit)
                }
                drawPath(tray, tint, style = Stroke(1.7f * unit, cap = StrokeCap.Round))
            }
            FeatureSymbol.Checking -> {
                drawCircle(tint, 8f * unit, point(12f, 12f), style = Stroke(1.7f * unit))
                line(8f, 12f, 11f, 15f)
                line(11f, 15f, 16f, 9f)
            }
            FeatureSymbol.Radar -> {
                drawCircle(tint, 9f * unit, point(12f, 12f), style = Stroke(1.3f * unit))
                drawCircle(tint.copy(alpha = 0.5f), 5f * unit, point(12f, 12f), style = Stroke(unit))
                line(12f, 12f, 18f, 6f)
                drawCircle(tint, 2f * unit, point(12f, 12f))
            }
        }
    }
}
