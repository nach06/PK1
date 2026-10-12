# KartLog — Sprint 8: pulido visible, preparación para pruebas en pista y análisis de rivales/karts

Rama `claude/kartlog-sprint-1-3hhlkb`. Parte del Sprint 7 (390 tests). **Cambio de esquema Room: v4 → v5** (una columna nullable
`sessions.signalScore`, migración explícita `MIGRATION_4_5`, sin `fallbackToDestructiveMigration`; esquema exportado en
`app/schemas/.../5.json`). Todo lo demás vive en ficheros (CSV del trazado v2) y en `SharedPreferences` (alias de pilotos unidos,
`driver_aliases`).

## Estado y verificación

__ESTADO__

> **Nada de esto se ha probado en un dispositivo real ni en un emulador.** Lo verificado son tests JVM (Robolectric, con modo gráfico
> nativo cuando importa el texto). Lo que no se puede comprobar así está en «No comprobado en dispositivo».

---

## Parte A — Pulido visible (causa raíz)

| # | Causa raíz | Qué cambia | Test (y mutación) |
|---|---|---|---|
| **A1** botones de Depuración con palabras partidas a 393 dp | Los botones de la rejilla usaban el relleno por defecto de 24 dp y etiquetas largas: a 393 dp el ancho útil (~150 dp) obligaba a partir palabras. | `DebugGridButton` con poco relleno y etiquetas cortas. | `DebugPanelWordsTest`: ninguna palabra partida en ningún botón (recorre el árbol sin fusionar). Mutación (relleno original) → falla. |
| **A2** controles cruzados de la Revisión < 12 sp | Estaban a 10–11 sp para ganar alto. | Fichas por contenido en `FlowRow` (dos filas con letra grande), texto ≥ 12 sp. | `ReviewDensityTest` (tamaño de letra y filas visibles). |
| **A3** clasificación ilegible: tu fila en rojo sobre rojo y nombres cortados | Color de fila con contraste < 3:1 y nombres a 1 línea con elipsis. | Tu fila en texto claro sobre rojo oscuro (contraste ≥ 4,5:1, calculado en el test), kart como `K28` bajo la posición, nombres que pasan a 2–3 líneas. | `ClassificationReadabilityTest` (contraste real WCAG + sin elipsis). |
| **A4** Detalle con trazado a 736 dp: < 3 vueltas con la barra de teclado | Cabecera, tarjeta y barra de teclado sumaban demasiado alto. | Barra plegada sin tiempo en blanco, cabecera y tarjeta más bajas, lienzo según la forma del trazado (óvalo vs. cuadrado). | `TrackCardLayoutTest` (393×808 con teclado: ≥ 3 vueltas enteras). **Mutación de la altura del panel no detectada** (ver «Riesgos»). |
| **A5** modo día activado por defecto y «Terminar (debug)» tapando el cronómetro | El modo día se guardaba al abrir la preparación sin que el usuario lo tocara. | Apagado por defecto, explicado en la preparación y solo se guarda si lo cambias. **«Terminar (debug)» pasa a la fila de la barra de delta** (no arriba a la izquierda: reservar una franja superior o ponerlo en el pie rompía el alto mínimo del cronómetro de Sprint 6, 127 dp, y el texto de batería; desviación documentada). | `DashboardDefaultsTest`. |
| **A6** dos reglas de redondeo y «Media oficial» alarmante por 1 ms | La app redondeaba y la hoja trunca. | Una regla (la app redondea al ms; la hoja trunca) y nota «oficial» sin alarma si difiere 1 ms. | `ClassificationReadabilityTest`, `AnalyticsTest`. |
| **A7** Marcar meta: AJUSTAR/VERIFICAR/GUARDAR fuera de la pantalla a 360×640 | Título, GPS e instrucciones ocupaban ~40 % del alto. | Datos de la meta en dos líneas, ajuste en una fila compacta y sin título/GPS/instrucciones con la meta pendiente. | `CalibrationReachabilityTest` (393×808 y 360×640, sin desplazar). |
| **A8** resumen de resistencia sin stints | El resumen solo mostraba totales. | Desglose por stint (piloto, vueltas, mejor), tiempo en boxes de cada parada y total. | `EnduranceFlowTest`. |

## Parte B — Preparación para pruebas en pista

