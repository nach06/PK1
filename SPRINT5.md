# KartLog — Informe del Sprint 4.1 (fallos del emulador) + Sprint 5 (GPS fiable en pista + datos a salvo)

> El plan previo (análisis de `SPRINT4.md`) está en `SPRINT5_PLAN.md`.
> Rama `claude/kartlog-sprint-1-3hhlkb`. **Un commit por tarea y `git push` tras cada una** (tabla de hashes al final).

## Estado
- Verificación final: `assembleDebug assembleRelease testDebugUnitTest compileDebugAndroidTestKotlin lintDebug` → ver «Verificación» al final.
- **Tests unitarios: 234, 0 fallos.** Los instrumentados (`androidTest`) siguen **sin ejecutarse** (compilan; no hay emulador en este entorno).
- Mutación comprobada en la lógica nueva (se rompió a propósito y el test falló): FAB, renumeración, filtro de muestras, fecha de la tanda GPS, elipsis, ajuste/verificación de meta, borrador (flush), `MIGRATION_2_3`, versión de esquema de la copia y pendiente de la consistencia.

## PARTE A — Sprint 4.1 (fallos del emulador, Pixel 3a API 35)

**A1. FAB tapaba contenido en Tandas.** *Causa:* la FAB era un `floatingActionButton` flotante sobre la lista: siempre se solapa con las cards al hacer scroll, y el `contentPadding` solo protegía a la última. *Solución:* la acción principal vive ahora en una **franja reservada de 84 dp al pie** (la lista termina encima de ella); al desplegar la FAB se oscurece el fondo (scrim) y tocar fuera la cierra. Test por NavHost con 8 tandas: la lista nunca llega a la FAB, ningún texto visible de una card se solapa con ella y la última card queda ≥ 24 dp por encima (mutación: franja a 0 dp → falla).

**A2. Numeración con huecos (V8, V7, V5…).** *Causa raíz:* no era un filtro ni una consulta: el número de vuelta es un **valor guardado** y al borrar una vuelta intermedia quedaba un hueco; solo se recalculaba el siguiente número. *Solución:* `LapDao.deleteAndRenumber` borra y renumera V1…Vn en una transacción (el índice único `(sessionId, number)` obliga a pasar antes por números negativos). Tests: borrar intermedia/primera/última/varias seguidas, conservar el origen de cada vuelta, medias y **RÉCORD** recalculados al borrar la mejor, y un test por NavHost (borrar V6 de 8 → quedan V1–V7 y la mejor se recalcula).

**A3. Muestras `.jpeg`.** `SampleImages` acepta `.png/.jpg/.jpeg/.webp` sin distinguir mayúsculas (`isSampleImage`, con test); `LEEME.txt` y los documentos citan ahora `muestra_b.jpeg`.

**A4. Probar el importador sin teclear.** Ajustes → Depuración → «Importar muestra de texto A/B»: leen los **mismos ficheros** de `app/src/test/resources/muestras/` (se añaden como assets solo de debug, una única copia) y los entregan al buzón del flujo real de Importar. Tests: parseo de ambos textos y NavHost (A → «Nacho · Kart 28»; B → karts).

**A5. Depuración accesible.** Sección «Depuración» **plegable al principio de Ajustes** (arranca plegada; «Simular tanda en pista» y «Probar OCR» los primeros). Solo debug. Test por NavHost (es la primera tarjeta, plegada oculta los botones, desplegada los muestra).

**A6. Tipo de sesión GPS.** Selector Libre / Clasificación / Carrera / **Resistencia** (nuevo valor `ENDURANCE`, sin migración: se guarda como texto) en la preparación del Dashboard. La **fecha es la de EMPEZAR** (no la de guardar) y `durationMin` se calcula (EMPEZAR → parar). Test por NavHost: guardar 3 h después conserva fecha, tipo y 47 min (mutación comprobada).

**A7. Ficheros huérfanos.** `TrackStore.deleteAll()`; `wipeAll` de depuración borra también `tracks/`. Borrar una tanda ya borraba su trazado; ahora hay test por NavHost (borra la suya y conserva la de otra tanda). Borrar un circuito solo es posible sin tandas, así que no deja trazados.

