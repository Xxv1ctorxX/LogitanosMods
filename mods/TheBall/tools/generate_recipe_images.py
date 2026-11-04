#!/usr/bin/env python3
"""Genera las imágenes de los crafteos (estilo mesa de crafteo) en docs/recipes/.

Uso (desde mods/TheBall):
    python tools/generate_recipe_images.py --jar <minecraft-client.jar>

El jar del cliente de Minecraft se usa solo para leer las texturas de los items vanilla (pan, carne, hilo...).
Tras compilar una vez, está en  .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-*/<versión>/*.jar
Si no pasas --jar, intenta encontrarlo ahí. Requiere Pillow.
"""
import argparse
import glob
import io
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_TEX = os.path.join(ROOT, "src/main/resources/assets/theball/textures")
OUT = os.path.join(ROOT, "docs/recipes")

ITEM = 4          # escala de los items (16px -> 64px)
SLOT = 72         # tamaño del hueco
PAD = 24

PANEL = (198, 198, 198)
SLOTFILL = (139, 139, 139)
DARK = (55, 55, 55)
WHITE = (255, 255, 255)
BLACK = (0, 0, 0)
TEXT = (64, 64, 64)


def font(size, bold=False):
    for name in ("arialbd.ttf" if bold else "arial.ttf", "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"):
        for base in ("C:/Windows/Fonts/", ""):
            try:
                return ImageFont.truetype(base + name, size)
            except OSError:
                pass
    return ImageFont.load_default(size)


# ------------------------------------------------------------------ texturas

class Textures:
    def __init__(self, jar):
        self.zip = zipfile.ZipFile(jar) if jar else None
        self.cache = {}

    def raw(self, ref):
        """ref: 'mod:item/x', 'mod:block/x', 'mc:item/x', 'mc:block/x'."""
        ns, path = ref.split(":")
        if ns == "mod":
            return Image.open(os.path.join(MOD_TEX, path + ".png")).convert("RGBA")
        if self.zip is None:
            raise SystemExit("Hace falta --jar para las texturas de Minecraft (" + ref + ")")
        data = self.zip.read("assets/minecraft/textures/" + path + ".png")
        return Image.open(io.BytesIO(data)).convert("RGBA")

    def icon(self, ref):
        """Devuelve el icono de 16x16 de un item (los bloques se dibujan como cubo isométrico)."""
        if ref in self.cache:
            return self.cache[ref]
        if ref == "mod:cone":
            img = cone_icon(self)
        elif ref.startswith("cube:"):
            img = cube_icon(self.raw(ref[5:]))
        else:
            img = self.raw(ref)
            if img.size[1] > img.size[0]:  # texturas animadas: primer fotograma
                img = img.crop((0, 0, img.size[0], img.size[0]))
        self.cache[ref] = img
        return img


