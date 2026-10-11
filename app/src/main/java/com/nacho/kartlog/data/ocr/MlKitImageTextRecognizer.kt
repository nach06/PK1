package com.nacho.kartlog.data.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nacho.kartlog.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nacho.kartlog.domain.importing.ImageTextRecognizer
import com.nacho.kartlog.domain.importing.OcrBox
import com.nacho.kartlog.domain.importing.OcrLayout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * OCR con ML Kit (modelo latino **incluido** en la app). La imagen solo se lee para reconocerla: no se
 * copia ni se guarda. Cada palabra se convierte en un [OcrBox] y [OcrLayout] reconstruye filas y columnas.
 */
class MlKitImageTextRecognizer(private val context: Context) : ImageTextRecognizer {
    override suspend fun recognize(imageUri: String): Result<String> = runCatching {
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
            val text = OcrLayout.reconstruct(boxes)
            if (BuildConfig.DEBUG) logForTuning(boxes, text)
            check(text.isNotBlank()) { "sin texto reconocido" }
            text
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
        return OcrBox(text, rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), angle)
    }

    /** Solo debug: cajas y texto reconstruido, para ajustar `ROW_TOLERANCE` y el anclaje de columnas con datos reales. */
    private fun logForTuning(boxes: List<OcrBox>, text: String) {
        Log.d(TAG, "cajas=${boxes.size} · ROW_TOLERANCE=${OcrLayout.ROW_TOLERANCE}")
        boxes.forEach {
            Log.d(TAG, "caja '%s' x=%.0f..%.0f y=%.0f..%.0f ang=%.2f°".format(
                it.text, it.left, it.right, it.top, it.bottom, Math.toDegrees(it.baselineAngle.toDouble()),
            ))
        }
        Log.d(TAG, "texto reconstruido:\n" + text.replace("\t", "⇥"))
    }

    private companion object {
        const val TAG = "KartLogOcr"
    }
}