**A8. Elipsis del circuito.** Verificada de verdad: el test usa **Robolectric en modo gráfico nativo** (métricas de texto reales) con un nombre de 90 caracteres: una línea y `isLineEllipsized` (mutación: quitando `maxLines/overflow` falla). Esto también desbloquea otros tests de texto que antes eran imposibles.

## PARTE B — Sprint 5

**B1. Meta más robusta** (`FinishAdjust`, `FinishVerification`, `CalibrationViewModel`, pantalla de meta).
- **Ajuste manual:** desplazar la puerta −10…+10 m en pasos de 1 m y ancho 10–40 m (pasos de 5), con el Canvas actualizado en vivo; se puede ajustar una meta ya guardada («Ajustar meta») o la recién marcada. Se persiste en `Circuit` (misma tabla, sin columnas nuevas).
- **Verificar meta** (tras guardar y desde la pantalla): el usuario da una vuelta y pulsa «AQUÍ» al cruzar la línea; el detector (con la meta guardada) localiza el cruce y se muestran: diferencia (tú − cruce detectado) en tiempo interpolado, rumbo de paso y su diferencia con el guardado, y separación del centro. Si pasa de **1 s o 20°** avisa y propone ajustar/repetir; si no se detecta cruce, lo dice.
- **Sentido inverso:** al volver a marcar, si el rumbo nuevo difiere > 90° del guardado se avisa (la meta se sustituye; sin columnas nuevas).
- Tests: dominio (desplazamiento exacto en metros, topes, tiempos de vuelta idénticos con la puerta movida, verificación buena/mal puesta/sin cruce), VM (topes, persistencia, cancelar, sentido inverso, verificación) y NavHost completo (ajustar +3 m y 25 m → guardar → verificar).

**B2. Dashboard: señal y robustez.**
- **Salud GPS** en 3 niveles (`GpsHealth`: bueno ≤ 8 m, regular ≤ 15 m, malo) con punto de color y los Hz medidos en los últimos 5 s.
- **Vueltas poco fiables:** cada vuelta recuerda si alguno de sus dos cruces tuvo un hueco de fixes > 2,5 s; el resumen lista esas vueltas con **Mantener/Descartar**, las cifras se recalculan y las descartadas no se guardan (test por NavHost: 6 vueltas, V3 descartada → 5 guardadas renumeradas).
- **Borrador cada 30 s** (`FileDraftStore`): las posiciones se **añaden** a `tracks/draft.partial.csv` y la preparación va en `draft.partial.json` (y, en resistencia, las paradas pedidas en `draft.partial.pits`). Las vueltas no se guardan: se **recalculan** al recuperar pasando las posiciones por el detector (determinista, mismas vueltas al milisegundo). Al abrir la app, si hay borrador: «Tanda interrumpida» → **Recuperar** (abre el resumen para guardar) / **Descartar**. Un borrador de un circuito borrado se tira sin avisar. *Desvío del encargo:* el nombre es `draft.partial.*` y no `{sessionId}.partial.csv` porque **la sesión aún no existe** (se crea al guardar).
- Tests: borrador (ida y vuelta, línea cortada por el crash, corrupto, orden begin→append→clear, volcado periódico y final al parar), restauración, huecos y los tres casos por NavHost (recuperar y guardar con su fecha original, descartar, sin borrador/circuito borrado).

**B3. GPS vs oficial.** Hecho: **sesgo sistemático** (≥ 3 vueltas, |media| > 50 ms y ≥ 80 % de las diferencias con el mismo signo) en el informe, y **«Precisión GPS ±0,xxx s (N tandas)»** en la card del circuito, calculada al vuelo (sin migración). *No hecho a propósito:* el botón «Desplazar meta X m / Aplicar al circuito». **Un desplazamiento de la puerta mueve igual la salida y la llegada de cada vuelta**, así que no cambia ningún tiempo de vuelta y no puede corregir un sesgo (solo lo haría si la velocidad de paso variase mucho entre vueltas, y entonces de forma aleatoria). Implementarlo habría dado una cifra falsa de precisión; el informe explica en pantalla de qué suele venir el sesgo (retardo del GPS / cómo cuenta la vuelta el cronometraje oficial). Si el sesgo real en pista es grande, la vía correcta es compensar el retardo del proveedor, no la meta: lo valoraremos con datos reales.

