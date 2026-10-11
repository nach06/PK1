package com.nacho.kartlog.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.util.LapTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Datos de la tarjeta para compartir. Solo datos propios: nunca clasificaciones ni vueltas de otros pilotos. */
data class ShareCardData(
    val circuitName: String,
    val dateText: String,
    val subtitle: String,
    val bestMs: Long?,
    val cleanAverageMs: Long?,
    val lapCount: Int,
    /** Trazado proyectado a metros (este, norte), ya submuestreado; `null` si la tanda no tiene trazado. */
    val trace: List<Pair<Float, Float>>? = null,
)

/** Dibuja la tarjeta «resumen de tanda» como imagen (1080×1350) con `Canvas`, sin pasar por la pantalla. */
object SessionShareImage {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private val Background = Color.rgb(10, 10, 12)
    private val Card = Color.rgb(24, 24, 28)
    private val Accent = Color.rgb(225, 29, 46)
    private val Green = Color.rgb(0, 230, 118)
    private val Muted = Color.rgb(160, 160, 170)

    fun render(data: ShareCardData, mono: Typeface = Typeface.MONOSPACE): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(Background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Franja de color arriba.
        paint.color = Accent
        c.drawRect(0f, 0f, WIDTH.toFloat(), 18f, paint)

        // Circuito, fecha y tipo.
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 76f
        c.drawText(fit(data.circuitName, paint, WIDTH - 120f), 60f, 140f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.color = Muted
        paint.textSize = 40f
        c.drawText(fit(data.dateText, paint, WIDTH - 120f), 60f, 205f, paint)
        c.drawText(fit(data.subtitle, paint, WIDTH - 120f), 60f, 258f, paint)

        // Mejor vuelta, grande.
        paint.color = Muted
        paint.textSize = 36f
        c.drawText("MEJOR VUELTA", 60f, 372f, paint)
        paint.color = Green
        paint.typeface = Typeface.create(mono, Typeface.BOLD)
        paint.textSize = 170f
        c.drawText(data.bestMs?.let(LapTime::format) ?: "–", 52f, 530f, paint)

        // Media limpia y nº de vueltas.
        paint.typeface = Typeface.DEFAULT
        paint.color = Muted
        paint.textSize = 36f
        c.drawText("MEDIA LIMPIA", 60f, 640f, paint)
        c.drawText("VUELTAS", 620f, 640f, paint)
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(mono, Typeface.BOLD)
        paint.textSize = 76f
        c.drawText(data.cleanAverageMs?.let(LapTime::format) ?: "–", 56f, 726f, paint)
        c.drawText(data.lapCount.toString(), 620f, 726f, paint)

        // Trazado.
        val box = RectF(60f, 790f, WIDTH - 60f, HEIGHT - 150f)
        paint.color = Card
        c.drawRoundRect(box, 36f, 36f, paint)
        data.trace?.takeIf { it.size >= 2 }?.let { drawTrace(c, it, box) }

        // Marca discreta.
        paint.color = Muted
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 40f
        c.drawText("KartLog", 60f, HEIGHT - 70f, paint)
        return bitmap
    }

    private fun drawTrace(c: Canvas, trace: List<Pair<Float, Float>>, box: RectF) {
        val minX = trace.minOf { it.first }
        val maxX = trace.maxOf { it.first }
        val minY = trace.minOf { it.second }
        val maxY = trace.maxOf { it.second }
        val pad = 56f
        val scale = min((box.width() - 2 * pad) / max(maxX - minX, 1f), (box.height() - 2 * pad) / max(maxY - minY, 1f))
        val left = box.left + (box.width() - (maxX - minX) * scale) / 2f
        val top = box.top + (box.height() - (maxY - minY) * scale) / 2f
        val path = Path()
        trace.forEachIndexed { i, (x, y) ->
            val px = left + (x - minX) * scale
            val py = top + (maxY - y) * scale // norte arriba
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Green
        }
        c.drawPath(path, paint)
    }

    /** Recorta con «…» para que el texto quepa en [maxWidth]. */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.length > 1 && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }
}

/** Escribe la imagen en `cache/exports` y abre el selector de compartir de Android. */
suspend fun shareSessionImage(context: Context, data: ShareCardData) {
    val intent = withContext(Dispatchers.Default) {
        val bitmap = SessionShareImage.render(data)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = data.circuitName.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "tanda" }
        val file = File(dir, "kartlog_resumen_$safe.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_summary_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
