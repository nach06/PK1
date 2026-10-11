# KartLog — Plan del Sprint 5 (robustez de la tanda GPS, privacidad, medición real y calidad)

> **Es un plan, no una entrega:** nada de lo que sigue está implementado. Sale de releer `SPRINT4.md` contra el código de la rama
> (`claude/kartlog-sprint-1-3hhlkb`) y de los puntos que se arrastran de los Sprints 1–3.1. Cada hallazgo indica dónde está, por qué importa y qué tarea lo cierra.

## 0. Cómo se ha hecho el análisis
- Lectura completa de `SPRINT4.md` (decisiones, riesgos, lista visual) y contraste con el código: `TimingSession`, `GateDetector`, `GpsSessionSaver`, `AppContainer`, `AndroidManifest.xml`, `build.gradle.kts`, `ImportDao`, `ImportScreen`, `MlKitImageTextRecognizer`, los tests y el informe de lint.
- Comprobado a mano en el repositorio (no son suposiciones): `android:allowBackup="true"`; `isMinifyEnabled = false` en release; no existe `.github/` (sin CI); las fotos de `cache/photos` nunca se borran; el simulador deja `container.locationSource` cambiado; la tanda GPS se guarda con `dateTime = now()` al pulsar GUARDAR; el lint tiene 36 avisos sin triar.

## 1. Hallazgos (por severidad)

### Alta — pérdida de datos, privacidad o medida incorrecta
| ID | Hallazgo | Evidencia | Se cierra en |
|---|---|---|---|
| H1 | **Una tanda GPS vive solo en memoria** hasta pulsar GUARDAR. Si Android mata el proceso (batería, memoria, fabricante agresivo, crash) se pierden todas las vueltas y el trazado. También en estado «parada» tras «Parar» desde la notificación. | `TimingSession` guarda `laps` y `fixes` en RAM; `START_NOT_STICKY` | **A1** |
| H2 | **Un cruce de meta perdido fusiona dos vueltas en silencio.** Si se descarta un cruce (precisión > 15 m, deriva lateral > ±10 m de la puerta, hueco de fixes) la vuelta siguiente mide *dos* vueltas y nada lo avisa ni lo marca. | `GateDetector`: devuelve `null` sin dejar rastro del candidato | **A2** |
| H3 | **La copia de seguridad en la nube sube lo que A5 prometía proteger:** `allowBackup="true"` incluye la BD (rivales, `importedRaw`) y `filesDir/tracks` (historial de posiciones). | `AndroidManifest.xml:15` | **B1** |
| H4 | **Se puede abandonar el Dashboard sin querer:** no hay `BackHandler`; un gesto atrás en pista cierra la pantalla (la tanda sigue en segundo plano, pero el piloto pierde el cronómetro). Y **EMPEZAR se habilita sin comprobar que haya GPS**. | `DashboardScreen`/`DashboardViewModel.start()` | **A3, A4** |
| H5 | **Fecha y tipo de la tanda GPS incorrectos:** la fecha es la hora de GUARDAR (no la de salida), el tipo siempre `FREE`, y `durationMin` queda vacío. Una tanda guardada horas después aparece en otro día/mes. | `GpsSessionSaver.save()` | **A5** |
| H6 | **El simulador contamina el GPS real:** «Simular tanda en pista» cambia `container.locationSource` y no se restaura; «Marcar meta» y «En pista» seguirían usando el óvalo falso hasta reiniciar la app. | `AppContainer`: `onSimulationReady = { locationSource = it }` | **A6** |

