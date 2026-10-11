# KartLog — Sprint 3: Importar tiempos (+ cierre del 2.2)

## Estado
S3-0 … S3-5 implementados. Un commit por tarea (`git log --oneline`). Comprobado en este entorno: `assembleDebug assembleRelease testDebugUnitTest compileDebugAndroidTestKotlin lintDebug`.
**No comprobado en dispositivo:** el OCR real (ML Kit), la cámara, la galería y el `Compartir` desde otras apps. Aquí no hay emulador (sin KVM); todo eso está cubierto
por tests de lógica pura y de Compose/Robolectric, no por una foto real.

## Aviso sobre las muestras
Las imágenes de las muestras A y B **no llegaron** a la sesión. Los *fixtures* de `app/src/test/resources/muestras/` se reconstruyeron a partir de los valores de control
del encargo (Nacho: kart 28, 5.º, mejor `01:19.074` en V5, 6 vueltas, media `01:21.157`; Deyvis: kart 26, 1.º, `01:16.515`, etc.); **los demás pilotos son inventados** pero coherentes
(mejor = mínimo, media truncada, nº de vueltas). Conviene sustituirlos/añadir los textos reales cuando se puedan transcribir.

## T1 — Auditoría fix → composable → ruta (Sprint 3)
| Pieza | Composable / clase | Ruta hasta ella |
|---|---|---|
| Importar tiempos | `ImportScreen` + `ImportViewModel` | Tandas → ⋮ «Importar tiempos» (o botón secundario del estado vacío) → `sessions/import` |
| Entrada compartida | `MainActivity` (`ACTION_SEND`) → `SharedIntents` → `ImportInbox` → `KartNavHost` (navega a `sessions/import`) | «Compartir → KartLog» desde otra app |
| Escanear (cámara/galería) | `ImportScreen` (`TakePicture` + FileProvider `photos/`, `PickVisualMedia`) → `MlKitImageTextRecognizer` → `OcrLayout` | Importar tiempos → ESCANEAR |
| Mi nombre de piloto | `SettingsScreen` (campo) → `UserSettings` (SharedPreferences `kartlog_settings`) | Ajustes → «Mi perfil» |
| Icono de vuelta importada | `LapRow` | Detalle de una tanda con vueltas `IMPORTED` |
| Clasificación de la sesión | `ClassificationCard` ← `SessionDetailViewModel.participants` | Detalle de una tanda importada |

`ImportFlowTest` recorre esa ruta desde el `NavHost` real (compartir texto → elegir «Nacho» → vista previa → guardar → Detalle con icono y tarjeta de clasificación).

## S3-0 — Base de datos v2
`sessions` gana `label`, `durationMin`, `importedRaw`; tablas nuevas `participants` y `participant_laps` (única `(participantId, number)`). `MIGRATION_1_2` es explícita (SQL copiado del
`2.json` exportado); no hay `fallbackToDestructiveMigration`. `MigrationTest` (Robolectric + `MigrationTestHelper`) migra una BD v1 con datos y verifica que se conservan;
`MigrationInstrumentedTest` (androidTest) hace lo mismo en dispositivo. **Mutación:** quitar una columna de la migración hace fallar el test.
`ImportDao.importSession` es una transacción: crea la tanda (o añade a una existente), inserta las vueltas propias como `IMPORTED`, los participantes y sus vueltas, y `importedRaw`.

## S3-1 / S3-2 — Parser y validación cruzada (`domain/importing`)
Pipeline puro, sin Android: texto → celdas (`PreparedText`) → filas/bloques (`GenericMatrixProfile`, extensible con `LapTextProfile`) → pilotos; la tabla resumen
(Pos/Piloto/Kart/V./Mejor/GAP) se consume primero y los metadatos (fecha, duración, título, tipo, circuito conocido) solo se buscan en la cabecera.
Formatos de tiempo: `1:12.467`, `01:12.467`, `1:12,467`, `72.467`, `72,467`, `1'12"467`; rango válido 15 s – 5 min (fuera de rango → aviso, no vuelta).
Validación (`ImportValidation`): mejor == mínimo, nº de vueltas == `V.`, `|media − floor(media real)| ≤ 1 ms`. Si algo no cuadra se propone un único dígito (en ámbar, **nunca se aplica solo**).
Las correcciones OCR (`O→0`, `l→1`, `S→5`) solo ocurren dentro de tokens con forma de tiempo y marcan la vuelta como «corregida por OCR».
Tests: `LapTextParserTest` (25), `ImportValidationTest` (12).

## S3-3 — Pantalla «Importar tiempos»
Entradas: pegar, escanear, compartir. «Yo» = columna cuyo nombre contiene (sin mayúsculas/acentos) «Mi nombre de piloto» de Ajustes; si no hay coincidencia única se pregunta.
Vista previa editable con teclado propio, filas descartables, celdas ⚠ en ámbar, tres indicadores (mejor, nº de vueltas, media) y sección plegable «Otros pilotos».
Destino: tanda nueva (precargada con fecha, circuito, tipo, duración y título del documento) o tanda existente (aviso si hay vueltas con tiempo idéntico).
**No se guarda nada sin pasar por la vista previa.** Se guardan: vueltas propias `IMPORTED`, `Participant` + `ParticipantLap` de los demás, y el texto original en `importedRaw`.
La foto **no se guarda**: se lee desde un fichero de caché temporal solo para el OCR.
`ImportViewModelTest` (11) cubre la lógica; `ImportEmptyPreview` e `ImportPreviewWithSuspectRowPreview` son los `@Preview` (este último con una fila en ámbar).

