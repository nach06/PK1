package com.nacho.kartlog.ui.importing

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.domain.importing.OcrBox
import com.nacho.kartlog.domain.importing.Quad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Decodifica una imagen de fichero reducida a ≤ [maxSide] px (fuera del hilo principal). `null` mientras carga o si falla. */
@Composable
internal fun rememberFileImage(uri: String?, maxSide: Int = 1600): State<ImageBitmap?> = produceState<ImageBitmap?>(initialValue = null, uri, maxSide) {
    value = if (uri == null) null else withContext(Dispatchers.IO) {
        runCatching {
            val path = Uri.parse(uri).path ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
}

/**
 * Miniatura de la hoja para comparar de un vistazo: con una celda seleccionada enseña la zona alrededor de su caja (ampliada)
 * con la caja resaltada; sin selección, la hoja entera. Las coordenadas de [box] son las de la imagen leída ([imageWidth] × [imageHeight]).
 */
@Composable
internal fun SheetPreview(
    imageUri: String,
    imageWidth: Int,
    imageHeight: Int,
    box: OcrBox?,
    modifier: Modifier = Modifier,
    highlight: Color = Color(0xFFFFB300),
) {
    val bitmap by rememberFileImage(imageUri)
    val state = box?.let { "box=${it.left.toInt()},${it.top.toInt()},${it.right.toInt()},${it.bottom.toInt()}" } ?: "whole"
    Canvas(
        modifier
            .semantics {
                contentDescription = "Foto de la hoja"
                stateDescription = state
            }
            .testTag("sheet_preview"),
    ) {
        val bmp = bitmap ?: return@Canvas
        // Factor entre la imagen leída y la decodificada (puede venir reducida).
        val k = bmp.width.toFloat() / imageWidth
        val view: androidx.compose.ui.geometry.Rect = if (box == null) {
            androidx.compose.ui.geometry.Rect(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())
        } else {
            // Ventana de ~3× el ancho de la celda (con el aspecto del lienzo) centrada en la caja y dentro de la imagen.
            val aspect = size.width / size.height
            val w = max(box.width * 3.5f, 160f).coerceAtMost(imageWidth.toFloat())
            val h = (w / aspect).coerceAtMost(imageHeight.toFloat())
            val w2 = (h * aspect).coerceAtMost(imageWidth.toFloat())
            val left = (box.centerX - w2 / 2f).coerceIn(0f, imageWidth - w2)
            val top = (box.centerY - h / 2f).coerceIn(0f, imageHeight - h)
            androidx.compose.ui.geometry.Rect(left, top, left + w2, top + h)
        }
        // Se dibuja con «encajar» cuando se ve la hoja entera (misma proporción que la imagen).
        val scale = if (box == null) min(size.width / view.width, size.height / view.height) else size.width / view.width
        val dstW = view.width * scale
        val dstH = view.height * scale
        val dstLeft = (size.width - dstW) / 2f
        val dstTop = (size.height - dstH) / 2f
        drawImage(
            bmp,
            srcOffset = IntOffset((view.left * k).toInt(), (view.top * k).toInt()),
            srcSize = IntSize((view.width * k).toInt().coerceAtLeast(1), (view.height * k).toInt().coerceAtLeast(1)),
            dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
            dstSize = IntSize(dstW.toInt().coerceAtLeast(1), dstH.toInt().coerceAtLeast(1)),
        )
        if (box != null) {
            drawRect(
                highlight,
                topLeft = Offset(dstLeft + (box.left - view.left) * scale, dstTop + (box.top - view.top) * scale),
                size = Size(box.width * scale, box.height * scale),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

/**
 * Foto con las cuatro esquinas del recorte arrastrables. [quad] está en coordenadas de la imagen ([imageWidth] × [imageHeight]);
 * se dibuja el cuadrilátero y sus asas, y al arrastrar una asa se llama a [onQuadChange] con la esquina movida (dentro de la imagen).
 */
@Composable
internal fun CropEditor(
    imageUri: String,
    imageWidth: Int,
    imageHeight: Int,
    quad: Quad,
    onQuadChange: (Quad) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by rememberFileImage(imageUri)
    val latestQuad by rememberUpdatedState(quad)
    val latestChange by rememberUpdatedState(onQuadChange)
    val density = LocalDensity.current.density
    val state = (0 until 4).joinToString(";") { "${quad.x[it].toInt()},${quad.y[it].toInt()}" }
    Box(
        modifier
            .semantics { stateDescription = "quad=$state" }
            .testTag("crop_editor"),
    ) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("crop_canvas")
                .pointerInput(imageWidth, imageHeight) {
                    // Se agarra la asa más cercana al DEDO en el momento de tocar (sin esperar al umbral de arrastre) y se arrastra.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val fit = fitOf(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                        val q0 = latestQuad
                        val nearest = (0 until 4).minBy { hypot(fit.sx(q0.x[it]) - down.position.x, fit.sy(q0.y[it]) - down.position.y) }
                        val grabbed = if (hypot(fit.sx(q0.x[nearest]) - down.position.x, fit.sy(q0.y[nearest]) - down.position.y) <= 48f * density) nearest else -1
                        if (grabbed >= 0) {
                            down.consume()
                            drag(down.id) { change ->
                                val delta = change.positionChange()
                                val q = latestQuad
                                val x = q.x.copyOf()
                                val y = q.y.copyOf()
                                x[grabbed] = (x[grabbed] + delta.x / fit.scale).coerceIn(0f, imageWidth.toFloat())
                                y[grabbed] = (y[grabbed] + delta.y / fit.scale).coerceIn(0f, imageHeight.toFloat())
                                latestChange(Quad(x, y))
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            val fit = fitOf(size.width, size.height, imageWidth, imageHeight)
            val path = Path().apply {
                moveTo(fit.sx(quad.x[0]), fit.sy(quad.y[0]))
                for (i in 1..3) lineTo(fit.sx(quad.x[i]), fit.sy(quad.y[i]))
                close()
            }
            drawPath(path, Color(0x33FFB300))
            drawPath(path, Color(0xFFFFB300), style = Stroke(width = 2.dp.toPx()))
            for (i in 0 until 4) {
                drawCircle(Color.White, radius = 12.dp.toPx(), center = Offset(fit.sx(quad.x[i]), fit.sy(quad.y[i])))
                drawCircle(Color(0xFFFFB300), radius = 9.dp.toPx(), center = Offset(fit.sx(quad.x[i]), fit.sy(quad.y[i])))
            }
        }
    }
}

/** Cómo se encaja la imagen en el lienzo (centrada, con la misma escala en ambos ejes). */
private class Fit(val scale: Float, val left: Float, val top: Float) {
    fun sx(x: Float) = left + x * scale
    fun sy(y: Float) = top + y * scale
}

private fun fitOf(canvasW: Float, canvasH: Float, imageW: Int, imageH: Int): Fit {
    val scale = min(canvasW / imageW, canvasH / imageH)
    return Fit(scale, (canvasW - imageW * scale) / 2f, (canvasH - imageH * scale) / 2f)
}