### Media — cumplimiento de lo pedido y riesgos de producto
| ID | Hallazgo | Evidencia | Se cierra en |
|---|---|---|---|
| H7 | **A1 no cumplió el texto del encargo** en el caso ambiguo (`1:25,415` → `1:25,475`): se pidió «propone la corrección» y no se propone ninguna. Se explicó, pero el usuario se queda sin ayuda. | `ImportValidation.findSingleDigitFix` | **B4** |
| H8 | **El OCR sigue sin validarse con una imagen real** (desde el Sprint 3). `OcrLayout` solo está probado con cajas sintéticas. | `SPRINT4.md` A2 pendiente de fotos | **P0, C0** |
| H9 | **Las fotos «temporales» no se borran** (`cache/photos/escaneo.jpg`, copias de las muestras de debug). El Sprint 3 dijo «no se guarda la foto». | `ImportScreen.kt:152`, `SampleImages` | **B2** |
| H10 | **Sin CI y sin ejecutar nunca los tests instrumentados** (`KartLogDaoTest`, `MigrationInstrumentedTest` solo compilan). Release **sin R8** (`isMinifyEnabled=false`): lo que se ha probado no es lo que se instalaría minificado. Peso del APK con ML Kit **sin medir**. | no hay `.github/`; `build.gradle.kts` | **D1–D3** |
| H11 | **Parches de test en lugar de soluciones:** `Thread.sleep(300)` en 8 clases por consultas Room huérfanas (flaky intermitente, visto 3 veces) y `LocalLiveClock` para esquivar un bucle de `delay`. | `*FlowTest.kt` tearDown, `DashboardScreen` | **D5** |
| H12 | **Decimales inconsistentes:** «±0.053 s» (tarjeta GPS, `Locale.ROOT`) frente a «±0,214» y «+0,060» (resto de la app). | `GpsAccuracyCard`, `LapTime` | **D6** |
| H13 | **A3 a 360×640 dp solo caben 6 vueltas** (se pidió ≥ 7) y la FAB desplegable añade un toque a la acción más usada (crear tanda). | `SPRINT4.md` A3/A4 | **D7** |
| H14 | **Importaciones duplicadas:** el mismo documento puede importarse dos veces en tandas distintas sin aviso; al sustituir una clasificación, `importedRaw` conserva el texto de la *primera* importación. | `ImportDao.fillImportedRaw` (solo si es NULL) | **B3** |
| H15 | **La medida nunca se ha contrastado con un GPS real:** no sabemos si `elapsedRealtimeNanos` tiene jitter respecto al tiempo GNSS, si el proveedor *fused* suaviza, ni la frecuencia real. La interpolación ignora la velocidad Doppler y el rumbo que ya trae cada fix. | `GateDetector`, `FusedLocationSource` | **C1–C5** |
| H16 | **Higiene de recursos:** sin *wake lock* parcial en el servicio; ficheros `tracks/` huérfanos tras `wipeAll`; los *backups* de tanda no se pueden sacar de la app (sin exportar trazado). | `TrackingService`, `DebugSeeder.wipeAll` | **A7, C1** |

### Baja
- Lint: `UnusedResources` (2), `PluralsCandidate` (3), `UseKtx` (3), `ObsoleteSdkInt` (3), `MonochromeLauncherIcon`, 14 `GradleDependency` y 6 `NewerVersionAvailable` (→ **D4**).
- `BUILD_TIME` en `BuildConfig` cambia en cada compilación y fuerza recompilar dependientes (→ **D4**).
- Un `ACTION_SEND` recibido con el Dashboard en marcha navega fuera de él (→ **A4**).
- La elipsis del circuito y otras medidas de texto no se pueden verificar con Robolectric «legacy» (→ **D7**: modo gráfico nativo).

## 2. Qué se necesita del responsable (bloquea parte del sprint)
| # | Entrega | Para qué |
|---|---|---|
| P0.1 | Pasar la **lista visual del Sprint 4** en el móvil real y anotar fallos | H8, H15; todo lo «no comprobado en dispositivo» |
| P0.2 | Copiar `muestra_a.png` y `muestra_b.jpg` a `app/src/debug/assets/muestras/` y enviar el **Logcat `KartLogOcr`** | calibrar `ROW_TOLERANCE`/anclaje de columnas (C0) |
| P0.3 | **Una tanda real en pista** con el Dashboard + los tiempos oficiales, y el trazado exportado (tras C1) | medir el error GPS vs oficial: decide el ESP32 |
| P0.4 | Respuesta a las **decisiones del §5** | A5, B1, D7 |

## 3. Plan por partes (un commit por tarea; `./gradlew testDebugUnitTest` al cerrar cada una)

