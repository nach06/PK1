package com.nacho.kartlog.domain.importing

/** Foto cargada y normalizada (orientación EXIF aplicada, reducida) lista para recortar. */
data class LoadedPhoto(
    /** URI de la copia normalizada (fichero en la caché de la app): es la que se enseña, se recorta y, si se pide, se conserva. */
    val uri: String,
    val width: Int,
    val height: Int,
    val quality: PhotoQualityReport,
)

/** Hoja recortada, enderezada y con el contraste mejorado, lista para el OCR. */
data class PreparedPhoto(val uri: String, val width: Int, val height: Int)

/** Prepara las fotos para el OCR. La implementación real (Android, `Bitmap`) vive en `data/ocr`. */
interface PhotoProcessor {
    /** Carga la foto de [sourceUri] (cámara, galería o compartida) y analiza su calidad. */
    suspend fun load(sourceUri: String): Result<LoadedPhoto>

    /** Gira la foto 90° en sentido horario (la copia normalizada se sustituye por la girada). */
    suspend fun rotate90(photo: LoadedPhoto): Result<LoadedPhoto>

    /** Endereza el cuadrilátero [quad] (coordenadas de la foto cargada) y, si [enhance], iguala la iluminación para el OCR. */
    suspend fun prepare(photo: LoadedPhoto, quad: Quad, enhance: Boolean): Result<PreparedPhoto>
}