## S3-4 — Compartir
`MainActivity` es `singleTop` y declara `ACTION_SEND` para `text/plain` e `image/*`. `SharedIntents.toContent` lo traduce (test) y lo deja en `ImportInbox`; `KartNavHost` abre la
pantalla de importación y el `ImportViewModel` lo recoge una única vez. No se vuelve a procesar al rotar (`savedInstanceState == null`).

## S3-5 — OCR (ML Kit, modelo latino incluido: `com.google.mlkit:text-recognition:16.0.1`)
`MlKitImageTextRecognizer` convierte cada *elemento* (palabra) de ML Kit en un `OcrBox` y delega en `OcrLayout.reconstruct`:
1. **Inclinación:** ángulo mediano de las esquinas de las cajas; se giran los centros para anularlo (mediana: tolera cajas con ángulo disparatado).
2. **Filas:** por Y, con tolerancia 0,6 × la altura mediana de línea.
3. **Columnas:** ancladas por X en torno a los tiempos; una celda perdida queda vacía en vez de desplazar el resto; las palabras contiguas se unen («Kart 26»).
4. Se emite texto con tabuladores que consume el parser igual que un texto pegado.
`OcrLayoutTest` (6) usa cajas sintéticas con orden barajado, inclinación de 3° y una celda perdida. **Mutación:** desactivar la corrección de inclinación hace fallar el test.
Si el OCR falla o no devuelve texto, la pantalla muestra «No se pudo leer la imagen…» y el usuario puede pegar el texto.

## Decisiones tomadas por mi cuenta (a validar)
- Al importar en una tanda **existente** se **sustituye** la clasificación previa de esa tanda (no se acumulan participantes de importaciones distintas).
- El **GAP** de la tarjeta se calcula en el Detalle como diferencia de mejor vuelta frente al líder (el de menor `bestMs`), no se guarda.
- La tarjeta «Clasificación de la sesión» arranca **plegada** y es el primer elemento de la lista del Detalle; tu fila va resaltada con el color primario y «· Tú».
- «Mi nombre de piloto» se guarda al teclear (sin botón); vacío = sin preselección.
- El icono de vuelta importada es `FileDownload` en gris (texto atenuado) para no competir con el ámbar de avisos.
- ML Kit 16.0.1 es la última versión estable publicada en Google Maven; el modelo va incluido en el APK (más peso, pero funciona sin conexión; tamaño exacto por medir).

## Riesgos y puntos a vigilar
- **OCR sin validar con una foto real** (la muestra B nunca se vio). Si una columna se desplaza, la validación cruzada lo delata (⚠) pero la reconstrucción por geometría puede necesitar ajustar `ROW_TOLERANCE` o el umbral de anclaje de columnas.
- Los *fixtures* de otros pilotos son sintéticos; formatos reales distintos (otros cronometrajes) requerirán un nuevo `LapTextProfile`.
- Los permisos de lectura de un `Uri` compartido son temporales: el OCR arranca nada más llegar el contenido; si la app se mata antes, habrá que volver a compartir.
- `importedRaw` guarda el texto completo (puede incluir nombres de otros pilotos); es solo local, pero conviene tenerlo presente si algún día se exporta.

## Cómo ejecutar los tests
```bash
export ANDROID_HOME=$HOME/android-sdk
./gradlew testDebugUnitTest                 # unitarios + Compose/Robolectric (ROBOLECTRIC_DEPS_DIR si se trabaja sin red)
./gradlew testDebugUnitTest --tests '*ImportFlowTest' --tests '*LapTextParserTest'
./gradlew compileDebugAndroidTestKotlin     # compila los instrumentados (aquí no hay emulador)
./gradlew :app:connectedDebugAndroidTest    # con emulador/dispositivo: KartLogDaoTest y MigrationInstrumentedTest
```

## Comprobación visual para el responsable
1. Ajustes: última línea con el **hash de build** del último commit; campo «Mi nombre de piloto».
2. Tandas → ⋮ → «Importar tiempos» (y, con la lista vacía, el botón secundario).
3. Pegar la muestra A: aparecen los avisos si los hay, el selector «¿Cuál eres tú?» (o ya elegida si el nombre coincide), la vista previa con tres indicadores ✓/⚠ y «Otros pilotos (13)» plegado.
4. Editar una vuelta con el teclado propio; descartar y recuperar una fila.
5. Guardar → Detalle: icono de importada en cada vuelta y «Clasificación de la sesión» (tu fila resaltada, GAP respecto al líder).
6. Compartir un texto desde otra app → KartLog abre Importar con el texto ya analizado.
7. ESCANEAR → Hacer foto / Elegir de la galería con una hoja de tiempos real **(pendiente de validar en dispositivo)**.
