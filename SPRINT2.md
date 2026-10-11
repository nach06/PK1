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
