package com.nacho.kartlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.theme.KartLogTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import com.patrykandpatrick.vico.compose.cartesian.layer.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.Zoom
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
 * @param referenceLabel texto de la leyenda de la línea de referencia (no se pinta sobre el gráfico).
 * @param highlightIndex punto resaltado en verde telemetría (la mejor vuelta).
 * @param clipOutliers calcula el eje con las vueltas limpias y recorta las demás (punto hueco ámbar).
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
    /** Texto de la leyenda para el punto resaltado. */
    highlightLabel: String = stringResource(R.string.legend_best),
    xSpacing: Int = 1,
    /** `true` en la gráfica de vueltas: las salidas de boxes se recortan en el borde superior. */
    clipOutliers: Boolean = false,
) {
    require(x.size == timesMs.size) { "x e y deben tener el mismo tamaño" }
    val kart = MaterialTheme.kartColors
    val lineColor = MaterialTheme.colorScheme.onSurface
    val axisTextSize = 11.sp

    val scale = remember(timesMs, referenceMs, clipOutliers) { computeChartScale(timesMs, referenceMs, clipOutliers) }
    val model = remember(x, scale) {
        CartesianChartModel(LineCartesianLayerModel.build { series(x, scale.plottedMs) })
    }
    // El rango de Y se fija a los datos (el automático incluye siempre el cero y aplastaría la gráfica).
    val rangeProvider = remember(scale) { CartesianLayerRangeProvider.fixed(minY = scale.minY, maxY = scale.maxY) }

    val normalPoint = LineCartesianLayer.Point(
        rememberShapeComponent(fill = fill(lineColor), shape = CorneredShape.Pill),
        sizeDp = 6f,
    )
    // Punto hueco ámbar: vuelta fuera de escala, recortada en el borde superior.
    val clippedPoint = LineCartesianLayer.Point(
        rememberShapeComponent(
            fill = fill(Color.Transparent),
            shape = CorneredShape.Pill,
            strokeFill = fill(kart.warning),
            strokeThickness = 2.dp,
        ),
        sizeDp = 10f,
    )
    val bestPoint = LineCartesianLayer.Point(
        rememberShapeComponent(fill = fill(kart.telemetry), shape = CorneredShape.Pill),
        sizeDp = 14f,
    )
    val pointProvider = remember(normalPoint, bestPoint, clippedPoint, x, highlightIndex, scale) {
        object : LineCartesianLayer.PointProvider {
            override fun getPoint(
                entry: LineCartesianLayerModel.Entry,
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineCartesianLayer.Point {
                val index = x.indexOf(entry.x)
                return when {
                    index in scale.clippedIndexes -> clippedPoint
                    highlightIndex != null && index == highlightIndex -> bestPoint
                    else -> normalPoint
                }
            }

            override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = bestPoint
        }
    }

    val labelComponent = rememberAxisLabelComponent(color = kart.textMuted, textSize = axisTextSize)
    val axisLine = rememberAxisLineComponent(fill = fill(kart.cardBorder))
    val axisTick = rememberAxisTickComponent(fill = fill(kart.cardBorder))
    val guideline = rememberAxisGuidelineComponent(fill = fill(kart.cardBorder))

    val decorations = if (referenceMs != null) {
        listOf(
            // Sin etiqueta sobre el gráfico: la explicación va en la leyenda, fuera del área de dibujo.
            HorizontalLine(
                y = { referenceMs.toDouble() },
                line = rememberLineComponent(fill = fill(kart.warning), thickness = 1.5.dp),
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
        // Holgura fija en los extremos para que el primer y el último punto (y su etiqueta) no se corten.
        layerPadding = { cartesianLayerPadding(unscalableStart = 12.dp, unscalableEnd = 12.dp) },
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Sin scroll ni zoom: el contenido completo se ajusta siempre al ancho (por defecto Vico usa
        // 32 dp por punto y desplaza, lo que cortaba las últimas vueltas).
        CartesianChartHost(
            chart = chart,
            model = model,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(
                zoomEnabled = false,
                initialZoom = Zoom.Content,
                minZoom = Zoom.Content,
                maxZoom = Zoom.Content,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
        ChartLegend(
            referenceText = if (referenceMs != null) referenceLabel ?: LapTime.format(referenceMs) else null,
            highlightText = if (highlightIndex != null) highlightLabel else null,
            clippedText = if (scale.clippedIndexes.isNotEmpty()) stringResource(R.string.legend_clipped) else null,
        )
    }
}

/** Leyenda fuera del área del gráfico: línea ámbar (media), punto verde (mejor) y punto hueco (fuera de escala). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(referenceText: String?, highlightText: String?, clippedText: String?) {
    if (referenceText == null && highlightText == null && clippedText == null) return
    val kart = MaterialTheme.kartColors
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (referenceText != null) {
            LegendItem(text = referenceText) {
                Box(
                    Modifier
                        .size(width = 20.dp, height = 3.dp)
                        .background(kart.warning),
                )
            }
        }
        if (highlightText != null) {
            LegendItem(text = highlightText) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(kart.telemetry, CircleShape),
                )
            }
        }
        if (clippedText != null) {
            LegendItem(text = clippedText) {
                Box(
                    Modifier
                        .size(10.dp)
                        .border(2.dp, kart.warning, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun LegendItem(text: String, symbol: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        symbol()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.kartColors.textMuted,
            maxLines = 1,
        )
    }
}

/** 10 vueltas con una salida de boxes de 1:26.000: la escala usa solo las limpias y la salida se recorta. */
@Preview(widthDp = 393, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun LapChartPreview() {
    val laps = listOf(86_000L, 72_900, 73_200, 72_600, 74_900, 72_800, 73_050, 72_450, 72_980, 72_858)
    KartLogTheme {
        Box(Modifier.padding(16.dp)) {
            TimeLineChart(
                x = laps.indices.map { it + 1.0 },
                timesMs = laps,
                xLabel = { it.toInt().toString() },
                referenceMs = 73_100,
                referenceLabel = "Media limpia 1:13.100",
                highlightIndex = laps.indexOf(laps.min()),
                clipOutliers = true,
            )
        }
    }
}
