package com.nacho.kartlog.ui.importing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.importing.PhotoProblem
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.theme.kartColors

/** Recorte y enderezado: esquinas arrastrables, giro de 90°, mejora de contraste y ANALIZAR. */
@Composable
internal fun CropContent(
    state: ImportUiState,
    onBack: () -> Unit,
    onQuad: (com.nacho.kartlog.domain.importing.Quad) -> Unit,
    onRotate: () -> Unit,
    onEnhance: (Boolean) -> Unit,
    onAnalyze: () -> Unit,
    onRetake: () -> Unit,
    onPasteInstead: () -> Unit,
) {
    val photo = state.photo
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.import_crop_title), onBack = onBack) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.error == ImportError.UNRELIABLE) {
                    KartCard(Modifier.fillMaxWidth().testTag("unreliable_card")) {
                        Text(stringResource(R.string.import_error_unreliable), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.kartColors.warning)
                        Text(stringResource(R.string.import_error_unreliable_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.textMuted)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_crop_retake), onRetake, modifier = Modifier.testTag("unreliable_retake")) }
                            Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_option_paste), onPasteInstead, modifier = Modifier.testTag("unreliable_paste")) }
                        }
                    }
                } else {
                    state.error?.let { ErrorText(it) }
                    Text(stringResource(R.string.import_crop_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.textMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_crop_rotate), onRotate, enabled = photo != null && !state.isBusy, modifier = Modifier.testTag("crop_rotate")) }
                    Text(stringResource(R.string.import_crop_enhance), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = photo?.enhance ?: true, onCheckedChange = onEnhance, modifier = Modifier.testTag("crop_enhance"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.import_crop_retake), onRetake, modifier = Modifier.testTag("crop_retake")) }
                    Box(Modifier.weight(1f)) {
                        PrimaryButton(stringResource(R.string.import_analyze), onAnalyze, enabled = photo != null && !state.isBusy, modifier = Modifier.testTag("crop_analyze"))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            if (photo != null) {
                CropEditor(
                    imageUri = photo.photo.uri,
                    imageWidth = photo.photo.width,
                    imageHeight = photo.photo.height,
                    quad = photo.quad,
                    onQuadChange = onQuad,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
            if (state.isBusy) {
                Column(
                    Modifier.fillMaxSize().background(Color(0x99000000)).testTag("crop_busy"),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.import_crop_reading), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

/** Aviso previo al OCR: la foto parece movida, oscura o con reflejos. */
@Composable
internal fun QualityPrompt(state: ImportUiState, onRetake: () -> Unit, onAnyway: () -> Unit) {
    val problems = state.photo?.photo?.quality?.problems.orEmpty()
    AlertDialog(
        onDismissRequest = onRetake,
        containerColor = MaterialTheme.kartColors.card,
        title = { Text(stringResource(R.string.import_quality_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("quality_title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (PhotoProblem.BLURRY in problems) Text(stringResource(R.string.import_quality_blurry), style = MaterialTheme.typography.bodyLarge)
                if (PhotoProblem.TOO_DARK in problems) Text(stringResource(R.string.import_quality_dark), style = MaterialTheme.typography.bodyLarge)
                if (PhotoProblem.GLARE in problems) Text(stringResource(R.string.import_quality_glare), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            TextButton(onClick = onRetake, modifier = Modifier.heightIn(min = 56.dp).testTag("quality_retake")) {
                Text(stringResource(R.string.import_quality_retake).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onAnyway, modifier = Modifier.heightIn(min = 56.dp).testTag("quality_anyway")) {
                Text(stringResource(R.string.import_quality_anyway).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}

/** La hoja entera (ya recortada) a pantalla completa, con la celda seleccionada resaltada. */
@Composable
internal fun ImageDialog(state: ImportUiState, onDismiss: () -> Unit) {
    val image = state.reviewImage ?: return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().padding(8.dp)) {
            SheetPreview(
                imageUri = image.uri, imageWidth = image.width, imageHeight = image.height, box = null,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            SecondaryButton(stringResource(R.string.action_close_image), onDismiss, modifier = Modifier.testTag("image_close"))
        }
    }
}
