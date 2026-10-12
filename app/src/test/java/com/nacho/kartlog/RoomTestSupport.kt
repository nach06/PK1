package com.nacho.kartlog

import kotlinx.coroutines.runBlocking

/**
 * Espera real (sin dormir) a que Room termine y cierra todas las bases abiertas por los contenedores del test (E3). También olvida la
 * caché estática de `FileProvider` (una por proceso): cada test de Robolectric tiene su propia carpeta de caché y, si otro test ya había
 * creado la estrategia, `getUriForFile` fallaba con «Failed to find configured root» en las pasadas completas (y no en solitario).
 */
fun awaitRoomClosed() {
    runBlocking { AppContainer.closeAllAndAwait() }
    runCatching {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (cache.get(null) as MutableMap<*, *>).clear()
    }
}
