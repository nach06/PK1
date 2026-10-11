# KartLog — Sprint 3.1 (cierre del importador) + Sprint 4 (Dashboard GPS en pista)

## PARTE A — Sprint 3.1

### A1. Fixtures reales
`app/src/test/resources/muestras/` contiene ahora las muestras A y B **reales** transcritas (todas las medias comprobadas con `floor(media real) ± 1 ms` y mejor = mínimo).
Con ellas el parser destapó tres fallos que los fixtures sintéticos ocultaban, ya corregidos:
- **Nombres partidos en la matriz** («Alvarommk l», «Cortamargari tas»): en el formato de una celda por línea «tas» (3 letras) se leía como otro piloto y descolocaba todo el bloque.
  Ahora un texto tras un nombre sin kart, cuando la cabecera trae «Kart NN», es continuación del nombre. Y el nombre de la **tabla resumen** (completo) prevalece sobre el de la matriz.
- **Tabla resumen de la muestra B** (`P · Kart · V. · Mejor`, sin columna de piloto): no se reconocía, así que no había posición ni mejor oficial. Ya se lee (solo si existe esa cabecera).
- **Celdas vacías** (J Robledo y Mar sin V6): 5 vueltas y `V.` = 5, sin desplazar columnas.

Tests nuevos: pilotos y vueltas exactas de A (14 pilotos, 2 bloques, nombres completos) y de B (posición, nº de vueltas, mejor, medias), validación ✓ en todos, la vuelta lenta de Kart 28
(`1:25,415`, > 107 % de su mejor) **se conserva y no es limpia**, y dos alteraciones:
- Kart 26 V5 `1:03,241` → `1:03,841`: rompe mejor y media y se propone **una** corrección (V5 → `1:03,241`).
- Kart 28 V8 `1:25,415` → `1:25,475` (el ejemplo del encargo): rompe la media (⚠) pero **no se propone una única corrección**: un cambio de 60 ms en la suma lo explican por igual otras 3 celdas
  (V1, V2, V3) con un solo dígito distinto; aritméticamente no se puede saber cuál es la errónea. Preferí no proponer una celda al azar; la V8 queda entre las celdas marcadas en ámbar.

### A2. Probar OCR sin cámara (solo debug)
Ajustes → Depuración → **«Probar OCR con muestra»**. Lee `app/src/debug/assets/muestras/*.png, jpg, jpeg o webp` (hoy solo hay un `LEEME.txt`: el responsable debe copiar allí `muestra_a.png` y `muestra_b.jpeg`).
Sin muestras: «Añade muestras en app/src/debug/assets/muestras/». Con una, la usa; con varias, pregunta cuál. La imagen se copia a la caché y se entrega al **mismo buzón** que «Compartir» (`ImportInbox`),
así que recorre el flujo real: ML Kit → `OcrLayout` → parser → vista previa. En debug, `MlKitImageTextRecognizer` escribe en Logcat (etiqueta **`KartLogOcr`**) cada caja
(`texto, x, y, ángulo`), el valor de `ROW_TOLERANCE` y el texto reconstruido con `⇥` por tabulador. Filtro sugerido: `adb logcat -s KartLogOcr`.

### A3. Clasificación al final del Detalle
La tarjeta pasa a ser el último elemento de la lista, plegada, con la cabecera «Clasificación de la sesión · N pilotos ▸» (▾ al abrir). Test a 360×780 dp con 10 vueltas y 14 pilotos:
se ven 8 vueltas con el teclado plegado y la tarjeta queda fuera de pantalla hasta hacer scroll. (A 360×640 dp caben 6; el requisito de ≥ 7 se cumple en móviles tipo Pixel 3a.)

### A4. Importar, visible
El botón principal de Tandas es ahora desplegable (`SpeedDialFab`): cerrado, «+ NUEVA TANDA ▴»; abierto, «+ NUEVA TANDA» e «IMPORTAR TIEMPOS» y el botón pasa a «CERRAR ▾».
Elegir una acción cierra el menú. Coste: crear una tanda pide **un toque más**. El menú ⋮ conserva «Importar tiempos» y el estado vacío su botón secundario.

### A5. Privacidad de rivales
- «Exportar todo a CSV» **no** incluye participantes, vueltas de rivales ni `importedRaw` (test con una tanda importada: el CSV no contiene nombres ni tiempos de rivales ni el texto original).
- Ajustes → «Borrar datos de rivales» (con confirmación): una transacción vacía `participants`, `participant_laps` (en cascada) e `importedRaw` de todas las tandas; tus tandas y vueltas `IMPORTED` se conservan (test).
- Importar sobre una tanda existente que ya tiene clasificación pide confirmación antes de sustituirla (test: descartar no guarda nada; confirmar sustituye).

