# KartLog — Sprint 7: cierre de fallos, pruebas en emulador sin fricción, delta en vuelta y OCR con muestras reales

Rama `claude/kartlog-sprint-1-3hhlkb`. Parte del estado del Sprint 6 (339 tests). **Sin cambios de esquema Room** (la BD sigue en la
versión 4): lo nuevo vive en ficheros (`Android/data/<app>/files/Download/` para las copias y volcados de depuración) y en
`SharedPreferences` (`gps_delay_ms`, `last_import_column`).

## Estado y verificación

`./gradlew assembleDebug assembleRelease testDebugUnitTest compileDebugAndroidTestKotlin lintDebug` →
**390 tests unitarios/Robolectric, 0 fallos, 0 omitidos** (BUILD SUCCESSFUL), lint **0 errores** (87 avisos, sin compararlos con los del
Sprint 6); los instrumentados solo se compilan (no hay emulador aquí). Las tres últimas pasadas completas consecutivas salieron verdes,
**pero la suite no ha sido 100 % estable durante el sprint**: en 7 pasadas completas el test `draftWithoutLaps_…` falló 3 veces (una antes de
alargar la duración del aviso, dos después; nunca aislado ni en grupos pequeños) y una vez falló una carrera del propio test del volcado
OCR (corregida). Detalle en «Riesgos».

> **Nada de esto se ha probado en un dispositivo real ni en un emulador.** Lo verificado son tests JVM (Robolectric, con modo gráfico
> nativo cuando importa el texto). Lo que no se puede comprobar así está en «No comprobado en dispositivo».

---

## A. Fallos del emulador — causa raíz

