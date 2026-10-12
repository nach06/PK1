# KartLog — Sprint 6: Dashboard horizontal + trazado + importar por foto con revisión

Rama `claude/kartlog-sprint-1-3hhlkb`. Parte del estado del Sprint 5 (Room v4, 234 tests). **Sin cambios de esquema Room** (no hay
migración nueva): la foto de la hoja vive en `filesDir/imports/{id}.jpg`, la copia automática en `filesDir/backups/` y el trazado se
recalcula del CSV existente.

## Estado y verificación

`./gradlew assembleDebug assembleRelease testDebugUnitTest compileDebugAndroidTestKotlin lintDebug` →
**339 tests unitarios/Robolectric, 0 fallos, 0 omitidos**, en dos pasadas completas consecutivas (BUILD SUCCESSFUL); los instrumentados solo se compilan (no hay emulador aquí).
La primera pasada de lint cazó dos errores reales (ver «Lint» más abajo) y se corrigieron antes del cierre.

> **Nada de esto se ha probado en un dispositivo real ni en un emulador.** Lo verificado son tests JVM (Robolectric con modo gráfico
> nativo para texto, píxeles y bitmaps). Lo que no se puede comprobar así está en «No comprobado en dispositivo».

---

## A. Fallos del emulador — causa raíz

| # | Causa raíz | Arreglo | Test |
|---|---|---|---|
| **A1** cronómetro invisible | `AutoSizeText` ocultaba el texto (`ready=false`) **cada vez que cambiaba el texto** y solo lo mostraba al llegar `onTextLayout`. El cronómetro cambia cada 50 ms, así que el texto casi nunca se dibujaba. Los tests no lo veían porque apagaban el reloj en vivo (`LocalLiveClock=false`). | `AutoSizeText` mide en la propia composición (`TextMeasurer`, sin estado intermedio y ajustando también por **alto**). El reloj del Dashboard es inyectable (`AppContainer.nanoClock` / `LocalNanoClock`). | `DashboardClockTest`: reloj en vivo + reloj manual, comprueba que el texto cambia **y que hay píxeles claros dibujados**. Mutaciones: congelar el reloj → falla; volver al `AutoSizeText` antiguo → falla (reproduce el bug real). |
| **A2** importar: campo gigante, botones fuera de vista, «GUARDAR» gris sin motivo | Campo de texto sin límite de alto en una columna con scroll; el botón gris no decía por qué. | Rediseño completo (Parte D): pegar texto = campo que ocupa lo que queda + PEGAR/ANALIZAR fijos abajo; en la revisión, si GUARDAR no está disponible siempre aparece el motivo. | `ImportFlowTest` (pega 400 líneas y comprueba que los botones están dentro de pantalla y el campo < 85 % del alto; `paste_hint` explica el botón desactivado; `save_blocked` en la revisión). |
| **A3** tanda en marcha sin aviso | Solo había el punto de la barra de estado. | `RunningBanner` sobre la barra inferior en cualquier pestaña: «Tanda en pista · Vuelta N · mejor X — VOLVER». | `RunningBannerTest` (NavHost). Mutación: sin banner → falla. |
| **A4** atrás sin confirmar | Atrás volvía sin más. | `BackHandler` + diálogo «¿Seguir en pista o salir sin terminar?». Salir mantiene la tanda y deja el banner; terminar sigue siendo solo la pulsación larga. | Mismo test. Mutación: sin `BackHandler` → falla. |
| **A5** «Hueco de mejora» empujaba la gráfica | Tarjeta alta con título + cifra + párrafo. | Una fila (título + cifra + ⓘ) con la explicación desplegable, y la gráfica de evolución pasa **antes**. | `AnalysisConsistencyFlowTest` a 393×808 dp: la gráfica entera cabe sin bajar, el hueco ≤ 64 dp. |
| **A6** franja de la FAB | Reservaba una franja a la derecha y dejaba el resto vacío. | Franja de ancho completo: recuento a la izquierda («3 tandas» / «1 de 3 tandas» con filtro) y la acción a la derecha. | `NavHostFixesTest.s6a6_…` |
| **A7** detalles | Ver abajo. | | |

