package com.nacho.kartlog.domain.importing

import kotlin.math.sqrt

/** Imagen en escala de grises (0..255), fila a fila. Dominio puro: la conversión desde `Bitmap` vive en la capa de datos. */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "tamaño inconsistente" }
    }

    operator fun get(x: Int, y: Int) = pixels[y * width + x]
}

enum class PhotoProblem { BLURRY, TOO_DARK, GLARE }

data class PhotoQualityReport(
    /** Varianza del Laplaciano dividida por la varianza de la luminosidad (independiente del contraste): baja = sin enfoque o movida. */
    val sharpness: Double,
    /** Luminosidad media 0..255. */
    val meanBrightness: Double,
    /** Fracción de píxeles quemados (≥ 250): reflejos del papel o del flash. */
    val glareFraction: Double,
    val problems: Set<PhotoProblem>,
) {
    val ok: Boolean get() = problems.isEmpty()
}

/**
 * Comprobaciones previas al OCR: foto movida (varianza del Laplaciano), demasiado oscura (luminosidad media) o con
 * reflejos (fracción de píxeles saturados). Umbrales conservadores: avisan, no bloquean; hay que afinarlos con fotos reales.
 */
object PhotoQuality {
    const val MIN_SHARPNESS = 0.5
    const val MIN_BRIGHTNESS = 70.0
    const val MAX_GLARE_FRACTION = 0.12

    fun analyze(image: GrayImage): PhotoQualityReport {
        var sum = 0L
        var sumSq = 0.0
        var burned = 0
        for (p in image.pixels) {
            sum += p
            sumSq += p.toDouble() * p
            if (p >= 250) burned++
        }
        val mean = sum.toDouble() / image.pixels.size
        val intensityVariance = (sumSq / image.pixels.size - mean * mean).coerceAtLeast(1.0)
        val sharpness = laplacianVariance(image) / intensityVariance
        val glare = burned.toDouble() / image.pixels.size
        val problems = buildSet {
            if (sharpness < MIN_SHARPNESS) add(PhotoProblem.BLURRY)
            if (mean < MIN_BRIGHTNESS) add(PhotoProblem.TOO_DARK)
            if (glare > MAX_GLARE_FRACTION) add(PhotoProblem.GLARE)
        }
        return PhotoQualityReport(sharpness, mean, glare, problems)
    }

    /** Varianza del Laplaciano 4-vecinos sobre el interior de la imagen. */
    fun laplacianVariance(image: GrayImage): Double {
        val w = image.width
        val h = image.height
        if (w < 3 || h < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val v = 4 * image[x, y] - image[x - 1, y] - image[x + 1, y] - image[x, y - 1] - image[x, y + 1]
            sum += v
            sumSq += v.toDouble() * v
            n++
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}

/** Mejora de contraste local previa al OCR: iguala la iluminación dividiendo cada píxel por la media de su entorno. */
object LocalContrast {
    /**
     * @param window lado (px) de la ventana del entorno; @param strength 0..1 mezcla entre original y corregida.
     * Con luz desigual (sombra de la mano, degradado) el fondo queda uniformemente claro y el texto, oscuro.
     */
    fun flatten(image: GrayImage, window: Int = 31, strength: Double = 1.0): GrayImage {
        val w = image.width
        val h = image.height
        val half = (window / 2).coerceAtLeast(1)
        // Imagen integral para la media del entorno en O(1) por píxel.
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += image[x, y]
                integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + row
            }
        }
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val x0 = (x - half).coerceAtLeast(0)
            val y0 = (y - half).coerceAtLeast(0)
            val x1 = (x + half + 1).coerceAtMost(w)
            val y1 = (y + half + 1).coerceAtMost(h)
            val area = (x1 - x0) * (y1 - y0)
            val total = integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] - integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
            val mean = (total.toDouble() / area).coerceAtLeast(1.0)
            val flat = (image[x, y] / mean * 235.0).coerceIn(0.0, 255.0)
            out[y * w + x] = (image[x, y] * (1 - strength) + flat * strength).toInt().coerceIn(0, 255)
        }
        return GrayImage(w, h, out)
    }
}

/** Fiabilidad de una lectura: si es demasiado pobre no se enseña la revisión, se pide repetir la foto o pegar el texto. */
object OcrReliability {
    const val MIN_BOXES = 12
    const val MIN_PARSEABLE_FRACTION = 0.5

