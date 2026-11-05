"""Modelos 3D de los objetos rituales (cajas con una paleta de colores) y definiciones de item que usan el icono 2D en
el inventario y el modelo 3D en mano, suelo y marcos. No toca el látigo ni el contrato."""
import os

import generate_assets as g

# Paleta 4x4 celdas de 4x4 px: índice -> (color RGBA)
PALETA = [
    (0xbc, 0x80, 0x5d), (0x89, 0x57, 0x38), (0x77, 0x85, 0x8d), (0x82, 0x9e, 0xaf),
    (0x24, 0x24, 0x2c), (0x5a, 0x5a, 0x68), (0xb4, 0xb0, 0xa4), (0xd8, 0xd2, 0xc2),
    (0x4a, 0x35, 0x50), (0x49, 0x31, 0x53), (0x14, 0x14, 0x1a), (0xd8, 0xc0, 0x90),
    (0xc8, 0x7a, 0x3c), (0xcd, 0xee, 0xff), (0xe3, 0xb2, 0x8c), (0x71, 0x8a, 0x76),
]
DIRS = ("down", "up", "north", "south", "west", "east")
TEXTURA = "theslave:item/paleta3d"


def paleta(ruta):
    def pixel(x,y):
        color = PALETA[(y // 4) * 4 + x // 4]
        shade = (-5, 0, 4, -2)[(x + y * 3) % 4]
        return tuple(max(0,min(255,c+shade)) for c in color) + (255,)
    g.png(ruta, 16, 16, pixel)


def caja(a, b, c, e=None):
    """Caja de a=(x,y,z) a b=(x,y,z) con el color c (índice de la paleta); e = color de las caras laterales opcional."""
    def uv(i):
        cx, cy = (i % 4) * 4, (i // 4) * 4
        return [cx + 1, cy + 1, cx + 3, cy + 3]
    caras = {}
    for d in DIRS:
        caras[d] = {"texture": "#t", "uv": uv(e if (e is not None and d not in ("up", "down")) else c)}
    return {"from": list(a), "to": list(b), "faces": caras}


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.65, 0.65, 0.65]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 1], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 1], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [1, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [1, 2, 0], "scale": [0.5, 0.5, 0.5]},
}

MODELOS = {
    "relicario_animas": [
        caja((5, 0, 5), (11, 1.5, 11), 0), caja((4.5, 1.5, 4.5), (11.5, 3, 11.5), 1),
        caja((5.5, 3, 5.5), (10.5, 12, 10.5), 2),
        caja((4.8, 2, 4.8), (5.5, 13, 5.5), 1), caja((10.5, 2, 4.8), (11.2, 13, 5.5), 0),
        caja((4.8, 2, 10.5), (5.5, 13, 11.2), 1), caja((10.5, 2, 10.5), (11.2, 13, 11.2), 0),
        caja((5.8, 4.5, 5.3), (6.7, 10.5, 5.5), 15), caja((7.5, 4.5, 5.3), (8.4, 10.5, 5.5), 3), caja((9.2, 4.5, 5.3), (10.1, 10.5, 5.5), 9), caja((7, 4.5, 10.5), (9, 10.5, 10.7), 3),
        caja((5.3, 4.5, 7), (5.5, 10.5, 9), 3), caja((10.5, 4.5, 7), (10.7, 10.5, 9), 3),
        caja((5, 12, 5), (11, 13, 11), 0), caja((6.5, 13, 6.5), (9.5, 14.5, 9.5), 1),
        caja((6, 14.5, 6), (10, 16, 10), 14),
    ],
    "ofrenda_muerte": [
        caja((4, 0, 4), (12, 2, 12), 4), caja((3, 2, 3), (13, 11, 13), 4, 5),
        caja((4, 11, 4), (12, 13, 12), 6), caja((5, 13, 5), (11, 15, 11), 4),
        caja((4, 3, 2.8), (5, 10, 3), 15), caja((7.5, 3, 2.8), (8.5, 10, 3), 9),
        caja((11, 3, 2.8), (12, 10, 3), 13),
        caja((2.8, 3, 7), (3, 10, 8), 9), caja((13, 3, 8), (13.2, 10, 9), 3),
        caja((3, 10, 3), (13, 11, 13), 7),
    ],
    "sello_almas": [
        caja((5, 0, 3), (11, 2, 13), 8), caja((3, 0, 5), (13, 2, 11), 8), caja((4, 0, 4), (12, 2, 12), 8),
        caja((5.5, 2, 5), (6.5, 2.5, 11), 7), caja((7.5, 2, 5), (8.5, 2.5, 11), 7), caja((9.5, 2, 5), (10.5, 2.5, 11), 7),
        caja((6.5, 0, 12.5), (9.5, 0.8, 16), 10), caja((7, 2.5, 7), (9, 3.5, 9), 3),
    ],
    "tributo_doble_condena": [
        caja((3, 0, 5), (8, 2, 10), 4), caja((8, 0, 6), (13, 2, 11), 6),
        caja((4.5, 2, 6), (5.5, 2.5, 9), 7), caja((6, 2, 6), (7, 2.5, 9), 7),
        caja((9.5, 2, 7), (10.5, 2.5, 10), 7), caja((11, 2, 7), (12, 2.5, 10), 7),
        caja((6.5, 0, 9.5), (9.5, 0.8, 14), 10), caja((7, 2.5, 7), (9, 3.5, 9), 3),
    ],
    "tributo_condena": [
        caja((2, 0, 3), (14, 1.5, 13), 6), caja((1.8, 0.2, 6.8), (14.2, 1.8, 9.2), 10),
        caja((6.8, 0.2, 2.8), (9.2, 1.8, 13.2), 10), caja((6.5, 1.8, 6.5), (9.5, 2.5, 9.5), 7),
        caja((7.2, 2.5, 7.2), (8.8, 2.8, 8.8), 3),
    ],
}


def generar(escribe, assets):
    paleta(os.path.join(assets, "textures", "item", "paleta3d.png"))
    for n, elementos in MODELOS.items():
        escribe(os.path.join(assets, "models", "item", n + "_icono.json"),
                {"parent": "minecraft:item/generated", "textures": {"layer0": "theslave:item/" + n}})
        escribe(os.path.join(assets, "models", "item", n + ".json"),
                {"textures": {"t": TEXTURA, "particle": TEXTURA}, "elements": elementos, "display": DISPLAY})
        plano = {"type": "minecraft:model", "model": "theslave:item/" + n + "_icono"}
        escribe(os.path.join(assets, "items", n + ".json"), {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui"], "model": plano}],
            "fallback": {"type": "minecraft:model", "model": "theslave:item/" + n}}})