**A7 en detalle**

- Icono GPS y «N stints» en las cards de Tandas (`SessionStats.hasGpsLaps/stintCount`; `AnalyticsTest` + `EnduranceFlowTest`).
- Fichas de tipo: ya mostraban la seleccionada; ahora además exponen el estado `selected` (comprobado por test). Empezar/guardar sin kart ya era posible (verificado en `RunningBannerTest`). **Sin cambios de comportamiento.**
- Depuración solo en debug: comprobado en el APK release (`DebugSeeder`, `SampleImages` y las etiquetas «Crear tanda GPS…» no están en el dex; `seedGpsSession` aparece 4 veces solo como nombre de método de la interfaz `DebugTools`, sin implementación). Matices honestos: las **cadenas** de los botones de depuración siguen en `resources.arsc` (el `strings.xml` es único) y el volcado de cajas OCR (`ocr_dumps`) está en código de `main` protegido por `BuildConfig.DEBUG` (nunca se ejecuta en release, pero el código existe).
- **Formato decimal único: punto** en cronómetros, deltas, desviaciones y errores GPS (`1:17.263`, `+0.988`, `±0.053 s`). Una sola familia de funciones en `LapTime` (`format`, `formatDelta`, `formatConsistency`, `formatSeconds`). El CSV público no cambia.

---

## B. Dashboard para el volante (apaisado)

**Implementado** (`RunningDashboard.kt`, `DashboardModels.kt`):

- **B1** `sensorLandscape` (los dos sentidos) al empezar, liberado al salir/terminar. Ajustes → «Dashboard en pista»: Apaisado (por defecto) / Automático / Vertical. `configChanges` en el manifiesto para que girar no recree la Activity.
- **B2** layout 640×360 sin scroll: tiempo de la vuelta en curso ≥ 96 sp monoespaciado arriba y a todo el ancho; barra de delta; abajo tres columnas — **izquierda DELTA** (grande, verde/rojo), **centro nº de vuelta** (+ stint y tiempo de stint en Resistencia), **derecha ÚLTIMA y MEJOR**; pie con estado GPS (punto + ±m + Hz, y batería baja) a la izquierda, PIT (Resistencia) y pulsación larga a la derecha, todos ≥ 64 dp de alto. Fondo negro puro. **Modo día/sol** (fondo claro) con interruptor en la preparación.
- **B3** al cerrar vuelta, 2 s a pantalla completa: delta enorme con color (verde = mejor de la tanda, rojo = más lenta) y **morado «MEJOR»** si bate el récord del circuito; primera vuelta muestra el tiempo. En Resistencia, banda «PIT · cruza la meta al salir» mientras se está en boxes. Vibración + pitido existentes; interruptor «Solo vibración».
- **B4** en marcha solo hay dos elementos táctiles: pulsación larga y PIT (Resistencia). Comprobado: ningún nodo con `OnClick` salvo `pit_button`.
- **B5** pantalla encendida, orientación, **brillo máximo opcional** (se restaura al salir) y aviso de batería < 20 % sin cargar.
- **B6** 5 `@Preview` (640×360, 800×360, día, aviso, vertical) y `DashboardLandscapeTest` a `w640dp-h360dp-land` con modo gráfico nativo: nada fuera de pantalla, sin desbordes de ancho **ni de alto**, tiempo ≥ 96 sp, controles ≥ 56 dp, orientación fijada/liberada, avisos de vuelta (texto y valor: `-0.495` en la simulación), batería, Resistencia, ajustes persistentes.
- **B7** recomendación de soporte al final de este documento.

**Capturas descritas en texto**

