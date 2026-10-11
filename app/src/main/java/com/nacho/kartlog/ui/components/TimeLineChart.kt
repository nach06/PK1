package com.nacho.kartlog.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.theme.kartColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

/**
 * Gráfica de líneas de tiempos (Vico). Eje Y en `m:ss.mmm`; un tiempo mayor queda más arriba.
 *
 * @param x posición de cada punto (nº de vuelta, índice de tanda…).
 * @param timesMs tiempo de cada punto en milisegundos.
 * @param xLabel texto del eje X para un valor de [x].
 * @param referenceMs línea horizontal de referencia (p. ej. la media limpia), opcional.
 * @param referenceLabel etiqueta de la línea de referencia.
 * @param highlightIndex punto resaltado en verde telemetría (la mejor vuelta).
 */
@Composable
fun TimeLineChart(
    x: List<Double>,
    timesMs: List<Long>,
    xLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    referenceMs: Long? = null,
    referenceLabel: String? = null,
    highlightIndex: Int? = null,
    xSpacing: Int = 1,
) {
    require(x.size == timesMs.size) { "x e y deben tener el mismo tamaño" }
    val kart = MaterialTheme.kartColors
    val lineColor = MaterialTheme.colorScheme.onSurface
    val axisTextSize = 11.sp

    val model = remember(x, timesMs) {
        CartesianChartModel(LineCartesianLayerModel.build { series(x, timesMs) })
    }
    // El rango de Y se ajusta a los datos (el automático incluye siempre el cero y aplastaría la gráfica).
    val rangeProvider = remember(timesMs, referenceMs) {
        val all = timesMs + listOfNotNull(referenceMs)
        val min = all.min().toDouble()
        val max = all.max().toDouble()
        val pad = ((max - min) * 0.12).coerceAtLeast(300.0)
        CartesianLayerRangeProvider.fixed(minY = min - pad, maxY = max + pad)
    }

    val normalPoint = LineCartesianLayer.Point(
        rememberShapeComponent(fill = fill(lineColor), shape = CorneredShape.Pill),
        sizeDp = 6f,
    )
    val bestPoint = LineCartesianLayer.Point(
        rememberShapeComponent(fill = fill(kart.telemetry), shape = CorneredShape.Pill),
        sizeDp = 14f,
    )
    val pointProvider = remember(normalPoint, bestPoint, x, highlightIndex) {
        object : LineCartesianLayer.PointProvider {
            override fun getPoint(
                entry: LineCartesianLayerModel.Entry,
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineCartesianLayer.Point =
                if (highlightIndex != null && entry.x == x.getOrNull(highlightIndex)) bestPoint else normalPoint

            override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = bestPoint
        }
    }

    val labelComponent = rememberAxisLabelComponent(color = kart.textMuted, textSize = axisTextSize)
    val axisLine = rememberAxisLineComponent(fill = fill(kart.cardBorder))
    val axisTick = rememberAxisTickComponent(fill = fill(kart.cardBorder))
    val guideline = rememberAxisGuidelineComponent(fill = fill(kart.cardBorder))

    val decorations = if (referenceMs != null) {
        listOf(
            HorizontalLine(
                y = { referenceMs.toDouble() },
                line = rememberLineComponent(fill = fill(kart.warning), thickness = 1.5.dp),
                labelComponent = rememberTextComponent(color = kart.warning, textSize = axisTextSize),
                label = { referenceLabel ?: LapTime.format(referenceMs) },
                verticalLabelPosition = com.patrykandpatrick.vico.core.common.Position.Vertical.Top,
            ),
        )
    } else {
        emptyList()
    }

    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                    pointProvider = pointProvider,
                ),
            ),
            rangeProvider = rangeProvider,
        ),
        startAxis = VerticalAxis.rememberStart(
            label = labelComponent,
            line = axisLine,
            tick = axisTick,
            guideline = guideline,
            valueFormatter = CartesianValueFormatter { _, value, _ -> LapTime.format(value.toLong()) },
            itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 4 }) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            label = labelComponent,
            line = axisLine,
            tick = axisTick,
            guideline = null,
            valueFormatter = CartesianValueFormatter { _, value, _ -> xLabel(value) },
            itemPlacer = remember(xSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { xSpacing }) },
        ),
        decorations = decorations,
    )

    CartesianChartHost(
        chart = chart,
        model = model,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    )
}