### A6. Auditoría del 2.2 (fix → composable → ruta → test por `NavHost`)
Todas las pantallas cuelgan de un único `NavHost` (`ui/navigation/KartNavHost.kt`). **Comprobación de build:** Ajustes muestra al final `KartLog · versión 0.1.0 · build <hash> · <fecha-hora>`;
el hash debe coincidir con `git log -1 --format=%h` (un `+` indica cambios sin commitear). `ConnectedFlowTest` lo verifica.

| Fix | Composable | Ruta | Test por NavHost |
|---|---|---|---|
| F1 gráfica/teclado excluyentes, teclado que cabe | `SessionDetailScreen` → `LapEntryPanel` + `TimeKeypad` | Tandas → card → `sessions/{id}` | `ConnectedFlowTest` (mutación comprobada) |
| F2 franja de las 2 últimas vueltas, teclas 52 dp | `LapEntryPanel.RecentLapsStrip` | `sessions/{id}` | `ConnectedFlowTest` |
| F3 eje Y con vueltas limpias, recorte de salidas | `TimeLineChart` + `ChartScale` | `sessions/{id}` → gráfica | `ConnectedFlowTest` («Media limpia 1:13.082», «Fuera de escala») |
| F4 gráfica completa, sin scroll | `TimeLineChart` (scroll desactivado) | ídem | **`NavHostFixesTest`** (cabe en el ancho y sin acción de scroll) — *nuevo* |
| F5 leyenda fuera del área de dibujo | `TimeLineChart.ChartLegend` | ídem y `analysis` | `ConnectedFlowTest` |
| F6 MEDIA LIMPIA sin recorte | `StatsRow` → `StatBadge` | `sessions/{id}` | **`NavHostFixesTest`** (valor dentro de la pantalla) — *nuevo* |
| F7 circuito por defecto y cifras del Análisis | `AnalysisModel` / `AnalysisScreen` | `analysis` | `ConnectedFlowTest` (mutación comprobada) |
| F8 `contentPadding` 8/96 dp, chips con ▾ | `SessionListScreen`, `CircuitsScreen` | `sessions`, `circuits` | **`NavHostFixesTest`** (la última card queda ≥ 96 dp sobre el borde) y `ConnectedFlowTest` («Circuito ▾») — *nuevo* |
| F9 botones secundarios de Ajustes, elipsis del circuito | `SettingsScreen` + `SecondaryButton`; `NewSessionScreen` | `settings`, `sessions/new` | **`NavHostFixesTest`** (botones de datos visibles) — *nuevo*. La **elipsis** no es verificable con Robolectric (mide texto de forma distinta): queda en la lista visual |

---

## PARTE B — Sprint 4: Dashboard de cronometraje por GPS

**Estado:** B0–B7 implementados, un commit por tarea (B1 y B2 van juntos: el detector se prueba con el simulador del B1).
Comprobado en este entorno: `assembleDebug assembleRelease testDebugUnitTest compileDebugAndroidTestKotlin lintDebug` → **177 tests unitarios, 0 fallos**.
**No comprobado en dispositivo:** `FusedLocationSource` (GPS real), el servicio en primer plano, la notificación, los permisos reales, la vibración/pitido y el consumo. Aquí no hay emulador ni GPS.