- *Esperando salida*: arriba `–:––.–––` gigante en blanco; barra de delta vacía (centro marcado); abajo «Delta –», «VUELTA 1» (en gris, «CRUZA LA META PARA EMPEZAR» hasta el primer cruce), «Última –», «Mejor –»; pie: punto verde «Señal buena · ±3 m · 1,0 Hz» y botón «MANTÉN PULSADO PARA TERMINAR».
- *En vuelta*: `0:41.2xx` cambiando; delta del último cierre en verde/rojo con la barra desplazada a ese lado (fondo de escala ±2 s); Última y Mejor a la derecha.
- *Cierre de vuelta*: toda la pantalla verde con `-0.352` enorme, «VUELTA 7» y el tiempo debajo; si es récord del circuito, fondo morado, etiqueta «MEJOR» y «Récord del circuito». A los 2 s vuelve al panel.
- *Resistencia*: en el centro «Stint 2 · Piloto» y su tiempo; en el pie un botón ámbar «PIT / CAMBIO»; tras pulsarlo, banda ámbar «PIT · cruza la meta al salir».

**Límites asumidos (a validar)**: el delta es la **última vuelta cerrada frente a la mejor anterior** (el delta *en vuelta* necesitaría una vuelta de referencia y es trabajo del Sprint 7); el aviso morado exige haber tenido historial en el circuito (la primera vez es verde); la preparación en horizontal es larga (se desplaza), por eso se espera prepararla en vertical y luego montar el móvil.

---

## C. Trazado de la tanda

`TrackAnalysis` (dominio) + `TrackCard` (UI), en el Detalle de cualquier tanda con `tracks/{id}.csv`:

- Traza proyectada con la proyección local existente, **norte arriba**, escala uniforme y centrada; **meta** dibujada (puerta + flecha de sentido).
- Selector **Todas / Mejor / Última / V1…Vn**; «Todas» = trazas superpuestas con baja opacidad; una vuelta = resaltada y las demás tenues.
- Modos de color: **vuelta**, **velocidad** (rojo→verde, leyenda en km/h) y **ritmo** (verde ganando / rojo perdiendo frente a la mejor, ±1 s). **Comparar con la mejor**: dos colores superpuestos con leyenda.
- Tocar un punto: «V3 · 98 km/h · 0:23.412 desde la meta».
- **C2** las vueltas se **recalculan pasando el CSV por `GateDetector`** (nada nuevo en disco) y se emparejan con las vueltas guardadas por tiempo (±5 ms, en orden; admite vueltas borradas). Si el circuito no tiene meta, o las vueltas se editaron y ya no coinciden, se muestra el trazado completo sin selector **con el motivo** (`track_note`).
- **C3** cálculo fuera del hilo principal (`Dispatchers.Default` vía `mapLatest`), ≤ 2000 puntos dibujados (submuestreo uniforme que conserva primer y último). Test con 18 vueltas / > 10 000 puntos por NavHost.
- **C4** tests: dominio (`TrackAnalysisTest`: proyección, vueltas y tiempos, asignación de puntos a vueltas, emparejamiento, submuestreo, escala/norte arriba, ritmo, selección del punto), NavHost (`TrackCardFlowTest`: aparece solo con trazado, selector cambia lo resaltado, modos, comparación, lectura al tocar, ritmo, sin meta/editadas, 10 000 puntos). **Mutaciones**: fin de vuelta desbordado → falla; emparejado ciego → falla; selector que ignora la selección → falla; sin volver al inicio de la lista → falla. (Un mutante sobrevive: no avanzar el cursor de la asignación es solo una optimización de rendimiento, no cambia el resultado.)
- **Bug real encontrado por el test de 10 000 puntos**: la tarjeta llega de forma asíncrona y se inserta *encima* de la lista; `LazyColumn` conserva la posición de lo que ya se veía, así que la tarjeta quedaba fuera de pantalla. Arreglado (vuelve al principio al aparecer).
- **C5 (opcional): no hecho.** Coste estimado: ½ jornada (miniatura de la mejor vuelta GPS por circuito).

