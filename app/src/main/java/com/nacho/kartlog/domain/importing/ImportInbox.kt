package com.nacho.kartlog.domain.importing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Contenido recibido desde otra app con «Compartir → KartLog». */
sealed interface SharedContent {
    data class Text(val text: String) : SharedContent

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

/** Reconoce el texto de una imagen. La implementación real (ML Kit) vive en `data/ocr`. */
interface ImageTextRecognizer {
    /** Devuelve el texto ya reconstruido por filas y columnas, listo para [LapTextParser]. */
    suspend fun recognize(imageUri: String): Result<String>
}