### Qué hay y dónde
| Tarea | Piezas | Tests |
|---|---|---|
| B0 | Room **v3**: `circuits.finishLat/finishLon/finishBearingDeg` (nulables) y `gateWidthM` (`NOT NULL DEFAULT 20`), `MIGRATION_2_3` explícita. Trazados en `filesDir/tracks/{sessionId}.csv` (`elapsedNs;lat;lon;speedMps;accuracyM`), borrados con la tanda | `MigrationTest` (2→3 y cadena 1→3; mutación comprobada), `FinishLineAndTrackTest`, instrumentado `MigrationInstrumentedTest` (compila) |
| B1 | `LocationSource` (flujo frío de `GpsFix`), `FusedLocationSource` (prioridad alta, 100 ms), `ReplayLocationSource` (CSV/GPX, `speedFactor`), `TrackFormats`, `TrackSimulator`/`OvalTrack` | `GateDetectorTest` (reproductor, CSV, GPX) |
| B2 | `GateDetector` (dominio puro): proyección local, puerta perpendicular al rumbo, solo sentido del rumbo, tiempo interpolado `t = |d1|/(|d1|+|d2|)`, vuelta mínima `max(20 s, 50 % mejor)`, precisión ≤ 15 m, hueco de fixes > 2,5 s → cruce «poco fiable» | 15 tests; mutaciones comprobadas (interpolación y sentido) |
| B3 | «Marcar meta»: `FinishCalibration` + `CalibrationViewModel` + pantalla con `Canvas` (traza, puerta, flecha), REPETIR/GUARDAR; acceso: Circuitos → ⋮ → «Marcar meta» (y desde EN PISTA si el circuito no tiene meta) | `FinishCalibrationTest`, `CalibrationViewModelTest`, `CalibrationFlowTest` (por NavHost) |
| B4 | Dashboard: preparación (circuito con meta, kart, lastre, temperaturas, estado), pantalla completa vertical/horizontal, vuelta en vivo, última/mejor/delta (verde/rojo), GPS (precisión y Hz, avisos), **pulsación larga 1,5 s con barra** para terminar, resumen y guardado (`LapSource.GPS`, icono en el Detalle) | `TimingSessionTest`, `TrackFlowTest` (por NavHost), `@Preview` vertical y horizontal |
| B5 | `TimingSession` (vive en la app, no en la pantalla) + `TrackingService` (primer plano, tipo `location`, notificación con «Parar»); permisos `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE(_LOCATION)`, `POST_NOTIFICATIONS` con pantalla de explicación y salida a Ajustes si se deniega | `CalibrationFlowTest` (sin permiso → explicación) |
| B6 | «Precisión del GPS vs oficial» en el Detalle (tanda con vueltas `GPS` e `IMPORTED`): tabla oficial · GPS · dif., error medio/máximo/sesgo, aviso si el nº de vueltas no coincide, **desplazamiento** ±3 para alinear una vuelta de salida | `GpsAccuracyTest`, `GpsAccuracyDetailTest` |
| B7 | Ajustes → Depuración → «Simular tanda en pista»: óvalo sintético de 12 vueltas (~58 s, la 1.ª y la 7.ª lentas) reproducido a 10×, abre el Dashboard | `DebugSeederTest` |

### Error esperado del detector (documentado en `GateDetector` y verificado en test)
- Un error de posición **a lo largo de la marcha** de ε metros desplaza el cruce ε/v segundos (a 20 m/s, 3 m = 150 ms); un error **lateral** no cambia la distancia a la línea y no afecta al tiempo (test con ruido lateral de 3 m: error ≤ 20 ms).
- Con ruido a lo largo de σ = 3 m, el error de una vuelta queda acotado por 2·3σ/v (cota derivada, no un número mágico; el test la aplica con la velocidad real).
- Con fixes limpios a 1 Hz y a 5 Hz el error de cada vuelta es ≤ 20 ms (interpolación exacta en recta a velocidad constante).
- **Mínimo esperable en el móvil:** el GPS interno suele dar 1 Hz con 3–5 m de precisión horizontal → 0,1–0,3 s por vuelta, como se anticipaba. `GPS vs oficial` (B6) medirá el valor real.

### Decisiones tomadas por mi cuenta (a validar)
1. **Tipo de sesión GPS = `FREE`** y fecha = hora de guardado: el Dashboard no pide tipo ni fecha (se editarían después). Si se prefiere elegir el tipo en la preparación, es un cambio pequeño.
2. **Marcar meta:** el punto es la **última posición** al pulsar MARCAR y el rumbo va del primer al último fix de los últimos 4 s (hace falta ≥ 8 m recorridos, precisión ≤ 15 m y recta). La puerta queda unos metros por delante de la línea real (reacción al pulsar); como el desfase es el mismo en todas las vueltas **no altera los tiempos de vuelta** (solo si la velocidad de paso varía mucho).
3. **El primer cruce no es una vuelta**: solo arranca el cronómetro (como pedía el encargo). Las vueltas de salida/entrada a boxes se cierran como cualquier otra (se pueden borrar en el Detalle).
4. **Parar desde la notificación** deja la tanda en estado «parada» (vueltas en memoria) a la espera de guardar o descartar al abrir la app; no se guarda sola.
5. **Tanda sin vueltas cerradas:** no se crea sesión; el resumen avisa y solo deja descartar.
6. **Emparejado GPS/oficial:** la vuelta GPS *n* con la oficial *n*, con un desplazamiento manual (−3…+3) por si el cronometraje oficial cuenta una vuelta de salida más.
7. **Aviso al cruzar la meta** (vibración + pitido corto) activado por defecto, con interruptor en Ajustes → Mi perfil.
8. `play-services-location` **21.3.0** (hay una 21.4.0; elegí la que conozco). Sin `ACCESS_BACKGROUND_LOCATION`: el servicio en primer plano arrancado con la app visible basta.
9. El GPX del simulador/emulador deduce la velocidad entre puntos y fija 5 m de precisión (el GPX no la trae).
10. La FAB de Tandas tiene ya tres acciones (Nueva tanda, Importar tiempos, En pista) y el menú ⋮ conserva los accesos.