---

## D. Importar desde una foto, con revisión

**Flujo**: Tandas → FAB → Importar → **un botón «IMPORTAR»** → hoja inferior con **Hacer foto / Elegir de galería / Pegar texto** → (foto) **recorte y enderezado** → OCR → **revisión editable** → GUARDAR. «Compartir → KartLog» (imagen o texto) entra por el mismo flujo; el texto salta directamente a la revisión.

**Formatos soportados** (`LapTextParser`, detección + forzado manual con «Formato detectado: … CAMBIAR»):
matriz vuelta × piloto (con fila `Avg.`), **una fila por piloto con las vueltas en columnas** (con posición y/o kart alrededor del nombre, celdas vacías conservan la alineación), **solo mis vueltas** (una columna o lista), tabla resumen. Correcciones del OCR **solo dentro de celdas de tiempo**: `O→0`, `l/I→1`, `S→5`, `B→8` y separadores (`1.12.467`, `1;12,467`, `1:12:467`), siempre marcadas «corregido por OCR». Los nombres no se tocan (test + mutación: aplicar la corrección a todo el texto → falla).

**Revisión** (cabe en una pantalla; solo la tabla se desplaza):

- Línea de «Datos de la tanda» (circuito, fecha, tipo, kart, duración; toca para editar todo en una hoja: destino nuevo/existente, circuito o «+ Nuevo», fecha/hora, tipo, kart, duración, título).
- Miniatura de la hoja con **la celda seleccionada resaltada** (ventana ampliada alrededor de su caja; toca para ver la hoja entera).
- Columnas por piloto (★ la tuya, ⚠ si sus controles no cuadran) y acciones: **Soy yo**, **Siguiente dudosa**, **Unir** (nombre partido por el OCR), **Dividir**.
- Tabla editable celda a celda con el teclado propio: **verde** OK, **ámbar** (corregida por OCR / baja confianza / no cuadra con los controles del documento), **rojo** (fuera de 15 s–5 min; **no se guarda**), **gris** (hueco; rellenable). Por fila: ✓ está bien, ✎ editar, ✕ quitar; «+ Añadir vuelta».
- Controles cruzados ya existentes (mejor = mínimo, nº de vueltas, media ±1 ms) y propuesta de un dígito con **Aplicar / Ignorar** (nunca se aplica sola).
- Pie: resumen **«Se importarán N pilotos y M vueltas · X celdas dudosas sin revisar · Y inválidas excluidas»**; si quedan ámbar/rojas, GUARDAR pide **confirmación explícita**; si no se puede guardar, el motivo está escrito debajo (tu columna sin elegir, sin vueltas válidas, sin circuito, sin tanda destino).
- Si la tanda destino ya tiene clasificación, se mantiene el diálogo de sustitución.

**Foto, calidad y fiabilidad** (D2/D6)

- Recorte con 4 esquinas arrastrables (agarre por proximidad al tocar), girar 90°, «Mejorar contraste» (iguala la iluminación local con imagen integral) y perspectiva real (`Matrix.setPolyToPoly`).
- Antes de analizar: **aviso de foto movida / oscura / con reflejos** (varianza del Laplaciano normalizada por el contraste, luminosidad media, fracción quemada) → «Repetir foto» o «Analizar igualmente».
- Tras el OCR: **puerta de fiabilidad** (< 12 cajas, ninguna vuelta, o < 50 % de celdas leíbles) → «No he podido leer la hoja con fiabilidad» + Repetir foto / Pegar texto; **no abre la revisión y no guarda nada** (test por NavHost y por ViewModel; mutación: sin puerta → falla).
- Una confianza de ML Kit igual a 0 se trata como «sin dato» (si no, todo saldría ámbar).

