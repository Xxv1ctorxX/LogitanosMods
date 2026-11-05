#!/usr/bin/env python3
"""Texturas de la actualización «El Trono y la Reina de Aluminio» (v1.1): trono, corona, armadura del Rey del
Aluminio, cetro y medalla. Pixel art generado con Pillow, con la misma paleta que generate_textures.py.

Uso (desde mods/TheBall):  python tools/generate_king_textures.py
No toca las texturas de la v1.0 (esas las genera generate_textures.py).
"""
import math
import random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/theball"
for sub in ("textures/item", "textures/block", "textures/entity", "textures/entity/equipment/humanoid",
            "textures/entity/equipment/humanoid_leggings"):
    (ROOT / sub).mkdir(parents=True, exist_ok=True)

CLEAR = (0, 0, 0, 0)
OUT = (40, 42, 50, 255)
GOLD_D = (150, 98, 18, 255)
GOLD = (222, 168, 40, 255)
GOLD_L = (255, 222, 96, 255)
GOLD_LL = (255, 246, 190, 255)
BLUE = (58, 123, 255, 255)
BLUE_L = (150, 196, 255, 255)
RED = (214, 52, 52, 255)
RED_L = (255, 130, 120, 255)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def gray(v, tint=(0, 2, 8)):
    v = max(0, min(255, int(v)))
    return (max(0, min(255, v + tint[0])), max(0, min(255, v + tint[1])), max(0, min(255, v + tint[2])), 255)


def outline(img, color=OUT):
    base = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if base.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and base.getpixel((nx, ny))[3] == 255:
                        px(img, x, y, color)
                        break


# Textura de la bola de aluminio (facetas de papel arrugado): el aluminio del cetro, la corona y la armadura la usa.
FOIL = Image.open(ROOT / "textures/entity/aluminum_ball.png").convert("RGBA")
_POS = None


def foil(x, y, factor=1.0):
    r, g, b, _ = FOIL.getpixel((60 + x % 48, 34 + y % 26))
    return (min(255, int(r * factor)), min(255, int(g * factor)), min(255, int(b * factor)), 255)


def metal(light, rnd, spread=10):
    """Aluminio pulido: light 0 (sombra) .. 1 (brillo). Dentro de face() usa la textura de papel de aluminio."""
    if _POS is not None:
        return foil(_POS[0], _POS[1], 0.74 + 0.5 * light)
    return gray(110 + 135 * light + rnd.randint(-spread, spread))


def gold(light):
    if light > 0.85:
        return GOLD_LL
    if light > 0.6:
        return GOLD_L
    if light > 0.3:
        return GOLD
    return GOLD_D


# ------------------------------------------------------------------ trono (bloque)
def throne_metal():
    """Placas de aluminio pulido con remaches y juntas."""
    img = Image.new("RGBA", (16, 16))
    rnd = random.Random(31)
    for y in range(16):
        for x in range(16):
            light = 0.55 + 0.25 * math.sin((x - y) * 0.45) + 0.1 * math.sin(x * 1.7)
            if y % 8 == 0 or x % 8 == 0:
                light -= 0.35                       # junta entre placas
            elif y % 8 == 1 or x % 8 == 1:
                light += 0.15                       # bisel iluminado
            img.putpixel((x, y), metal(max(0.0, min(1.0, light)), rnd, 6))
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px(img, x, y, (236, 240, 248, 255))         # remaches
        px(img, x + 1, y + 1, (96, 100, 112, 255))
    img.save(ROOT / "textures/block/throne_metal.png")


def throne_cushion():
    """Terciopelo rojo con capitoné (rombos) y botones dorados."""
    img = Image.new("RGBA", (16, 16))
    rnd = random.Random(17)
    for y in range(16):
        for x in range(16):
            d = (abs((x % 8) - 4) + abs((y % 8) - 4)) / 8.0   # 0 en el centro del rombo, 1 en las costuras
            v = 1.0 - 0.6 * d
            r = int(120 + 95 * v + rnd.randint(-8, 8))
            img.putpixel((x, y), (r, int(r * 0.13), int(r * 0.2), 255))
            if abs((x % 8) - 4) + abs((y % 8) - 4) == 4:
                img.putpixel((x, y), (92, 12, 24, 255))        # costura
    for (x, y) in ((0, 0), (8, 0), (0, 8), (8, 8), (4, 4), (12, 4), (4, 12), (12, 12)):
        if (x + y) % 8 == 0:
            px(img, x, y, GOLD_L)
    img.save(ROOT / "textures/block/throne_cushion.png")


