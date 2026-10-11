# KartLog — Informe final del Sprint 1

**Para:** Arquitecto y Lead Designer · **Rama:** `claude/kartlog-sprint-1-3hhlkb`

## Estado
Alcance del Sprint 1 **completo (Tareas 1–7)**: crear tanda con datos de pista, guardar vueltas manualmente, histórico, detalle y exportación CSV.
Compila (`assembleDebug`), lint sin errores y 11 tests unitarios en verde. **Pendiente: primera validación visual en emulador/dispositivo** (el entorno de desarrollo no tiene KVM).

## Entregado
| Tarea | Resultado |
|---|---|
| 1. Estructura | Gradle + Version Catalog, KSP, Compose BOM, Material 3, Room, Navigation. `Application` + `AppContainer` (DI manual). Paquetes `data/`, `domain/`, `ui/`. |
| 2. Room | `circuits`, `sessions`, `laps` con FKs, índice único de nombre y `(sessionId, number)`, cascada en vueltas, convertidores de enums, 3 DAOs, `KartLogDatabase` v1 con esquema exportado. Listado con JOIN (nº vueltas y mejor vuelta). |
| 3. Repos y VMs | Interfaces en `domain/`, implementaciones y mapeadores en `data/`. `SessionListViewModel`, `NewSessionViewModel`, `SessionDetailViewModel` con `StateFlow`. Tiempos en ms; formateo `m:ss.mmm` en utilidades de dominio. |
| 4. Design System | Tema oscuro permanente con los tokens indicados; Chakra Petch (títulos) y JetBrains Mono (cifras) en `res/font`. `KartCard`, `StatBadge`, `BigStepper`, `ChipSelector`, `TempSlider`, `TimeKeypad`, `PrimaryButton`, `RacingTextField` + `@Preview`. |
| 5. Pantallas | Histórico (cards, mejor vuelta en verde 40 sp, estado vacío, FAB), Nueva tanda (alta rápida de circuito en diálogo), Detalle (stats, teclado de tiempos, lista con mejor vuelta resaltada, editar/borrar con confirmación, borrar sesión). Vibración al guardar vuelta. |
| 6. Navegación y prueba | 3 rutas (`sessions`, `sessions/new`, `sessions/{sessionId}`). Prueba funcional automatizada: circuito → tanda → 15 vueltas → cerrar y reabrir BD → todo persiste. |
| 7. CSV | `numero_vuelta;tiempo;tiempo_ms;origen`, compartido vía `FileProvider` y selector de Android. |

## Entrada de tiempos (pieza crítica)
Se teclean solo dígitos y se rellena de derecha a izquierda: `4 8 5 3 2` → `0:48.532`. Retroceso borra un dígito (mantener pulsado limpia todo). Tras guardar, el campo se limpia y el nº de vuelta avanza solo. Segundos ≥ 60 o tiempo 0 no se pueden guardar y se avisa en rojo.

## Verificación
- 8 tests de dominio (formateo, validación, media/mejor, CSV).
- 1 test de flujo con Room real en fichero (15 vueltas, edición, borrado, reapertura, cascada).
- 1 test de UI recorriendo las pantallas reales.
- 3 tests de DAO instrumentados: compilan, **no ejecutados**.

## Decisiones tomadas por mi cuenta (a validar)
1. **Temperaturas** ambiente/pista: el slider arranca en 20/30 °C y siempre se guardan. Kart y lastre arrancan en "—" y se guardan como nulos si no se tocan.
2. **Editar vuelta:** reutiliza el teclado principal (modo edición, botón "Actualizar vuelta").
3. **Lista de vueltas** de más reciente a más antigua, para que la última quede bajo el teclado.
4. **Circuito duplicado:** se selecciona el existente en lugar de dar error.
5. **Sin selector de fecha:** la fecha es el momento de guardar.
6. **Sin librería de iconos:** flechas y símbolos como texto.
7. **Versiones:** Kotlin 2.2.21, AGP 8.13.2, compileSdk/targetSdk 36, minSdk 26. Hay versiones más nuevas (AGP 9.x) no adoptadas por riesgo de migración.
8. **Chips de circuito** en lugar de desplegable (escalará mal con muchos circuitos; revisar en S2).

## Riesgos y puntos a revisar en el primer arranque
- Marcas cada 5 °C del `TempSlider`: pueden salir desplazadas unos píxeles.
- Teclado del sistema (notas) puede tapar el botón GUARDAR en Nueva tanda.
- Legibilidad a pleno sol y uso con guantes: solo validable en dispositivo real.
- Diálogo de alta de circuito: probado a nivel de ViewModel, no de UI.

## Para el Sprint 2
El campo `Lap.source` ya distingue `MANUAL/IMPORTED/GPS/LIVE_TIMING`. Los tiempos están en ms y el listado ya expone mejor vuelta por sesión, base para análisis y gráficas.