def avg(img):
    px = [p for p in img.get_flattened_data() if p[3] > 0]
    n = len(px)
    return tuple(sum(p[i] for p in px) // n for i in range(3)) + (255,)


def cone_icon(tex):
    """Cono de trafico en 16x16 a partir de sus texturas."""
    orange = avg(tex.raw("mod:block/cone_orange"))
    white = avg(tex.raw("mod:block/cone_white"))
    base = avg(tex.raw("mod:block/cone_base"))
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # (fila, mitad del ancho, color) de arriba a abajo
    rows = [(1, 1, orange), (2, 1, orange), (3, 2, orange), (4, 2, orange), (5, 3, white), (6, 3, white),
            (7, 4, orange), (8, 4, orange), (9, 5, orange), (10, 5, orange), (11, 6, orange)]
    for y, half, color in rows:
        for x in range(8 - half, 8 + half):
            px[x, y] = color
    for y in (12, 13):
        for x in range(1, 15):
            px[x, y] = base
    return img


def cube_icon(face):
    """Cubo isométrico (como el item de un bloque) de 16x16 a partir de una cara."""
    S = 16 * 8
    face = face.resize((16, 16), Image.NEAREST).resize((S, S), Image.NEAREST)
    W, H = S * 2, S * 2
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    def shade(img, k):
        r, g, b, a = img.split()
        f = lambda c: c.point(lambda v: int(v * k))
        return Image.merge("RGBA", (f(r), f(g), f(b), a))

    cx = W // 2
    # top: rombo; left: lateral izquierdo; right: lateral derecho
    # Las transformaciones afines de PIL van de destino a origen: se resuelven a mano por cara.
    def paste_face(img, k, origin, ux, uy, vx, vy):
        # destino(x,y) = origin + u*(ux,uy) + v*(vx,vy), con u,v en [0,1)
        det = ux * vy - uy * vx
        a, b = vy / det, -vx / det
        d, e = -uy / det, ux / det
        c = -(origin[0] * a + origin[1] * b)
        f = -(origin[0] * d + origin[1] * e)
        layer = img.transform((W, H), Image.AFFINE, (a * S, b * S, c * S, d * S, e * S, f * S), Image.NEAREST)
        out.alpha_composite(shade(layer, k))

    paste_face(face, 1.0, (cx, 0), S // 2, S // 4, -S // 2, S // 4)       # cara superior
    paste_face(face, 0.8, (cx - S // 2, S // 4), S // 2, S // 4, 0, S // 2)  # izquierda
    paste_face(face, 0.6, (cx, S // 2), S // 2, -S // 4, 0, S // 2)          # derecha
    bbox = out.getbbox()
    out = out.crop(bbox)
    scale = 16 / max(out.size)
    out = out.resize((max(1, round(out.width * scale)), max(1, round(out.height * scale))), Image.NEAREST)
    res = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    res.paste(out, ((16 - out.width) // 2, (16 - out.height) // 2))
    return res


# ------------------------------------------------------------------ dibujo

def bevel(d, box, light, dark, fill=None, width=2):
    x0, y0, x1, y1 = box
    if fill:
        d.rectangle(box, fill=fill)
    for i in range(width):
        d.line([(x0 + i, y0 + i), (x1 - i - 1, y0 + i)], fill=light)
        d.line([(x0 + i, y0 + i), (x0 + i, y1 - i - 1)], fill=light)
        d.line([(x0 + i, y1 - i), (x1 - i, y1 - i)], fill=dark)
        d.line([(x1 - i, y0 + i), (x1 - i, y1 - i)], fill=dark)


def draw_slot(img, d, x, y, size=SLOT):
    bevel(d, (x, y, x + size - 1, y + size - 1), DARK, WHITE, fill=SLOTFILL, width=2)


def draw_item(img, tex, ref, x, y, size=SLOT, count=None):
    icon = tex.icon(ref)
    scale = (size - 8) // 16
    big = icon.resize((icon.width * scale, icon.height * scale), Image.NEAREST)
    img.alpha_composite(big, (x + (size - big.width) // 2, y + (size - big.height) // 2))
    if count and count > 1:
        f = font(26, bold=True)
        t = str(count)
        d = ImageDraw.Draw(img)
        w = d.textlength(t, font=f)
        tx, ty = x + size - w - 6, y + size - 34
        d.text((tx + 2, ty + 2), t, font=f, fill=(63, 63, 63))
        d.text((tx, ty), t, font=f, fill=WHITE)


def draw_arrow(d, x, y):
    c = SLOTFILL
    d.rectangle((x, y + 12, x + 36, y + 28), fill=c)
    d.polygon([(x + 36, y), (x + 36, y + 40), (x + 62, y + 20)], fill=c)


def draw_plus(d, x, y):
    d.rectangle((x, y + 12, x + 28, y + 20), fill=SLOTFILL)
    d.rectangle((x + 10, y + 2, x + 18, y + 30), fill=SLOTFILL)


def new_panel(w, h):
    img = Image.new("RGBA", (w, h), PANEL + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, w - 1, h - 1), outline=BLACK)
    bevel(d, (1, 1, w - 2, h - 2), WHITE, (85, 85, 85), width=3)
    return img, d


def header(d, title, w):
    d.text((PAD, 14), title, font=font(26, bold=True), fill=TEXT)


def footer(d, text, w, h):
    if text:
        d.text((PAD, h - PAD - 22), text, font=font(20), fill=TEXT)


def text_width(title, size, bold, note):
    d = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    w = d.textlength(title, font=font(size, bold))
    if note:
        w = max(w, d.textlength(note, font=font(20)))
    return int(w)


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    img.convert("RGB").save(os.path.join(OUT, name + ".png"), optimize=True)
    print("  ", name + ".png")


def crafting(tex, name, grid, result, count=1, note=None, title="Mesa de crafteo", shapeless=False):
    """grid: lista de 3 filas con 3 refs (o None)."""
    grid = [list(r) + [None] * (3 - len(r)) for r in grid] + [[None] * 3] * (3 - len(grid))
    rows = 3
    cols = 3
    gw = cols * SLOT
    gh = rows * SLOT
    out_size = 96
    full_title = title + ("  (sin forma)" if shapeless else "")
    w = max(PAD + gw + 28 + 62 + 28 + out_size + PAD, text_width(full_title, 26, True, note) + 2 * PAD)
    top = 56
    h = top + max(gh, 3 * SLOT) + (60 if note or shapeless else PAD)
    img, d = new_panel(w, h)
    header(d, full_title, w)
    gx, gy = PAD, top
    full_h = max(gh, 3 * SLOT)
    for r in range(rows):
        for c in range(cols):
            draw_slot(img, d, gx + c * SLOT, gy + r * SLOT)
    for r, row in enumerate(grid):
        for c, ref in enumerate(row):
            if ref:
                draw_item(img, tex, ref, gx + c * SLOT, gy + r * SLOT)
    ax = gx + gw + 28
    draw_arrow(d, ax, gy + full_h // 2 - 20)
    ox = ax + 62 + 28
    oy = gy + full_h // 2 - out_size // 2
    draw_slot(img, d, ox, oy, out_size)
    draw_item(img, tex, result, ox, oy, out_size, count)
    footer(d, note, w, h)
    save(img, name)


def simple(tex, name, title, rows, note=None):
    """rows: [(lista_de_entradas, resultado, cantidad)] -> cada fila es entradas -> salida."""
    max_in = max(len(r[0]) for r in rows)
    w = max(PAD + max_in * SLOT + (max_in - 1) * 34 + 28 + 62 + 28 + SLOT + PAD,
            text_width(title, 26, True, note) + 2 * PAD)
    top = 56
    h = top + len(rows) * (SLOT + 14) + (50 if note else PAD)
    img, d = new_panel(w, h)
    header(d, title, w)
    for i, (ins, res, cnt) in enumerate(rows):
        y = top + i * (SLOT + 14)
        x = PAD
        for j, ref in enumerate(ins):
            if j:
                draw_plus(d, x, y + SLOT // 2 - 16)
                x += 34
            draw_slot(img, d, x, y)
            draw_item(img, tex, ref, x, y)
            x += SLOT
        x = PAD + max_in * SLOT + (max_in - 1) * 34 + 28
        draw_arrow(d, x, y + SLOT // 2 - 20)
        x += 62 + 28
        draw_slot(img, d, x, y)
        draw_item(img, tex, res, x, y, SLOT, cnt)
    footer(d, note, w, h)
    save(img, name)


# ------------------------------------------------------------------ recetas

def build(tex):
    foil = "mod:item/aluminum_foil"
    ingot = "mod:item/aluminum_ingot"
    raw = "mod:item/raw_aluminum"
    ball = "mod:item/aluminum_ball"
    block = "cube:mod:block/aluminum_block"
    ore = "cube:mod:block/aluminum_ore"
    deep = "cube:mod:block/deepslate_aluminum_ore"
    bread = "mc:item/bread"
    meat = "mc:item/cooked_porkchop"
    string = "mc:item/string"
    meat_note = "Carne: chuleta, filete, cordero, pollo o conejo cocinados"

    crafting(tex, "bocata", [[bread, bread, bread], [ingot, meat, ingot], [bread, bread, bread]],
             "mod:item/bocata", note=meat_note)
    crafting(tex, "durum", [[ingot, bread, ingot], [ingot, meat, ingot], [ingot, bread, ingot]],
             "mod:item/durum", note=meat_note)
    crafting(tex, "goal", [[ingot, ingot, ingot], [string, string, string], [ingot, None, ingot]],
             "mod:item/goal")
    crafting(tex, "cone", [["mc:item/orange_dye", "cube:mc:block/white_wool"]], "mod:cone", 4, shapeless=True)
    crafting(tex, "aluminum_foil", [[ingot, ingot]], foil, shapeless=True)
    crafting(tex, "aluminum_ball", [[foil, foil, foil], [foil, foil, foil], [foil, foil, foil]], ball,
             shapeless=True, note="9 papeles de aluminio (en cualquier posición)")
    crafting(tex, "aluminum_block", [[ingot, ingot, ingot], [ingot, ingot, ingot], [ingot, ingot, ingot]], block)
    crafting(tex, "aluminum_ingots_from_block", [[block]], ingot, 9, shapeless=True)

    simple(tex, "furnace", "Horno (10 s)  /  Alto horno (5 s)",
           [([ore], ingot, 1), ([deep], ingot, 1), ([raw], ingot, 1)],
           note="La mena sin Toque de seda suelta aluminio en bruto")
    simple(tex, "stonecutter", "Cortapiedras", [([block], foil, 5)])
    simple(tex, "anvil", "Yunque", [([ball, ball], ball, 1)],
           note="Suma las hojas de las dos bolas (máx. 2000)")


def find_jar():
    hits = glob.glob(os.path.join(ROOT, ".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-*/*/*.jar"))
    hits = [h for h in hits if not h.endswith(".backup")]
    return hits[0] if hits else None


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", help="jar del cliente de Minecraft (para las texturas vanilla)")
    args = ap.parse_args()
    jar = args.jar or find_jar()
    print("Texturas de Minecraft desde:", jar)
    print("Generando en", OUT)
    build(Textures(jar))
