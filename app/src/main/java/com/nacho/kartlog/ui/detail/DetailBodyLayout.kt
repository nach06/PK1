package com.nacho.kartlog.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cuerpo del Detalle: cabecera arriba, lista de vueltas en medio y panel de teclado abajo.
 *
 * Reparto de la altura, por prioridad:
 * 1. La cabecera mantiene su altura natural.
 * 2. El panel recibe todo lo que queda **menos [minListHeight]** (así la lista nunca desaparece con el
 *    teclado abierto: el teclado encoge antes), pero nunca menos de [minPanelHeight] (por debajo las
 *    teclas serían demasiado pequeñas; entonces manda el teclado y la lista cede).
 * 3. La lista ocupa el resto y se desplaza dentro de él, sin pasar por debajo del panel.
 */
@Composable
internal fun DetailBodyLayout(
    header: @Composable () -> Unit,
    list: @Composable () -> Unit,
    panel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minListHeight: Dp = 114.dp,
    minPanelHeight: Dp = 384.dp,
) {
    Layout(
        modifier = modifier,
        content = {
            header()
            list()
            panel()
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val total = constraints.maxHeight
        val headerPlaceable = measurables[0].measure(Constraints(maxWidth = width, maxHeight = total))
        val remaining = (total - headerPlaceable.height).coerceAtLeast(0)
        val panelMax = maxOf(remaining - minListHeight.roundToPx(), minOf(remaining, minPanelHeight.roundToPx()))
        val panelPlaceable = measurables[2].measure(Constraints(maxWidth = width, maxHeight = panelMax))
        val listHeight = (remaining - panelPlaceable.height).coerceAtLeast(0)
        val listPlaceable = measurables[1].measure(Constraints.fixed(width, listHeight))
        layout(width, total) {
            headerPlaceable.placeRelative(0, 0)
            listPlaceable.placeRelative(0, headerPlaceable.height)
            panelPlaceable.placeRelative(0, headerPlaceable.height + listHeight)
        }
    }
}