def throne_gold():
    img = Image.new("RGBA", (16, 16))
    rnd = random.Random(5)
    for y in range(16):
        for x in range(16):
            light = 0.55 + 0.3 * math.sin((x + y) * 0.5) + rnd.uniform(-0.08, 0.08)
            img.putpixel((x, y), gold(light))
    img.save(ROOT / "textures/block/throne_gold.png")


# ------------------------------------------------------------------ corona (entidad): mitad metal, mitad joya
def crown_texture():
    """32x16. Se tiñe con el color de cada pieza: debe ser casi blanca con sombras suaves."""
    img = Image.new("RGBA", (32, 16))
    rnd = random.Random(3)
    for y in range(16):
        for x in range(16):
            light = 0.8 - 0.35 * (y / 15.0) + 0.12 * math.sin(x * 0.9)
            if y in (2, 13):
                light += 0.2                       # filetes brillantes del aro
            if y in (3, 12):
                light -= 0.25
            v = int(170 + 85 * max(0.0, min(1.0, light)) + rnd.randint(-5, 5))
            img.putpixel((x, y), (v, v, v, 255))
        for x in range(16, 32):
            fx, fy = x - 16, y
            # facetas de una joya tallada: cuatro triángulos con distinta luz y brillo arriba a la izquierda
            if fx + fy < 16:
                v = 235 if fx > fy else 205
            else:
                v = 165 if fx > fy else 140
            if fx + fy < 5:
                v = 255
            img.putpixel((x, y), (v, v, v, 255))
    img.save(ROOT / "textures/entity/crown.png")


# ------------------------------------------------------------------ items (pixel art con sombreado de bordes)
RAMPS = {
    '#': [(120, 130, 150), (170, 180, 198), (208, 214, 227), (232, 237, 245), (255, 255, 255)],   # aluminio
    'g': [(120, 74, 14), (176, 116, 22), (234, 184, 46), (255, 224, 104), (255, 247, 200)],     # oro
    'S': [(54, 60, 78), (78, 86, 108), (104, 114, 138), (136, 148, 172), (186, 196, 214)],      # suela / sombra
    'R': [(120, 16, 34), (170, 28, 44), (222, 52, 62), (255, 120, 120), (255, 214, 214)],       # rubi
    'B': [(20, 54, 150), (36, 88, 206), (64, 134, 250), (150, 204, 255), (230, 246, 255)],      # zafiro
    'E': [(18, 96, 52), (30, 140, 74), (60, 200, 104), (150, 240, 170), (226, 255, 232)],       # esmeralda
    'w': [(170, 176, 190), (206, 210, 222), (236, 238, 245), (250, 250, 253), (255, 255, 255)], # cinta blanca
    'r': [(110, 14, 30), (150, 22, 40), (196, 40, 52), (232, 78, 82), (255, 140, 130)],         # cinta roja
}
OUTLINE = (30, 34, 48, 255)


