package com.nacho.kartlog.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «Deshacer» al borrar una vuelta o una tanda (Sprint 8, D2). El borrado no se ejecuta al confirmar: el elemento se **oculta** de las
 * lecturas (los repositorios filtran con [hiddenSessionIds]/[hiddenLapIds]) y se borra de verdad pasados [windowMs] salvo que se
 * pulse «Deshacer». Solo hay un borrado deshacible a la vez: si llega otro, el anterior se confirma en ese momento. Si la app muere
 * durante la ventana el borrado no llega a ejecutarse (falla hacia conservar el dato, nunca hacia perderlo).
 */
class PendingDeletions(private val scope: CoroutineScope, private val windowMs: Long = WINDOW_MS) {
    enum class Kind { LAP, SESSION }

    /** [arg] es el nº de vuelta para el mensaje; [commit] hace el borrado real. */
    class Pending(val token: Long, val kind: Kind, val id: Long, val arg: Int, val commit: suspend () -> Unit)

    private val hidden = MutableStateFlow<List<Pending>>(emptyList())
    private val _current = MutableStateFlow<Pending?>(null)
    private var timer: Job? = null
    private var nextToken = 0L

    /** El borrado que aún se puede deshacer (con su aviso en pantalla), o `null`. */
    val current: StateFlow<Pending?> = _current.asStateFlow()

    /** Ocultos: el deshacible y los que ya se están confirmando (para que no reaparezcan antes de borrarse de verdad). */
    val hiddenItems: StateFlow<List<Pending>> = hidden.asStateFlow()

    @Synchronized
    fun schedule(kind: Kind, id: Long, arg: Int = 0, commit: suspend () -> Unit): Pending {
        _current.value?.let { finish(it) }
        val pending = Pending(++nextToken, kind, id, arg, commit)
        hidden.update { it + pending }
        _current.value = pending
        timer = scope.launch {
            delay(windowMs)
            finish(pending)
        }
        return pending
    }

    /** Cancela el borrado: el elemento vuelve a verse. `false` si ya no era el deshacible (se confirmó o llegó otro). */
    @Synchronized
    fun undo(token: Long): Boolean {
        val pending = _current.value?.takeIf { it.token == token } ?: return false
        timer?.cancel()
        _current.value = null
        hidden.update { list -> list - pending }
        return true
    }

    @Synchronized
    private fun finish(pending: Pending) {
        if (_current.value === pending) {
            _current.value = null
            timer?.cancel()
        }
        scope.launch {
            runCatching { pending.commit() }
            hidden.update { it - pending }
        }
    }

    companion object {
        const val WINDOW_MS = 8_000L
    }
}