| # | Causa raíz | Qué cambia | Test (y mutación) |
|---|---|---|---|
| **A1** «faltan muestras» con las imágenes en `app/src/debug/assets/muestras/` | **No se pudo reproducir aquí** y no se puede afirmar la causa. Hechos comprobados: (1) en el repositorio **no están** `muestra_a.png` / `muestra_b.jpeg` (solo `LEEME.txt`); (2) el listado exigía nombres/extensiones concretos; (3) si el IDE abre otra carpeta Gradle (p. ej. `pk1 > KartLog > pk1`), compila **otros** assets y nunca verá los tuyos. | El botón, si no encuentra imágenes, abre un **diálogo de diagnóstico**: ruta exacta que usa la compilación (`<carpeta Gradle>/app/src/debug/assets/muestras`, de `BuildConfig.PROJECT_DIR`, vacío en release), nº de imágenes y el contenido literal de `assets.list("muestras")` (con subcarpetas). El listado acepta **cualquier imagen** (png, jpg, jpeg, webp, bmp, gif, heic…; mayúsculas, doble extensión, subcarpetas). Nuevo botón **«Elegir imagen del almacenamiento»** (selector del sistema), que no depende de ninguna carpeta. | `SampleImagesTest` (AssetManager falso: mayúsculas, dobles extensiones, subcarpetas, diagnóstico), `DebugSamplesFlowTest.missingSamples_…`. |
| **A2** diálogo «Tanda interrumpida» cortado en apaisado | El mensaje no era desplazable: a 360 dp de alto los botones salían de la ventana. | Mensaje con scroll interno; los dos botones siempre dentro. | `DraftLandscapeTest` (`w640dp-h360dp-land`): ambos botones con `bottom ≤ 360 dp`. |
| **A3** recuperar un borrador sin vueltas dejaba «Tanda terminada · 0 vueltas» y bloqueaba simular / «En pista» | Se restauraba sin mirar si había alguna vuelta; la `TimingSession` quedaba en STOPPED. | Antes de ofrecer el diálogo se **recalcula en una sesión desechable** (`countRecoverableLaps`, sin tocar la real ni el disco): si salen 0 vueltas se borra el borrador y se avisa «Tanda interrumpida sin vueltas: descartada» (snackbar largo). Nunca queda una `TimingSession` colgada. | `DraftRecoveryFlowTest.draftWithoutLaps_…` (aviso, sin diálogo, borrador borrado, `IDLE`). Mutación (`< 0`) → falla. |
| **A4** lista de Tandas con alto 0 en apaisado | Barra (56) + navegación (80) + franja (84) + chips (72) + cabecera de mes = ~300 dp de 360. | Modo compacto (`screenHeightDp < 480`): franja inferior 60 dp, chips de 40 dp en **una fila**, cabecera de mes y separaciones mínimas y **card de una sola fila** (circuito + fecha, mejor, delta/nº de vueltas). La card completa mide ~220 dp y no cabía en ningún caso. | `SessionListLandscapeTest` (`w640dp-h360dp-land`, modo nativo): la 1.ª card **entera** (no recortada) por encima del recuento y de la barra. Mutación → falla. |
| **A5** tarjeta Trazado: chips que saltan, lienzo enorme | La leyenda aparecía/desaparecía con su propio alto; el lienzo era fijo de 200 dp; el selector y los modos ocupaban dos filas y la lectura otra. | Lienzo = 16 % del alto de pantalla (110–160 dp). **Modos de color en la cabecera** de la tarjeta (una fila con «TRAZADO … ▴»). La **lectura y la leyenda se superponen al lienzo** (sin alto propio: nada de lo de debajo se mueve). Selector de vuelta en **una fila** que se desplaza hasta la vuelta elegida. | `TrackCardLayoutTest` (393×808): lienzo ≤ 40 % del alto útil, chips sin moverse al cambiar de modo, **≥ 3 vueltas enteras visibles debajo**, chip de la vuelta elegida dentro de la fila. Mutación (lienzo 30 %) → falla. |
| **A6** aviso «Tanda en pista» chillón | Fondo rojo pleno. | Superficie oscura con el rojo solo en el borde superior (2 dp), el punto ● y «VOLVER ›». El alto del aviso **ya** entraba en el `contentPadding` de las pantallas (el `Scaffold` lo mide en la barra inferior y se lo resta al `NavHost`); se comprueba con test. | `RunningBannerListTest`: con tanda en marcha, la última card entera por encima del aviso. Mutación (quitar el padding del `NavHost`) → falla. |
| **A7** IMPORTAR pegado al borde inferior | La columna centraba contenido con huecos. | Texto y botón **juntos, arriba**. | `ImportFlowTest`: botón a ≤ 48 dp del texto y en la mitad superior. Mutación (`SpaceBetween`) → falla. |
| **A8** Revisión: solo 4 filas visibles | ~240 dp de pie (controles, resumen, motivo, botón), cabeceras altas y filas de 56 dp. | Controles cruzados **en una línea** (✓ etiqueta valor), resumen y motivo más cortos, filas de 48 dp (tiempo 20 sp), cabeceras con menos relleno. **El motivo «No se puede guardar» sigue visible.** Con la letra al 130 % el chip pasa a dos líneas (ver E4). | `ReviewDensityTest` (393×808, 12 vueltas): **≥ 7 filas enteras** + `save_blocked` visible. Mutación (pantalla del Sprint 6) → 5 filas → falla. |
| **A9** sin nombre guardado | — | Al pulsar «Soy yo» se recuerda la columna (`nombre|kart`); la próxima importación la **sugiere** (☆ en la ficha, ficha vista, aviso «Sugerida…») **sin darla por tuya** (hay que pulsar «Soy yo»). Con nombre guardado no cambia nada. | `DriverMatcherSuggestionTest`, `ImportSuggestionFlowTest` (NavHost). Mutación (no guardar) → falla. |

---

## B. Atajos de depuración (solo builds debug; el emulador solo admite **un clic**)

Todos cuelgan de `container.debugTools != null` (en release es `null`: los botones no se dibujan). Detalle de cómo se llega con clics:

| # | Dónde | Qué hace |
|---|---|---|
| **B1** | Dashboard en marcha (arriba a la derecha) | **«TERMINAR (DEBUG)»**: un toque = la pulsación larga. El test de «solo hay PIT como control» lo ignora a propósito (en release sigue sin haber botones de un toque). |
| **B2** | Marcar meta (Circuitos → ⋮ → Marcar meta) | **«Marcar meta (simulada)»**: pase simulado por el óvalo → resultado con rumbo, **AJUSTAR** (±1 m, ancho) y **GUARDAR** a la vista. **«Verificar (simulada)»** hace la pasada de comprobación. Para que se vea todo sin desplazar se pasaron las **acciones a una barra fija abajo** y el gráfico se compacta con resultado. |
| **B3** | Ajustes → Depuración | **«Muestra A como Nacho (kart 28)»**: crea el circuito GP3, abre la revisión con la columna Nacho ya marcada → **GUARDAR** activo con un clic. |
| **B4** | Ajustes → Depuración | **«Exportar copia (debug, a Descargas)»** (sin selector ni hoja de compartir; enseña la ruta: `Android/data/com.nacho.kartlog/files/Download/kartlog-debug-backup-*.zip` y, en Android 10+, copia en `Descargas/KartLog`) y **«Restaurar última copia (debug)»** (sin selector; pide la confirmación habitual). |
| **B5** | Ajustes → Depuración | **«Simular resistencia (2 pit)»**: arranca una resistencia de 12 vueltas aceleradas con PIT automático tras las vueltas 3 y 6 (pilotos «Piloto B», «Piloto C»). |
| **B6** | Ajustes, Detalle y Revisión | **Aviso «hay más abajo»** (degradado + flecha, no recoge toques) mientras se pueda seguir bajando; en debug añade **«IR AL FINAL (DEBUG)»**. En Ajustes ya existía el botón del panel. |

Tests: `DebugShortcutsFlowTest` (B1, B2, B4, B5, B6, E1), `DebugSamplesFlowTest` (B3), `PitScriptTest`. Mutaciones: `debugShortcuts=false` → B1 falla;
aspecto del gráfico (B2) → falla; ordenar al revés la «última copia» (B4) → falla; umbral del `PitScript` (B5) → falla; ocultar el aviso siempre (B6) → falla.

---

## C. Delta en vuelta

**Diseño** (`ReferenceProfile`, `LiveDeltaTracker`, `TimingSession`, `CircuitReferenceLoader`):

- **Vuelta de referencia**: la mejor vuelta GPS **de esta tanda**; hasta que haya una, la mejor vuelta GPS **del circuito con trazado guardado** (se recalcula con la meta actual; se miran las 3 mejores tandas). Una vuelta solo vale si ambos cruces son fiables, no tiene huecos de fixes > 3 s y no es una vuelta de PIT.
- **Perfil**: trazado de la vuelta proyectado a metros alrededor de la meta, remuestreado cada ~5 m, con el tiempo en cada punto.
- **Delta en vivo** = `t_actual − t_ref(posición)`: la posición actual se **proyecta sobre el trazado de referencia** (segmento más cercano en una ventana por delante del último hallado, para no confundir tramos que pasan cerca) y se lee ahí el tiempo de referencia. Al no depender de la distancia recorrida por cada vuelta, **al cruzar la meta el delta coincide con la diferencia real de vueltas**.
- Entre dos posiciones del GPS (1 Hz) la posición se **extrapola** con la velocidad del último tramo (máx. 2 s) y el Dashboard lo calcula con su reloj de 50 ms (≥ 4 lecturas/s, comprobado). Suavizado de primer orden con τ = 0,12 s (retraso ≈ 0,12 s ≤ 0,3 s; el test de escalón llega al 90 % en 300 ms).
- **Dashboard**: el DELTA grande y la barra pasan a ser el delta en vuelta (verde = por delante, rojo = por detrás) con una etiqueta: **vs mejor** (referencia de la tanda), **vs circuito** o **sin referencia** (entonces se conserva el delta de siempre: última vuelta contra la mejor anterior). El aviso de cierre de vuelta (2 s) sigue enseñando el delta final como antes.
- Recuperar un borrador reconstruye también la referencia (reproduce los fixes por el mismo camino).

**Error esperado (C2)** — medido en `LiveDeltaTest.expectedErrorWithNoisyOneHertzGps_…` (óvalo, GPS a 1 Hz, ruido 1,5 m lateral y 0,8 m a lo largo, vuelta 1 s más lenta que la referencia, lecturas en el fix y 0,5 s después):

| | RMS | Máximo |
|---|---|---|
| Delta intermedio (10–90 % de la vuelta) | **≈ 150 ms** | **≈ 325 ms** |
| Delta al cruzar la meta (sin ruido) | — | **±50 ms** (test: 1 000 ms de diferencia → medido dentro de ±50) |

Origen: el error de posición *a lo largo* de la marcha (ε m ⇒ ε/v s: 2 m a 15 m/s ≈ 130 ms), la extrapolación (error de cambio de velocidad en ~0,5 s de media; las frenadas bruscas son lo peor) y el filtro. **El delta intermedio es una indicación (décimas), no un tiempo oficial.** Con 10 Hz el error intermedio bajaría de forma clara, pero depende del móvil.

