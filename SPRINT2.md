# KartLog — Informe final del Sprint 1.5 + Sprint 2

**Para:** Arquitecto y Lead Designer · **Rama:** `claude/kartlog-sprint-1-3hhlkb`

## Estado
Partes A (correcciones) y B (navegación, análisis, gráficas) **completas**. Compila (`assembleDebug` y `assembleRelease`),
lint sin errores (31 avisos), **49 tests unitarios en verde** (dos pasadas seguidas, sin intermitencias) y 5 tests de DAO
instrumentados que compilan. El esquema de Room **no ha cambiado** (sigue en versión 1, sin migraciones).
**Pendiente: validación visual en emulador/dispositivo** (el entorno de desarrollo no tiene KVM): ver lista al final.

## Tareas completadas (un commit por tarea salvo las dos parejas indicadas)
| Tarea | Resultado |
|---|---|
| A1 | `AutoSizeText`: una línea, sin partir, de 40 sp a 28 sp según ancho. Aplicado en detalle y cards. MEJOR/MEDIA con `weight` 1,2/0,8. |
| A2 | Detalle reorganizado: barra con título en una línea con elipsis y menú ⋮ (Exportar CSV, Borrar tanda con confirmación); cabecera compacta; lista con `weight(1f)`; panel de teclado fijado abajo y plegable (`rememberSaveable`; empieza desplegado con 0 vueltas o al editar). |
| A3 | Fila de vuelta de 56 dp: `V5` · tiempo · diferencia (`+2,927`) o `MEJOR`; tocar edita; menú ⋮ por fila con confirmación al borrar. |
| A4 + A5 (1 commit) | Nueva tanda: circuito en hoja con buscador (recientes primero, preselecciona el de la última tanda creada), «+ NUEVO» secundario, temperaturas nulas con «Quitar», fecha y hora editables (`DatePicker`/`TimePicker`), teclado sin tapar notas ni GUARDAR. Alta de circuito en hoja inferior con botones siempre visibles, mayúscula por palabra y «Hecho» guarda. |
| A6 | `DebugSeeder` solo en el source set `debug` (en `release` la fábrica devuelve `null`): 3 circuitos, 12 tandas en ~3 meses, salidas de boxes +15–20 %, una tanda sin vueltas. Botones «Cargar datos de prueba» y «Borrar todos los datos» (doble confirmación) en Ajustes. |
| B1 | Barra inferior con Tandas · Análisis · Circuitos · Ajustes, estado conservado (`saveState`/`restoreState`), oculta en Nueva tanda y Detalle. |
| B2 | `Analytics` (dominio puro): récord, media limpia (≤ 107 %), consistencia, delta/`RÉCORD`, ranking de karts, condiciones, evolución. 14 tests con casos límite (vuelta exactamente al 107 %, tanda vacía, una vuelta). |
| B3 + B4 (1 commit) | Card rediseñada (delta/RÉCORD, mejor vuelta en verde, iconos de condiciones, media limpia, sparkline en `Canvas`); lista con cabeceras de mes (`stickyHeader`), filtros por circuito y tipo, orden por fecha o mejor vuelta, estado vacío de filtros. |
| B5 | Gráfica Vico en la cabecera del detalle (plegable): tiempos por vuelta, media limpia como línea de referencia, mejor vuelta en verde, eje Y en `m:ss.mmm`. |
| B6 | Análisis: selector de circuito, RÉCORD / MEDIA LIMPIA / Nº DE TANDAS, evolución (Vico), ranking de karts, efecto de condiciones; con < 2 tandas muestra un mensaje en lugar de gráficas. |
| B7 | Circuitos: nombre, longitud, récord, nº de tandas; tocar abre Análisis con ese circuito; editar; borrar bloqueado con aviso «Tiene N tandas» (sin tandas, con confirmación). |
| B8 | Ajustes: «Exportar todo a CSV» (`fecha;circuito;tipo;kart;lastre_kg;temp_ambiente;temp_pista;estado_pista;vuelta;tiempo;tiempo_ms;origen`) por `FileProvider`, versión de la app y herramientas debug. |

