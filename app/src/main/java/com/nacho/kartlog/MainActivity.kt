package com.nacho.kartlog

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema oscuro permanente: barras del sistema siempre con iconos claros.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as KartLogApp).container
        // Al recrear la actividad (rotación) el intent original ya se entregó: no volver a importarlo.
        if (savedInstanceState == null) {
            deliverShared(intent, container)
            // Copia semanal automática (discreta: solo un aviso corto cuando se hace).
            lifecycleScope.launch {
                container.autoBackup.runIfDue()?.let {
                    Toast.makeText(this@MainActivity, getString(R.string.auto_backup_done), Toast.LENGTH_SHORT).show()
                }
            }
        }
        setContent {
            KartLogTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KartNavHost(container = container)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverShared(intent, (application as KartLogApp).container)
    }

    private fun deliverShared(intent: Intent?, container: AppContainer) {
        SharedIntents.toContent(intent)?.let(container.importInbox::put)
    }
}