| # | Qué hay | Test |
|---|---|---|
| **B1** registro bruto del GPS | CSV del trazado **v2** con rumbo, proveedor y marca de cruce; lee los v1 antiguos. | `RawGpsLogTest` |
| **B2** exportar GPX / CSV | Detalle → ⋮ → «Exportar GPX (trazado)» (una pista por vuelta, tiempo en `<name>`) y «Exportar CSV del GPS». Sin datos de rivales. | `TrackExportTest`, `TrackExportFlowTest` |
| **B3** calibración en pista | Ajustes → Calibración (AQUÍ por paso, tabla GPS − manual por vuelta, sesgo medio, «Aplicar compensación (n ms)»). Atajo: «Calibración simulada (debug)». | `FieldCalibrationTest`, `FieldCalibrationFlowTest` |
| **B4** calidad de señal | Al guardar una tanda GPS se calcula % de fixes con ±8 m, frecuencia, huecos y una puntuación (buena/regular/mala), que se guarda en `signalScore` (Room v5) y se ve en la card y el Detalle. | `SignalQualityTest`, `SignalQualityFlowTest`, `MigrationTest` (4→5 y cadena 1→5) |
| **B5** «Antes de salir» | Ajustes → «Antes de salir»: batería, ubicación, permiso, brillo, ahorro de batería y soporte en verde/ámbar con atajo a los ajustes del sistema. | `PreRideChecksTest`, `PreRideFlowTest` |
| **B6** «Ensayo en casa» | Detalle → ⋮ → «Ensayo en casa (reproducir)»: reproduce una tanda GPS guardada por el Dashboard como si fuera en vivo; **no se guarda**. | `ReplayFlowTest` |

## Parte C — Rivales y karts

Todo el análisis es **local** (dominio puro `RivalAnalytics`): nada de esto sale en un export público.

| # | Qué hay | Test (y mutación) |
|---|---|---|
| **C1** pantalla Rivales | Análisis → botón «Rivales y condiciones». Lista de pilotos (agrupados por nombre sin tildes/signos), nº de tandas juntos, su mejor vuelta y tu ritmo medio frente a él. | `RivalAnalyticsTest`, `RivalsFlowTest`. Mutaciones: alias ignorados → falla; empate como victoria → falla. |
| **C2** cara a cara | Tocar un rival: mejor vuelta y media limpia ganadas/perdidas, mejor racha ganando por mejor vuelta y barras de la diferencia de media por tanda (izquierda = vas más rápido). | `RivalAnalyticsTest.bestStreak…`, `RivalsFlowTest.rivalsList…` |
| **C3** ranking de karts | El ranking por circuito ya existía (`KartStats`); se añade el aviso «Pocas muestras: menos de 3 tandas con este kart» bajo cada kart con < 3 tandas. | `RivalsFlowTest.kartRankingWarns…`. Mutación (umbral 2) → falla. |
| **C4** efecto de las condiciones | Correlación de Pearson entre temperatura de pista/ambiente/lastre y tu ritmo relativo al circuito, con dispersión y la advertencia «correlación, no causa». **Solo con ≥ 8 tandas con media limpia**; si no, el mensaje «Hacen falta al menos 8…». | `RivalAnalyticsTest.conditions…`, `RivalsFlowTest.conditions…`. Mutación (umbral 7) → falla. |
| **C5** privacidad | Test con el seed (que ahora trae rivales): el CSV de todas las tandas no contiene ningún nombre de rival, y la tarjeta compartible solo admite los campos conocidos (añadir uno obliga a revisar el test). «Borrar datos de rivales» vacía la pantalla Rivales **y olvida los alias unidos** (son nombres de rivales). | `RivalPrivacyTest`. Mutación (no borrar alias) → falla. |
| **C6** «Unir pilotos» | En Rivales: «Unir pilotos» → tocar dos filas → diálogo de confirmación → desde entonces cuentan como uno. Se guarda un mapa de alias (`SharedPreferences`), es transitivo e idempotente y se deshace con «Deshacer uniones». | `RivalAnalyticsTest.aliases…`, `RivalsFlowTest.mergingTwoSpellings…` |

## Parte D — Robustez y calidad

