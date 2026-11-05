"""Genera texturas (PNG, solo libreria estandar), modelos, definiciones de items, blockstate y recetas.

Uso (desde mods/TheSlave):  python tools/generate_assets.py
"""
import json
import math
import os
import random
import shutil
import struct
import zlib

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(RAIZ, "assets", "theslave")
DATA = os.path.join(RAIZ, "data", "theslave")


def png(ruta, w, h, px):
    """px: funcion (x, y) -> (r, g, b, a)."""
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(bytes(px(x, y)))

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    datos = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    datos += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    with open(ruta, "wb") as f:
        f.write(datos)


def lienzo(w, h):
    return [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]


def rect(l, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= y < len(l) and 0 <= x < len(l[0]):
                l[y][x] = c


def guardar(ruta, l):
    png(ruta, len(l[0]), len(l), lambda x, y: l[y][x])


# ---------------------------------------------------------------- items
def px_(l, x, y, c):
    if 0 <= y < len(l) and 0 <= x < len(l[0]):
        l[y][x] = c


def contorno(l, color):
    """Dibuja un borde oscuro de 1 px alrededor de lo opaco."""
    h, w = len(l), len(l[0])
    marcas = []
    for y in range(h):
        for x in range(w):
            if l[y][x][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and l[ny][nx][3] != 0:
                        marcas.append((x, y))
                        break
    for x, y in marcas:
        l[y][x] = color


# ---- geometria compartida del latigo enrollado (icono 2D y modelo 3D usan la misma espiral)
TEX = 32
ESP_C = (11.5, 15.5)      # coordenadas de imagen, y hacia abajo
ESP_R = 10.3
ESP_RHO = 0.18
ESP_VUELTAS = 2.4
ESP_DIR = -1
# Derivada exacta: r'(t) = log(rho)*r, theta'(t) = dir*2*pi*vueltas.
# La cuerda sale hacia arriba-izquierda; el mango se prolonga abajo-derecha.
ESP_TH0 = -3 * math.pi / 4 - math.atan2(ESP_DIR * math.tau * ESP_VUELTAS, math.log(ESP_RHO))
MANGO_LARGO = 11.0
MANGO_ANCHO = 1.35


def _w(t):
    """Semianchura decreciente, con huecos visibles entre vueltas incluso junto a la punta."""
    return 0.115 * ESP_R * ESP_RHO ** t + 0.4


def espiral(n=120):
    """Espiral logaritmica analitica: puntos (x, y, radio_cuerda, tx, ty)."""
    if n < 1:
        raise ValueError("La espiral necesita al menos un segmento")
    pts = []
    for i in range(n + 1):
        t = i / n
        r = ESP_R * ESP_RHO ** t
        th = ESP_TH0 + ESP_DIR * math.tau * ESP_VUELTAS * t
        dr, dt = math.log(ESP_RHO) * r, ESP_DIR * math.tau * ESP_VUELTAS
        tx = dr * math.cos(th) - r * math.sin(th) * dt
        ty = dr * math.sin(th) + r * math.cos(th) * dt
        m = math.hypot(tx, ty)
        pts.append((ESP_C[0] + r * math.cos(th), ESP_C[1] + r * math.sin(th), _w(t), tx / m, ty / m))
    return pts


def mango_geom(largo=None):
    """Mango recto: arranca en el inicio de la espiral y sigue la tangente hacia fuera. Devuelve (x0,y0,x1,y1)."""
    largo = MANGO_LARGO if largo is None else largo
    x, y, _, tx, ty = espiral(1)[0]
    return x, y, x - tx * largo, y - ty * largo


def latigo_16():
    """Variante pixel-art 16x16 de la misma curva, sin aros ni cola."""
    return latigo(16)


def latigo(resolucion=TEX):
    """Icono 32x32 ENROLLADO: espiral logaritmica de 2,4 vueltas con huecos entre ellas y mango recto tangente."""
    l = lienzo(resolucion, resolucion)
    MED = (130, 84, 48, 255)      # cuero #825430
    LUZ = (174, 120, 74, 255)     # borde claro #AE784A
    PUN = (197, 179, 142, 255)    # punta #C5B38E
    rnd = random.Random(11)
    pts = espiral(400)
    x0, y0, x1, y1 = mango_geom()
    lx, ly = x1 - x0, y1 - y0
    ln = math.hypot(lx, ly)
    ancho_m = MANGO_ANCHO
    for y in range(resolucion):
        for x in range(resolucion):
            px, py = (x + 0.5) * TEX / resolucion, (y + 0.5) * TEX / resolucion
            mejor = None
            for k, (sx, sy, w, tx, ty) in enumerate(pts):
                d = math.hypot(px - sx, py - sy)
                if d < w and (mejor is None or d - w < mejor[0]):
                    mejor = (d - w, k, px - sx, py - sy, w, tx, ty)
            # mango (segmento recto)
            u = max(0.0, min(1.0, ((px - x0) * lx + (py - y0) * ly) / (ln * ln)))
            cx_, cy_ = x0 + lx * u, y0 + ly * u
            dm = math.hypot(px - cx_, py - cy_)
            if dm < ancho_m and u > 0.03:
                # v: posicion a lo ancho (-1 luz arriba-izquierda .. +1 sombra), para dar volumen cilindrico
                nx, ny = -ly / ln, lx / ln
                v = ((px - cx_) * nx + (py - cy_) * ny) / ancho_m
                g = rnd.randint(-7, 7)
                paso = int(u * ln / 1.6)                                   # vueltas de cuero a lo largo del mango
                if u > 0.92 or u < 0.08:
                    base = (174, 120, 74) if v < 0.2 else (110, 72, 44)            # anillas de cuero claro en los extremos
                elif paso % 2 == 0:
                    base = (92 + g, 62 + g, 38 + g) if v < 0.3 else (58, 40, 27)    # banda de cuero oscuro
                else:
                    base = (138 + g, 98 + g, 58 + g) if v < -0.1 else ((112 + g, 78 + g, 46 + g) if v < 0.5 else (70, 48, 31))   # madera veteada
                l[y][x] = (base[0], base[1], base[2], 255)
                continue
            if mejor is None:
                continue
            borde, k, ox, oy, w, tx, ty = mejor
            # v: posicion a lo ancho de la cuerda (-1..1); a: avance a lo largo (para el trenzado)
            nx, ny = -ty, tx
            v = (ox * nx + oy * ny) / w
            luz = (ox * -0.7 + oy * -0.7) / w
            trenza = (k // 6 + int(math.floor(v * 1.6 + 2))) % 2     # franjas diagonales de trenza
            c = MED if trenza else (112, 72, 42, 255)
            if luz > 0.5:
                c = LUZ if trenza else (150, 100, 60, 255)
            if abs(v) > 0.55 and k < len(pts) * 0.9:
                c = (42, 27, 18, 255)                     # costura oscura entre vueltas contiguas
            if k > len(pts) * 0.97:
                c = PUN                                   # punta clara hacia el centro
            l[y][x] = c
    return l


def contrato():
    """Pergamino antiguo: hoja amarillenta, manchas, bordes quemados y rollos con varillas."""
    import random
    r = random.Random(3)
    l = lienzo(16, 16)
    base = (216, 188, 130, 255)
    mancha = (192, 158, 98, 255)
    borde = (150, 108, 62, 255)
    quemado = (104, 70, 40, 255)
    tinta = (96, 62, 40, 255)
    tinta_clara = (140, 100, 66, 255)
    madera = (112, 72, 40, 255)
    madera_luz = (164, 114, 66, 255)
    # hoja con bordes irregulares
    for y in range(3, 14):
        izq = 3 + (1 if r.random() < 0.3 else 0)
        der = 13 - (1 if r.random() < 0.3 else 0)
        for x in range(izq, der):
            c = base
            if r.random() < 0.22:
                c = mancha
            if x == izq or x == der - 1:
                c = borde
            px_(l, x, y, c)
    for x in range(4, 12):
        px_(l, x, 13, borde)
    # esquina rasgada y quemadura
    px_(l, 12, 12, (0, 0, 0, 0))
    px_(l, 11, 13, (0, 0, 0, 0))
    px_(l, 3, 11, quemado)
    px_(l, 3, 12, (0, 0, 0, 0))
    px_(l, 4, 12, quemado)
    # rollos arriba y abajo con varilla
    rect(l, 3, 1, 13, 3, madera_luz if False else (196, 164, 108, 255))
    rect(l, 3, 2, 13, 3, borde)
    rect(l, 1, 1, 3, 3, madera)
    rect(l, 13, 1, 15, 3, madera)
    px_(l, 1, 1, madera_luz)
    px_(l, 13, 1, madera_luz)
    rect(l, 3, 13, 13, 15, (196, 164, 108, 255))
    rect(l, 3, 13, 13, 14, borde)
    rect(l, 1, 13, 3, 15, madera)
    rect(l, 13, 13, 15, 15, madera)
    px_(l, 1, 13, madera_luz)
    px_(l, 13, 13, madera_luz)
    # escritura desvaida
    for y, ini, fin in ((4, 5, 11), (6, 5, 10), (8, 5, 11)):
        for x in range(ini, fin):
            if (x + y) % 4 != 0:
                px_(l, x, y, tinta if (x + y) % 3 else tinta_clara)
    # firma garabateada
    for x, y in ((5, 10), (6, 11), (7, 10), (8, 11), (9, 10)):
        px_(l, x, y, tinta)
    # lacre pequeno
    px_(l, 10, 10, (150, 30, 32, 255))
    px_(l, 11, 10, (110, 20, 26, 255))
    px_(l, 10, 11, (110, 20, 26, 255))
    px_(l, 11, 11, (150, 30, 32, 255))
    contorno(l, (46, 32, 24, 255))
    return l


def tumba_bloque():
    rnd = random.Random(7)
    l = lienzo(16, 16)
    for y in range(16):
        for x in range(16):
            v = 78 + rnd.randint(-10, 10)
            l[y][x] = (v, v + 2, v + 6, 255)
    for y in range(16):
        l[y][0] = (52, 54, 60, 255)
        l[y][15] = (52, 54, 60, 255)
    for x in range(16):
        l[0][x] = (96, 98, 104, 255)
        l[15][x] = (44, 46, 50, 255)
    # cruz grabada
    rect(l, 7, 3, 9, 12, (40, 42, 46, 255))
    rect(l, 4, 5, 12, 7, (40, 42, 46, 255))
    return l


# ---------------------------------------------------------------- skins 64x64
def skin(ruta, piel, camisa, pantalon, pelo, ojos=(20, 20, 20, 255)):
    rnd = random.Random(hash(ruta) & 0xFFFF)
    l = lienzo(64, 64)

    def caja(x, y, w, h, d, color_top, color_lado, color_fondo=None):
        # layout estandar de skin: top (d..d+w), bottom, laterales
        rect(l, x + d, y, x + d + w, y + d, color_top)
        rect(l, x + d + w, y, x + d + 2 * w, y + d, color_fondo or color_lado)
        rect(l, x, y + d, x + d, y + d + h, color_lado)
        rect(l, x + d, y + d, x + d + w, y + d + h, color_lado)
        rect(l, x + d + w, y + d, x + 2 * d + w, y + d + h, color_lado)
        rect(l, x + 2 * d + w, y + d, x + 2 * d + 2 * w, y + d + h, color_lado)

    # cabeza
    caja(0, 0, 8, 8, 8, pelo, piel)
    rect(l, 8, 8, 16, 10, pelo)           # flequillo (cara frontal)
    rect(l, 16, 8, 32, 16, pelo)          # nuca/lados
    rect(l, 9, 12, 11, 13, ojos)
    rect(l, 13, 12, 15, 13, ojos)
    rect(l, 11, 15, 13, 16, (120, 60, 50, 255))
    # torso
    caja(16, 16, 8, 12, 4, camisa, camisa)
    # brazos y piernas (derecho e izquierdo)
    caja(40, 16, 4, 12, 4, piel, camisa)
    rect(l, 44, 26, 48, 32, piel)
    caja(32, 48, 4, 12, 4, piel, camisa)
    caja(0, 16, 4, 12, 4, pantalon, pantalon)
    caja(16, 48, 4, 12, 4, pantalon, pantalon)
    # ruido para que no se vea plano
    for y in range(64):
        for x in range(64):
            r, g, b, a = l[y][x]
            if a:
                d = rnd.randint(-6, 6)
                l[y][x] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)
    if ruta:
        guardar(ruta, l)
    return l


def muerte(ruta):
    l = lienzo(64, 64)
    negro = (10, 10, 14, 255)
    hueso = (214, 208, 190, 255)
    capa = (22, 22, 30, 255)

    def caja(x, y, w, h, d, top, lado):
        rect(l, x + d, y, x + d + w, y + d, top)
        rect(l, x + d + w, y, x + d + 2 * w, y + d, lado)
        rect(l, x, y + d, x + d, y + d + h, lado)
        rect(l, x + d, y + d, x + d + w, y + d + h, lado)
        rect(l, x + d + w, y + d, x + 2 * d + w, y + d + h, lado)
        rect(l, x + 2 * d + w, y + d, x + 2 * d + 2 * w, y + d + h, lado)

    caja(0, 0, 8, 8, 8, negro, negro)
    rect(l, 10, 10, 14, 14, hueso)          # cara de calavera dentro de la capucha
    rect(l, 10, 11, 11, 13, negro)
    rect(l, 13, 11, 14, 13, negro)
    rect(l, 11, 14, 13, 16, hueso)
    caja(16, 16, 8, 12, 4, capa, capa)
    caja(40, 16, 4, 12, 4, capa, capa)
    rect(l, 44, 26, 48, 32, hueso)
    caja(32, 48, 4, 12, 4, capa, capa)
    rect(l, 36, 58, 40, 64, hueso)
    caja(0, 16, 4, 12, 4, capa, capa)
    caja(16, 48, 4, 12, 4, capa, capa)
    guardar(ruta, l)


# ---------------------------------------------------------------- json
def escribe(ruta, obj):
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    with open(ruta, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def latigo_textura():
    """16x16: mitad izquierda empunadura (madera), mitad derecha cuero trenzado."""
    rnd = random.Random(7)
    l = lienzo(16, 16)
    for y in range(16):
        for x in range(8):
            v = rnd.randint(-7, 7)
            c = (92, 62, 38) if (y // 2) % 2 == 0 else (138, 98, 58)      # vueltas de cuero alternas
            l[y][x] = (c[0] + v, c[1] + v, c[2] + v, 255)
        for x in range(8, 16):
            trenza = (x + y) % 4 < 2
            v = rnd.randint(-4, 4)
            base = (130, 84, 48) if trenza else (73, 48, 31)
            l[y][x] = (base[0] + v, base[1] + v, base[2] + v, 255)
    return l


def latigo_modelo():
    """Modelo vanilla de respaldo; el cliente sustituye sus cajas por un tubo continuo."""
    madera = [0, 0, 8, 16]
    cuero = [8, 0, 16, 16]
    caras = ("down", "up", "north", "south", "west", "east")

    def caja(a, b, uv, rot=None):
        e = {"from": a, "to": b, "faces": {d: {"texture": "#t", "uv": uv} for d in caras}}
        if rot:
            e["rotation"] = rot
        return e

    el = []

    def segmento(cx, cy, cz, largo, grueso, ang_deg, uv):
        """Caja con eje largo a ang_deg (y hacia arriba), usando solo giros de 22,5 grados sobre z."""
        k = round(ang_deg / 22.5) % 8            # 0..7 -> 0..157,5 grados
        if k <= 2:
            giro, lx, ly = k * 22.5, largo, grueso
        elif k <= 5:
            giro, lx, ly = (k - 4) * 22.5, grueso, largo
        else:
            giro, lx, ly = (k - 8) * 22.5, largo, grueso
        e = caja([cx - lx / 2, cy - ly / 2, cz - grueso / 2], [cx + lx / 2, cy + ly / 2, cz + grueso / 2], uv)
        if giro:
            e["rotation"] = {"angle": giro, "axis": "z", "origin": [cx, cy, cz]}
        return e

    # espiral logaritmica (misma que el icono) en un plano casi frontal, con ligera profundidad entre vueltas
    pts = espiral(180)
    f = 16.0 / TEX
    for (x0, y0, w0, _, _), (x1, y1, w1, _, _) in zip(pts, pts[1:]):
        mx, my = f * (x0 + x1) / 2, 16 - f * (y0 + y1) / 2   # a coordenadas de modelo (y arriba)
        dx, dy = f * (x1 - x0), -f * (y1 - y0)
        largo = math.hypot(dx, dy) + 0.10
        grueso = f * (w0 + w1)                                # diametro = 2 * radio medio
        el.append(segmento(mx, my, 8.0, largo, grueso, math.degrees(math.atan2(dy, dx)), cuero))
    # mango recto y rigido, tangente a la vuelta exterior
    hx0, hy0, hx1, hy1 = (v * f for v in mango_geom())
    mx, my = (hx0 + hx1) / 2, 16 - (hy0 + hy1) / 2
    ang = math.degrees(math.atan2(-(hy1 - hy0), hx1 - hx0))
    largo_m = math.hypot(hx1 - hx0, hy1 - hy0)
    el.append(segmento(mx, my, 8.0, largo_m, 2.0 * MANGO_ANCHO * (16.0 / TEX), ang, madera))
    # virola junto a la cuerda y remate en el extremo
    el.append(segmento(hx0 + (hx1 - hx0) * 0.08, 16 - (hy0 + (hy1 - hy0) * 0.08), 8.0, 1.0, 2.6, ang, cuero))
    el.append(segmento(hx0 + (hx1 - hx0) * 0.96, 16 - (hy0 + (hy1 - hy0) * 0.96), 8.0, 0.8, 2.4, ang, cuero))
    return {
        "textures": {"t": "theslave:item/latigo_3d", "particle": "theslave:item/latigo_3d"},
        "elements": el,
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, -0.5, 0], "scale": [0.95, 0.95, 0.95]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [0, 0, 45], "translation": [0, 3.5, 1], "scale": [0.75, 0.75, 0.75]},
            "thirdperson_lefthand": {"rotation": [0, 0, 45], "translation": [0, 3.5, 1], "scale": [0.75, 0.75, 0.75]},
            "firstperson_righthand": {"rotation": [0, -15, 0], "translation": [-1, 1, 0], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, -15, 0], "translation": [-1, 1, 0], "scale": [0.68, 0.68, 0.68]},
        },
    }


