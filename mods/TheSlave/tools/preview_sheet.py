"""Hoja de contacto ampliada de las texturas (solo stdlib) para revisarlas a ojo.

Uso: python -I tools/preview_sheet.py <salida.png> <grupo>   grupo = items | bloques | entidades
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate_assets as g  # noqa: E402
import generate_lore_assets as L  # noqa: E402

TEX = L.TEX
GRUPOS = {
    "items": ("item", ["contrato", "latigo", "relicario_animas", "ofrenda_muerte", "tributo_condena", "sello_almas",
                         "tributo_doble_condena", "brujula_ceniza_00", "brujula_ceniza_08", "brujula_ceniza_16"]),
    "bloques": ("block", ["altar_side", "altar_top", "altar_front", "altar_vela", "altar_llama", "piedra_infectada",
                            "pizarra_infectada", "tierra_infectada", "cesped_infectado_top", "cesped_infectado_side",
                            "grava_infectada", "arena_infectada", "netherrack_infectada", "piedra_negra_infectada",
                            "raiz_ceniza", "raicillas_infectadas", "losa_ceniza", "velas_notario", "urna_animas",
                            "archivo_condenas", "mesa_notario", "lampara_animas", "piedra_registro", "tumba"]),
    "entidades": ("entity", ["notario", "la_muerte", "esclavo_0", "esclavo_f_0_1"]),
}


def main():
    out, grupo = sys.argv[1], sys.argv[2]
    carpeta, nombres = GRUPOS[grupo]
    imgs = []
    for n in nombres:
        ruta = os.path.join(TEX, carpeta, n + ".png")
        if os.path.exists(ruta):
            imgs.append(L.leer_png(ruta))
    esc = 8 if grupo != "entidades" else 4
    celda = max(len(i) for i in imgs) * esc + 8
    cols = 6 if grupo != "entidades" else 2
    filas = (len(imgs) + cols - 1) // cols
    W, H = cols * celda, filas * celda
    lienzo = [[(70, 70, 76, 255)] * W for _ in range(H)]
    for k, im in enumerate(imgs):
        ox, oy = (k % cols) * celda + 4, (k // cols) * celda + 4
        for y, fila in enumerate(im):
            for x, p in enumerate(fila):
                c = p if p[3] == 255 else ((90, 90, 96, 255) if (x + y) % 2 else (60, 60, 66, 255)) if p[3] == 0 else p
                for dy in range(esc):
                    for dx in range(esc):
                        lienzo[oy + y * esc + dy][ox + x * esc + dx] = c
    g.png(out, W, H, lambda x, y: lienzo[y][x])


main()