| # | Qué hay | Test |
|---|---|---|
| **D1** editar tanda | Detalle → ⋮ → «Editar tanda» (formulario relleno). Actualiza circuito, fecha, tipo, kart, lastre, temperaturas, estado y notas; **no** toca vueltas, título importado, duración, texto original ni calidad GPS. | `EditSessionFlowTest`. Mutación (crear en vez de actualizar) → falla. |
| **D2** deshacer | Borrar una vuelta o una tanda muestra «Deshacer» (**8 s**). El elemento se oculta al instante (y la lista de vueltas se renumera igual que el borrado real) y se borra de verdad al cerrarse la ventana. Solo hay un borrado deshacible a la vez (uno nuevo confirma el anterior). Si la app muere durante la ventana el borrado no se ejecuta (falla hacia conservar). | `PendingDeletionsTest` (reloj virtual), `DeleteUndoFlowTest`. Mutaciones (ventana /10, filtro de ocultas desactivado) → fallan. |
| **D3** buscar | Tandas → ⋮ → «Buscar»: circuito, notas, título importado, kart y tipo; sin tildes ni mayúsculas; todas las palabras deben aparecer; se combina con los filtros. | `SessionSearchTest`, `SessionSearchFlowTest`. Mutación (`any` en vez de `all`) → falla. |
| **D4** migraciones reales | Bases de datos reales de v1, v2 y v3 en `src/test/resources/migration/` abiertas con el **constructor de producción**; test instrumentado de la cadena completa. Comando en dispositivo/emulador: `./gradlew :app:connectedDebugAndroidTest --tests "com.nacho.kartlog.data.MigrationInstrumentedTest"` (**no ejecutado aquí**: no hay emulador). | `MigrationSampleDbTest`. Mutación (quitar `MIGRATION_2_3`) → falla. |
| **D5** textos sin recortar | `TextNotClippedTest` ampliado a Detalle con trazado, Rivales, Rivales cara a cara, Calibración y Marcar meta; **y ahora mide el árbol sin fusionar** (el texto de dentro de cards clicables no se medía). | Mutación (elipsis en Rivales) → falla. |
| **D6** test intermitente | **Causa raíz encontrada** (ver abajo). | `DraftRecoveryFlowTest` |

### D6 — causa del `DraftRecoveryFlowTest.draftWithoutLaps_…` intermitente

El aviso «Tanda interrumpida sin vueltas: descartada» es un `Snackbar` de duración `Long` (10 s). Su temporizador corre en el **reloj
virtual de Compose**, que `waitUntil` adelanta a ~16 ms por cada 10 ms reales (≈ 1,6× más rápido). Con la máquina cargada, el
aviso aparecía y se retiraba entre dos comprobaciones y el test no lo veía. **Reproducido** saturando la CPU (8 procesos ocupados):
1 fallo en 6 pasadas, con el diagnóstico mostrando el borrador ya borrado, fase `IDLE` y el aviso ausente de la pantalla (no era un fallo
de la función). **Arreglo:** el test congela el reloj (`mainClock.autoAdvance = false`) y avanza fotograma a fotograma comprobando tras cada
uno, así el aviso no puede pasar sin ser observado. Resultado: __D6RESULT__

---

## Decisiones a validar

1. «Terminar (debug)» está en la fila de la barra de delta y no arriba a la izquierda (A5): reservar la esquina quitaba alto al cronómetro.
2. «Herramientas» de Ajustes (Antes de salir / Calibración) son **una fila de dos botones**: una tarjeta con título empujaba los botones de
   datos fuera de la vista a 360×720 (lo detectó `NavHostFixesTest`).
3. Rivales se agrupan por **nombre normalizado** (sin tildes, mayúsculas ni signos): «Carlos Ruiz» y «C. Ruiz» son pilotos distintos hasta
   que los unes a mano (C6). No hay unión automática por parecido para no mezclar a dos personas.
4. Deshacer = **ocultar y borrar a los 8 s** (no «borrar y restaurar»): evita reconstruir tanda, vueltas, stints, rivales, trazado y foto. El
   coste: durante 8 s los datos siguen en disco y, si la app muere en ese intervalo, **no se borran**.
5. El efecto de las condiciones es una correlación simple con un mínimo de 8 tandas; no se ajusta por kart ni por rivales.
6. La búsqueda de texto no se recuerda al reabrir la app (solo circuito, tipo y orden se recuerdan).

## Riesgos

- **A4**: la mutación de la altura del panel de teclado no hizo fallar `TrackCardLayoutTest` (el test comprueba ≥ 3 vueltas, no la altura del
  panel); se reforzó el margen pero la mutación concreta sigue sin detectarse.
- **Commits agrupados**: C1, C2, C3, C4 y C6 comparten ViewModel, pantalla y tests y se subieron en **un solo commit** (`9c9693e`); la
  petición era un commit por tarea.
- **Código de depuración en release**: el seed (con rivales) vive en `src/debug`; los atajos de `main` son inalcanzables en release
  (`debugTools == null`).
- **Deshacer** (D2): mientras dura la ventana los listados no muestran el elemento pero la BD aún lo tiene (backup/exportación manual en ese
  intervalo lo incluirían).
- **Migración v5** probada en JVM con esquemas exportados y bases de datos de ejemplo; el test instrumentado solo se compila aquí.

---

## Lista visual para el emulador (solo clics)

Ajustes → **Depuración ▸** → «Datos de prueba» (carga 14 tandas **con rivales**) y «Tanda GPS de ejemplo» (óvalo con trazado) te dan
todo el material; el resto son clics.