Tests: `LiveDeltaTest` (6 + 1: sin referencia; vuelta más lenta ⇒ diferencia ±50 ms y a mitad ≈ 500 ± 150; refresco entre fixes sin saltos > 150 ms; hueco de fixes ⇒ la vuelta rápida con hueco **no** es referencia; referencia del circuito desde la V1 y sustitución por la de la tanda; escalón/suavizado; error esperado), `InLapDeltaFlowTest` (NavHost: etiqueta y valor ≈ −500 a mitad de la V2), `CircuitReferenceLoaderTest`. Mutaciones: quitar la guarda de huecos → falla; no extrapolar → fallan 2; el Dashboard con el delta antiguo → falla.

---

## D. Muestras reales y OCR

- **D1 — NO ejecutado.** Las imágenes no están en el repositorio y ML Kit necesita un dispositivo con Google Play services. `OCR_MUESTRAS.md` (raíz) trae los pasos exactos para el responsable (≈ 5 min, solo clics) y qué hay que mandar de vuelta.
- **D2** — En la pantalla de recorte, **indicador de calidad** siempre visible: nitidez, brillo y reflejos con el valor medido y el umbral que se exige (`mín. 0.50`, `mín. 70`, `máx. 12 %`), ✓ verde / ⚠ ámbar (`PhotoQuality.MIN_SHARPNESS/MIN_BRIGHTNESS/MAX_GLARE_FRACTION`; los umbrales siguen sin afinar con fotos reales). En la revisión (solo debug): **«DUMP OCR (DEBUG)»** guarda `ocr-dump-AAAAMMDD-HHMMSS.json` junto a las copias y abre la hoja de compartir con el JSON. Test: el volcado se lee de vuelta con el mismo número de cajas.
- **D3** — Pendiente de los volcados reales. Preparado: `OcrDumpsPipelineTest` recorre **todos** los `.json` de `src/test/resources/ocr/` por layout → analizador → revisión y exige formato reconocido, pilotos con vueltas, tiempos entre 15 s y 5 min y revisión coherente (un volcado basura lo hace fallar; hoy hay uno, el ruidoso del Sprint 6). Cuando lleguen los reales, se copian ahí y se añaden aserciones concretas por muestra.

---

## E. Deuda y robustez

- **E1 — retardo del GPS.** Documentado en `GateDetector` y medido en `GateDetectorTest`: el ruido **lateral** (σ 3 m) no mueve el cruce (< 30 ms); el ruido **a lo largo** (σ 3 m) lo mueve ≈ σ/v (RMS entre 20 ms y la cota ~340 ms del test, a 13 m/s); un retardo **constante** del proveedor fusionado (300 ms) **no cambia los tiempos de vuelta** (±20 ms) y solo corre el instante absoluto del cruce; un retardo que **varía** entre pasadas (100 ms) sí es 100 ms de error. Ajustes → Depuración → **«Compensación de retardo GPS (ms)»** (pasos de 50, −500…+500, 0 por defecto) resta ese retardo al cruce interpolado (`GateConfig.delayCompensationMs`). Test NavHost del selector con topes.
- **E2 — copia semanal en segundo plano.** `JobScheduler` periódico (7 días) con batería **no baja** y almacenamiento **no justo**, sin dependencias nuevas (WorkManager no está en la caché de Gradle offline y `setRequiresBatteryNotLow` ya existe desde Android 8 = nuestro `minSdk`). Hace lo mismo que la copia al abrir la app (se mantiene como red de seguridad). Limitaciones: no sobrevive a un reinicio hasta abrir la app otra vez (no se pidió `RECEIVE_BOOT_COMPLETED`); se programa al abrir la app si el interruptor está activado. Test: un único trabajo con sus restricciones y cancelación.
- **E3 — cierre de Room.** `AppContainer.closeAndAwait()` / `closeAllAndAwait()` cancelan el trabajo en segundo plano y cierran la BD esperando de verdad; 33 clases de test sustituyeron `Thread.sleep(300)` por `awaitRoomClosed()`. (`TextNotClippedTest` conserva un `sleep(300)` en el *cuerpo* del test, para las animaciones de entrada.)
- **E4 — textos sin recortar.** `TextNotClippedTest` (parametrizado: Tandas, Detalle, Circuitos, Análisis, Ajustes, Revisión; 360×640 dp, letra 1,3×, modo nativo) comprueba que ningún texto termina en puntos suspensivos, se sale de su caja ni de la ventana. **Encontró dos fallos reales de mi propio A8** (línea de datos de la Revisión a una línea con elipsis; controles cruzados «VUELTAS 12» desbordados): ahora la línea admite 2 líneas y, con letra grande, los controles pasan a etiqueta arriba / valor abajo. Nota técnica: `hasVisualOverflow`, `didExceedMaxLines` y `getLineEnd` dan falsos positivos con las métricas del modo nativo; el test mide elipsis, ancho de línea y alto contra las restricciones.