**Al guardar (D5)**: tu tanda como `IMPORTED`, rivales como hasta ahora, `importedRaw` = texto reconstruido. **Foto original** en `filesDir/imports/{id}.jpg` (reducida a ≤ 2048 px) con interruptor «Guardar foto de la hoja» (sí por defecto); se borra con la tanda y con «Borrar datos de rivales». *Decisión*: las fotos **no** entran en el `.zip` de copia (peso); restaurar una copia deja las tandas sin foto.

**Pruebas sin cámara (D7)**: Ajustes → Depuración → «Probar importación con muestra A/B (imagen)» mete `muestra_a.png` / `muestra_b.jpeg` (de `app/src/debug/assets/muestras/`, aún no están en el repositorio: solo el `LEEME.txt`) por el flujo completo recorte → OCR → revisión. En debug cada lectura se vuelca a Logcat (`KartLogOcr`, con la confianza de cada caja) y a `filesDir/ocr_dumps/*.json` (formato `OcrDump`) para convertir lecturas reales en fixtures. Añadido: «Crear tanda GPS de ejemplo (con trazado)» para ver Parte C y las cards sin salir a pista.

**Tests (D8)**: parser/revisión/guardado extremo a extremo por `NavHost` con un **volcado de OCR ruidoso** (`src/test/resources/ocr/muestra_a_ruidosa.json`, generado por `tools/gen_ocr_fixture.py`: foto inclinada 0,7°, jitter vertical, `O1:20.704`/`0l:22.740`, una celda que el OCR no vio, un nombre partido en dos cajas, confianzas bajas): `OcrFixtureTest` (las 14 columnas se leen como la hoja limpia, las celdas corregidas salen ámbar, la ausente gris, los controles lo notan) y `ImportFlowTest` (caso «nada que revisar» → guarda sin preguntar; celda **editada a mano** con el teclado → entra; rechazo por mala calidad; foto conservada o no según el ajuste; recorte arrastrando una esquina; cambio de formato; hoja y celda resaltadas). Mutaciones comprobadas: corrección O→0 global, celdas rojas guardadas, sin confirmación de dudosas, sin puerta de fiabilidad, sin aviso de calidad, unir que pisa celdas.

> **Hallazgo de la fixture sintética**: el anclaje de columnas de `OcrLayout` usaba cualquier cosa «con forma de tiempo» (fechas, `GAP +0.801`) y una sola caja suelta creaba una columna. Ahora solo anclan los `m:ss.mmm` completos y una columna necesita ≥ 2 tiempos. Es un cambio sobre código que hasta ahora solo había visto datos sintéticos: **a validar con fotos reales**.

### D9. Estado real del OCR en dispositivo — lo que hay que comprobar con 3 hojas reales

**Nunca se ha probado con una foto real.** Con tres hojas (idealmente: la de Asupark impresa, la de un correo en pantalla del móvil, y una con la hoja ligeramente curvada/torcida):

1. ¿Las esquinas iniciales (foto entera) bastan o hay que arrastrarlas? ¿Se arrastran bien con el pulgar? ¿Se ve el aviso de foto movida/oscura/reflejos cuando toca, y no sale sin motivo con una foto buena? (umbrales `MIN_SHARPNESS=0.5`, `MIN_BRIGHTNESS=70`, `MAX_GLARE_FRACTION=0.12` sin calibrar).
2. «Mejorar contraste» activado/desactivado: ¿cambia el número de celdas legibles? (hoy está activado por defecto; sin datos reales podría empeorar).
3. Mirar `adb logcat -s KartLogOcr` y los JSON de `ocr_dumps`: ¿cuántas cajas, qué confianza dan las celdas buenas y las malas? El umbral ámbar (< 0,7) y la puerta (12 cajas, 50 %) son estimaciones.
4. ¿Las columnas salen bien alineadas (cabeceras ↔ tiempos)? Compartid los JSON y se convierten en fixtures reales (`src/test/resources/ocr/`, `OcrDump.fromJson`).
5. ¿Los nombres largos / con tildes / con kart salen enteros? ¿Aparecen columnas partidas que haya que **Unir**?
6. ¿La miniatura resalta la caja correcta al seleccionar una celda? (las coordenadas son las de la imagen **recortada**).
7. Tiempo de análisis en el móvil real y consumo (todo es local).