### PARTE A — Robustez de la tanda GPS (prioridad máxima)
- **A1. Persistencia incremental de la tanda (H1).** `TrackRecorder`: añade cada fix a `tracks/draft.csv` (flush cada ≤ 5 s o 20 fixes) y guarda `draft.json` con `setup` + vueltas cerradas + hora de inicio cada vez que se cierra una vuelta. Al arrancar la app, si existe un borrador: diálogo «Hay una tanda GPS sin guardar (N vueltas · fecha)» → *Recuperar* (abre el resumen para guardar) / *Descartar*. El borrador se borra al guardar o descartar.
  *Aceptación:* matar el proceso durante una tanda y reabrir ofrece recuperar todas las vueltas cerradas y el trazado hasta el último flush. *Tests:* recrear `AppContainer` sobre el mismo directorio (simula la muerte), borrador corrupto/truncado no rompe, guardar elimina el borrador.
- **A2. Calidad de vuelta y cruces perdidos (H2).** El detector registra cada *candidato* descartado (motivo: precisión, fuera de puerta, rebote, hueco) y marca cada vuelta con `LapQuality`: `OK`, `UNRELIABLE_GAP`, `SUSPECT_LONG` (> 1,6 × mediana de las vueltas limpias y con candidato descartado dentro) o `AFTER_DROPPED_CANDIDATE`. Para aceptar derivas: tolerancia lateral = `ancho/2 + precisión del fix` y ancho por defecto configurable por circuito (20 m → propuesta 30 m). Dashboard y Detalle muestran ⚠ en esas vueltas; el Detalle ofrece **«Dividir vuelta»** (en el instante del candidato descartado, si se conoce) y «Eliminar». La calidad se guarda en `Lap` solo si se decide en §5 (si no, se deriva del trazado al abrir).
  *Tests:* simular un cruce con deriva lateral de 12 m y otro con precisión 18 m en medio de una tanda → la vuelta fusionada sale marcada y «Dividir» devuelve las dos vueltas con error ≤ 50 ms; sin candidatos descartados no aparece ningún ⚠.
- **A3. Comprobación de GPS antes de empezar (H4).** La preparación escucha el GPS (solo con la pantalla visible) y muestra «GPS ±x m · y Hz»; **EMPEZAR** exige un fix reciente (< 3 s) con precisión ≤ 10 m, con opción «Empezar igualmente» tras confirmar. *Tests:* sin fix → deshabilitado; precisión 12 m → aviso; con fix bueno → habilitado.
- **A4. Protección contra gestos accidentales (H4).** `BackHandler` en el Dashboard en marcha: se ignora (con aviso discreto «Mantén pulsado para terminar»); un `ACTION_SEND` durante una tanda no navega (queda encolado hasta terminar). *Test por NavHost:* atrás en marcha no cambia de pantalla.
- **A5. Datos correctos de la tanda GPS (H5).** `dateTime` = hora del **primer cruce** (o de EMPEZAR si no hubo), `durationMin` calculada, tipo elegible en la preparación (por defecto Libre) con las mismas fichas que «Nueva tanda». *Test:* guardar 3 h después conserva la fecha de salida.
- **A6. Simulador aislado (H6).** El origen simulado deja de reemplazar al real: se pasa como argumento de la sesión (`TimingSession.start(source)`) y la UI de depuración lo selecciona solo para esa sesión; al terminar/descartar se restaura el `FusedLocationSource`. *Test:* tras simular, «Marcar meta» usa el origen real.
- **A7. Higiene (H16).** `PARTIAL_WAKE_LOCK` mientras el servicio cronometra (con *timeout* de seguridad); barrido de `tracks/` al arrancar (ficheros sin sesión ni borrador → borrar); `wipeAll` también vacía `tracks/`. *Tests:* barrido conserva los ficheros con sesión y borra los huérfanos.