**B4. Resistencia.** Room **v4** (`MIGRATION_3_4` explícita; `stints` + `laps.stintId` nulable; test 3→4 y cadena 1→4, y el instrumentado compila). En el Dashboard (tipo Resistencia): botón **«Pit / cambio»** → diálogo de dos toques (piloto opcional: nombres ya usados o «Escribir nombre»); la vuelta en curso se descarta y el siguiente cruce de meta cierra el **tiempo en boxes** (del último cruce antes al primero después, como se pidió) y abre el stint siguiente. El nº de vuelta ignora la parada. Se guardan los stints (rango de vueltas, piloto, parada que cierra cada uno), que se **recalculan al borrar vueltas**. Detalle: tarjeta plegable «Stints · N» con vueltas, mejor, media limpia y parada por stint, y el **tiempo total en boxes**. Las paradas también sobreviven a la recuperación de borrador. *Sin live timing de rivales.*

**B5. Copia de seguridad.** Ajustes → «Copia de seguridad»: **Exportar** (zip con `manifest.json`, la BD con el WAL volcado y `tracks/`; sin borradores) y **Restaurar** desde el selector de archivos con confirmación («se SUSTITUIRÁN todos los datos…», con fecha, trazados y esquema de la copia). Valida antes de tocar nada (zip legible, manifiesto, BD que abre y cuya versión coincide, nombres sin rutas peligrosas); **rechaza** copias dañadas/incompletas y de **esquema más nuevo** con mensajes distintos, y no cambia nada si falla. Tras restaurar la app se **reinicia** (la BD se sustituye en disco). El diálogo avisa de que el zip **incluye datos de rivales** (no es el CSV público). Tests: exportar → borrar todo → restaurar → **mismos conteos en todas las tablas** y trazados, ocho tipos de copia mala, VM y diálogo.

**B6. Análisis.** Sin pantallas nuevas: **«Hueco de mejora»** (media limpia − récord) y **«Consistencia por tanda»** con barras (últimas 10) y **tendencia** por mínimos cuadrados (mejorando / estable / empeorando, umbral 10 ms por tanda, ≥ 3 tandas). *Nota sobre «mejor teórico»:* sin sectores no existe un mejor teórico real; se muestra el hueco entre media y récord y así se dice en la pantalla.

## Decisiones tomadas por mi cuenta (a validar)
1. FAB: franja reservada de 84 dp al pie en vez de dejarla flotando (cuesta altura de lista, pero nunca tapa una card).
2. Cualquier tanda GPS puede ser «Resistencia»; sin pit ni stints se comporta como una normal (con tipo Resistencia siempre se crea al menos un stint).
3. «Pit / cambio»: el campo de texto no está visible por defecto (dos toques con guantes y sin teclado); además un `TextField` enfocado en el diálogo impedía que los tests de Compose llegaran a reposo.
4. Verificación de meta: se compara el **cruce detectado** con el momento en que el usuario pulsa AQUÍ (no con una segunda calibración); umbral 1 s / 20°.
5. Borrador: se rehacen las vueltas desde las posiciones en vez de guardarlas (menos estado en disco, mismos resultados).
6. Restaurar acepta copias de esquema **igual o anterior** (Room migra al abrir) y rechaza las más nuevas.
7. La copia no incluye preferencias (nombre de piloto, aviso al cruzar la meta).
8. B3 sin «desplazar meta» (ver arriba).

## Riesgos y NO comprobado en dispositivo
- **Todo lo del GPS real sigue sin probarse en un móvil:** `FusedLocationSource`, servicio en primer plano, notificación, permisos, vibración/pitido, consumo. Tampoco el OCR (A2 del Sprint 4 sigue pendiente de las fotos).
- **Recuperar tanda interrumpida:** probado simulando la muerte del proceso (ficheros en disco + app nueva), no matando la app de verdad; el reparto de volcados cada 30 s se prueba con reloj inyectado.
- **Restaurar copia:** el reinicio real (`killProcess` + relanzar) no se puede probar aquí (los tests inyectan `restartApp`); comprobarlo en el móvil: tras restaurar la app debe volver a abrirse sola con los datos de la copia.
- **Selector de archivos** de «Restaurar copia» (`OpenDocument`): solo comprobado el flujo posterior a elegir el fichero.
- Los 3 tests intermitentes por consultas Room huérfanas siguen mitigados con `Thread.sleep(300)` (deuda D5 del plan).
- Pit: si el pit lane no pasa por la línea de meta, «el primer cruce después» puede tardar una vuelta entera; el tiempo en boxes incluiría la vuelta de salida.
- Verificar la meta con el `Fused` real puede dar `NoCrossing` si el proveedor entrega < 1 Hz: se ve el aviso y se repite.
- Room v4: con la migración 3→4 hay que mirar en el móvil que una BD v3 real (la de la v. instalada) abre sin pérdida.