---

## E. Mejoras

| | Estado | Notas |
|---|---|---|
| **E1** resumen al terminar con comparativa y gráfica | ✔ hecho | «Nuevo récord del circuito −1.xxx (antes …)» / «Tu mejor del circuito … · esta tanda +…» / «Primera tanda en este circuito», y la gráfica de vueltas con la referencia del récord. `SummaryComparisonTest` (3 casos + mutación). |
| **E2** compartir resumen como imagen | ✔ hecho | Menú ⋮ del Detalle → «Compartir resumen (imagen)»: PNG 1080×1350 con circuito, fecha, tipo/kart, **mejor, media limpia, nº de vueltas y trazado**; **sin datos de rivales** (los datos de la tarjeta no pueden contenerlos). |
| **E3** filtros persistentes | ✔ hecho | Circuito, tipo **y orden**; un circuito borrado no vacía la lista. |
| **E4** copia automática semanal | ✔ hecho | Al abrir la app, si pasó ≥ 7 días y hay datos: `filesDir/backups/kartlog_auto_*.zip` (últimas 4), aviso breve (Toast) y última fecha + interruptor en Ajustes. La misma copia que la manual (**incluye rivales**); no sale de la app salvo que se comparta. No usa WorkManager (solo al abrir la app). |
| E5 live timing por red | no (según lo pedido) | |
| E6 ESP32 / sensores | no (según lo pedido) | |
| **E7** sectores/minisectores desde el trazado | solo viabilidad | Viable con lo que ya hay: la proyección + vueltas recalculadas permiten partir el circuito en N tramos por distancia fraccionada (lo que ya usa el modo «ritmo»); el problema real es la **precisión del GPS a 1 Hz** (±15–20 m en un tramo de 100 m) → los sectores cortos serían ruido. Coste estimado 2–3 jornadas incluyendo definir los puntos de sector en el circuito. |
| **E8** mejor vuelta ideal por tramos | idea Sprint 7 | Depende de E7: tomar el mejor tiempo de cada tramo entre vueltas. Misma limitación de precisión. Coste ~1 jornada sobre E7. |

---

## Decisiones a validar

1. **Punto decimal en toda la app** (en lugar de coma en los deltas).
2. **Dashboard**: tiempo en curso arriba a todo el ancho (no en el centro de tres columnas) para poder llegar a ≥ 96 sp; delta = último cierre frente a la mejor anterior.
3. **Aviso de récord morado** solo con historial previo en el circuito.
4. **Atrás en marcha**: diálogo (no salida directa); el aviso persistente existe en las pestañas, no en el Detalle ni en pantallas secundarias, y **no hay aviso por «borrador pendiente de recuperar»** porque ese caso ya lo cubre el diálogo de recuperación al abrir.
5. **Fotos fuera del `.zip`** de copia.
6. **Hoja de «Datos de la tanda»** en vez de edición en línea para no desbordar la pantalla.
7. **Trazado como primer elemento de la lista del Detalle** (visible sin bajar; empuja las vueltas hacia abajo; plegable).
8. **Un solo commit por bloque coherente** en vez de uno por sub-tarea cuando comparten ficheros: B1–B6 (`18731cc`), C1–C4 (`d855380`), D1/D2/D4/D5/D6/D7/D8 + A2 en dos commits de dominio y uno de UI. El resto, uno por tarea.

## Riesgos