## Decisiones tomadas por mi cuenta (a validar)
1. **Vico 2.5.1, no 2.5.2.** La 2.5.2 exige `compileSdk 37` y AGP 9.1 (arrastra `core-ktx 1.19`); la 2.5.1 tiene la misma API y funciona con SDK 36. Además existe ya la serie 3.x. La API se verificó leyendo el código fuente publicado en Maven (la documentación web no era accesible desde el entorno).
2. **`material-icons-extended` se añadió en A2**, no en B1: los iconos de atrás/⋮ ya hacían falta.
3. **Cabecera del detalle muestra «Media limpia»** (no la media simple), para ser coherente con la card y el Análisis.
4. **Gráfica del detalle plegada por defecto**, para respetar las 7 vueltas visibles; se despliega con «Gráfica de vueltas».
5. **Preselección de circuito:** el de la tanda con mayor `id` (última *creada*), no la de fecha más reciente. En el selector, los circuitos van por uso reciente y luego por nombre.
6. **Media limpia histórica del circuito** = media de todas las vueltas limpias agrupadas (no media de medias).
7. **Consistencia** = desviación típica *poblacional* de las vueltas limpias. Está calculada y testeada (`SessionStats.consistencyMs`) pero **no se muestra en ninguna pantalla**, porque el documento no indica dónde.
8. **Cabeceras de mes solo con orden por fecha**; con orden por mejor vuelta la lista es plana (tandas sin vueltas al final).
9. **Nº de tandas de un circuito** (Circuitos, bloqueo de borrado) cuenta *todas* las tandas, también las vacías; el Análisis cuenta solo las que tienen vueltas.
10. **Fecha por defecto de Nueva tanda** = instante de abrir el formulario, truncado al minuto. El slider de temperatura, sin tocar, queda en «—» con el pulgar atenuado en 20 °C (ambiente) / 30 °C (pista).
11. **Exportación total:** `tipo`, `estado_pista` y `origen` con el nombre del enum (FREE, DRY, MANUAL…), fecha `yyyy-MM-dd HH:mm`, decimales con punto, nombres con `;` o comillas entrecomillados.
12. **Ajustes provisionales eliminados:** el acceso por ⋮ de la lista (A6) se retiró al llegar la barra inferior (B1).
13. Las parejas A4+A5 y B3+B4 van en un solo commit cada una porque comparten archivos.

## Riesgos y puntos a vigilar
- **Nada de esto se ha visto en pantalla.** Los tests de UI usan Robolectric (gráficos *legacy*, texto sin métricas reales), así que no validan aspecto, tamaños ni solapes.
- **7 vueltas con el teclado plegado:** el cálculo da ~410 dp para la lista en un Pixel 3a, justo para 7 filas de 57 dp; con notas en la cabecera (1 línea más) puede bajar a 6,5. Si falla, el recurso más fácil es ocultar las notas del detalle.
- **Vico:** componer y medir sin excepciones está probado, pero no el aspecto (colores, tamaño de etiquetas de 11 sp, punto verde de la mejor vuelta, línea ámbar de referencia).
- **`TempSlider`:** las marcas cada 5 °C se dibujan asumiendo que el carril ocupa de centro a centro del pulgar; puede haber desfase de píxeles.
- **Hojas inferiores con teclado** (circuito, nuevo circuito): el comportamiento real de `imePadding` dentro de `ModalBottomSheet` solo se comprueba en dispositivo.
- **Rendimiento:** el análisis se recalcula completo cada vez que cambia una vuelta o tanda. Con miles de vueltas es inapreciable; con decenas de miles convendría hacerlo incremental o en SQL.
- **Robolectric y `clip` no uniforme:** en tests, un `clip` con esquinas redondeadas desiguales rompe el *hit-test*; en dispositivo no. Por eso el panel del teclado pinta su forma con `background(color, shape)` en lugar de `Surface`/`clip`.

## Cómo ejecutar los tests
```
./gradlew testDebugUnitTest                 # 49 tests (JVM + Robolectric); ver README para ROBOLECTRIC_DEPS_DIR
./gradlew :app:connectedDebugAndroidTest    # 5 tests de DAO; requiere emulador/dispositivo conectado
# Solo la clase de DAO:
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nacho.kartlog.data.KartLogDaoTest
```
Desde Android Studio: clic derecho en `app/src/androidTest/java/com/nacho/kartlog/data/KartLogDaoTest.kt` → *Run 'KartLogDaoTest'* con un emulador arrancado.

## Lista de comprobación visual para el responsable
Instalar la variante **debug**, abrir **Ajustes → Cargar datos de prueba** y revisar:

