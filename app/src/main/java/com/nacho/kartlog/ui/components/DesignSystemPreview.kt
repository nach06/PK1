package com.nacho.kartlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors

/** Preview de todos los componentes del Design System (solo para Android Studio). */
@Preview(widthDp = 400, heightDp = 1500, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun DesignSystemPreview() {
    KartLogTheme {
        var kart by remember { mutableIntStateOf(7) }
        var temp by remember { mutableStateOf<Int?>(24) }
        var type by remember { mutableStateOf("Libre") }
        var digits by remember { mutableStateOf("48532") }
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KartCard(Modifier.fillMaxWidth()) {
                StatBadge(
                    label = "Mejor",
                    value = "0:48.532",
                    valueColor = MaterialTheme.kartColors.telemetry,
                    valueStyle = KartTextStyles.bestLap,
                )
            }
            BigStepper("Kart nº", "$kart", { kart-- }, { kart++ })
            ChipSelector(listOf("Libre", "Clasificación", "Carrera"), type, { type = it }, { it })
            TempSlider("Temperatura ambiente", temp, { temp = it }, { temp = null }, { "$it °C" })
            RacingTextField(value = "", onValueChange = {}, placeholder = "Notas")
            PrimaryButton(text = "Guardar vuelta", onClick = {})
            StatBadge(label = "Entrada", value = LapTime.formatDigits(digits), valueStyle = KartTextStyles.timeEntry)
            TimeKeypad(
                onDigits = { digits = LapTime.appendDigits(digits, it) },
                onBackspace = { digits = digits.dropLast(1) },
                onClear = { digits = "" },
            )
        }
    }
}