### Riesgos y puntos a vigilar
- **Precisión real del GPS interno** (la incógnita que este sprint quiere medir): con la cabeza del móvil en el volante y cielo abierto debería cumplirse, pero cubiertas, naves cercanas o un volante metálico degradan la señal. El Dashboard lo muestra (precisión ±m y Hz) y marca las vueltas con hueco de fixes.
- **El proveedor «fused» puede suavizar** o filtrar posiciones y entregar menos de 1 Hz en algunos móviles; el detector interpola, pero conviene mirar el «Hz» del Dashboard en pista.
- **Consumo y térmica:** GPS al máximo + pantalla encendida + servicio. En una tanda larga hay que ir con el móvil cargando o con batería suficiente.
- **Fabricantes agresivos con la batería** (Xiaomi, Huawei…) pueden matar el servicio aunque sea de primer plano; si pasa, excluir KartLog de la optimización de batería.
- **Sin validar en dispositivo:** permisos reales (incluida la denegación permanente), notificación, `FOREGROUND_SERVICE_LOCATION` en Android 14+, vibración y pitido.
- `wipeAll` de depuración (`clearAllTables`) no borra los ficheros de `tracks/` (quedarían huérfanos); «Borrar datos de rivales» tampoco los toca (son datos propios).
- **Tests de Compose con Robolectric:** el cronómetro en vivo se desactiva en los tests (`LocalLiveClock`) porque un bucle infinito de `delay` impide que Compose llegue a reposo. Las consultas Room en vuelo al cerrar la actividad provocaban fallos intermitentes ajenos al código (`UncaughtExceptionsBeforeTest`); se mitigó esperando 300 ms antes de borrar la BD. Si reaparece, repetir la ejecución.
- El rumbo de la meta se guarda al marcarla: cambiar el sentido de marcha del circuito exige volver a marcarla.

### Cómo probar sin salir de casa
1. **Simulador integrado (build debug):** Ajustes → Depuración → «Simular tanda en pista» → EMPEZAR. Reproduce 12 vueltas a 10× (~70 s) y puedes terminar con pulsación larga y guardar.
2. **Emulador con una ruta GPX:** Android Studio → Emulator → *Extended controls* (⋯) → **Location** → pestaña **Routes** → *Load GPX/KML* y elige el fichero → ajusta la velocidad → *Play route*. En KartLog: Circuitos → ⋮ → Marcar meta mientras la ruta pasa por la línea → EN PISTA. Concede el permiso de ubicación al emulador. Para repetir la ruta en bucle, marca *Repeat*.
3. **Comandos:** `./gradlew testDebugUnitTest` (todo lo de arriba) y `./gradlew :app:connectedDebugAndroidTest` (instrumentados, con emulador).

### Lista de comprobación visual para el responsable
**Parte A**
1. Ajustes: la línea de build trae el hash del último commit (`git log -1 --format=%h`); aparecen «Mi nombre de piloto», el interruptor de aviso y «Borrar datos de rivales».
2. Tandas: el botón principal se despliega con «Nueva tanda», «Importar tiempos» y «En pista».
3. Importar la muestra A real: «Nacho · Kart 28» elige tus 6 vueltas; «Alvarommkl» y «Cortamargaritas» salen completos en la clasificación.
4. Detalle de una tanda importada: ≥ 7 vueltas a la vista con el teclado plegado; «Clasificación de la sesión · 14 pilotos ▸» al final de la lista.
5. Importar sobre una tanda con clasificación: diálogo «¿Sustituir la clasificación?».
6. Ajustes → «Probar OCR con muestra» (con las fotos en `app/src/debug/assets/muestras/`): mira Logcat `KartLogOcr` y la vista previa resultante.
7. Elipsis del circuito en «Nueva tanda» con un nombre muy largo (no verificable en Robolectric).

**Parte B**
8. Circuitos: cada card muestra «Meta marcada»/«Sin meta»; ⋮ → «Marcar meta» pide el permiso con su explicación.
9. Marcar meta: indicador «GPS ±x m · y Hz», el dibujo con traza y puerta tras MARCAR, valores (lat, lon, rumbo, precisión), REPETIR y GUARDAR.
10. EN PISTA con un circuito sin meta: aviso y botón «MARCAR META»; EMPEZAR deshabilitado.
11. Dashboard en marcha (vertical y horizontal): cifras grandes legibles a una mano de distancia, delta verde/rojo, aviso «SIN SEÑAL GPS»/«precisión baja», pantalla siempre encendida, pulsación larga de 1,5 s con barra (un toque corto no termina).
12. Con la pantalla apagada la notificación sigue (vuelta y mejor) y «Parar» deja la tanda esperando al abrir la app.
13. Guardar: el Detalle abre con el icono de GPS en cada vuelta; si importas los tiempos oficiales sobre esa tanda aparece «Precisión del GPS vs oficial ±0.xxx s».