def generar_latigo():
    """Regenera exclusivamente el latigo y la geometria que consume su renderer."""
    tex = os.path.join(ASSETS, "textures", "item")
    guardar(os.path.join(tex, "latigo.png"), latigo())
    guardar(os.path.join(tex, "latigo_3d.png"), latigo_textura())
    escribe(os.path.join(ASSETS, "models", "item", "latigo.json"), latigo_modelo())
    escribe(os.path.join(ASSETS, "models", "item", "latigo_icon.json"), {
        "parent": "minecraft:item/generated", "textures": {"layer0": "theslave:item/latigo"}
    })
    escribe(os.path.join(ASSETS, "items", "latigo.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": "theslave:item/latigo_icon"}}],
        "fallback": {"type": "minecraft:model", "model": "theslave:item/latigo"}
    }})
    # Coordenadas en bloques de modelo (0..1), compartidas por icono y tubo 3D.
    puntos = [[x / 32, (32 - y) / 32, 0.5 + 0.002 * i / 180, w / 32]
              for i, (x, y, w, _, _) in enumerate(espiral(180))]
    hx0, hy0, hx1, hy1 = mango_geom()
    escribe(os.path.join(ASSETS, "latigo_geometry.json"), {
        "points": puntos,
        "handle": [[hx1 / 32, (32 - hy1) / 32, 0.5, MANGO_ANCHO / 32], puntos[0]]
    })