**Tandas (inicio)**
- [ ] Cabeceras de mes pegajosas («Octubre 2026», «Septiembre 2026»…) que se quedan arriba al desplazar.
- [ ] Cada card: circuito a la izquierda; a la derecha `RÉCORD` verde o diferencia gris (`+2,927`); mejor vuelta verde grande **en una línea** (p. ej. `1:12.450`); fecha · tipo · kart; iconos sol/gota/paraguas y temperaturas; fila inferior con nº de vueltas, media limpia y sparkline con punto verde en la mejor vuelta.
- [ ] La tanda sin vueltas muestra «Sin vueltas» gris y no tiene sparkline ni delta.
- [ ] Fila de chips (Circuito, Libre, Clasificación, Carrera): filtrar y volver a pulsar para quitar; con un filtro sin resultados aparece «Sin resultados» y «Quitar filtros».
- [ ] Menú ⋮ → ordenar por mejor vuelta: lista plana, mejores arriba, la vacía al final.

**Nueva tanda** (botón «+ NUEVA TANDA»)
- [ ] Circuito: botón con el de la última tanda; al pulsarlo, hoja con buscador (prueba «zuera», «alonso»). «+ NUEVO» es de contorno, no rojo.
- [ ] Fecha y hora abren sus selectores; el valor elegido se refleja en la fila.
- [ ] Las dos temperaturas arrancan en «—»; al arrastrar aparece el valor y «Quitar».
- [ ] Con el teclado abierto en Notas, el campo y el botón GUARDAR siguen visibles.
- [ ] «+ NUEVO» abre una hoja con Cancelar/Guardar visibles sobre el teclado, nombre con mayúscula por palabra y «Hecho» que guarda.

**Detalle de tanda** (abrir una con ~12 vueltas)
- [ ] Título del circuito en una línea con «…» si es largo; menú ⋮ con «Exportar CSV» y «Borrar tanda».
- [ ] Cabecera compacta; MEJOR (verde) y MEDIA LIMPIA **sin partirse** en dos líneas.
- [ ] Con el teclado plegado caben **al menos 7 vueltas**; el panel inferior muestra `0:00.000` y «ABRIR TECLADO».
- [ ] «Gráfica de vueltas» despliega la gráfica: línea de tiempos, línea ámbar de media limpia, punto verde en la mejor vuelta, eje Y en `m:ss.mmm`.
- [ ] Tocar una fila abre el teclado en modo edición (fila resaltada, «EDITANDO V3»); teclear `4 8 5 3 2` muestra `0:48.532`; vibración al guardar.
- [ ] Menú ⋮ de fila: Editar y Borrar (con confirmación).

**Análisis**
- [ ] Selector de circuito (por defecto el de la tanda más reciente) y tres cifras: RÉCORD, MEDIA LIMPIA, TANDAS.
- [ ] Gráfica de evolución con fechas en el eje X; ranking de karts ordenado por mejor vuelta; efecto de condiciones con la diferencia respecto a seco.
- [ ] Un circuito con menos de 2 tandas con vueltas muestra el mensaje en lugar de gráficas.

**Circuitos**
- [ ] Nombre, longitud, récord verde y nº de tandas. Tocar abre Análisis con ese circuito.
- [ ] ⋮ → Editar (nombre duplicado da error) y Borrar: con tandas, aviso «Tiene N tandas»; sin tandas, confirmación.

**Ajustes y barra inferior**
- [ ] La barra (Tandas, Análisis, Circuitos, Ajustes) resalta el destino activo en rojo; al volver a una pestaña conserva su posición y filtros; desaparece en Nueva tanda y Detalle.
- [ ] «Exportar todo a CSV» abre el selector de compartir; la versión aparece abajo; la tarjeta «Depuración» solo existe en debug; «Borrar todos los datos» pide dos confirmaciones.


---

# Sprint 2.1 — Correcciones visuales tras validar en emulador (Pixel 3a API 35)

## Estado
F1–F9 **hechas, un commit por tarea**. Compila (debug y release), **57 tests unitarios en verde** (se ejecutaron tras cada tarea;
F2 se repitió tres veces seguidas sin intermitencias). Esquema de Room intacto. **Sigue pendiente la validación visual**:
lo único que ahora verifican los tests de UI es la *estructura* (qué se ve, qué se oculta, qué queda en pantalla), no el aspecto.

