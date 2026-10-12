package com.nacho.kartlog.domain.importing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Contenido recibido desde otra app con «Compartir → KartLog». */
sealed interface SharedContent {
    /** [meName]: solo en depuración, nombre de la columna que se da por «tuya» en esta importación (sin tocar los ajustes). */
    data class Text(val text: String, val meName: String? = null) : SharedContent

    /** [uri] de una imagen (captura o foto); se pasa por OCR. */
    data class Image(val uri: String) : SharedContent
}

/**
 * Buzón entre la `Activity` (que recibe el `ACTION_SEND`) y la pantalla «Importar tiempos»: el contenido
 * espera aquí hasta que la pantalla lo recoge ([take]).
 */
class ImportInbox {
    private val _content = MutableStateFlow<SharedContent?>(null)
    val content: StateFlow<SharedContent?> = _content.asStateFlow()

    fun put(content: SharedContent) {
        _content.value = content
    }

    /** Devuelve el contenido pendiente y lo vacía (una sola vez). */
    fun take(): SharedContent? {
        val current = _content.value
        _content.value = null
        return current
    }
}

/** Resultado del OCR de una imagen: las cajas, la tabla reconstruida (con la caja de cada celda) y el tamaño de la imagen leída. */
data class OcrScan(val boxes: List<OcrBox>, val grid: OcrGrid, val imageWidth: Int, val imageHeight: Int) {
    /** El texto que consume [LapTextParser]. */
    val text: String get() = grid.text
}

/** Reconoce el texto de una imagen. La implementación real (ML Kit) vive en `data/ocr`. */
interface ImageTextRecognizer {
    /** Lee la imagen y devuelve las cajas y la tabla reconstruida por filas y columnas, lista para [LapTextParser]. */
    suspend fun scan(imageUri: String): Result<OcrScan>
}
