#!/usr/bin/env python3
"""Modelo 3D del Cetro del Rey de Aluminio (varita con bola y corona) + su textura.

Genera:
  textures/item/scepter_model.png            16x16: cuatro zonas de 8x8 (aluminio de la bola, oro, oro oscuro, perla/rubi)
  models/item/aluminum_scepter_3d.json       los cubos (palo, anillas, collar, bola, corona)
  models/item/aluminum_scepter_hand.json     pose en la mano (varita inclinada hacia delante)
  models/item/aluminum_scepter_aim.json      pose al cargar/lanzar (punta de la corona apuntando al frente)
  items/aluminum_scepter.json                2D en inventario/suelo/marco; 3D en la mano

Uso (desde mods/TheBall):  python tools/generate_scepter_model.py
"""
import json
import random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/theball"

# ------------------------------------------------------------------ textura
ball = Image.open(ROOT / "textures/entity/aluminum_ball.png").convert("RGBA")
tex = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
rnd = random.Random(5)
for y in range(8):
    for x in range(8):
        tex.putpixel((x, y), ball.getpixel((64 + x, 36 + y)))            # aluminio con facetas
        g = 1.0 - 0.06 * y + rnd.uniform(-0.04, 0.04)
        tex.putpixel((8 + x, y), (int(255 * g), int(200 * g), int(58 * g), 255))      # oro
        d = 0.78 + rnd.uniform(-0.04, 0.04)
        tex.putpixel((x, 8 + y), (int(222 * d), int(150 * d), int(30 * d), 255))      # oro oscuro
        tex.putpixel((8 + x, 8 + y), (250, 252, 255, 255) if x < 4 else (214, 44, 56, 255))  # perla | rubi
tex.save(ROOT / "textures/item/scepter_model.png")

REGION = {"foil": [0, 0, 8, 8], "gold": [8, 0, 16, 8], "dark": [0, 8, 8, 16], "pearl": [8, 8, 12, 12], "ruby": [12, 8, 16, 12]}


def box(cx, cy, cz, sx, sy, sz, mat, rot=None):
    """Cubo centrado en (cx, cy, cz) con tamaño (sx, sy, sz); las coordenadas se miden desde el centro del modelo."""
    e = {
        "from": [8 + cx - sx / 2, 8 + cy - sy / 2, 8 + cz - sz / 2],
        "to": [8 + cx + sx / 2, 8 + cy + sy / 2, 8 + cz + sz / 2],
        "faces": {f: {"uv": REGION[mat], "texture": "#tex"} for f in ("north", "south", "east", "west", "up", "down")},
    }
    if rot:
        axis, angle = rot
        e["rotation"] = {"origin": [8 + cx, 8 + cy, 8 + cz], "axis": axis, "angle": angle}
    return e


els = []
# palo (de y=-5 a 9) con anillas y contera
els.append(box(0, 2, 0, 1.6, 14, 1.6, "foil"))
els.append(box(0, -5.6, 0, 2.6, 1.8, 2.6, "gold"))
for y in (-2.5, 1.5, 5.5):
    els.append(box(0, y, 0, 2.3, 0.9, 2.3, "gold"))
# collar bajo la bola
els.append(box(0, 9.6, 0, 4.2, 1.2, 4.2, "gold"))
els.append(box(0, 10.5, 0, 3.0, 0.8, 3.0, "dark"))
# bola de aluminio (cubo + tres cubos girados 45 grados: se ve casi esferica)
oy = 14.0
els.append(box(0, oy, 0, 6.4, 6.4, 6.4, "foil"))
els.append(box(0, oy, 0, 5.6, 6.0, 5.6, "foil", ("y", 45)))
els.append(box(0, oy, 0, 6.0, 5.6, 5.6, "foil", ("x", 45)))
els.append(box(0, oy, 0, 5.6, 5.6, 6.0, "foil", ("z", 45)))
# corona dorada sobre la bola, alineada con el palo
cy = oy + 3.9
els.append(box(0, cy, 2.1, 5.0, 1.6, 0.9, "gold"))
els.append(box(0, cy, -2.1, 5.0, 1.6, 0.9, "gold"))
els.append(box(2.1, cy, 0, 0.9, 1.6, 3.4, "gold"))
els.append(box(-2.1, cy, 0, 0.9, 1.6, 3.4, "gold"))
els.append(box(0, cy - 0.8, 0, 3.4, 0.4, 3.4, "dark"))
for sx in (-2.1, 2.1):
    for sz in (-2.1, 2.1):
        els.append(box(sx, cy + 1.8, sz, 0.9, 2.0, 0.9, "gold"))
        els.append(box(sx, cy + 3.1, sz, 0.8, 0.8, 0.8, "pearl"))
for (sx, sz) in ((0, 2.1), (0, -2.1), (2.1, 0), (-2.1, 0)):
    els.append(box(sx, cy + 1.3, sz, 0.9, 1.0, 0.9, "gold"))
els.append(box(0, cy, 2.55, 1.0, 1.0, 0.3, "ruby"))

model3d = {"textures": {"tex": "theball:item/scepter_model", "particle": "theball:item/scepter_model"}, "elements": els}


def display(idle):
    # Mano derecha / izquierda en primera persona (vista desde la camara) y en tercera persona.
    tp = 12 if idle else 10
    if idle:
        fp = [-38, 0, -8]
        fpt = [1.5, 2.0, 1.0]
    else:
        fp = [-78, 0, -4]            # al cargar o lanzar: la corona apunta al frente
        fpt = [1.5, 1.0, 0.0]
    return {
        "firstperson_righthand": {"rotation": fp, "translation": fpt, "scale": [0.85, 0.85, 0.85]},
        "firstperson_lefthand": {"rotation": [fp[0], 0, -fp[2]], "translation": [-fpt[0], fpt[1], fpt[2]], "scale": [0.85, 0.85, 0.85]},
        # En tercera persona +Y del modelo es "hacia delante" desde la mano y +Z "hacia arriba": inclinamos la punta
        # hacia arriba para que se vea empunado (en reposo) y mas horizontal al lanzar.
        "thirdperson_righthand": {"rotation": [tp, 0, 0], "translation": [0, -2.0, 0.5], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_lefthand": {"rotation": [tp, 0, 0], "translation": [0, -2.0, 0.5], "scale": [0.8, 0.8, 0.8]},
    }


def write(path, data):
    path.write_text(json.dumps(data, indent=1), encoding="utf-8")


m = ROOT / "models/item"
# Los "base" solo aportan las transformaciones de mano; la forma la dibuja ScepterSpecialRenderer (esfera real).
for old in ("aluminum_scepter_3d.json",):
    (m / old).unlink(missing_ok=True)
write(m / "aluminum_scepter_hand.json", {"textures": {"particle": "theball:item/scepter_model"}, "display": display(True)})
write(m / "aluminum_scepter_aim.json", {"textures": {"particle": "theball:item/scepter_model"}, "display": display(False)})
write(m / "aluminum_scepter.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "theball:item/aluminum_scepter"}})
write(ROOT / "items/aluminum_scepter.json", {
    "model": {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"],
                   "model": {"type": "minecraft:model", "model": "theball:item/aluminum_scepter"}}],
        "fallback": {
            "type": "minecraft:condition",
            "property": "minecraft:using_item",
            "on_true": {"type": "minecraft:special", "base": "theball:item/aluminum_scepter_aim", "model": {"type": "theball:scepter"}},
            "on_false": {"type": "minecraft:special", "base": "theball:item/aluminum_scepter_hand", "model": {"type": "theball:scepter"}},
        },
    }
})
print("Modelo 3D del cetro generado")