### PARTE B — Privacidad y datos importados
- **B1. Copias de seguridad (H3).** Según la decisión §5-1: `allowBackup="false"` (recomendado, app personal sin nube) o `dataExtractionRules`/`fullBackupContent` que excluyan BD, `tracks/` y preferencias. Añadir **«Exportar copia completa»** (ZIP con BD + trazados, vía *Share*) y **«Importar copia»** para no perder datos al cambiar de móvil. *Tests:* el manifiesto declara la política elegida (test de lectura del XML); round-trip exportar→importar sobre una BD con rivales y trazados.
- **B2. Fotos temporales (H9).** Borrar `cache/photos/*` al terminar el OCR (éxito, fallo o cancelación) y purgar al arrancar. *Test:* tras reconocer, el fichero ya no existe.
- **B3. Duplicados y texto original (H14).** Huella (SHA-256 del texto normalizado) en `sessions.importedRawHash` (**Room v4** con migración explícita y test) → aviso «Este documento ya está importado en la tanda X»; al sustituir una clasificación se actualiza también `importedRaw`/huella. *Tests:* migración 3→4 y cadena 1→4; importar dos veces avisa; sustituir actualiza el texto.
- **B4. Candidatos de corrección (H7).** Cuando la validación no puede elegir una celda, se muestran las candidatas ordenadas (V1/V2/V3/V8 con el valor que propondría cada una) como fichas; **nunca se aplica nada solo**. *Tests:* el caso `1:25,475` lista V8 entre las candidatas y aplicar V8 restaura la media; las otras no rompen la validación.

### PARTE C — Medir de verdad (decide el ESP32)
- **C0. Calibrar el OCR con imágenes reales (H8)** *(depende de P0.2).* Ajustar `ROW_TOLERANCE`/anclaje con los logs; añadir como fixtures los textos reconstruidos reales y un test «foto → texto → pilotos» que compare con los valores de control de A y B. Si el resultado es insuficiente, plan B documentado (recorte manual de la imagen / pegar texto).
- **C1. Exportar el trazado.** En el Detalle de una tanda GPS: «Exportar trazado (GPX/CSV)» por *Share*; y «Reproducir trazado» en depuración (`ReplayLocationSource.fromText`).
- **C2. Registro de temporización.** Columna extra `utcMs` (`Location.time`) en el CSV de trazados (formato v2; el lector acepta v1) para medir el jitter entre `elapsedRealtimeNanos` y el tiempo GNSS y la frecuencia real. Informe en depuración: Hz medio, desviación del intervalo, precisión media.
- **C3. Fuente GPS «puro».** `PlatformGpsLocationSource` (`LocationManager.GPS_PROVIDER`) como alternativa a *fused*, seleccionable en Ajustes → Depuración, para comparar con los mismos tiempos oficiales (B6).
- **C4. Banco de pruebas offline del detector.** Con trazados grabados (C1) se comparan variantes: interpolación lineal actual vs. proyección con velocidad Doppler/rumbo, ancho de puerta, umbral de precisión. Se adopta una variante **solo si mejora el error medio frente al oficial** en los trazados reales.
- **C5. Alineación automática en «Precisión vs oficial».** Elegir por defecto el desplazamiento que minimiza el error medio absoluto (el usuario puede cambiarlo).

### PARTE D — Calidad, CI y deuda de tests
- **D1. CI** (`.github/workflows/ci.yml`): `testDebugUnitTest lintDebug assembleRelease` en cada push, con caché de Gradle; artefactos: informes de test/lint y APK debug.
- **D2. Tests instrumentados en CI** con emulador (API 35): `connectedDebugAndroidTest` (`KartLogDaoTest`, migraciones 1→2→3→4) y un *smoke test* de arranque. Es la primera vez que se ejecutarán.
- **D3. Release real.** Activar R8 (`isMinifyEnabled=true`, `shrinkResources`) con reglas para Room/ML Kit si hacen falta; *smoke test* del APK release en emulador; **informe de tamaño** del APK (con y sin modelo ML Kit incluido) para decidir si se pasa al modelo no empaquetado.
- **D4. Lint y dependencias.** Cerrar `UnusedResources`, `PluralsCandidate`, `UseKtx`, `ObsoleteSdkInt`, icono monocromo; subir dependencias con test tras cada grupo; `lint-baseline.xml` solo para lo que se decida diferir. `BUILD_TIME` solo en builds de depuración locales (en CI/Release, el hash).
- **D5. Higiene de tests (H11).** `AppContainer.close()` (cierra BD y cancela `appScope`) llamado desde los `tearDown`; eliminar los `Thread.sleep(300)`; sustituir `LocalLiveClock` por un `Ticker` inyectable. *Criterio:* 5 ejecuciones seguidas de la suite completa sin un solo fallo intermitente.
- **D6. Formato de números (H12).** Un único `NumberFormats` (coma decimal, `Locale("es","ES")` fijo para la UI; los tiempos de vuelta siguen `m:ss.mmm`) usado por consistencia, GPS, deltas, error medio; test que recorre las cadenas de UI y falla si aparece `\d\.\d{3} s`.
- **D7. UI de Detalle y pruebas de texto (H13).** Cabecera compacta por debajo de 700 dp de alto para llegar a **≥ 7 vueltas a 360×640 dp**; revisar la FAB según §5-3; pasar los tests de texto a `@GraphicsMode(NATIVE)` de Robolectric (4.14.1 lo soporta) para poder verificar **la elipsis del circuito** y el recorte de MEDIA LIMPIA con métricas reales.