## Tareas
| Tarea | Qué se hizo | Cómo se comprobó |
|---|---|---|
| **F1** | Gráfica y teclado son **mutuamente excluyentes** (abrir uno pliega el otro; editar una vuelta abre el teclado y pliega la gráfica). El teclado es ahora una cuadrícula propia (`Layout`) cuyas filas miden `min(alto preferido, alto disponible / 4)`, y en el panel va con `weight(1f, fill = false)`: **encoge en vez de recortarse**, así que GUARDAR VUELTA nunca queda fuera. | `DetailLayoutTest` (360×640 dp, 10 vueltas): gráfica abierta ↔ teclado, teclas 1/5/9/00/0/⌫ y GUARDAR VUELTA a la vista. |
| **F2** | Franja `V10 1:12.858 · V9 1:12.951` (las 2 últimas guardadas) encima del display, solo con el teclado abierto. Teclas de 52 dp (antes 56). | Mismo test: la franja es visible con el teclado abierto. |
| **F3** | Eje Y calculado con las **vueltas limpias**: mínimo = mejor − 0,3 s, máximo = mayor limpia + 0,3 s (la media limpia siempre entra en el rango). Las vueltas fuera de rango se **recortan en el borde superior** y se dibujan como **punto hueco ámbar**. Eje Y en `m:ss.mmm`. Lógica pura en `computeChartScale`. | `ChartScaleTest` (5 casos: salida de boxes recortada, sin salidas, referencia, una vuelta, evolución sin recorte). |
| **F4** | Causa: Vico usa por defecto zoom `max(fijo 32 dp/punto, Content)` y desplazamiento, así que 10 vueltas no cabían y se cortaban. Ahora **sin scroll ni zoom y con `Zoom.Content`**, más 12 dp de holgura fija en ambos extremos. Etiquetas del eje X cada 1 vuelta (≤ 12) o cada 2 (13–24). | **Solo análisis del código fuente de Vico 2.5.1; no verificable aquí** (ver riesgos). |
| **F5** | Sin texto sobre el gráfico: queda la línea ámbar. Leyenda **fuera del área de dibujo**: `— Media limpia 1:12.981` (línea ámbar) y `● Mejor` (punto verde); sale además `○ Fuera de escala` cuando hay vueltas recortadas. En el Análisis el punto se llama «Récord». | `DetailLayoutTest` comprueba los tres textos de la leyenda. |
| **F6** | `StatBadge` admite `minValueSize` y alto de valor fijo; MEDIA LIMPIA usa `AutoSizeText` (baja hasta 18 sp) y los pesos pasan a 1,1/0,9. | Cálculo: fuente mono = 0,6 em/carácter → `1:12.981` y `0:47.388` (8 caracteres) a 28 sp miden 134 dp; el ancho de MEDIA en un Pixel 3a (393 dp) es ≈ 144 dp y en 360 dp ≈ 137 dp, así que caben sin encoger. `@Preview` `StatsRowPreview` con ambos valores. |
| **F7** | Circuito por defecto del Análisis = el de **más tandas con vueltas**; empate → el de la tanda más reciente. La elección se recuerda mientras la app siga en memoria (`AnalysisSelection` vive en el `AppContainer`). Las tres cifras, en una `Row` con 12 dp de separación, alineadas arriba y con valor de alto fijo (40 dp) que encoge hasta 18 sp. | `AnalysisModelTest` (defecto, desempate, tandas vacías no cuentan, selección explícita). |
| **F8** | `contentPadding` superior 8 dp e inferior 96 dp en Tandas y Circuitos. Los filtros pasan a `LazyRow` con `contentPadding` horizontal 16 dp: empieza siempre en 0 (primer chip completo) y conserva el scroll horizontal. | `NavigationFlowTest`: el chip «Circuito» y «Libre» están a la vista. |
| **F9** | Nuevo `SecondaryButton` (contorno 2 dp). Ajustes: «Exportar todo a CSV» y «Cargar datos de prueba» con contorno `primary`; «Borrar todos los datos» con contorno y texto de aviso (ámbar). El nombre del circuito seleccionado en Nueva tanda usa `maxLines = 1` + elipsis. | Compila; el texto de los botones sigue en mayúsculas (lo cubre `NavigationFlowTest`). |

