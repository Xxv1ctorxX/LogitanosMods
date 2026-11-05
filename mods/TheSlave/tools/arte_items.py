"""Iconos 16x16 de los objetos rituales y fotogramas de la brújula, dibujados a mano en arte ASCII con paleta propia.

No toca el látigo ni el contrato. Lo usa generate_lore_assets.py.
"""
import math
import os

import generate_assets as g

T = (0, 0, 0, 0)


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


PAL = {
    ".": T,
    "o": rgb(0x14100e),   # contorno
    "O": rgb(0x241a16),
    # oro
    "h": rgb(0xffe9a0), "G": rgb(0xb59b62), "g": rgb(0x9c7424), "d": rgb(0x5e4318),
    # cristal
    "w": rgb(0xf2fbff), "c": rgb(0xb4dcf0), "C": rgb(0x6fa4c6), "v": rgb(0x44709a),
    # alma azul
    "b": rgb(0x829eaf), "B": rgb(0xc0cbd1), "n": rgb(0x2f78d0),
    # hierro negro / ceniza
    "K": rgb(0x3a3a46), "k": rgb(0x5a5a68), "j": rgb(0x24242c), "a": rgb(0xb4b0a4), "A": rgb(0x8a867c),
    "z": rgb(0xd8d2c2),
    # pergamino
    "P": rgb(0xd8c090), "p": rgb(0xb09468), "q": rgb(0x7c6444), "i": rgb(0x4a3828),
    # lacre
    "R": rgb(0xa82a52), "r": rgb(0x701838), "s": rgb(0xd8527a), "M": rgb(0x493153), "m": rgb(0x83688e),
    # cobre
    "V": rgb(0x718a76),
    "u": rgb(0xc87a3c), "U": rgb(0x8a4c22),
    # rojo joya
    "e": rgb(0xd83a48), "E": rgb(0x8a1424),
}


def arte(filas, pal=PAL):
    l = g.lienzo(16, 16)
    filas = list(filas)
    top = (16 - len(filas)) // 2
    for y, f in enumerate(filas):
        izq = (16 - len(f)) // 2
        for x, c in enumerate(f):
            l[top + y][izq + x] = pal[c]
    return l


RELICARIO = [
    "..oooooo..",
    ".ohhGGGgo.",
    ".oGhGGGgo.",
    "..odGGgo..",
    ".ooccccoo.",
    "ogcwccCCgo",
    "ogwwVbcCgo",
    "ogcVbMmCgo",
    "ogcVbMmCgo",
    "ogccbbbCgo",
    "ogcccbCCgo",
    "ogccCCCvgo",
    "ooGGGGGGoo",
    "ohGhGGGgdo",
    ".oooooooo.",
]

OFRENDA = [
    ".....oooooo.....",
    "....ozzzzaao....",
    "...ojKKKKKjjo...",
    "...ozzaazzzao...",
    "..ojKKKKKKKjjo..",
    "..ojKkjjkkKjjo..",
    "..ojKVjbnjMjjo..",
    "..ojKVjBnjmjjo..",
    "..ojKnjjMjKjjo..",
    "..ojKkjjkkKjjo..",
    "...ojKKKKKjjo...",
    "...ozzaazzzao...",
    "....ojjjjjjo....",
    ".....oooooo.....",
]

TRIBUTO = [
    "oooooooooooooo",
    "oAzaaaakjaaaAo",
    "oaaaaaakjaaaAo",
    "oaaAaaaKjaaaAo",
    "ojkkkkkzzakkjo",
    "ojjjjjjzBbjjjo",
    "oaaaAaaKjaaaAo",
    "oaaaaaaKjaaaAo",
    "oAAAAAAKjAAAAo",
    "oooooooooooooo",
]

SELLO = [
    ".....oooooo.....",
    "...oojKKKjjoo...",
    "..ojKKKMjKKjjo..",
    ".ojKkkKmKkkKjjo.",
    ".ojKzjMMjzjKjjo.",
    ".ojKKKkmKKKKjjo.",
    ".ojKzjMMjzjKjjo.",
    ".ojKKKkmKKKKjjo.",
    "..ojKKMbMKjjo...",
    "...ojKcBKjjo....",
    "....ojKjjo......",
    ".....oooo.......",
]

