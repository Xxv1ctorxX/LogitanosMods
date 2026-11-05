"""Emblema del mod: pixel art nativo de 64x64, ampliado sin suavizado a 256x256.

Solo escribe icon.png; no ejecuta el generador base ni modifica objetos del mod.
"""
from pathlib import Path
import generate_assets as pixels


ROOT = Path(__file__).resolve().parents[1]
PALETTE = {
    "void": 0x0D0C14, "stone": 0x191622, "rim": 0x32263F,
    "rim_light": 0x63516E, "purple": 0x61427F, "violet": 0x9370AD,
    "blue": 0x93BED2, "light": 0xD0E6E8, "hood": 0x24212D,
    "cloth": 0x38313F, "fold": 0x4B4054, "bone": 0xD9D2B9,
    "bone_shadow": 0xA69B87, "bone_dark": 0x706776,
    "iron": 0x797681, "iron_light": 0xB0A9AE, "iron_dark": 0x45404D,
}


def generate():
    canvas = pixels.lienzo(64, 64)

    def rect(x0, y0, x1, y1, color):
        rgb = PALETTE[color]
        pixels.rect(canvas, x0, y0, x1, y1,
                    ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255))

    # Placa de obsidiana con esquinas escalonadas; todos los bordes son pixeles reales.
    for y in range(2, 62):
        cut = 6 if y < 4 or y >= 60 else 4 if y < 6 or y >= 58 else 2
        rect(cut, y, 64-cut, y+1, "rim")
        rect(cut+1, y, 63-cut, y+1, "void")
    rect(8, 3, 56, 4, "rim_light")
    rect(4, 8, 5, 55, "rim_light")
    rect(8, 60, 56, 61, "stone")
    rect(59, 8, 60, 55, "stone")

    # El Umbral: arco de piedra infectada con una fisura violeta interior.
    for x0, y0, x1, y1 in [(24, 7, 40, 9), (19, 9, 45, 11),
                           (16, 11, 48, 14), (13, 14, 51, 19),
                           (11, 19, 53, 52)]:
        rect(x0, y0, x1, y1, "stone")
    for x0, y0, x1, y1 in [(25, 9, 39, 11), (20, 11, 44, 13),
                           (17, 13, 47, 16), (15, 16, 49, 21),
                           (13, 21, 51, 51)]:
        rect(x0, y0, x1, y1, "purple")
    for x0, y0, x1, y1 in [(25, 11, 39, 13), (20, 13, 44, 15),
                           (18, 15, 46, 18), (16, 18, 48, 22),
                           (15, 22, 49, 51)]:
        rect(x0, y0, x1, y1, "void")
    rect(20, 11, 25, 12, "violet")
    rect(16, 17, 17, 28, "violet")
    rect(14, 31, 15, 43, "violet")
    rect(48, 24, 49, 33, "violet")
    rect(49, 38, 50, 46, "violet")

    # Capucha de La Muerte; silueta maciza que sigue siendo legible en 32x32.
    rect(27, 15, 37, 17, "hood")
    rect(23, 17, 41, 20, "hood")
    rect(20, 20, 44, 27, "hood")
    rect(18, 27, 46, 39, "hood")
    rect(16, 39, 48, 50, "hood")
    rect(22, 21, 25, 37, "cloth")
    rect(25, 18, 29, 21, "fold")
    rect(20, 29, 22, 42, "fold")
    rect(18, 43, 21, 49, "cloth")
    rect(41, 25, 43, 40, "cloth")
    rect(44, 40, 46, 49, "cloth")
    rect(24, 23, 40, 37, "void")

    # Mascara osea: volumen pixelado, ojos frios, sin detalle fotografico.
    rect(27, 23, 37, 25, "bone")
    rect(25, 25, 39, 32, "bone")
    rect(27, 32, 37, 36, "bone_shadow")
    rect(37, 25, 39, 32, "bone_shadow")
    rect(25, 30, 27, 32, "bone_dark")
    rect(27, 27, 31, 30, "void")
    rect(33, 27, 37, 30, "void")
    rect(28, 28, 30, 29, "blue")
    rect(34, 28, 36, 29, "blue")
    rect(31, 30, 33, 32, "bone_dark")
    rect(28, 33, 29, 35, "void")
    rect(31, 33, 32, 36, "void")
    rect(34, 33, 35, 35, "void")
    rect(28, 38, 36, 40, "fold")
    rect(26, 40, 38, 43, "cloth")
    rect(30, 42, 34, 48, "void")

    # Dos cadenas atadas al pacto, eslabones discretos de hierro ennegrecido.
    for side in (0, 1):
        for i in range(5):
            x = 9+i*3 if side == 0 else 50-i*3
            y = 47-i*2
            rect(x, y, x+5, y+4, "void")
            rect(x+1, y, x+4, y+1, "iron_light")
            rect(x, y+1, x+1, y+3, "iron")
            rect(x+4, y+1, x+5, y+3, "iron_dark")
            rect(x+1, y+3, x+4, y+4, "iron")

    # Altar de Ceniza y sello de alma azul grisaceo.
    rect(19, 49, 45, 52, "void")
    rect(20, 49, 44, 50, "rim_light")
    rect(22, 51, 42, 55, "cloth")
    rect(18, 55, 46, 58, "stone")
    rect(18, 55, 46, 56, "fold")
    rect(30, 47, 34, 53, "purple")
    rect(31, 46, 33, 54, "violet")
    rect(29, 49, 35, 51, "blue")
    rect(31, 48, 33, 52, "light")
    for x, y in [(8, 25), (54, 16), (53, 44), (10, 36)]:
        rect(x, y, x+1, y+2, "violet")

    target = ROOT / "src/main/resources/assets/theslave/icon.png"
    pixels.png(str(target), 256, 256, lambda x, y: canvas[y//4][x//4])
    print(target)


if __name__ == "__main__":
    generate()
