package com.nacho.kartlog.ui.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.nacho.kartlog.MainActivity

/** Reinicia la app desde cero (nuevo proceso): necesario tras restaurar una copia, porque la BD se sustituye en disco. */
fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Process.killProcess(Process.myPid())
}
