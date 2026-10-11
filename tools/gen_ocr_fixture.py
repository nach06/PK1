#!/usr/bin/env python3
"""Genera un volcado de OCR «ruidoso» (formato OcrDump) a partir de la muestra A en texto, para los tests.

Ruido: ligera inclinación de la foto, jitter vertical por palabra, confusiones O/0 y l/1 en celdas de tiempo, una celda ausente,
nombres partidos en dos cajas y una confianza baja en algunas celdas. Determinista (semilla fija). Los volcados REALES de ML Kit
(Ajustes → Depuración → Probar importación con muestra A/B; archivo en filesDir/ocr_dumps/) se guardan junto a este en
src/test/resources/ocr/ y se prueban igual.

Uso: python3 tools/gen_ocr_fixture.py
"""
import json
import math
import random

SRC = "app/src/test/resources/muestras/muestra_a_correo_tabs.txt"
OUT = "app/src/test/resources/ocr/muestra_a_ruidosa.json"
rnd = random.Random(20261003)
TILT = 0.012

lines = open(SRC, encoding="utf-8").read().split("\n")
boxes = []
for row, line in enumerate(lines):
    pieces = line.split("\t")
    for col, piece in enumerate(pieces):
        if not piece.strip():
            continue
        text = piece.strip()
        is_time = len(text) == 9 and text[2] == ":" and text[5] == "."
        conf = 0.97
        # Confusiones del OCR solo en celdas de tiempo.
        if is_time and rnd.random() < 0.04:
            text = text.replace("0", "O", 1)
            conf = 0.62
        elif is_time and rnd.random() < 0.03:
            text = text.replace("1", "l", 1)
            conf = 0.66
        # Una celda ausente (el OCR no la vio): Asensio, vuelta 1.
        if row == 24 and col == 4:
            continue
        # Nombres partidos en dos cajas (Diego | Sanchez).
        parts = [text]
        if text == "Diego Sanchez":
            parts = ["Diego", "Sanchez"]
        x0 = 40 + col * 190
        y0 = 60 + row * 46 + rnd.uniform(-4, 4)
        x = x0
        for k, part in enumerate(parts):
            w = 20 + 8 * len(part)
            left, top, right, bottom = x, y0, x + w, y0 + 26
            cx, cy = (left + right) / 2, (top + bottom) / 2
            # La foto está ligeramente girada: se rota el centro y se anota el ángulo de la línea base.
            rx = cx * math.cos(TILT) - cy * math.sin(TILT)
            ry = cx * math.sin(TILT) + cy * math.cos(TILT)
            boxes.append({
                "text": part,
                "left": round(rx - w / 2, 1), "top": round(ry - 13, 1), "right": round(rx + w / 2, 1), "bottom": round(ry + 13, 1),
                "angle": TILT, "confidence": conf,
            })
            x += w + 8
json.dump({"width": 1600, "height": 2200, "boxes": boxes}, open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
print(len(boxes), "cajas ->", OUT)
