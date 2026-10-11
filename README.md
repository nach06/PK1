# KartLog (nombre provisional)

App Android personal para registrar y analizar tandas de karting. 100 % offline.
Kotlin · Jetpack Compose · Material 3 · Room (KSP) · Navigation Compose · inyección manual (`AppContainer`).

**Sprint 1:** crear tanda (con alta rápida de circuito) → meter vueltas con teclado propio → histórico y detalle → exportar CSV.

```
./gradlew assembleDebug                 # APK en app/build/outputs/apk/debug/
./gradlew testDebugUnitTest             # dominio + flujo funcional + UI (Robolectric)
./gradlew connectedDebugAndroidTest     # tests de DAO (requiere emulador/dispositivo)
```

Notas de entorno:
- Necesita Android SDK (platform 36) y JDK 17+; `local.properties` con `sdk.dir` (no se versiona).
- Robolectric descarga su jar de Android en tiempo de ejecución. En entornos sin acceso a Maven en runtime,
  descárgalo antes y exporta `ROBOLECTRIC_DEPS_DIR=<carpeta con android-all-instrumented-*.jar>`.
