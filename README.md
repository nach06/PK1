# KartLog (nombre provisional)

App Android personal para registrar y analizar tandas de karting. 100 % offline.
Kotlin · Jetpack Compose · Material 3 · Room (KSP) · Navigation Compose · inyección manual (`AppContainer`).

Sprint 1: crear tanda → meter vueltas con teclado propio → histórico y detalle.

```
./gradlew assembleDebug                 # compilar
./gradlew test                          # tests unitarios (dominio)
./gradlew connectedDebugAndroidTest     # tests de DAO (emulador/dispositivo)
```
