# OCR con muestras reales — guía para el responsable

Estado (Sprint 7, D1): **no se ha podido ejecutar imagen → OCR → revisión con `muestra_a.png` / `muestra_b.jpeg` en este entorno**
(las imágenes no están en el repositorio y ML Kit necesita un dispositivo/emulador con Google Play services). Lo que sí está probado
es todo el tramo posterior a la lectura, con volcados sintéticos y con el texto de las muestras (`OcrFixtureTest`, `ImportFlowTest`).

## Pasos (≈ 5 minutos, solo clics, build debug)

1. Copia las dos imágenes a **`<carpeta Gradle del proyecto>/app/src/debug/assets/muestras/`** (cualquier extensión de imagen,
   mayúsculas o minúsculas, también en subcarpetas). Si el botón dice que faltan, el diálogo enseña la ruta exacta que usa la
   compilación y lo que hay empaquetado (`assets.list("muestras")`). Alternativa sin carpeta: **Ajustes → Depuración → Elegir imagen
   del almacenamiento**.
   **Importante (Windows / Android Studio):** si abres el proyecto desde otra ruta (p. ej. `C:/Users/nachr/pk1/pk1/pk1`), las muestras
   deben estar bajo **esa** carpeta Gradle: `<carpeta Gradle>/app/src/debug/assets/muestras/`, no en otra copia del repositorio. La ruta
   que usa tu compilación sale en **`BuildConfig.PROJECT_DIR`** (se muestra en el diálogo de diagnóstico de muestras de Ajustes →
   Depuración); compara esa ruta con la carpeta donde has copiado las imágenes. Tras copiarlas hay que volver a compilar (Run), porque
   se empaquetan como assets de la build debug.
2. Vuelve a ejecutar la app (Run) y entra en **Ajustes → Depuración → Probar importación con muestra A/B (imagen)**.
3. En el recorte comprueba el **indicador de calidad** (nitidez / brillo / reflejos con su umbral) y pulsa **ANALIZAR**.
4. En la revisión pulsa **DUMP OCR (DEBUG)**: guarda `ocr-dump-AAAAMMDD-HHMMSS.json` en
   `Android/data/com.nacho.kartlog/files/Download/` (Device Explorer del emulador) y abre la hoja de compartir con el JSON.
5. Pásame esos `.json` (y cuántas celdas has visto mal/ámbar/rojas). Se copian a `app/src/test/resources/ocr/` y
   `OcrDumpsPipelineTest` los recorre solos (layout → analizador → revisión); después se añaden aserciones concretas por muestra (D3).

## Qué mirar

- ¿Se reconoce el formato (matriz vuelta × piloto / tabla por kart)?
- ¿Cuántas celdas salen ámbar o rojas y por qué? (La nota de cada fila lo dice: `O→0` corregida, poca confianza, sin pareja…)
- ¿El indicador de calidad marcó algo en una foto que a ti te parece buena? Dime los valores para afinar `PhotoQuality`.
