package com.nacho.kartlog.data.ocr

import android.content.Context
import android.net.Uri
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
}