def main():
    tex =os.path.join(ASSETS, "textures")
    guardar(os.path.join(tex, "item", "latigo.png"), latigo())
    guardar(os.path.join(tex, "item", "contrato.png"), contrato())
    guardar(os.path.join(tex, "block", "tumba.png"), tumba_bloque())
    skin(os.path.join(tex, "entity", "esclavo_0.png"), (198, 150, 112, 255), (112, 92, 64, 255), (70, 62, 54, 255), (52, 36, 24, 255))
    skin(os.path.join(tex, "entity", "esclavo_1.png"), (160, 112, 80, 255), (84, 96, 110, 255), (60, 56, 52, 255), (26, 22, 20, 255))
    skin(os.path.join(tex, "entity", "esclavo_2.png"), (176, 190, 120, 255), (120, 80, 52, 255), (88, 66, 44, 255), (88, 112, 52, 255))
    muerte(os.path.join(tex, "entity", "la_muerte.png"))

    guardar(os.path.join(tex, "item", "latigo_3d.png"), latigo_textura())
    escribe(os.path.join(ASSETS, "models", "item", "latigo.json"), latigo_modelo())
    escribe(os.path.join(ASSETS, "models", "item", "contrato.json"),
            {"parent": "minecraft:item/generated", "textures": {"layer0": "theslave:item/contrato"}})
    for nombre in ("latigo", "contrato"):
        escribe(os.path.join(ASSETS, "items", nombre + ".json"),
                {"model": {"type": "minecraft:model", "model": "theslave:item/" + nombre}})
    escribe(os.path.join(ASSETS, "items", "tumba.json"),
            {"model": {"type": "minecraft:model", "model": "theslave:block/tumba"}})
    escribe(os.path.join(ASSETS, "blockstates", "tumba.json"),
            {"variants": {"": {"model": "theslave:block/tumba"}}})
    escribe(os.path.join(ASSETS, "models", "block", "tumba.json"), {
        "textures": {"all": "theslave:block/tumba", "particle": "theslave:block/tumba"},
        "elements": [{
            "from": [2, 0, 2], "to": [14, 12, 14],
            "faces": {d: {"texture": "#all", "uv": [0, 0, 16, 16], "cullface": False}
                      for d in ("down", "up", "north", "south", "west", "east")}
        }]
    })

    escribe(os.path.join(DATA, "recipe", "latigo.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["  T", " L ", "PL "],
        "key": {"T": "minecraft:string", "L": "minecraft:leather", "P": "minecraft:stick"},
        "result": {"id": "theslave:latigo", "count": 1}
    })

    # en_us: copia del es_es (el servidor es hispanohablante)
    print("Assets generados en", os.path.abspath(RAIZ))


if __name__ == "__main__":
    import sys
    if "--whip-only" in sys.argv:
        generar_latigo()
    else:
        main()
        generar_latigo()