`@Preview` añadidos: gráfica con 10 vueltas y una salida de boxes (`LapChartPreview`, F3/F4/F5), panel de teclado con poca altura
(`LapEntryPanelTightPreview`, F1/F2) y fila MEJOR/MEDIA (`StatsRowPreview`, F6).

## Decisiones tomadas por mi cuenta (a validar)
1. **Leyenda con un tercer elemento** («○ Fuera de escala», solo si hay vueltas recortadas): el punto hueco ámbar necesita explicación y no cabía en el texto de la media.
2. **Evolución de tandas (Análisis) no recorta**: el recorte de F3 solo aplica a la gráfica de vueltas (`clipOutliers = true`); en la de evolución cada punto ya es una mejor vuelta.
3. **Altura de teclas:** preferida 52 dp; si el panel no cabe, baja por debajo de 52 dp *antes* que perder GUARDAR VUELTA (prioridad de F1 sobre F2).
4. **Los pesos de MEJOR/MEDIA** pasan de 1,2/0,8 a 1,1/0,9 para dar a MEDIA el ancho que necesitaba.
5. **Gráfica del detalle sigue plegada por defecto**, y se pliega al abrir el teclado.
6. Se retiró la comprobación automática de F4 que había intentado (desplazamiento máximo de Vico = 0): resultó vacía —Robolectric no ejecuta la fase de dibujo en la que Vico calcula el scroll, y pasaba igual con el zoom por defecto—, así que prefiero no dejar un test que dé falsa seguridad.

## Riesgos
- **F4 sin verificación objetiva.** La corrección sale de leer el código de Vico (`rememberVicoZoomState` por defecto = `max(fixed, Content)`; `Zoom.Content` ajusta al ancho). Es lo primero a mirar en el emulador.
- **Teclas por debajo de 52 dp** en pantallas muy bajas (< ~600 dp de alto útil con gráfica cerrada): el objetivo táctil mínimo de 56 dp deja de cumplirse, a cambio de que GUARDAR VUELTA siga visible.
- **Cabecera con la gráfica abierta** ocupa ≈ 450 dp; en móviles bajos la lista de vueltas puede quedar con casi nada mientras se ve la gráfica (es el comportamiento buscado: gráfica o teclado, no ambos).
- Los tests de UI usan Robolectric con métricas de texto simplificadas: no validan tamaños reales de fuente.

## Comprobación visual para el responsable (Pixel 3a, datos del DebugSeeder)
**Detalle de tanda (≥ 10 vueltas, con salida de boxes)**
- [ ] Abrir la gráfica: se ven **todas** las vueltas hasta el borde derecho, sin scroll; etiquetas del eje X cada 1 o 2 vueltas; la salida de boxes aparece como **punto hueco ámbar pegado arriba** y el resto de la línea ocupa bien el alto.
- [ ] Eje Y en `m:ss.mmm`; línea ámbar de la media; **ningún texto sobre el área del gráfico**; debajo, la leyenda `— Media limpia …`, `● Mejor` y `○ Fuera de escala`.
- [ ] Con la gráfica abierta, pulsar «ABRIR TECLADO»: la gráfica se pliega y el teclado aparece **entero** (1–9, 00, 0, ⌫ y GUARDAR VUELTA). Al pulsar «GRÁFICA DE VUELTAS» con el teclado abierto, este se pliega.
- [ ] Teclado abierto: sobre el display se ve `V10 … · V9 …` y, bajo el panel, **al menos 1–2 filas de vueltas**.
- [ ] MEDIA LIMPIA completa (`1:12.981`, `0:47.388`), sin recorte por la derecha; MEJOR en una línea.
- [ ] Editar una vuelta (tocar una fila) abre el teclado en modo edición y pliega la gráfica.

**Análisis**
- [ ] Abre el circuito con más tandas con vueltas (no el de la última tanda); al elegir otro y cambiar de pestaña y volver, **se mantiene**.
- [ ] RÉCORD · MEDIA LIMPIA · TANDAS en una fila alineada arriba, con cifras de igual altura y sin cortes.

**Tandas y Circuitos**
- [ ] La última card queda **por encima** del botón flotante (Tandas) y hay aire arriba del primer elemento.
- [ ] Los chips de filtro empiezan con «Circuito» completo y siguen desplazándose en horizontal.

