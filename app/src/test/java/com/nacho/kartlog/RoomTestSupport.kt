package com.nacho.kartlog

import kotlinx.coroutines.runBlocking

/** Espera real (sin dormir) a que Room termine y cierra todas las bases abiertas por los contenedores del test (E3). */
fun awaitRoomClosed() = runBlocking { AppContainer.closeAllAndAwait() }