- **OCR real sin probar** (D9): umbrales de calidad, de confianza y de fiabilidad son estimaciones; el cambio del anclaje de columnas en `OcrLayout` se validó con fixtures sintéticos.
- **Perspectiva y contraste local** probados con imágenes sintéticas, no con fotos reales (luz desigual, papel curvado).
- **Confianza de ML Kit** (`Element.confidence`): si en el dispositivo viene siempre 0 se trata como sin dato (no hay ámbar por confianza); si viniera baja en general, saldrían muchas ámbar (se puede revisar con «Siguiente dudosa»).
- **Brillo máximo y orientación** dependen de la `Activity`; probados en Robolectric, no en hardware.
- **Rendimiento del trazado** en el móvil con trazados de horas: la proyección y la asignación son lineales; el dibujado va limitado a 2000 puntos, pero el modo «ritmo» calcula distancias por vuelta en composición.
- **Compose en el volante**: el reloj en vivo recompone cada 50 ms; el medidor de texto de `AutoSizeText` mide en cada cambio (barato con fuente monoespaciada, pero no medido en un móvil de gama baja).
- **Carrera de test corregida (no del producto)**: varios tests de Dashboard pulsaban EMPEZAR antes de que Room entregara el circuito (botón aún desactivado) y fallaban al azar bajo carga; ahora esperan al circuito (`SummaryComparisonTest`, `RunningBannerTest`, `DashboardLandscapeTest`, `DashboardClockTest`). 3 pasadas seguidas de la suite completa sin fallos tras el arreglo.
- **Flaky conocido**: los `tearDown` con `Thread.sleep(300)` para consultas Room en vuelo (deuda anterior, sin cambios).

## No comprobado en dispositivo

Todo lo anterior. En concreto: orientación apaisada fija y giro de 180° con el soporte; legibilidad a 60–80 cm y a pleno sol (modo día); vibración/pitido; brillo máximo; batería; el banner en pestañas con el servicio real en primer plano; cámara del sistema y selector de galería reales; el OCR real (D9); arrastre de esquinas con el dedo; EXIF de fotos reales; compartir PNG con una app destino real; copia automática al abrir con una BD grande; tarjeta «Trazado» con un trazado real de GPS (ruido, huecos).

## Lista de comprobación visual para el responsable

**Emulador Pixel 3a (393×808 dp), sin scroll** — Ajustes → Depuración → «Crear tanda GPS de ejemplo (con trazado)» y «Simular tanda en pista» te ahorran salir a pista.

1. **Tandas**: abajo a la izquierda «N tandas», a la derecha «+ NUEVA TANDA»; filtra por tipo → «1 de N tandas»; cierra y abre la app → el filtro sigue. La card de la tanda de ejemplo lleva el icono GPS.
2. **Detalle de la tanda de ejemplo**: arriba, «TRAZADO» abierto con el óvalo, la puerta de meta y la flecha. Prueba V3, Mejor, Última, Velocidad, Ritmo, Comparar con la mejor, y tocar un punto. Menú ⋮ → «Compartir resumen (imagen)».
3. **Ajustes → Dashboard en pista**: elige Apaisado/Automático/Vertical; sube «Solo vibración» y «Brillo máximo».
4. **Simular tanda en pista** → se abre el Dashboard: ¿ves el tiempo corriendo (A1)? Gira el emulador a horizontal (Ctrl+Flecha). Cierra una vuelta: ¿pantalla verde/morada 2 s? Atrás → diálogo; «Salir sin terminar» → banner rojo sobre la barra con «VOLVER».
5. **Terminar** (mantener pulsado): resumen con comparativa y gráfica.
6. **Importar → texto**: Tandas → + → Importar → IMPORTAR → «Pegar texto». Pega el texto de una muestra: el campo no te tapa los botones.
7. **Ajustes → Depuración → «Importar muestra de texto A»**: llegas a la revisión. Toca una columna y «Soy yo»; prueba ✎, ✓, ✕, «Siguiente dudosa», «Unir», el formato (CAMBIAR) y los datos de la tanda (línea superior). GUARDAR con ámbar/rojas pide confirmación.
8. **Debug → «Probar importación con muestra A/B (imagen)»** (cuando pongas `muestra_a.png` / `muestra_b.jpeg` en `app/src/debug/assets/muestras/`): recorte → ANALIZAR → revisión.

