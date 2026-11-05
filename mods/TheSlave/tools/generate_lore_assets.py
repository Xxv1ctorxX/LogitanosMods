"""Genera los assets de la ampliación oscura: bloques infectados y decorativos, altar, objetos rituales, brújula
(32 fotogramas), skins del Notario y de los fusionados, modelos, blockstates, loot tables, tags, recetas y textos.

Solo biblioteca estándar. Uso (desde mods/TheSlave):  python -I tools/generate_lore_assets.py
Importa utilidades de generate_assets.py; no ejecutar su generador completo, que sobrescribe assets protegidos.
"""
import json
import math
import os
import random
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate_assets as g  # noqa: E402
import arte_items  # noqa: E402
import modelos3d  # noqa: E402
import arte_hogar  # noqa: E402

ASSETS = g.ASSETS
DATA = g.DATA
TEX = os.path.join(ASSETS, "textures")
ES = os.path.join(ASSETS, "lang", "es_es.json")

lienzo, rect, guardar, px_, contorno, escribe = g.lienzo, g.rect, g.guardar, g.px_, g.contorno, g.escribe

MORADO = (117, 80, 139, 255)
MORADO_OSC = (67, 43, 80, 255)
CENIZA = (172, 170, 162, 255)


def ruido(color, var, seed):
    """Ruido fino más manchas de 4x4 (sin costuras: la rejilla gruesa se repite cada 16 px)."""
    r = random.Random(seed)
    grueso = [[r.randint(-var, var) for _ in range(4)] for _ in range(4)]
    l = lienzo(16, 16)
    for y in range(16):
        for x in range(16):
            d = r.randint(-var // 2, var // 2) + grueso[y // 4][x // 4]
            l[y][x] = (max(0, min(255, color[0] + d)), max(0, min(255, color[1] + d)), max(0, min(255, color[2] + d)), 255)
    return l


def grietas(l, seed, n, largo=7):
    r = random.Random(seed)
    for _ in range(n):
        x, y = r.randint(1, 14), r.randint(1, 14)
        for i in range(largo):
            if 0 <= x < 16 and 0 <= y < 16 and l[y][x][3]:
                l[y][x] = MORADO if i % 3 else MORADO_OSC
            x += r.choice((-1, 0, 1))
            y += r.choice((0, 1))


def motas(l, seed, n, color=CENIZA):
    r = random.Random(seed)
    for _ in range(n):
        x, y = r.randint(0, 15), r.randint(0, 15)
        if l[y][x][3]:
            l[y][x] = color


def disco(l, cx, cy, rad, color):
    for y in range(16):
        for x in range(16):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= rad * rad:
                l[y][x] = color


def leer_png(ruta):
    d = open(ruta, "rb").read()
    pos, idat = 8, b""
    w = h = 0
    while pos < len(d):
        n = struct.unpack(">I", d[pos:pos + 4])[0]
        t = d[pos + 4:pos + 8]
        c = d[pos + 8:pos + 8 + n]
        pos += 12 + n
        if t == b"IHDR":
            w, h = struct.unpack(">II", c[:8])
        elif t == b"IDAT":
            idat += c
    raw = zlib.decompress(idat)
    st = w * 4 + 1
    return [[tuple(raw[y * st + 1 + x * 4:y * st + 5 + x * 4]) for x in range(w)] for y in range(h)]


# ---------------------------------------------------------------- terreno infectado
def tex_bloque(nombre, l):
    guardar(os.path.join(TEX, "block", nombre + ".png"), l)


def terreno():
    l = ruido((58, 58, 64), 7, 11)
    grietas(l, 12, 4)
    motas(l, 13, 8)
    tex_bloque("piedra_infectada", l)

    l = ruido((44, 44, 54), 5, 21)
    for y in range(0, 16, 4):
        for x in range(16):
            l[y][x] = (30, 30, 38, 255)
    grietas(l, 22, 3, 6)
    tex_bloque("pizarra_infectada", l)

    l = ruido((92, 76, 64), 9, 31)
    r = random.Random(32)
    for _ in range(14):
        x, y = r.randint(0, 15), r.randint(0, 15)
        l[y][x] = (24, 20, 22, 255)
    grietas(l, 33, 3, 5)
    tex_bloque("tierra_infectada", l)

    top = ruido((100, 112, 94), 8, 41)
    motas(top, 42, 10, (130, 136, 122, 255))
    grietas(top, 43, 2, 4)
    tex_bloque("cesped_infectado_top", top)
    lado = ruido((92, 76, 64), 9, 44)
    for y in range(4):
        for x in range(16):
            lado[y][x] = (100, 112, 94, 255) if (x + y) % 5 else (80, 92, 78, 255)
    r = random.Random(45)
    for x in range(0, 16, 3):
        lado[4 + r.randint(0, 1)][x] = (24, 20, 22, 255)
    grietas(lado, 46, 2, 4)
    tex_bloque("cesped_infectado_side", lado)

    l = ruido((72, 72, 76), 14, 51)
    r = random.Random(52)
    for _ in range(26):
        x, y = r.randint(0, 14), r.randint(0, 14)
        c = r.choice(((48, 48, 52, 255), (96, 96, 98, 255), (60, 60, 64, 255)))
        rect(l, x, y, x + 2, y + 2, c)
    for _ in range(4):
        l[r.randint(0, 15)][r.randint(0, 15)] = MORADO
    tex_bloque("grava_infectada", l)

    l = ruido((150, 140, 120), 8, 61)
    r = random.Random(62)
    for _ in range(18):
        x, y = r.randint(0, 14), r.randint(0, 14)
        rect(l, x, y, x + 2, y + 1, (96, 88, 80, 255))
    grietas(l, 63, 3, 5)
    tex_bloque("arena_infectada", l)

    l = ruido((92, 34, 38), 10, 71)
    r = random.Random(72)
    for _ in range(20):
        x, y = r.randint(0, 14), r.randint(0, 14)
        rect(l, x, y, x + 2, y + 2, (44, 16, 20, 255))
    grietas(l, 73, 4, 8)
    motas(l, 74, 6)
    tex_bloque("netherrack_infectada", l)

    l = ruido((24, 22, 28), 4, 81)
    grietas(l, 82, 4, 8)
    tex_bloque("piedra_negra_infectada", l)


# ---------------------------------------------------------------- decoración
def decoracion():
    r = random.Random(91)
    # raíz de ceniza: corteza negra con nudos morados
    l = ruido((22, 19, 24), 4, 92)
    for x in range(0, 16, 3):
        for y in range(16):
            l[y][x] = (12, 10, 14, 255)
    for _ in range(3):
        cx, cy = r.randint(2, 13), r.randint(2, 13)
        rect(l, cx, cy, cx + 2, cy + 2, (110, 46, 160, 255))
        l[cy][cx] = (170, 90, 230, 255)
    tex_bloque("raiz_ceniza", l)

    # raicillas: filamentos finos sobre transparente (plano cruzado)
    l = lienzo(16, 16)
    for x0 in (3, 7, 11, 14):
        x = x0
        for y in range(16):
            l[y][x] = (24, 20, 26, 255)
            if r.random() < 0.3:
                x = max(0, min(15, x + r.choice((-1, 1))))
    for _ in range(5):
        l[r.randint(0, 15)][r.randint(0, 15)] = MORADO
    tex_bloque("raicillas_infectadas", l)

    l = ruido((112, 110, 108), 6, 93)
    motas(l, 94, 30, (64, 62, 66, 255))
    tex_bloque("losa_ceniza", l)

    # velas: cera gris en la mitad superior izquierda, llama azul en la inferior derecha
    l = lienzo(16, 16)
    rect(l, 0, 0, 8, 8, (186, 182, 168, 255))
    rect(l, 0, 0, 1, 8, (150, 146, 134, 255))
    rect(l, 7, 0, 8, 8, (214, 210, 196, 255))
    rect(l, 8, 8, 16, 16, (88, 170, 255, 255))
    rect(l, 10, 10, 14, 14, (190, 230, 255, 255))
    tex_bloque("velas_notario", l)

    # urna: recipiente negro cerrado con cera y tela, brillo azul entre las juntas
    l = ruido((30, 28, 34), 4, 95)
    rect(l, 0, 6, 16, 8, (156, 152, 138, 255))
    rect(l, 0, 8, 16, 9, (80, 170, 255, 255))
    for x in range(0, 16, 4):
        l[3][x] = (80, 170, 255, 255)
    tex_bloque("urna_animas", l)

    # archivo: madera oscura, huecos numerados, pergaminos y cintas
    l = ruido((58, 38, 30), 5, 96)
    for y in (0, 5, 10, 15):
        rect(l, 0, y, 16, y + 1, (30, 20, 16, 255))
    for fila in (1, 6, 11):
        for x in range(1, 15, 3):
            rect(l, x, fila, x + 2, fila + 3, (200, 185, 150, 255) if (x + fila) % 2 else (150, 30, 36, 255))
    tex_bloque("archivo_condenas", l)

    # mesa: tablero quemado con un contrato abierto y marca de sello
    l = ruido((50, 34, 28), 5, 97)
    rect(l, 3, 3, 13, 13, (205, 190, 150, 255))
    for y in (5, 7, 9):
        rect(l, 4, y, 11, y + 1, (96, 62, 40, 255))
    rect(l, 10, 10, 12, 12, (140, 30, 36, 255))
    tex_bloque("mesa_notario", l)

    # lámpara: jaula de hierro ennegrecido con luz azul
    l = ruido((26, 26, 32), 3, 98)
    rect(l, 4, 3, 12, 13, (110, 190, 255, 255))
    rect(l, 6, 5, 10, 11, (210, 238, 255, 255))
    for x in (3, 8, 12):
        rect(l, x, 0, x + 1, 16, (22, 22, 28, 255))
    rect(l, 0, 7, 16, 8, (22, 22, 28, 255))
    tex_bloque("lampara_animas", l)

    # piedra del registro: pizarra con las tres marcas verticales de ceniza
    l = ruido((38, 38, 46), 4, 99)
    for x in (5, 8, 11):
        rect(l, x, 4, x + 1, 12, CENIZA)
    tex_bloque("piedra_registro", l)


# ---------------------------------------------------------------- altar
def altar():
    l = ruido((40, 40, 48), 4, 101)
    for y in (0, 5, 10, 15):
        rect(l, 0, y, 16, y + 1, (22, 22, 28, 255))
    for y, off in ((1, 4), (6, 10), (11, 4)):
        for x in range(off, 16, 8):
            rect(l, x, y, x + 1, y + 4, (22, 22, 28, 255))
    tex_bloque("altar_side", l)

    l = ruido((52, 52, 60), 3, 102)
    for a in range(0, 360, 6):
        x = int(8 + 5.5 * math.cos(math.radians(a)))
        y = int(8 + 5.5 * math.sin(math.radians(a)))
        if 0 <= x < 16 and 0 <= y < 16:
            l[y][x] = MORADO_OSC
    motas(l, 103, 12)
    tex_bloque("altar_top", l)

    l = ruido((46, 46, 54), 3, 104)
    for a in range(0, 360, 6):
        x = int(8 + 6.5 * math.cos(math.radians(a)))
        y = int(8 + 6.5 * math.sin(math.radians(a)))
        if 0 <= x < 16 and 0 <= y < 16:
            l[y][x] = MORADO
    for x in (6, 8, 10):
        rect(l, x, 4, x + 1, 12, CENIZA)
    tex_bloque("altar_front", l)

    l = ruido((200, 196, 182), 6, 105)
    for y in range(0, 16, 5):
        rect(l, 0, y, 16, y + 1, (160, 156, 144, 255))
    tex_bloque("altar_vela", l)

    l = ruido((39, 26, 47), 4, 106)
    for y in range(16):
        rect(l, 4, y, 12, y + 1, (73, 49, 83, 255))
    rect(l, 6, 5, 10, 13, (154, 175, 184, 255))
    tex_bloque("altar_llama", l)


# ---------------------------------------------------------------- objetos
def item(nombre, l):
    guardar(os.path.join(TEX, "item", nombre + ".png"), l)


def objetos():
    # relicario: marco dorado, cristal y un hilo de alma azul
    l = lienzo(16, 16)
    rect(l, 5, 2, 11, 15, (190, 160, 60, 255))
    rect(l, 6, 4, 10, 13, (170, 214, 236, 255))
    rect(l, 6, 1, 10, 3, (214, 184, 80, 255))
    for x, y in ((8, 5), (8, 6), (7, 7), (8, 8), (9, 9), (8, 10), (8, 11)):
        l[y][x] = (96, 190, 255, 255)
    l[8][8] = (210, 240, 255, 255)
    contorno(l, (40, 30, 20, 255))
    item("relicario_animas", l)

    # ofrenda: plato negro con ceniza, huesos y una llama azul
    l = lienzo(16, 16)
    for y in range(16):
        for x in range(16):
            if ((x + 0.5 - 8) / 7.0) ** 2 + ((y + 0.5 - 11) / 3.2) ** 2 <= 1:
                l[y][x] = (34, 34, 40, 255)
    rect(l, 4, 10, 12, 12, (24, 24, 30, 255))
    rect(l, 5, 9, 11, 11, (150, 148, 142, 255))
    rect(l, 4, 9, 7, 10, (226, 220, 200, 255))
    rect(l, 9, 8, 12, 9, (226, 220, 200, 255))
    for y in range(3, 8):
        l[y][8] = (90, 170, 255, 255)
    l[5][8] = (200, 236, 255, 255)
    l[4][7] = (90, 170, 255, 255)
    contorno(l, (16, 16, 20, 255))
    item("ofrenda_muerte", l)

    # tributo: pergamino plegado con cinta negra y una moneda de oro
    l = lienzo(16, 16)
    rect(l, 2, 3, 13, 13, (196, 180, 142, 255))
    rect(l, 2, 3, 13, 4, (222, 206, 164, 255))
    rect(l, 7, 3, 9, 13, (28, 28, 34, 255))
    disco(l, 11, 11, 2.4, (214, 176, 54, 255))
    l[10][10] = (250, 230, 130, 255)
    contorno(l, (46, 32, 24, 255))
    item("tributo_condena", l)

    # sello: lacre rojo oscuro con tres marcas de ceniza
    l = lienzo(16, 16)
    disco(l, 8, 7.5, 5.6, (110, 24, 60, 255))
    disco(l, 8, 7.5, 4.2, (140, 40, 82, 255))
    for x in (6, 8, 10):
        rect(l, x, 5, x + 1, 11, (224, 218, 200, 255))
    rect(l, 7, 13, 9, 16, (28, 28, 34, 255))
    contorno(l, (30, 10, 20, 255))
    item("sello_almas", l)

    # doble condena: dos lacres superpuestos
    l = lienzo(16, 16)
    disco(l, 5.8, 8, 4.6, (70, 40, 120, 255))
    disco(l, 10.2, 8, 4.6, (110, 24, 60, 255))
    for x in (4, 6, 9, 11):
        rect(l, x, 6, x + 1, 11, (224, 218, 200, 255))
    rect(l, 7, 13, 9, 16, (28, 28, 34, 255))
    contorno(l, (24, 10, 24, 255))
    item("tributo_doble_condena", l)


def brujula(k):
    l = lienzo(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 7.4:
                l[y][x] = (44, 42, 48, 255) if d > 6.0 else (22, 20, 26, 255)
    for x, y in ((7, 1), (8, 1), (14, 7), (14, 8), (7, 14), (8, 14), (1, 7), (1, 8)):
        l[y][x] = (184, 115, 51, 255)
    a = k * 2 * math.pi / 32
    for i in range(0, 56):
        t = i * 0.1
        x = int(math.floor(8 + math.sin(a) * t))
        y = int(math.floor(8 - math.cos(a) * t))
        if 0 <= x < 16 and 0 <= y < 16:
            l[y][x] = (96, 180, 255, 255) if t > 4.0 else (232, 226, 208, 255)
    for i in range(0, 20):
        t = i * 0.1
        x = int(math.floor(8 - math.sin(a) * t))
        y = int(math.floor(8 + math.cos(a) * t))
        if 0 <= x < 16 and 0 <= y < 16:
            l[y][x] = (170, 164, 150, 255)
    rect(l, 7, 7, 9, 9, (170, 30, 40, 255))
    contorno(l, (14, 12, 16, 255))
    return l


# ---------------------------------------------------------------- skins
def skins():
    g.skin(os.path.join(TEX, "entity", "notario.png"), (196, 192, 184, 255), (56, 56, 62, 255), (34, 34, 40, 255),
           (118, 118, 128, 255))
    ruta = os.path.join(TEX, "entity", "notario.png")
    l = leer_png(ruta)
    # Capucha carbon, mascara de hueso agrietado y manos asimetricas.
    rect(l, 8, 8, 16, 16, (21, 18, 26, 255))
    rect(l, 9, 10, 15, 15, (166, 157, 137, 255))
    rect(l, 10, 11, 12, 12, (24, 20, 29, 255))
    rect(l, 13, 11, 15, 12, (24, 20, 29, 255))
    for x,y in ((12,10),(12,11),(11,12),(12,13),(12,14)):
        l[y][x] = (62, 49, 64, 255)
    for y in range(20, 32):
        rect(l, 23, y, 25, y+1, (28, 23, 32, 255))
    rect(l, 23, 23, 25, 25, (73, 49, 83, 255))
    rect(l, 44, 28, 48, 32, (42, 33, 40, 255))
    rect(l, 36, 60, 40, 64, (148, 139, 122, 255))
    rect(l, 20, 31, 28, 32, (22, 22, 28, 255))
    guardar(ruta, l)
    base = [leer_png(os.path.join(TEX, "entity", "esclavo_%d.png" % i)) for i in range(3)]
    for a in range(3):
        for b in range(3):
            res = [fila[:] for fila in base[a]]
            # Cada cara UV de cabeza y torso: frontal y espalda se dividen en sentidos opuestos.
            # No dividir globalmente la imagen: brazos y piernas tienen islas UV propias.
            regiones = [
                (12, 0, 16, 8), (20, 0, 24, 8), (12, 8, 16, 16),
                (16, 8, 24, 16), (24, 8, 28, 16),
                (24, 16, 28, 20), (32, 16, 36, 20), (24, 20, 28, 32),
                (28, 20, 32, 32), (32, 20, 36, 32),
                (16, 48, 32, 64), (32, 48, 48, 64),
            ]
            for x0,y0,x1,y1 in regiones:
                for y in range(y0,y1):
                    for x in range(x0,x1):
                        res[y][x] = base[b][y][x]
            for x,y0,y1 in ((12,8,16),(28,8,16),(24,20,32),(36,20,32)):
                for y in range(y0,y1):
                    res[y][x] = (36,29,40,255) if y%3 else (115,105,122,255)
            guardar(os.path.join(TEX, "entity", "esclavo_f_%d_%d.png" % (a, b)), res)


# ---------------------------------------------------------------- modelos
CARAS = ("down", "up", "north", "south", "west", "east")


def cara(tex, uv=(0, 0, 16, 16)):
    return {"texture": tex, "uv": list(uv)}


def caja(frm, to, tex="#t", uv=(0, 0, 16, 16), caras=None):
    el = {"from": list(frm), "to": list(to), "faces": {d: cara(tex, uv) for d in CARAS}}
    if caras:
        for d, v in caras.items():
            el["faces"][d] = v
    return el


def modelo_bloque(nombre, texturas, elementos):
    t = dict(texturas)
    t.setdefault("particle", list(texturas.values())[0])
    escribe(os.path.join(ASSETS, "models", "block", nombre + ".json"), {"textures": t, "elements": elementos})


def item_bloque(nombre, modelo=None):
    escribe(os.path.join(ASSETS, "items", nombre + ".json"),
            {"model": {"type": "minecraft:model", "model": "theslave:block/" + (modelo or nombre)}})


def estado_simple(nombre, modelo=None):
    escribe(os.path.join(ASSETS, "blockstates", nombre + ".json"),
            {"variants": {"": {"model": "theslave:block/" + (modelo or nombre)}}})


def cubo(nombre, tex=None):
    escribe(os.path.join(ASSETS, "models", "block", nombre + ".json"),
            {"parent": "minecraft:block/cube_all", "textures": {"all": "theslave:block/" + (tex or nombre)}})
    estado_simple(nombre)
    item_bloque(nombre)


def modelos():
    for n in ("piedra_infectada", "pizarra_infectada", "tierra_infectada", "grava_infectada", "arena_infectada",
              "netherrack_infectada", "piedra_negra_infectada", "raiz_ceniza", "archivo_condenas", "piedra_registro"):
        cubo(n)
    escribe(os.path.join(ASSETS, "models", "block", "cesped_infectado.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": "theslave:block/cesped_infectado_top", "side": "theslave:block/cesped_infectado_side",
                     "bottom": "theslave:block/tierra_infectada"}})
    estado_simple("cesped_infectado")
    item_bloque("cesped_infectado")

    escribe(os.path.join(ASSETS, "models", "block", "raicillas_infectadas.json"),
            {"parent": "minecraft:block/cross", "textures": {"cross": "theslave:block/raicillas_infectadas"}})
    estado_simple("raicillas_infectadas")
    item_bloque("raicillas_infectadas")

    modelo_bloque("losa_ceniza", {"t": "theslave:block/losa_ceniza"}, [caja((0, 0, 0), (16, 2, 16))])
    estado_simple("losa_ceniza")
    item_bloque("losa_ceniza")

    velas = [caja((5, 0, 5), (7, 5, 7), "#t", (0, 0, 8, 8)), caja((9, 0, 6), (11, 3, 8), "#t", (0, 0, 8, 8)),
             caja((6, 0, 9), (8, 4, 11), "#t", (0, 0, 8, 8)),
             caja((5.5, 5, 5.5), (6.5, 6.5, 6.5), "#t", (8, 8, 16, 16)),
             caja((9.5, 3, 6.5), (10.5, 4.5, 7.5), "#t", (8, 8, 16, 16)),
             caja((6.5, 4, 9.5), (7.5, 5.5, 10.5), "#t", (8, 8, 16, 16))]
    modelo_bloque("velas_notario", {"t": "theslave:block/velas_notario"}, velas)
    estado_simple("velas_notario")
    item_bloque("velas_notario")

    modelo_bloque("urna_animas", {"t": "theslave:block/urna_animas"}, [
        caja((4, 0, 4), (12, 2, 12), "#t", (0, 0, 4, 4)),
        caja((3, 2, 3), (13, 10, 13), "#t", (0, 0, 16, 16), {"up": cara("#t", (0, 0, 4, 4))}),
        caja((3, 10, 3), (13, 11, 13), "#t", (0, 6, 16, 8)),
        caja((4, 11, 4), (12, 12, 12), "#t", (0, 0, 4, 4)),
    ])
    estado_simple("urna_animas")
    item_bloque("urna_animas")

    patas = [caja((1, 0, 1), (3, 12, 3), "#t", (0, 0, 2, 2)), caja((13, 0, 1), (15, 12, 3), "#t", (0, 0, 2, 2)),
             caja((1, 0, 13), (3, 12, 15), "#t", (0, 0, 2, 2)), caja((13, 0, 13), (15, 12, 15), "#t", (0, 0, 2, 2))]
    modelo_bloque("mesa_notario", {"t": "theslave:block/mesa_notario"}, [caja((0, 12, 0), (16, 16, 16))] + patas)
    estado_simple("mesa_notario")
    item_bloque("mesa_notario")

    marco = [caja((4, 0, 4), (12, 1, 12), "#t", (0, 0, 3, 3)),
             caja((4, 9, 4), (12, 10, 12), "#t", (0, 0, 3, 3)),
             caja((6, 10, 7), (7, 13, 9), "#t", (0, 0, 3, 3)),
             caja((9, 10, 7), (10, 13, 9), "#t", (0, 0, 3, 3)),
             caja((7, 12, 7), (9, 13, 9), "#t", (0, 0, 3, 3)),
             caja((7, 3, 7), (9, 7, 9), "#t", (9, 5, 11, 10))]
    for x,z in ((4,4),(11,4),(4,11),(11,11)):
        marco.append(caja((x,1,z), (x+1,9,z+1), "#t", (0,0,3,3)))
    modelo_bloque("lampara_animas", {"t": "theslave:block/lampara_animas"}, marco)
    estado_simple("lampara_animas")
    item_bloque("lampara_animas")

    # altar: base y losa con el símbolo del Notario al norte; cuatro velas; llamas como modelos aparte
    base = [
        caja((0, 0, 0), (16, 2, 16), "#side"),
        caja((2, 2, 2), (14, 8, 14), "#side", (0, 0, 16, 16), {"north": cara("#front")}),
        caja((1, 8, 1), (15, 9, 15), "#side", (0, 0, 16, 16), {"up": cara("#top")}),
        caja((1, 9, 1), (15, 12, 3), "#side"),
        caja((1, 9, 13), (15, 12, 15), "#side"),
        caja((1, 9, 3), (3, 12, 13), "#side"),
        caja((13, 9, 3), (15, 12, 13), "#side"),
    ]
    centros = ((2.5, 2.5), (13.5, 2.5), (2.5, 13.5), (13.5, 13.5))
    for cx, cz in centros:
        base.append(caja((cx - 1, 10, cz - 1), (cx + 1, 14, cz + 1), "#vela"))
    tx = {"side": "theslave:block/altar_side", "top": "theslave:block/altar_top", "front": "theslave:block/altar_front",
          "vela": "theslave:block/altar_vela", "llama": "theslave:block/altar_llama", "particle": "theslave:block/altar_side"}
    modelo_bloque("altar_ceniza", tx, base)
    for i, (cx, cz) in enumerate(centros):
        modelo_bloque("altar_llama_%d" % i, tx, [caja((cx - 0.5, 14, cz - 0.5), (cx + 0.5, 15.8, cz + 0.5), "#llama")])
    multipart = []
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        ap = {"model": "theslave:block/altar_ceniza"}
        if y:
            ap["y"] = y
        multipart.append({"when": {"facing": facing}, "apply": ap})
    for i in range(4):
        for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            multipart.append({"when": {"vela_%d" % i: "true", "facing": facing},
                              "apply": {"model": "theslave:block/altar_llama_%d" % i, "y": angle}})
    escribe(os.path.join(ASSETS, "blockstates", "altar_ceniza.json"), {"multipart": multipart})
    item_bloque("altar_ceniza")

    for n in ("relicario_animas", "ofrenda_muerte", "tributo_condena", "sello_almas", "tributo_doble_condena"):
        escribe(os.path.join(ASSETS, "models", "item", n + ".json"),
                {"parent": "minecraft:item/generated", "textures": {"layer0": "theslave:item/" + n}})
        escribe(os.path.join(ASSETS, "items", n + ".json"),
                {"model": {"type": "minecraft:model", "model": "theslave:item/" + n}})

    entradas = []
    for k in range(32):
        item("brujula_ceniza_%02d" % k, arte_items.brujula(k))
        escribe(os.path.join(ASSETS, "models", "item", "brujula_ceniza_%02d.json" % k),
                {"parent": "minecraft:item/generated", "textures": {"layer0": "theslave:item/brujula_ceniza_%02d" % k}})
        entradas.append({"threshold": 0.0 if k == 0 else k - 0.5,
                         "model": {"type": "minecraft:model", "model": "theslave:item/brujula_ceniza_%02d" % k}})
    entradas.append({"threshold": 31.5, "model": {"type": "minecraft:model", "model": "theslave:item/brujula_ceniza_00"}})
    escribe(os.path.join(ASSETS, "items", "brujula_ceniza.json"), {"model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone",
        "scale": 32.0, "entries": entradas}})


