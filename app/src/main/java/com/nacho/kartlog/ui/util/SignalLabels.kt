package com.nacho.kartlog.ui.util

import androidx.annotation.StringRes
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.gps.SignalQuality

@StringRes
fun SignalQuality.Score.labelRes(): Int = when (this) {
    SignalQuality.Score.GOOD -> R.string.signal_good
    SignalQuality.Score.FAIR -> R.string.signal_fair
    SignalQuality.Score.POOR -> R.string.signal_poor
}