### PARTE E — Documentación y cierre
- **E1.** Consolidar `SPRINT1..5.md` en `docs/` con un índice, y un `README` con: cómo comprobar la build (hash), comandos, estructura de capas y los tres modos de prueba del GPS (simulador, GPX en emulador, trazado grabado).
- **E2.** `SPRINT5.md` (este fichero) se convierte en el informe: hallazgos cerrados, decisiones, riesgos residuales, lista visual actualizada.

## 4. Orden de ejecución y dependencias
1. **Semana 1 (sin dependencias del responsable):** A1 → A2 → A3 → A4 → A5 → A6 → A7 → B1 → B2 (todo lo que protege datos y evita fallos en pista).
2. **Semana 2:** B3 → B4 → D5 → D6 → D1/D2 (suelo de calidad antes de seguir añadiendo funciones).
3. **Con datos del responsable (P0.x):** C0, C1, C2 → C3 → C4 → C5; D3 y D7 en paralelo.
4. Cierre: D4, E1, E2 y la revisión visual.

## 5. Decisiones que necesito del responsable (con mi propuesta)
1. **Copias de seguridad (H3/B1):** ¿`allowBackup=false` + exportar/importar copia manual *(recomendado)*, o dejar la nube pero excluyendo rivales y trazados?
2. **Calidad de vuelta (A2):** ¿se persiste en BD (nueva columna `lapQuality`, Room v4 junto a la huella de B3) o se deriva del trazado al abrir? *Propongo persistir*, porque así sobrevive a borrar el fichero del trazado.
3. **FAB de Tandas (D7):** ¿mantener el desplegable (un toque más para «Nueva tanda») o toque = «Nueva tanda» y pulsación larga / flecha = resto? *Propongo toque corto = Nueva tanda y un segundo botón pequeño para el menú.*
4. **Ancho de puerta por defecto (A2):** ¿20 m (actual) o 30 m? *Propongo 30 m con tolerancia por precisión*, a validar con la pista real.
5. **Tipo de sesión GPS (A5):** ¿siempre elegible en la preparación, o recordar el último? *Propongo recordar el último.*

## 6. Riesgos del propio plan
- Varias tareas (A3, A7, C2, C3) solo se validan del todo en el móvil; dejaré tests de lógica y una lista visual, pero la confirmación depende de P0.1/P0.3.
- Room v4 (B3 + decisión 2) obliga a una migración más: se cubre con `MigrationTest` encadenado 1→4 y el instrumentado en CI.
- Activar R8 (D3) puede romper reflexión en ML Kit/Room: por eso va con *smoke test* de release antes de aceptarlo.
- El trabajo de C4 solo vale si hay trazados reales; sin ellos se queda en infraestructura de comparación.

## 7. Criterios de cierre del sprint
- Ninguna de las pérdidas de datos H1–H3 reproducible (tests de recuperación, de privacidad y de borrado).
- Suite completa verde **5 veces seguidas** y CI en verde en la rama.
- `connectedDebugAndroidTest` ejecutado al menos una vez con éxito en emulador.
- Una tanda real medida contra los tiempos oficiales y un veredicto escrito sobre el ESP32 (error medio y máximo, con trazado adjunto).