DOBLE = [
    "....ooooooo.....",
    "..oojKKjAAaoo...",
    ".ojKKkkjAzaaao..",
    ".ojKMKkjAzmaao..",
    ".ojKzKkjAzzaao..",
    ".ojjjjzBzjjjjo..",
    ".ojKKkcBcAaaao..",
    ".ojKzKkjAzzaao..",
    ".ojKMKkjAzmaao..",
    "..ojKKkjAaaoo...",
    "...oooooooo.....",
]


def objetos(guardar_item):
    cobre = {**PAL, "G": rgb(0xbc805d), "g": rgb(0x895738),
             "h": rgb(0xe3b28c), "d": rgb(0x50382a)}
    guardar_item("relicario_animas", arte(RELICARIO, cobre))
    guardar_item("ofrenda_muerte", arte(OFRENDA))
    guardar_item("tributo_condena", arte(TRIBUTO))
    guardar_item("sello_almas", arte(SELLO))
    guardar_item("tributo_doble_condena", arte(DOBLE))


def brujula(k):
    l = g.lienzo(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d > 7.6:
                continue
            ang = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
            if d > 6.4:                        # carcasa de hierro ennegrecido con bisel de luz arriba-izquierda
                luz = 0.5 + 0.5 * math.cos(ang + 2.4)
                v = int(34 + 40 * luz)
                l[y][x] = (v, v - 2, v + 6, 255)
            elif d > 5.6:                      # aro interior oscuro
                l[y][x] = (18, 16, 22, 255)
            else:                              # esfera con un tinte violeta hacia el borde
                t = d / 5.6
                l[y][x] = (int(26 + 14 * t), int(20 + 6 * t), int(38 + 22 * t), 255)
    for x, y in ((7, 0), (8, 0), (15, 7), (15, 8), (7, 15), (8, 15), (0, 7), (0, 8)):
        pass
    for x, y in ((7, 1), (8, 1), (14, 7), (14, 8), (7, 14), (8, 14), (1, 7), (1, 8)):
        l[y][x] = (200, 124, 60, 255)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        l[y][x] = (138, 76, 34, 255)
    a = k * 2 * math.pi / 32
    puntos = []
    for i in range(0, 60):
        t = i * 0.1
        puntos.append((int(math.floor(8 + math.sin(a) * t)), int(math.floor(8 - math.cos(a) * t)), t))
    for x, y, t in puntos:                      # sombra de la aguja
        sx, sy = x + 1, y + 1
        if 0 <= sx < 16 and 0 <= sy < 16 and l[sy][sx][3] and l[sy][sx][0] < 60:
            l[sy][sx] = (10, 8, 14, 255)
    for x, y, t in puntos:
        if 0 <= x < 16 and 0 <= y < 16:
            l[y][x] = (88, 180, 255, 255) if t > 4.2 else (236, 230, 212, 255)
    px, py, _ = puntos[-1]
    if 0 <= px < 16 and 0 <= py < 16:
        l[py][px] = (205, 238, 255, 255)
    for i in range(0, 22):
        t = i * 0.1
        x = int(math.floor(8 - math.sin(a) * t))
        y = int(math.floor(8 + math.cos(a) * t))
        if 0 <= x < 16 and 0 <= y < 16:
            l[y][x] = (150, 144, 130, 255)
    for dx, dy, c in ((0, 0, (216, 58, 72, 255)), (1, 0, (138, 20, 36, 255)), (0, 1, (138, 20, 36, 255)),
                      (1, 1, (90, 12, 24, 255))):
        l[7 + dy][7 + dx] = c
    l[7][7] = (255, 150, 150, 255)
    # reflejo del cristal
    for x, y in ((3, 4), (4, 3), (4, 4)):
        if l[y][x][0] < 70:
            l[y][x] = (90, 84, 110, 255)
    g.contorno(l, (10, 8, 12, 255))
    return l
