package com.nacho.kartlog

import android.app.Application
import com.nacho.kartlog.data.ocr.MlKitImageTextRecognizer

class KartLogApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also { it.imageTextRecognizer = MlKitImageTextRecognizer(this) }
    }
}
