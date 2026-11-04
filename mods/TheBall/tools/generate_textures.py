#!/usr/bin/env python3
"""Genera las texturas del mod (pixel art). Uso: python3 tools/generate_textures.py"""
import math
import random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/theball"
(ROOT / "textures/item").mkdir(parents=True, exist_ok=True)
(ROOT / "textures/entity").mkdir(parents=True, exist_ok=True)

OUT = (40, 42, 50, 255)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def gray(v, tint=(0, 2, 8)):
    v = max(0, min(255, int(v)))
    return (max(0, min(255, v + tint[0])), max(0, min(255, v + tint[1])), max(0, min(255, v + tint[2])), 255)


# ------------------------------------------------------------------ papel de aluminio (item)
def foil_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(7)
    # hoja arrugada, ligeramente inclinada
    for y in range(2, 14):
        for x in range(2, 14):
            if (x - 2) + (y - 2) < 2 or (13 - x) + (13 - y) < 2:  # esquinas cortadas
                continue
            base = 200 + 25 * math.sin((x + y) * 0.9) + rnd.randint(-14, 14)
            if (x * 3 + y * 5) % 7 == 0:
                base -= 30  # arrugas
            if (x * 5 + y * 2) % 9 == 0:
                base += 25
            px(img, x, y, gray(base))
    # contorno
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and img.getpixel((nx, ny))[3] == 255 and img.getpixel((nx, ny)) != OUT:
                        px(img, x, y, OUT)
                        break
    img.save(ROOT / "textures/item/aluminum_foil.png")


