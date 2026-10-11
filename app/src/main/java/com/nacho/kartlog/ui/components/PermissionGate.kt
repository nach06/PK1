package com.nacho.kartlog.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.theme.kartColors

/** Permisos del cronometraje por GPS: la ubicación es obligatoria; las notificaciones (Android 13+), recomendables. */
object GpsPermissions {
    val required = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val optional: Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    fun hasRequired(context: Context) = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Pantalla de explicación + petición de permisos antes de enseñar [content]. Si el usuario deniega (y Android ya no
 * vuelve a preguntar), ofrece abrir los ajustes de la app.
 */
@Composable
fun GpsPermissionGate(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(GpsPermissions.hasRequired(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = GpsPermissions.hasRequired(context)
        denied = !granted
    }
    if (granted) {
        content()
        return
    }
    val canAskAgain = !denied || context.findActivity()?.let {
        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
    } == true
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).testTag("permission_gate"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.kartColors.textMuted, textAlign = TextAlign.Center)
        if (denied) {
            Text(
                stringResource(R.string.permission_denied),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.kartColors.warning,
                textAlign = TextAlign.Center,
            )
        }
        if (canAskAgain) {
            PrimaryButton(
                text = stringResource(R.string.permission_grant),
                onClick = { launcher.launch(GpsPermissions.required + GpsPermissions.optional) },
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.permission_open_settings),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
