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
Ajustes → Depuración → **«Probar OCR con muestra»**. Lee `app/src/debug/assets/muestras/*.png|jpg|jpeg|webp` (hoy solo hay un `LEEME.txt`: el responsable debe copiar allí `muestra_a.png` y `muestra_b.jpg`).
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
