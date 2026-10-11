package com.nacho.kartlog

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.nacho.kartlog.domain.importing.SharedContent

/** Traduce un `ACTION_SEND` recibido en el contenido que entiende «Importar tiempos». */
object SharedIntents {
    fun toContent(intent: Intent?): SharedContent? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type.orEmpty()
        return when {
            type.startsWith("image/") ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { SharedContent.Image(it.toString()) }
            type.startsWith("text/") ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { SharedContent.Text(it) }
            else -> null
        }
    }
}