---

## Decisiones a validar

1. Referencia del delta = **mejor vuelta de la tanda** en cuanto existe, aunque el circuito tenga una más rápida (como pedía el enunciado). ¿Prefieres comparar siempre con la del circuito?
2. En el modo compacto de Tandas (apaisado) la card pierde fecha larga, condiciones y sparkline. ¿Vale?
3. Con letra grande (> 1,15×) los controles cruzados de la Revisión ocupan más alto: menos filas visibles, pero nada cortado.
4. La copia en segundo plano es `JobScheduler` y no WorkManager (sin dependencia nueva). Si prefieres WorkManager hay que añadirlo con red.
5. «Sugerida» (A9) no marca «Soy yo»: un clic más, a cambio de no equivocarse de columna.

## Riesgos

- **Test intermitente** `DraftRecoveryFlowTest.draftWithoutLaps_…`: 3 fallos en 7 pasadas completas, 0 en solitario y 0 en las 3 últimas. **No se halló la causa** (sospecha: carrera del aviso con el reloj virtual de Compose bajo carga; el aviso es un snackbar que nunca se llegó a ver en el fallo). Se alargó la duración del aviso y el test imprime los textos en pantalla, el estado del borrador y la fase si vuelve a fallar (mensaje `DIAG`). Si reaparece, es un fallo de **test** conocido, no de la función (aislado pasa siempre), pero no está resuelto.
- **Código de depuración en release**: igual que en el Sprint 6, el código de los atajos de `main` existe en el APK (`debug_finish`, `PitScript`, `EnduranceSimulation`) pero es **inalcanzable** (`debugTools == null`); las implementaciones (`DebugSeeder`, `DebugBackupFolder`, `SampleImages`) **no** están en el dex (comprobado).
- **Delta intermedio**: ±0,1–0,3 s con GPS a 1 Hz (ver C2). Si el móvil entrega 10 Hz mejora; si el GPS salta, la proyección puede enganchar un tramo cercano (se limita a una ventana de ~400 m y 60 m de distancia al trazado).
- **Compartir el volcado OCR** manda el JSON completo por `EXTRA_TEXT` (decenas de KB; el límite de Binder es ~1 MB).
- Los `Thread.sleep` que quedan en cuerpos de test (esperas de animación) son deuda menor.

---

## Lista visual para el emulador (solo clics) — con los atajos de la Parte B