# ------------------------------------------------------------------ bola (item)
def ball_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(11)
    cx, cy, r = 7.5, 7.5, 6.6
    lx, ly = -0.5, -0.6
    for y in range(16):
        for x in range(16):
            dx, dy = (x - cx) / r, (y - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1.0:
                continue
            dz = math.sqrt(1 - d2)
            lit = max(0.0, dx * lx + dy * ly + dz * 0.7)
            v = 90 + 140 * lit
            v += rnd.randint(-16, 16)
            if (x * 7 + y * 3) % 5 == 0:
                v -= 28
            px(img, x, y, gray(v))
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and img.getpixel((nx, ny))[3] == 255 and img.getpixel((nx, ny)) != OUT:
                        px(img, x, y, OUT)
                        break
    # brillo
    px(img, 5, 4, (250, 252, 255, 255))
    px(img, 6, 4, (240, 244, 250, 255))
    px(img, 5, 5, (240, 244, 250, 255))
    img.save(ROOT / "textures/item/aluminum_ball.png")
    return img


# ------------------------------------------------------------------ utilidades de contorno
def is_metal(c):
    """Gris (papel de aluminio): sin tono cálido ni verde/rojo marcado."""
    return max(c[:3]) - min(c[:3]) <= 22


def outline(img, color, metal_color=None):
    """Contorno de 1 px. Si se da metal_color, el borde junto a píxeles de papel usa ese color."""
    base = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if base.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and base.getpixel((nx, ny))[3] == 255:
                        c = base.getpixel((nx, ny))
                        px(img, x, y, metal_color if (metal_color and is_metal(c)) else color)
                        break


def foil_px(light, rnd):
    """Color de papel de aluminio. light: 0 (sombra) .. 1 (brillo)."""
    return gray(120 + 125 * light + rnd.randint(-10, 10))


# ------------------------------------------------------------------ bocata (item)
# Dos panes, dentro carne con tomate y lechuga; la mitad izquierda va envuelta en papel de aluminio.
def bocata_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(5)
    crust_d = (150, 90, 38, 255)
    crust = (200, 134, 62, 255)
    crust_l = (232, 172, 96, 255)
    crumb = (246, 224, 166, 255)
    lettuce = (104, 176, 62, 255)
    lettuce_l = (150, 210, 90, 255)
    tomato = (214, 56, 44, 255)
    tomato_l = (238, 96, 80, 255)
    meat = (126, 70, 42, 255)
    meat_d = (96, 50, 30, 255)

    X0, X1 = 1, 14      # ancho total
    WRAP = 8            # hasta aquí (hacia la izquierda) está envuelto: un poco más de la mitad

    # --- pan de arriba (cúpula) y de abajo
    for x in range(X0, X1 + 1):
        top = 3 if X0 + 1 <= x <= X1 - 1 else 4
        for y in range(top, 7):
            c = crust_l if y == top else crust
            if y == 6:
                c = crumb          # miga hacia dentro
            px(img, x, y, c)
        for y in range(11, 14):
            c = crumb if y == 11 else (crust if y == 12 else crust_d)
            if x in (X0, X1) and y == 13:
                continue
            px(img, x, y, c)
    # semillas/brillo en la tapa
    for (x, y) in ((9, 4), (12, 4), (10, 5)):
        px(img, x, y, crust_d)
    # --- relleno: lechuga (arriba, ondulada), tomate, carne
    for x in range(X0, X1 + 1):
        px(img, x, 7, lettuce if x % 2 == 0 else lettuce_l)
        if x % 3 == 0:
            px(img, x, 6, lettuce)             # la lechuga asoma por encima
        px(img, x, 8, tomato if x % 4 != 1 else tomato_l)
        px(img, x, 9, meat)
        px(img, x, 10, meat_d if x % 2 else meat)
    # tomate en rodajas: pipas
    for x in (9, 12):
        px(img, x, 8, tomato_l)

    # --- papel de aluminio: NO es una capa extra. Solo cambia la textura de la mitad izquierda,
    # dentro de la misma silueta del bocata (el papel es fino).
    shape = img.copy()
    for y in range(16):
        for x in range(0, WRAP + 2):
            if shape.getpixel((x, y))[3] == 0:
                continue
            if x == WRAP + 1 and y % 2 == 1:
                continue                       # borde irregular entre papel y pan
            light = 0.55 + 0.35 * math.sin(y * 1.1) + 0.2 * math.sin(x * 2.3 + y * 0.7)
            light = max(0.0, min(1.0, light))
            if y <= 4:
                light = min(1.0, light + 0.2)  # algo más de brillo arriba
            if y >= 12:
                light = max(0.0, light - 0.2)  # y de sombra abajo
            px(img, x, y, foil_px(light, rnd))
    # arrugas del papel (también dentro de la silueta)
    for x in (2, 4, 6):
        for y in range(3, 14):
            if (x + y) % 3 == 0 and shape.getpixel((x, y))[3] != 0:
                c = img.getpixel((x, y))
                px(img, x, y, (max(0, c[0] - 38), max(0, c[1] - 38), max(0, c[2] - 34), 255))

    # borde marrón en el pan, gris acero oscuro en el papel de aluminio
    outline(img, (70, 42, 22, 255), (58, 62, 76, 255))
    img.save(ROOT / "textures/item/bocata.png")


# ------------------------------------------------------------------ durum (item)
# Tubo gordo en diagonal envuelto en papel de aluminio. De la punta (arriba a la derecha) asoma un
# buen cacho de durum: tortilla enrollada y, en la boca abierta, relleno con salsa de yogur.
# El papel y la tortilla están manchados de salsa. Todo queda DENTRO de la silueta del tubo.
def durum_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(9)
    ux, uy = math.sqrt(0.5), -math.sqrt(0.5)     # eje del tubo (hacia arriba a la derecha)
    vx, vy = math.sqrt(0.5), math.sqrt(0.5)      # perpendicular (hacia abajo a la derecha)
    # Medidas pensadas para que el durum quepa ENTERO en 16x16 (base redonda y punta dentro, con margen para el contorno)
    c0x, c0y = 5.0, 11.4
    L_FOIL = 6.6                                 # el papel llega hasta aquí
    L_END = 9.4                                  # extremo del durum
    R = 2.8
    EA, EB = 1.7, R - 0.3                        # semiejes de la boca abierta (grande: se ve el interior)

    tort = (238, 208, 148, 255)
    tort_d = (198, 158, 96, 255)
    tort_dd = (170, 126, 70, 255)
    meat = (140, 78, 46, 255)
    lettuce = (116, 188, 68, 255)
    tomato = (216, 58, 46, 255)
    sauce = (255, 252, 244, 255)
    sauce_d = (226, 222, 204, 255)

    def in_tube(s, w):
        if s < 0:
            return s * s + w * w <= R * R
        return s <= L_END + EA and abs(w) <= R

    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - c0x, y + 0.5 - c0y
            s = dx * ux + dy * uy
            w = dx * vx + dy * vy

            # boca abierta (elipse) en el extremo
            n = math.sqrt(((s - L_END) / EA) ** 2 + (w / EB) ** 2)
            if n <= 1.0:
                if n > 0.86:
                    px(img, x, y, tort if w < 0 else tort_d)           # aro fino de tortilla
                else:
                    # interior visible en capas, de izquierda a derecha: lechuga, tomate, carne,
                    # y el yogur asomando por el lado derecho (de donde sale el chorreón)
                    if w < -1.2:
                        c = lettuce
                    elif w < -0.4:
                        c = tomato
                    elif w < 0.55:
                        c = meat
                    else:
                        c = sauce
                    # un poco de irregularidad para que no parezcan franjas
                    if (x * 3 + y * 5) % 7 == 0 and -1.2 <= w < 0.55:
                        c = sauce_d if w > -0.4 else lettuce
                    px(img, x, y, c)
                continue

            if not in_tube(s, w) or s > L_END:
                continue

            if s <= L_FOIL:
                # papel de aluminio
                light = 0.5 - 0.42 * (w / R)
                fold = math.sin(s * 2.4) * 0.12 + (0.14 if int(s * 1.3) % 2 else -0.06)
                if s > L_FOIL - 1.0:
                    light += 0.15                  # borde del papel
                light = max(0.0, min(1.0, light + fold))
                px(img, x, y, foil_px(light, rnd))
            else:
                # tortilla enrollada (cacho de durum): clara arriba-izquierda, oscura abajo-derecha
                c = tort if w < -0.5 else (tort_d if w < 1.6 else tort_dd)
                if int((s + 10) * 1.1) % 3 == 0 and w > 0:
                    c = tort_dd                    # marcas de plancha
                px(img, x, y, c)

    # --- manchas de salsa de yogur: solo sobre píxeles que ya son del tubo (nada fuera de la silueta)
    def stain(s0, w0, ra, rb, color_main, color_edge, seed):
        r = random.Random(seed)
        for y in range(16):
            for x in range(16):
                if img.getpixel((x, y))[3] == 0:
                    continue
                dx, dy = x + 0.5 - c0x, y + 0.5 - c0y
                s = dx * ux + dy * uy
                w = dx * vx + dy * vy
                if s > L_END - 0.6 and False:
                    continue
                d = math.sqrt(((s - s0) / ra) ** 2 + ((w - w0) / rb) ** 2)
                d += r.uniform(-0.18, 0.18)       # borde irregular
                if d < 0.62:
                    px(img, x, y, color_main)
                elif d < 0.9:
                    px(img, x, y, color_edge)

    # el yogur se ha escurrido por el papel, junto a la tortilla
    stain(3.7, 0.8, 1.5, 1.2, sauce, sauce_d, 1)
    stain(2.0, -0.9, 0.8, 0.7, sauce, sauce_d, 2)
    # y mancha un poco la tortilla
    stain(6.0, 1.4, 0.8, 0.8, sauce, sauce_d, 3)

    # --- el yogur rebosa por el borde derecho de la boca y CAE: un hilillo corto y sinuoso (se desvía
    # una columna al bajar, se ensancha en la punta como una gota que cuelga y suelta una gotita).
    # (12, 7) es el último píxel de tortilla del borde derecho, junto a la boca.
    px(img, 12, 7, sauce)                            # el yogur asoma por el borde
    for (x, y, c) in (
        (12, 8, sauce),      # sale del borde
        (12, 9, sauce_d),    # hilo fino
        (11, 10, sauce),     # se desvía hacia la izquierda
        (11, 11, sauce),     # engorda: gota que cuelga
        (12, 11, sauce_d),
    ):
        px(img, x, y, c)
    px(img, 11, 13, sauce)                           # gotita suelta, más abajo

    outline(img, (62, 62, 74, 255))
    img.save(ROOT / "textures/item/durum.png")


# ------------------------------------------------------------------ textura de la esfera (128x64)
def sphere_texture():
    W, H = 128, 64
    img = Image.new("RGBA", (W, H))
    rnd = random.Random(2024)

    def unit(theta, phi):
        return (math.sin(theta) * math.cos(phi), math.cos(theta), math.sin(theta) * math.sin(phi))

    # puntos de las "arrugas" repartidos por la esfera (3D => sin costura ni deformación en los polos)
    pts = []
    for _ in range(150):
        z = rnd.uniform(-1, 1)
        a = rnd.uniform(0, 2 * math.pi)
        s = math.sqrt(1 - z * z)
        p = (s * math.cos(a), z, s * math.sin(a))
        # normal de la faceta: la de la esfera inclinada al azar
        n = [p[0] + rnd.uniform(-0.9, 0.9), p[1] + rnd.uniform(-0.9, 0.9), p[2] + rnd.uniform(-0.9, 0.9)]
        ln = math.sqrt(sum(c * c for c in n)) or 1
        n = [c / ln for c in n]
        pts.append((p, n, rnd.uniform(-14, 14)))

    light = (-0.45, 0.8, 0.4)
    ll = math.sqrt(sum(c * c for c in light))
    light = tuple(c / ll for c in light)

    for y in range(H):
        theta = math.pi * (y + 0.5) / H
        for x in range(W):
            phi = 2 * math.pi * (x + 0.5) / W
            p = unit(theta, phi)
            best = (9, None)
            second = 9
            for q, n, off in pts:
                d = (p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 + (p[2] - q[2]) ** 2
                if d < best[0]:
                    second = best[0]
                    best = (d, (n, off))
                elif d < second:
                    second = d
            n, off = best[1]
            lit = max(0.0, n[0] * light[0] + n[1] * light[1] + n[2] * light[2])
            v = 120 + 110 * lit + off
            # bordes de las facetas: líneas de pliegue oscuras
            if math.sqrt(second) - math.sqrt(best[0]) < 0.045:
                v -= 42
            v += rnd.randint(-8, 8)
            img.putpixel((x, y), gray(v))
    img.save(ROOT / "textures/entity/aluminum_ball.png")


# ------------------------------------------------------------------ icono del mod
def icon(ball):
    ball.resize((128, 128), Image.NEAREST).save(ROOT / "icon.png")


foil_item()
bocata_item()
durum_item()
b = ball_item()
sphere_texture()
icon(b)
print("Texturas generadas en", ROOT)