    /** Cualquier cosa con forma de tiempo aunque el OCR la haya estropeado: `1:1?.4?7`, `1.12.46`, `l:12:467`… */
    private val looseTime = Regex("""[0-9OoIlSB]{1,2}\s?[:;.,'’]\s?[0-9OoIlSB]{2}\s?[.,:;"”]\s?[0-9OoIlSB]{1,3}""")

    data class Assessment(
        val boxCount: Int,
        val parseableCells: Int,
        val unparseableCandidates: Int,
        val reliable: Boolean,
        val reason: Reason?,
    ) {
        val parseableFraction: Double
            get() = if (parseableCells + unparseableCandidates == 0) 0.0 else parseableCells.toDouble() / (parseableCells + unparseableCandidates)
    }

    enum class Reason { TOO_FEW_BOXES, NO_LAPS, MOSTLY_UNREADABLE }

    fun assess(boxes: List<OcrBox>, doc: ParsedDocument): Assessment {
        val valid = doc.drivers.sumOf { it.laps.size }
        val invalid = doc.drivers.sumOf { it.invalidCells.size }
        // Cajas que parecen tiempos pero ni el analizador pudo leer: celdas ilegibles.
        val loose = boxes.count { looseTime.containsMatchIn(it.text) }
        val unparseable = (loose - valid - invalid).coerceAtLeast(0) + invalid
        val fraction = if (valid + unparseable == 0) 0.0 else valid.toDouble() / (valid + unparseable)
        val reason = when {
            boxes.size < MIN_BOXES -> Reason.TOO_FEW_BOXES
            valid == 0 -> Reason.NO_LAPS
            fraction < MIN_PARSEABLE_FRACTION -> Reason.MOSTLY_UNREADABLE
            else -> null
        }
        return Assessment(boxes.size, valid, unparseable, reason == null, reason)
    }
}

/** Cuadrilátero de recorte (esquinas en orden: arriba-izq., arriba-der., abajo-der., abajo-izq.) y su tamaño de salida. */
data class Quad(val x: FloatArray, val y: FloatArray) {
    init {
        require(x.size == 4 && y.size == 4)
    }

    /** Ancho de la hoja enderezada: la media de los bordes superior e inferior. */
    val outputWidth: Int get() = ((dist(0, 1) + dist(3, 2)) / 2f).toInt().coerceAtLeast(1)

    /** Alto de la hoja enderezada: la media de los bordes izquierdo y derecho. */
    val outputHeight: Int get() = ((dist(0, 3) + dist(1, 2)) / 2f).toInt().coerceAtLeast(1)

    private fun dist(a: Int, b: Int): Float = sqrt(((x[a] - x[b]) * (x[a] - x[b]) + (y[a] - y[b]) * (y[a] - y[b])).toDouble()).toFloat()

    /** Gira el recorte 90° en sentido horario: reordena las esquinas para que "arriba" pase a ser "derecha". */
    fun rotated90(): Quad = Quad(
        floatArrayOf(x[3], x[0], x[1], x[2]),
        floatArrayOf(y[3], y[0], y[1], y[2]),
    )

    companion object {
        /** El recorte inicial es la imagen entera, con un pequeño margen. */
        fun full(width: Int, height: Int, margin: Float = 0f) = Quad(
            floatArrayOf(margin, width - margin, width - margin, margin),
            floatArrayOf(margin, margin, height - margin, height - margin),
        )

        /** Ordena cuatro puntos arbitrarios como arriba-izq., arriba-der., abajo-der., abajo-izq. */
        fun ordered(px: FloatArray, py: FloatArray): Quad {
            val idx = (0 until 4).toList()
            val sum = idx.map { px[it] + py[it] }
            val diff = idx.map { px[it] - py[it] }
            val tl = sum.indexOf(sum.min())
            val br = sum.indexOf(sum.max())
            val tr = diff.indexOf(diff.max())
            val bl = diff.indexOf(diff.min())
            val order = listOf(tl, tr, br, bl)
            return Quad(FloatArray(4) { px[order[it]] }, FloatArray(4) { py[order[it]] })
        }
    }

    override fun equals(other: Any?) = other is Quad && x.contentEquals(other.x) && y.contentEquals(other.y)
    override fun hashCode() = 31 * x.contentHashCode() + y.contentHashCode()
}