1. **Ajustes → DEPURACIÓN ▸** (un clic abre el panel; cabe sin desplazar). Comprueba el nuevo botón **«Muestra A como Nacho (kart 28)»** → revisión con **≥ 7 filas** visibles, motivo/ GUARDAR, miniatura (si hay foto) → **GUARDAR** → Detalle.
2. **Probar importación con muestra A/B (imagen)**: si no hay imágenes, el diálogo con la ruta y el contenido de `assets`; si las has puesto, recorte con el **indicador de calidad** (3 fichas con umbral) → ANALIZAR → revisión → **DUMP OCR (DEBUG)** (ruta en `Android/data/.../Download`). Alternativa: **Elegir imagen del almacenamiento**.
3. **Simular tanda en pista** → Dashboard: tras la V1 el DELTA pasa de «sin referencia» a «vs mejor» y se mueve **entre posiciones**; al cerrar vuelta, el aviso verde/rojo. **TERMINAR (DEBUG)** (esquina) → resumen.
4. **Simular resistencia (2 pit)** → Dashboard en resistencia: «Stint 1 → 2 → 3» solo, con la banda PIT. **TERMINAR (DEBUG)**.
5. **Circuitos → ⋮ → Marcar meta → Marcar meta (simulada)** → AJUSTAR (±1 m, ancho) y **GUARDAR** a la vista (barra inferior fija) → **Verificar (simulada)**.
6. **Ajustes → Depuración → CREAR TANDA GPS DE EJEMPLO**, luego Tandas → Detalle: tarjeta **Trazado** compacta con **≥ 3 vueltas** debajo; cambiar de modo (Vuelta/Velocidad/Ritmo) y comprobar que los chips de abajo **no se mueven**; elegir una vuelta lejana y ver que el selector la trae a la vista.
7. Con la tanda en marcha, cambiar de pestaña: **aviso oscuro con acento rojo** y la última card entera sobre él.
8. **Exportar copia (debug, a Descargas)** → ruta visible; **Restaurar última copia (debug)** → diálogo de confirmación (CANCELAR para no reiniciar).
9. **Aviso «hay más abajo»**: Ajustes (colapsa/expande Depuración), Detalle con muchas vueltas y Revisión con muchas filas: flecha mientras se pueda bajar; **IR AL FINAL (DEBUG)** y desaparece.
10. **Compensación de retardo GPS** (Depuración): +50/−50, topes ±500.
11. Girar a apaisado (Dashboard, Tandas con datos, diálogo de borrador interrumpido): nada cortado.
12. Tamaño de letra del sistema al máximo: ningún texto con «…» en las 6 pantallas principales.

## Lista para el móvil real (no comprobado)

- Que el delta en vuelta se **mueve de verdad** cada ~0,25 s en pista y no «tiembla» más de unas décimas; que coincide con la diferencia final al cerrar la vuelta.
- Retardo del GPS fusionado real: ¿cuántos ms? Probar la compensación en una recta con una línea marcada a ojo.
- Calidad de foto con papel real (umbrales de nitidez/brillo/reflejos) y las muestras reales de OCR (D1).
- Copia en segundo plano: dejar el móvil una semana (o forzar con `adb shell cmd jobscheduler run -f com.nacho.kartlog 7001`) y mirar `filesDir/backups`.
- Apaisado con el móvil en el volante: tarjetas de Tandas, aviso «Tanda en pista», diálogo de borrador.
- Vibración/pitido y pantalla encendida (heredado del Sprint 6).

## No comprobado en dispositivo

Todo lo anterior: el comportamiento del GPS real, de ML Kit, del selector de ficheros, de `JobScheduler`, de `MediaStore`/Descargas, de la orientación y del brillo; y que las imágenes de muestra estén donde las busca la compilación.

---

## Hashes por tarea

| Tarea | Commit |
|---|---|
| A1 diagnóstico de muestras, listado tolerante, selector | `648c560` |
| A2 + A3 diálogo apaisado / borrador sin vueltas | `4419d7b` |
| A3 (test estable) | `2d5bdc8`, `946d2c3` |
| A4 Tandas en apaisado | `61248c1` |
| A5 tarjeta Trazado | `27d6b6c` |
| A6 aviso «Tanda en pista» | `a1ddd7c` |
| A7 pantalla de importación | `84d027a` |
| A8 revisión compacta | `c311d30` |
| A9 columna sugerida | `3919826` |
| B1 TERMINAR (debug) | `97e028b` |
| B2 meta simulada | `9233776` |
| B3 muestra A como Nacho | `96411f8` |
| B4 copia a Descargas / restaurar | `f9548e9` |
| B5 resistencia simulada | `6ca7488` |
| B6 «hay más abajo» | `bdd75ec` |
| C1 delta en vuelta | `04eb3a1` |
| C2 error esperado | `f5e8089` |
| D1 guía de muestras | `5566d00` |
| D2 calidad de foto + dump OCR | `2903620` (+ `4f8bb8f` test estable) |
| D3 harness de volcados | `d66bbb9` |
| E1 retardo GPS | `8d39e9f` |
| E2 copia en segundo plano | `37e847f` |
| E3 cierre de Room | `37421f6` |
| E4 textos sin recortar | `5e96c88` |

## Push

Todo en `origin/claude/kartlog-sprint-1-3hhlkb` (un commit por tarea, `git push` tras cada uno). Código y tests cerrados en `b6cecaf` (informe) sobre `4f8bb8f`/`946d2c3`; tras este commit de cierre `git rev-parse HEAD == origin/claude/kartlog-sprint-1-3hhlkb` (comprobado con `git fetch`).