| # | Dónde / qué pulsar | Qué comprobar | Atajo debug |
|---|---|---|---|
| A1 | Ajustes → Depuración ▸ | Ningún botón con palabras partidas (393 dp) | — |
| A2/A3 | «Muestra A como Nacho» → revisión; Detalle de una tanda importada | Fichas ≥ 12 sp; tu fila legible sobre rojo oscuro | «Muestra A como Nacho (kart 28)» |
| A4 | Tandas → tanda GPS de ejemplo → barra de teclado | ≥ 3 vueltas visibles | «Tanda GPS de ejemplo» |
| A5 | «Simular tanda» → Dashboard | Modo día apagado; «Terminar (debug)» en la fila de la barra de delta | «Terminar (debug)» |
| A7 | Circuitos → ⋮ → Marcar meta | AJUSTAR/VERIFICAR/GUARDAR sin desplazar | «Marcar meta (simulada)» |
| A8 | «Simular resistencia» → Terminar | Resumen con stints y boxes | «Simular resistencia (2 pit)» |
| B2 | Detalle de la tanda GPS → ⋮ → Exportar GPX / CSV del GPS | Hoja de compartir con el fichero | — |
| B3 | Ajustes → Calibración | Informe GPS − manual, sesgo y «Aplicar compensación» | «Calibración simulada (debug)» |
| B4 | Tandas / Detalle de la tanda GPS | Línea de calidad de señal | «Tanda GPS de ejemplo» |
| B5 | Ajustes → Antes de salir | Verde/ámbar y enlaces al sistema | — |
| B6 | Detalle de la tanda GPS → ⋮ → Ensayo en casa | Dashboard reproduciendo; GUARDAR desactivado | — |
| C1/C2 | Análisis → Rivales y condiciones → «Carlos Ruiz» | Lista, ganadas/perdidas, racha y barras | «Datos de prueba» |
| C3 | Análisis (circuito con karts de 1–2 tandas) | «Pocas muestras…» en el ranking | «Datos de prueba» |
| C4 | Rivales → bajar a «Condiciones y ritmo» | Con 14 tandas: correlaciones; tras «Borrar todo» + 3 tandas manuales: mensaje de ≥ 8 | «Datos de prueba» |
| C5 | Ajustes → Borrar datos de rivales → Rivales | Pantalla vacía | — |
| C6 | Rivales → Unir pilotos → «Carlos Ruiz», «C. Ruiz» → Unir | Un solo piloto; «Deshacer uniones» lo revierte | «Datos de prueba» |
| D1 | Detalle → ⋮ → Editar tanda → Aumentar kart → Guardar | Vuelve al Detalle con el kart nuevo | — |
| D2 | Borrar una vuelta / la tanda | Aviso «Deshacer» 8 s; pulsarlo la recupera | — |
| D3 | Tandas → ⋮ → Buscar | Pulsar «Buscar «…» (debug)» filtra sin teclear | «Buscar «…» (debug)» |

## Lista para el móvil real (día de pista)

**Cómo montarlo:** móvil fijado al volante en apaisado (Dashboard), pantalla visible, cargador/powerbank si la tanda es larga, ahorro de
batería **desactivado** para KartLog, ubicación «alta precisión», permiso de ubicación concedido «mientras se usa la app».

**Antes de salir:** Ajustes → **Antes de salir** (todo verde); Circuitos → **Marcar meta** en la línea real (ajuste ±1 m y VERIFICAR con una
pasada); Ajustes → **Calibración** con 2–3 vueltas marcando AQUÍ en cada paso por meta y **Aplicar compensación** si el sesgo es estable.

**Qué pulsar:** Tandas → En pista → elegir circuito, kart y tipo → arrancar; ¡no tocar el móvil en marcha! (PIT solo en resistencia);
al volver, pulsar largo para terminar y GUARDAR.

**Qué traer de vuelta:**
1. Por cada tanda GPS: Detalle → ⋮ → **Exportar GPX** y **Exportar CSV del GPS** (con rumbo, proveedor y marca de cruce).
2. El **informe de calibración** (sesgo medio, vueltas, número) y una captura del informe de **calidad de señal** de cada tanda.
3. Los tiempos oficiales de la hoja de la pista (para comparar GPS vs oficial en el Detalle).
4. Anotar: móvil, ubicación del móvil en el kart, si había cobertura, y cualquier salto raro del delta.

## No comprobado en dispositivo

El comportamiento del GPS real y su calidad, ML Kit, los selectores de ficheros y la hoja de compartir con GPX/CSV, `JobScheduler`,
la orientación y el brillo, las migraciones en un móvil con datos reales (solo con esquemas exportados y bases de ejemplo en JVM; el
test instrumentado únicamente se compila), el rendimiento de las pantallas nuevas y que el aviso de deshacer se vea sobre el teclado/teclado
de tiempo del Detalle.

## Hashes de los commits

__HASHES__