**Ajustes y Nueva tanda**
- [ ] «Exportar todo a CSV» y «Cargar datos de prueba» con **contorno rojo** (no rellenos); «Borrar todos los datos» con **contorno ámbar**.
- [ ] Elegir un circuito de nombre largo: el botón del selector lo muestra con «…».

---

# Sprint 2.2 — Verificación real del 2.1

## T0. Identificar la build
En Ajustes, la última línea es ahora `KartLog · versión 0.1.0 · build <hash corto> · <fecha-hora>`; el hash lleva un **`+`** si el árbol de trabajo
tenía cambios sin commitear al compilar. Se genera en `app/build.gradle.kts` (`git rev-parse --short HEAD` y hora local) y se expone con
`BuildConfig.GIT_HASH` / `BuildConfig.BUILD_TIME`. **Es lo primero que hay que mirar en el emulador:** si el hash no es el del último commit de la rama
(`git log -1 --format=%h`), la app que se está viendo no es la última.

## T1. Auditoría de conexión: fix → composable → ruta
Todas las pantallas cuelgan de un único `NavHost` (`ui/navigation/KartNavHost.kt`), creado en `MainActivity`. No existe ningún segundo `NavHost`,
ni pantallas alternativas, ni versiones antiguas de los composables: cada pieza tiene una única definición y se usa desde la ruta indicada.

| Fix | Composable que lo implementa | Ruta de navegación hasta él |
|---|---|---|
| F1 gráfica/teclado excluyentes; teclado que encoge | `SessionDetailScreen` (estados `keypadExpanded`/`chartExpanded`) → `LapEntryPanel` + `TimeKeypad` (`Layout` propio) | Tandas (`sessions`) → tocar card → `sessions/{sessionId}` |
| F2 franja `V10 … · V9 …`, teclas 52 dp | `LapEntryPanel.RecentLapsStrip` | `sessions/{sessionId}` |
| F3 eje Y con vueltas limpias, recorte | `TimeLineChart` + `computeChartScale` (`ChartScale.kt`) | `sessions/{sessionId}` → «Gráfica de vueltas» |
| F4 gráfica completa sin scroll | `TimeLineChart` (`Zoom.Content`, scroll desactivado, `layerPadding`) | ídem |
| F5 leyenda fuera del gráfico | `TimeLineChart.ChartLegend` (también en la evolución del Análisis) | ídem y `analysis` |
| F6 MEDIA LIMPIA sin recorte | `SessionDetailScreen.StatsRow` → `StatBadge` → `AutoSizeText` | `sessions/{sessionId}` |
| F7 circuito por defecto y 3 cifras | `AnalysisModel.resolveCircuitId` ← `AnalysisViewModel`; fila de cifras en `AnalysisScreen` | pestaña Análisis (`analysis`) |
| F8 `contentPadding`, chips de filtro | `SessionListScreen` (`LazyColumn`, `FilterRow`/`LazyRow`); `CircuitsScreen` | pestañas Tandas (`sessions`) y Circuitos (`circuits`) |
| F9 botones secundarios, elipsis | `SettingsScreen` + `SecondaryButton`; `NewSessionScreen` (botón de circuito) | pestaña Ajustes (`settings`); Tandas → «+ NUEVA TANDA» (`sessions/new`) |

**Resultado de la auditoría:** ningún fix quedó sin conectar en el código de la rama. Se buscaron composables sin referencias y símbolos con un único uso:
solo había **código muerto menor**, ya eliminado: `LapTime.averageMs` (sustituida por la media limpia), `SessionFilters.hasActiveFilter` y 8 strings sin usar
(`action_add`, `format_temps`, `label_best_short`, `detail_not_found`, `stat_average`, `stat_laps`, `laps_title`, `lap_number`).
Otras diferencias reales respecto a lo pedido en T2, corregidas: MEDIA LIMPIA con 4 dp de margen interior y el chip «Circuito ▾» con la flecha.

**Hipótesis más probable de lo visto en el emulador: una build antigua.** El `SPRINT2.md` de la carpeta local del responsable pesa 19 KB, que es el tamaño
de la versión *con* el Sprint 2.1 (la anterior pesa ~10,5 KB), así que ese clon sí tenía los commits; pero la captura muestra clones **anidados**
(`pk1\pk1\pk1\pk1`, cada uno con su propio `.git`) y Android Studio puede tener abierto uno más antiguo. La línea de build de T0 lo confirma o lo descarta.
