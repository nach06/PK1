package com.nacho.kartlog.data.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nacho.kartlog.BuildConfig
import com.nacho.kartlog.domain.importing.ImageTextRecognizer
import com.nacho.kartlog.domain.importing.OcrBox
import com.nacho.kartlog.domain.importing.OcrDump
import com.nacho.kartlog.domain.importing.OcrLayout
import com.nacho.kartlog.domain.importing.OcrScan
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * OCR con ML Kit (modelo latino **incluido** en la app). La imagen solo se lee para reconocerla. Cada palabra se convierte en un
 * [OcrBox] (con su confianza) y [OcrLayout] reconstruye filas y columnas conservando la caja de cada celda.
 *
 * En la build debug cada lectura se vuelca además a Logcat (`KartLogOcr`) y a `filesDir/ocr_dumps/` (ficheros .json), para convertir lecturas
 * reales en fixtures de los tests.
 */
class MlKitImageTextRecognizer(private val context: Context) : ImageTextRecognizer {
    override suspend fun scan(imageUri: String): Result<OcrScan> = runCatching {
        val image = InputImage.fromFilePath(context, Uri.parse(imageUri))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(Result.success(it)) }
                    .addOnFailureListener { cont.resume(Result.failure(it)) }
            }.getOrThrow()
            val boxes = result.textBlocks.flatMap { block ->
                block.lines.flatMap { line -> line.elements.mapNotNull { element -> element.toBox() } }
            }
            val grid = OcrLayout.reconstructGrid(boxes)
            val scan = OcrScan(boxes, grid, image.width, image.height)
            if (BuildConfig.DEBUG) dumpForTuning(imageUri, scan)
            check(grid.text.isNotBlank()) { "sin texto reconocido" }
            scan
        } finally {
            recognizer.close()
        }
    }

    private fun com.google.mlkit.vision.text.Text.Element.toBox(): OcrBox? {
        val rect = boundingBox ?: return null
        val corners = cornerPoints
        // Inclinación: de la esquina superior izquierda a la superior derecha de la palabra.
        val angle = if (corners != null && corners.size >= 2) {
            OcrLayout.angleOf(corners[0].x.toFloat(), corners[0].y.toFloat(), corners[1].x.toFloat(), corners[1].y.toFloat())
        } else {
            0f
        }
        return OcrBox(text, rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), angle, confidence)
    }

    /** Solo debug: cajas y texto reconstruido en Logcat y un JSON con las cajas, para ajustar el layout con datos reales. */
    private fun dumpForTuning(imageUri: String, scan: OcrScan) {
        Log.d(TAG, "cajas=${scan.boxes.size} · imagen=${scan.imageWidth}x${scan.imageHeight} · ROW_TOLERANCE=${OcrLayout.ROW_TOLERANCE}")
        scan.boxes.forEach {
            Log.d(TAG, "caja '%s' x=%.0f..%.0f y=%.0f..%.0f ang=%.2f° conf=%.2f".format(
                Locale.ROOT, it.text, it.left, it.right, it.top, it.bottom, Math.toDegrees(it.baselineAngle.toDouble()), it.confidence,
            ))
        }
        Log.d(TAG, "texto reconstruido:\n" + scan.text.replace("\t", "⇥"))
        runCatching {
            val json = OcrDump.toJson(scan)
            Log.d(TAG, "dump JSON: $json")
            val dir = File(context.filesDir, "ocr_dumps").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
            File(dir, "${stamp}_${File(Uri.parse(imageUri).path.orEmpty()).nameWithoutExtension}.json").writeText(json)
        }
    }

    private companion object {
        const val TAG = "KartLogOcr"
    }
}