**Móvil real**: monta el móvil en el soporte, pon la orientación en Apaisado, empieza una tanda y comprueba: (a) legibilidad del tiempo y del delta, (b) que el giro de 180° mantiene todo, (c) pulsación larga con guantes, (d) en Resistencia, PIT y el diálogo de dos toques, (e) brillo y modo día a pleno sol, (f) el aviso de batería al <20 %. Después, **fotografía tres hojas** y sigue la lista D9; comparte los JSON de `ocr_dumps` para fijar umbrales.

## B7. Soporte del móvil (recomendación, sin imágenes)

Montarlo en el **centro del volante o ligeramente por encima del cubo, en horizontal**, con el borde inferior del móvil por encima del aro para no tapar el cuadro de mandos ni el campo visual de la pista; es la zona que menos se mueve respecto a los ojos y la que menos vibra (el aro del volante y los radios sí). Mejor un soporte rígido con brida o abrazadera al volante que uno de ventosa o pinza de salpicadero; amortigua con una pieza de goma entre soporte y volante, nunca en el móvil. Para el GPS: antena del móvil **hacia el cielo** (pantalla hacia ti, sin la mano ni el casco tapando la parte superior del móvil), y evita fundas metálicas o magnéticas. Con kart de alquiler, comprueba antes de salir que el soporte no interfiere con el giro del volante ni con el airbag/amarres si los hubiera.

---

## Tabla de commits

| Tarea | Hash |
|---|---|
| A1 cronómetro | `3a9983d` |
| A3 + A4 banner y atrás | `d129c87` |
| A6 franja de la FAB | `79c5f3e` |
| A5 hueco de mejora | `6bbf095` |
| A7 (1/4) icono GPS y stints | `4ddfcdd` |
| A7 (2/4, 3/4) formato decimal, fichas, kart | `d10484e` |
| A7 (4/4) Depuración solo en debug | verificado en el APK release, sin cambios de código |
| B1–B6 Dashboard apaisado | `18731cc` |
| C1–C4 trazado | `d855380` |
| D3 analizador tolerante | `0bf6f45` |
| D2/D6 dominio (rejilla OCR, calidad, recorte, fiabilidad) | `66cbe10` |
| D4 modelo de revisión | `fae634d` |
| D5 almacén de fotos | `76e5115` |
| D1/D2/D4/D5/D6/D7/D8 + A2 flujo y pantallas | `dc860e5` |
| D7 atajos de depuración + tests de procesado de foto | `4150999` |
| E1 resumen | `47ddde5` |
| E3 filtros | `32e1819` |
| E4 copia automática | `66d2282` |
| E2 compartir imagen | `382c3de` |
| B4 test de elementos pulsables | `607bcf4` |
| Lint (`removeLast`, textos en composición) | `7354204` |
| Informe, confianza 0 = sin dato y tests sin carreras (`UiFlowTest`, EMPEZAR antes de tener circuito) | `5a38991` |

### Lint

La primera pasada de `lintDebug` falló con 4 errores reales: `List.removeLast()` (API 35, **habría roto en Android < 15** con `NoSuchMethodError`) en `RowPerDriverProfile`, y dos lecturas de recursos con `LocalContext` dentro de un `onClick`. Corregidos; segunda pasada limpia.

### Último commit y push

Último commit: `5a38991` (este informe), subido con `git push -u origin claude/kartlog-sprint-1-3hhlkb`; `HEAD` y `origin/claude/kartlog-sprint-1-3hhlkb` coinciden.