## Lista de comprobación visual para el responsable
1. **Tandas** (≥ 3 cards): la FAB está en una franja propia, no tapa «Media limpia» de ninguna card; al desplegarla el fondo se oscurece y tocar fuera la cierra.
2. **Detalle:** borrar una vuelta intermedia deja la lista V1…Vn sin huecos y recalcula MEJOR / MEDIA LIMPIA.
3. **Ajustes → Depuración** (arriba, plegada): «Simular tanda en pista», «Probar OCR con muestra» (con `muestra_a.png`/`muestra_b.jpeg` en `app/src/debug/assets/muestras/`), «Importar muestra de texto A/B».
4. **Nueva tanda** con un circuito de nombre muy largo: el nombre se corta con «…» en una línea.
5. **EN PISTA → preparación:** fichas Libre / Clasificación / Carrera / Resistencia. Tras guardar, la tanda aparece con su fecha de salida.
6. **Circuitos → ⋮ → Marcar meta:** AJUSTAR META (± metros y ancho, el dibujo se mueve), VERIFICAR META (pulsar AQUÍ al cruzar: debe salir «Meta verificada»), y el aviso de sentido contrario si marcas al revés.
7. **Dashboard en marcha:** punto de color y texto «GPS BUENO/REGULAR/MALO · ±x m · y Hz»; en Resistencia, «Stint N» y «PIT / CAMBIO» (al usarlo: «EN BOXES…» hasta cruzar la meta).
8. **Resumen:** si hubo huecos de señal, las vueltas poco fiables salen con MANTENER/DESCARTAR.
9. **Matar la app a mitad de una tanda** (deslizarla en recientes) y reabrirla: «Tanda interrumpida» → Recuperar muestra el resumen con las vueltas; Descartar la borra.
10. **Detalle de una resistencia:** «Stints · N ▸» al final con tiempo total en boxes; desplegado, cada stint con vueltas, mejor y media limpia.
11. **Detalle con GPS + oficial:** «Precisión del GPS vs oficial ±0,xxx s» (con coma) y, si procede, el aviso de sesgo sistemático; **Circuitos:** «Precisión GPS ±0,xxx s (N tandas)».
12. **Ajustes → Copia de seguridad:** Exportar (aparece el menú de compartir con el .zip), Restaurar (elige el zip, lee el aviso, confirma: la app se reinicia sola con los datos de la copia).
13. **Análisis:** «Hueco de mejora» y «Consistencia por tanda» con su tendencia.

## Commits (todos publicados con `git push`)
| Tarea | Hash |
|---|---|
| A1 FAB en franja reservada | `6d89e05` |
| A2 renumeración al borrar | `21f00a7` |
| A3 muestras .jpeg | `9bb4de7` |
| A4 muestras de texto | `f92eb93` |
| A5 Depuración plegable | `9d26faa` |
| A6 tipo y fecha GPS | `b6d41b6` |
| A7 trazados huérfanos | `6eedca6` |
| A8 elipsis | `7316ccb` |
| B1 meta robusta | `c08f0d7` |
| B2 señal, vueltas dudosas y borrador | `791a0c9` |
| B3 sesgo y precisión por circuito | `d728f27` |
| B4 resistencia (WIP + final) | `cf8a3db`, `e83413e` |
| B5 copia de seguridad | `db1a62a` |
| B6 análisis | `00a6d72` |

## Verificación final

`./gradlew assembleDebug assembleRelease testDebugUnitTest compileDebugAndroidTestKotlin lintDebug` → **BUILD SUCCESSFUL** (EXIT 0).
Tests unitarios/Robolectric: **234 ejecutados, 0 fallos, 0 omitidos**. Lint sin errores.
Los tests instrumentados (migraciones) solo se compilan: no hay emulador en este entorno.