def sprite(name, rows, base=2):
    """rows: 16 cadenas de 16 caracteres; '.' vacio. Cada material usa su rampa de 5 tonos: borde superior/izquierdo
    mas claro, inferior/derecho mas oscuro (y un brillo extra en la esquina), y contorno oscuro alrededor."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    h, w = len(rows), len(rows[0])

    def at(x, y):
        return rows[y][x] if 0 <= x < w and 0 <= y < h else '.'

    for y in range(h):
        for x in range(w):
            ch = at(x, y)
            if ch == '.':
                continue
            t = base
            if at(x, y - 1) != ch:
                t += 1
            if at(x - 1, y) != ch:
                t += 1
            if at(x, y + 1) != ch:
                t -= 1
            if at(x + 1, y) != ch:
                t -= 1
            if at(x - 1, y - 1) != ch and at(x, y - 1) == ch and at(x - 1, y) == ch:
                t += 1
            t = max(0, min(4, t))
            if ch == '#':
                img.putpixel((x, y), foil(x + 7, y + 3, (0.84, 0.96, 1.08, 1.16, 1.24)[t]))
                continue
            r, g, b = RAMPS[ch][t]
            img.putpixel((x, y), (r, g, b, 255))
    outline(img, OUTLINE)
    img.save(ROOT / f"textures/item/{name}.png")


CROWN = ["." * 16] * 2 + [
    ".......ww.......",
    "..w....##....w..",
    "..#w...##...w#..",
    "..##w.w##w.w##..",
    ".w############w.",
    ".##############."[:16],
    ".gggggggggggggg.",
    ".ggRRggBBggRRgg.",
    ".gggggggggggggg.",
] + ["." * 16] * 5
def wrap(rows, top=1):
    """Centra un dibujo de 14 columnas en el lienzo de 16x16 (deja 1 px a cada lado para el contorno)."""
    out = ["." * 16] * top + ["." + r + "." for r in rows]
    return out + ["." * 16] * (16 - len(out))


# Los iconos ocupan todo el lienzo (14x14 + contorno), como las armaduras de vanilla.
CHESTPLATE = wrap([
    "gggg......gggg",
    "#####....#####",
    "######gg######",
    "##g###BB###g##",
    "##g###BB###g##",
    "##g########g##",
    "##g########g##",
    "##g########g##",
    "ggg########ggg",
    "..g########g..",
    "..##########..",
    "..##########..",
    "..gggggggggg..",
])
LEGGINGS = wrap([
    "gggggggggggggg",
    "gggggggBBggggg"[:7] + "BBggggg",
    "##############",
    "##############",
    "#####....#####",
    "####......####",
    "####......####",
    "gggg......gggg",
    "####......####",
    "####......####",
    "####......####",
    "####......####",
    "####......####",
    "SSSS......SSSS",
])
BOOTS = wrap([
    "..............",
    "..............",
    "ggggg....ggggg",
    "#####....#####",
    "#####....#####",
    "#####....#####",
    "#####....#####",
    "######..######",
    "######..######",
    "######..######",
    "SSSSSS..SSSSSS",
    "..............",
], top=2)


def armor_icon(name, shape):
    sprite(name, shape)


def crown_item():
    sprite("aluminum_crown", CROWN)


def scepter_item():
    """Cetro VERTICAL y simetrico: corona, orbe y vara sobre el mismo eje (x = 7..8)."""
    grid = [list("." * 16) for _ in range(16)]

    def put(x, y, ch):
        if 0 <= x < 16 and 0 <= y < 16:
            grid[y][x] = ch

    cx, cy, r = 8.0, 5.3, 3.3
    for y in range(16):
        for x in range(16):
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                put(x, y, '#')
    for x in (5, 7, 8, 10):
        put(x, 0, 'g')
    for x in range(5, 11):
        put(x, 1, 'g')
    for x in range(6, 10):
        put(x, 9, 'g')
    for y in range(10, 16):
        ring = y in (11, 13)
        put(7, y, 'g' if ring else '#')
        put(8, y, 'g' if ring else 'S')
    sprite("aluminum_scepter", ["".join(r) for r in grid])


def medal_item():
    grid = [list("." * 16) for _ in range(16)]

    def put(x, y, ch):
        if 0 <= x < 16 and 0 <= y < 16:
            grid[y][x] = ch

    # cinta en V (roja y blanca)
    for i in range(7):
        for dx in range(3):
            put(2 + i + dx, i, 'r')
            put(13 - i - dx, i, 'w')
    # medallon de oro con bola de aluminio al centro
    cx, cy = 7.5, 10.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= 4.8:
                put(x, y, 'g')
            if d <= 2.4:
                put(x, y, '#')
    sprite("tribute_medal", ["".join(r) for r in grid])


# ------------------------------------------------------------------ armadura puesta (64x32)
def face(img, x0, y0, w, h, painter):
    for y in range(h):
        for x in range(w):
            global _POS
            _POS = (x0 + x, y0 + y)
            c = painter(x, y, w, h)
            _POS = None
            if c is not None:
                img.putpixel((x0 + x, y0 + y), c)


def cube_faces(u, v, w, h, d):
    """Caras de un cubo del modelo humanoide: devuelve [(x, y, ancho, alto, nombre)]."""
    return [
        (u + d, v, w, d, "top"), (u + d + w, v, w, d, "bottom"),
        (u, v + d, d, h, "right"), (u + d, v + d, w, h, "front"),
        (u + d + w, v + d, d, h, "left"), (u + d + w + d, v + d, w, h, "back"),
    ]


def plate_painter(rnd, trim_rows=(), trim_cols_edge=True, emblem=None):
    def paint(x, y, w, h):
        light = 0.72 - 0.25 * (y / max(1, h - 1)) + 0.1 * math.sin(x * 1.3)
        c = metal(max(0.0, min(1.0, light)), rnd, 7)
        if y in trim_rows or (trim_cols_edge and (x == 0 or x == w - 1)):
            c = gold(0.65 - 0.02 * y)
        if emblem is not None:
            e = emblem(x, y, w, h)
            if e is not None:
                c = e
        return c
    return paint


def chest_emblem(x, y, w, h):
    # pequeña corona dorada con joya azul en el centro del pecho
    cx = w // 2
    if y == 3 and x in (cx - 2, cx, cx + 1) and w >= 8:
        return GOLD_L
    if y == 4 and cx - 2 <= x <= cx + 1 and w >= 8:
        return GOLD
    if y == 5 and x in (cx - 1, cx) and w >= 8:
        return BLUE
    return None


def armor_layers():
    rnd = random.Random(99)
    # --- capa humanoide: corona (cabeza), peto (cuerpo + brazos), botas (parte baja de las piernas)
    img = Image.new("RGBA", (64, 32), CLEAR)

    # Corona: aro alrededor de la parte de arriba de la cabeza (caras laterales), abierta por arriba.
    for (x0, y0, w, h, name) in cube_faces(0, 0, 8, 8, 8):
        if name in ("top", "bottom"):
            continue

        def crown_band(x, y, w_, h_, front=(name == "front")):
            if y == 0:
                return (250, 252, 255, 255) if x % 3 == 1 else None          # perlas en las puntas
            if y == 1:
                return metal(0.85, rnd, 4) if x % 3 != 0 else None          # puntas
            if 2 <= y <= 3:
                if front and y == 3 and x in (3, 4):
                    return BLUE if x == 3 else BLUE_L
                if front and y == 3 and x in (1, 6):
                    return RED
                return metal(0.95 - 0.2 * (y - 2), rnd, 5) if y == 2 else gold(0.7)
            return None
        face(img, x0, y0, w, h, crown_band)

    # Peto: cuerpo (16,16) 8x12x4 y brazos (40,16) 4x12x4.
    for (x0, y0, w, h, name) in cube_faces(16, 16, 8, 12, 4):
        emblem = chest_emblem if name == "front" else None
        face(img, x0, y0, w, h, plate_painter(rnd, trim_rows=(0, h - 1) if h > 4 else (), emblem=emblem))
    for (x0, y0, w, h, name) in cube_faces(40, 16, 4, 12, 4):
        def arm(x, y, w_, h_, face_name=name):
            if face_name == "bottom":
                return None                                   # la cara inferior del brazo quedaba como una losa bajo la mano
            if h_ > 4 and y > 7:
                return None                                   # el brazo solo cubre hombro y brazo
            light = 0.8 - 0.05 * y
            c = metal(light, rnd, 6)
            if h_ > 4 and y in (0, 7):
                c = gold(0.7)
            return c
        face(img, x0, y0, w, h, arm)

    # Botas: piernas (0,16) 4x12x4, solo la parte de abajo (filas 7..11 de las caras laterales).
    for (x0, y0, w, h, name) in cube_faces(0, 16, 4, 12, 4):
        def boot(x, y, w_, h_):
            if name == "top":
                return None
            if h_ > 4 and y < 7:
                return None
            c = metal(0.75 - 0.05 * (y - 7), rnd, 6)
            if h_ > 4 and y == 7:
                c = gold(0.75)
            return c
        face(img, x0, y0, w, h, boot)
    img.save(ROOT / "textures/entity/equipment/humanoid/aluminum_king.png")

    # --- capa de pantalones: cintura (cuerpo) y piernas
    img = Image.new("RGBA", (64, 32), CLEAR)
    for (x0, y0, w, h, name) in cube_faces(16, 16, 8, 12, 4):
        def waist(x, y, w_, h_):
            if name in ("top",):
                return None
            if h_ > 4 and y < 8:
                return None
            c = metal(0.7 - 0.04 * (y - 8), rnd, 6)
            if h_ > 4 and y == 8:
                c = gold(0.75)                                   # cinturón
            if name == "front" and h_ > 4 and y == 9 and x in (3, 4):
                c = BLUE                                         # hebilla
            return c
        face(img, x0, y0, w, h, waist)
    for (x0, y0, w, h, name) in cube_faces(0, 16, 4, 12, 4):
        def leg(x, y, w_, h_):
            if name == "bottom":
                return None
            if h_ > 4 and y > 9:
                return None
            c = metal(0.78 - 0.03 * y + 0.08 * math.sin(x), rnd, 6)
            if h_ > 4 and y == 4 and name == "front":
                c = gold(0.7)                                    # rodillera
            return c
        face(img, x0, y0, w, h, leg)
    img.save(ROOT / "textures/entity/equipment/humanoid_leggings/aluminum_king.png")


throne_metal()
throne_cushion()
throne_gold()
crown_texture()
crown_item()
armor_icon("aluminum_chestplate", CHESTPLATE)
armor_icon("aluminum_leggings", LEGGINGS)
armor_icon("aluminum_boots", BOOTS)
scepter_item()
medal_item()
armor_layers()
print("Texturas de la v1.1 generadas en", ROOT)