# ---------------------------------------------------------------- datos
PICO = ["piedra_infectada", "pizarra_infectada", "netherrack_infectada", "piedra_negra_infectada", "urna_animas",
        "lampara_animas", "piedra_registro", "altar_ceniza"]
PALA = ["tierra_infectada", "cesped_infectado", "grava_infectada", "arena_infectada", "losa_ceniza"]
HACHA = ["archivo_condenas", "mesa_notario", "raiz_ceniza", "raicillas_infectadas", "velas_notario"]


def datos():
    for n in PICO + PALA + HACHA:
        escribe(os.path.join(DATA, "loot_table", "blocks", n + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                       "entries": [{"type": "minecraft:item", "name": "theslave:" + n}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": "theslave:blocks/" + n})
    base = os.path.join(os.path.dirname(DATA), "minecraft", "tags", "block", "mineable")
    for tool, lst in (("pickaxe", PICO), ("shovel", PALA), ("axe", HACHA)):
        escribe(os.path.join(base, tool + ".json"), {"replace": False, "values": ["theslave:" + n for n in lst]})
    escribe(os.path.join(DATA, "recipe", "relicario_animas.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:glass", "minecraft:glass", "minecraft:glass", "minecraft:gold_ingot"],
        "result": {"id": "theslave:relicario_animas", "count": 1}})
    escribe(os.path.join(DATA, "recipe", "brujula_ceniza.json"), {"type": "theslave:brujula_ceniza"})


# ---------------------------------------------------------------- textos
def textos():
    d = json.load(open(ES, encoding="utf-8"))
    for k in [k for k in d if k.startswith("theslave.req.") or k.startswith("theslave.altar.")
              or k.startswith("item.theslave.contrato.lore")]:
        del d[k]
    d.update({
        "item.theslave.contrato": "Contrato de Condena",
        "item.theslave.relicario_animas": "Relicario de Ánimas",
        "item.theslave.ofrenda_muerte": "Ofrenda a la Muerte",
        "item.theslave.tributo_condena": "Tributo de la Condena",
        "item.theslave.sello_almas": "Sello de Almas",
        "item.theslave.tributo_doble_condena": "Tributo de la Doble Condena",
        "item.theslave.brujula_ceniza": "Brújula de Ceniza",
        "block.theslave.altar_ceniza": "Altar de Ceniza",
        "block.theslave.piedra_infectada": "Piedra infectada",
        "block.theslave.pizarra_infectada": "Pizarra profunda infectada",
        "block.theslave.tierra_infectada": "Tierra infectada",
        "block.theslave.cesped_infectado": "Césped infectado",
        "block.theslave.grava_infectada": "Grava infectada",
        "block.theslave.arena_infectada": "Arena infectada",
        "block.theslave.netherrack_infectada": "Netherrack infectada",
        "block.theslave.piedra_negra_infectada": "Piedra negra infectada",
        "block.theslave.raiz_ceniza": "Raíz de Ceniza",
        "block.theslave.raicillas_infectadas": "Raicillas infectadas",
        "block.theslave.losa_ceniza": "Losa de ceniza",
        "block.theslave.velas_notario": "Velas del Notario",
        "block.theslave.urna_animas": "Urna de ánimas",
        "block.theslave.archivo_condenas": "Archivo de condenas",
        "block.theslave.mesa_notario": "Mesa del Notario",
        "block.theslave.lampara_animas": "Lámpara de ánima",
        "block.theslave.piedra_registro": "Piedra del Registro",
        "entity.theslave.notario": "Notario de Ceniza",

        "theslave.brujula.buscando": "Buscando un enclave oculto... La aguja responderá al confirmar su entrada.",
        "theslave.brujula.espera": "La búsqueda sigue en curso. Espera a que responda la ceniza.",
        "theslave.contrato.usa_altar": "Este contrato se entrega ante un Altar de Ceniza, de noche.",
        "theslave.contrato.antiguo": "Un contrato antiguo: ya no invoca a nadie. Solo sirve para liberar a un esclavo.",
        "theslave.contrato.tooltip_antiguo": "Contrato antiguo. Solo libera a un esclavo.",
        "theslave.contrato.tooltip_grado": "Condena de grado %s",
        "theslave.contrato.tooltip_altar": "Se entrega ante un Altar de Ceniza, con su Ofrenda.",

        "theslave.alma.hambrienta": "Almas Hambrientas: %s",
        "theslave.alma.vigilante": "Almas Vigilantes: %s",
        "theslave.alma.acechante": "Almas Acechantes: %s",
        "theslave.alma.capturada.0": "Un Alma Hambrienta entra en el relicario.",
        "theslave.alma.capturada.1": "Un Alma Vigilante entra en el relicario.",
        "theslave.alma.capturada.2": "Un Alma Acechante entra en el relicario.",
        "theslave.relicario.capacidad": "Capacidad: %s / %s",

        "theslave.brujula.sin_rastro": "Sin rastro en esta región.",
        "theslave.brujula.cerca": "La ceniza está cerca.",
        "theslave.brujula.desciende": "El rastro desciende.",
        "theslave.brujula.asciende": "El rastro asciende.",
        "theslave.brujula.rastro": "La aguja se estremece.",

        "theslave.altar.vacio": "Un Altar de Ceniza. Pide un objeto ritual y una noche cerrada.",
        "theslave.altar.ocupado": "El altar ya está en ceremonia.",
        "theslave.altar.ya_ceremonia": "Ya tienes una ceremonia en curso.",
        "theslave.altar.no_noche": "La Muerte solo atiende de noche.",
        "theslave.altar.no_overworld": "Este altar solo responde en el Overworld.",
        "theslave.altar.amanece": "Amanece. La ceremonia se rompe sin consumir nada.",
        "theslave.altar.lejos": "Te has alejado demasiado del altar.",
        "theslave.altar.interrumpida": "La ceremonia se interrumpe. No se ha consumido nada.",
        "theslave.altar.sin_espacio_detras": "Detrás del altar falta espacio libre (3 de fondo, 3 de ancho, 3 de alto).",
        "theslave.altar.camino_tumba": "Deja paso entre el portal, el altar y la tumba. La lápida debe quedar delante del portal.",
        "theslave.altar.sin_espacio_delante": "Delante del altar falta espacio libre (2 de fondo, 3 de ancho, 3 de alto).",
        "theslave.altar.sin_suelo": "Falta suelo firme junto al altar.",
        "theslave.altar.sin_reserva": "No tienes ninguna compra pendiente en el Notario.",
        "theslave.altar.ofrenda_ajena": "Esta ofrenda no corresponde a tu contrato pendiente.",
        "theslave.altar.falta_contrato": "Falta el Contrato de Condena de esta compra.",
        "theslave.altar.falta_latigo": "Falta el látigo de esta compra, y debe estar sin desgastar.",
        "theslave.altar.ofrenda_incompleta": "La ofrenda no contiene todas las almas que exige tu contrato.",
        "theslave.altar.sin_tumba": "No hay cerca del altar una tumba tuya que este tributo pueda levantar.",
        "theslave.altar.sin_tumba_fusionada": "No hay cerca del altar una tumba de un esclavo fusionado tuya.",
        "theslave.altar.tributo_incompleto": "Falta un Tributo de la Condena con sus almas.",
        "theslave.altar.tributo_doble_incompleto": "Falta un Tributo de la Doble Condena con sus almas.",
        "theslave.altar.sello_incompleto": "Falta un Sello de Almas con sus almas.",
        "theslave.altar.fusion_pocos": "Hacen falta dos esclavos tuyos, sin fundir, a menos de 6 bloques y con paso libre.",
        "theslave.altar.fusion_cambio": "Algo cambió durante la ceremonia. No se ha consumido nada.",
        "theslave.altar.fusion_sin_paso": "Los esclavos no han podido llegar a su puesto. No se ha consumido nada.",

        "theslave.tumba.usa_altar": "Lleva el tributo al Altar de Ceniza, de noche, cerca de esta tumba.",
        "theslave.tumba.antigua": "Esta lápida pertenece a un vínculo que ya ha cambiado.",
        "theslave.tumba.recoger_alma": "Recoger alma",
        "theslave.req.alma_personal": "Alma del condenado",
        "block.theslave.lecho_ceniza": "Lecho de Ceniza",
        "item.theslave.alma_vinculada": "Alma del Condenado",
        "theslave.lecho.agradecimiento": "«Gracias por devolverme la vida. Este lecho guardará mi lugar a tu lado. Colócalo en casa.»",
        "theslave.lecho.ajeno": "Este lecho no pertenece a uno de tus vínculos activos.",
        "theslave.lecho.ya_colocado": "Ya tiene un lecho. Recógelo antes de cambiarlo de lugar.",
        "theslave.lecho.sin_espacio": "El lecho necesita dos bloques despejados sobre suelo firme.",
        "theslave.lecho.colocado": "El alma ya recuerda su hogar.",
        "theslave.lecho.recogido": "Has recogido su lecho sin romper el vínculo.",
        "theslave.lecho.hogar": "Su lugar en casa. Agáchate e interactúa para recogerlo.",
        "theslave.lecho.sin_alma": "Esta tumba ya entregó su alma. Llévala al altar con su tributo.",
        "theslave.lecho.alma_extraida": "Su alma está contigo. Preséntala con el tributo en el altar, de noche.",
        "theslave.lecho.entregado": "Has recibido el Lecho de Ceniza de tu condenado.",
        "theslave.altar.falta_alma": "Lleva el alma del condenado y el tributo al altar.",
        "theslave.estado.descansando": "Descansando",
        "theslave.dice.descanso": "Necesito descansar. Retomaré el encargo cuando me recupere.",
        "theslave.dice.sin_comida": "Me he quedado sin comida. Guardo el encargo hasta que pueda continuar.",
        "theslave.dice.provisiones": "Guardaré esta comida para el trabajo.",
        "theslave.dice.reanudo": "Ya puedo continuar con el encargo.",
        "theslave.req.noche": "Es de noche",
        "theslave.req.altar": "Un Altar de Ceniza cerca",
        "theslave.req.tributo": "Tributo de la Condena",
        "theslave.req.tributo_doble": "Tributo de la Doble Condena",

        "theslave.orden.titulo": "Órdenes",
        "theslave.orden.seguirme": "Seguirme",
        "theslave.orden.esperar": "Esperar aquí",
        "theslave.orden.normal": "Esclavo",
        "theslave.orden.fusionado": "Esclavo fusionado",
        "theslave.orden.estado_sigue": "Te sigue",
        "theslave.orden.estado_espera": "Espera donde está",
        "theslave.orden.no": "Ahora mismo no puede recibir órdenes.",
        "theslave.orden.ocupado": "Está ocupado. Cancela primero su tarea con el látigo.",
        "theslave.orden.cargado": "Carga demasiado para seguirte. Que entregue primero.",
        "theslave.orden.sigo": "Te seguirá.",
        "theslave.orden.espero": "Esperará aquí.",

        "theslave.notario.titulo": "Notario de Ceniza",
        "theslave.notario.frase": "Todo pacto tiene precio. Todo precio, su recibo.",
        "theslave.notario.vinculos": "Vínculos: %s de %s",
        "theslave.notario.almas": "Almas en tu inventario: Hambrientas %s, Vigilantes %s, Acechantes %s",
        "theslave.notario.comprar": "Comprar",
        "theslave.notario.devolver": "Devolver",
        "theslave.notario.hecho": "Hecho: %s",
        "theslave.notario.error": "No ha podido ser: %s",
        "theslave.notario.p_hierro": "%s hierro",
        "theslave.notario.p_oro": "%s oro",
        "theslave.notario.p_esmeralda": "%s esmeralda",
        "theslave.notario.p_diamante": "%s diamante",
        "theslave.notario.p_almas": "%s almas de cada tipo",
        "theslave.notario.p_devolver": "Contrato, ofrenda y látigo intactos; se te devuelve todo",
        "theslave.notario.ya_contrato": "Ya tienes un contrato sin usar.",
        "theslave.notario.sin_difunto": "No tienes ningún esclavo difunto.",
        "theslave.notario.sin_fusionado": "No tienes ningún esclavo fusionado difunto.",
        "theslave.notario.sin_fusionables": "Necesitas dos esclavos vivos sin fundir.",
        "theslave.notario.falta": "Te falta algo del precio (minerales o almas).",
        "theslave.notario.sin_hueco": "No te caben los objetos en el inventario.",
        "theslave.notario.devolver_falta": "Para devolver necesitas contrato, ofrenda y látigo intactos.",
        "theslave.notario.devolver_sin_relicario": "Necesitas un relicario con hueco para recuperar las almas.",
        "theslave.oferta.adquisicion": "Contrato de Condena",
        "theslave.oferta.tributo": "Tributo de la Condena",
        "theslave.oferta.sello": "Sello de Almas",
        "theslave.oferta.doble": "Tributo de la Doble Condena",
        "theslave.oferta.relicario": "Relicario de Ánimas",
        "theslave.oferta.altar": "Altar de Ceniza",
        "theslave.oferta.devolver": "Devolución de una compra sin usar",

        "theslave.fusion.titulo": "Fusión",
        "theslave.fusion.confirmar": "Fundir",
        "theslave.fusion.cancelar": "Cancelar",
        "theslave.fusion.intercambiar": "Intercambiar mitades",
        "theslave.fusion.izquierda": "Izquierda: %s",
        "theslave.fusion.derecha": "Derecha: %s",
        "theslave.fusion.resultado": "Aptitudes resultantes",
        "theslave.fusion.aviso": "Se gasta un Sello de Almas. Sin vuelta atrás.",

        "theslave.apt.apetito": "Apetito",
        "theslave.apt.resistencia": "Resistencia",
        "theslave.apt.agilidad": "Agilidad",
        "theslave.apt.mineria": "Destreza minera",
        "theslave.apt.recuperacion": "Recuperación",
        "theslave.apt.esp.apetito": "Especialidad: apetito",
        "theslave.apt.esp.resistencia": "Especialidad: resistencia",
        "theslave.apt.esp.agilidad": "Especialidad: agilidad",
        "theslave.apt.esp.mineria": "Especialidad: destreza minera",
        "theslave.apt.esp.recuperacion": "Especialidad: recuperación",
    })
    with open(ES, "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write("\n")
    with open(os.path.join(ASSETS, "lang", "en_us.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    terreno()
    decoracion()
    altar()
    objetos()
    arte_items.objetos(item)
    skins()
    modelos()
    modelos3d.generar(escribe, ASSETS)
    arte_hogar.generar(escribe, ASSETS)
    datos()
    textos()
    print("Assets de la ampliación oscura generados")
