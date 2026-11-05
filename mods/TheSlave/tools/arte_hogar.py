"""Lecho personal y alma transportable: pixel art nativo y modelos de cajas, sin recursos protegidos."""
import copy
import os
import generate_assets as g
import arte_items as a
import modelos3d as m

LECHO = [
    "..oooooooooooo..", "..ojKzzzzzzKjo..", "..ojKzaAAazKjo..", "..ojKzzzzzzKjo..",
    "..ojKKKKKKKKjo..", "..ojKkkkbkkKjo..", "..ojKkkkmkkKjo..", "..ojKkkkbkkKjo..",
    "..ojKkkkmkkKjo..", "..ojKkkkbkkKjo..", "..ojKkkkmkkKjo..", "..ojKkkkkkkKjo..",
    "..ojKzzzzzzKjo..", "..ojjjjjjjjjjo..", "..ooo......ooo..", "................",
]
ALMA = [
    ".......oo.......", "......ojbo......", ".....ojbBbo.....", "....ojbBBBbo....",
    "...ojbBzzBBbo...", "..ojbBzBzBBBbo..", ".ojbBBzBBzBBBbo.", ".ojbBBBzzBBBBbo.",
    "..ojbBBBBBBBbo..", "...ojbBBBBBbo...", "....ojbBBbbo....", ".....ojbbo......",
    ".....ojbo.......", "......oo........", "................", "................",
]


def elementos(part, grave=False, fused=False):
    boxes = [m.caja((1, 2, 0), (15, 4, 16), 4),
             m.caja((1, 0, 1), (3, 3, 3), 10), m.caja((13, 0, 1), (15, 3, 3), 10),
             m.caja((1, 0, 13), (3, 3, 15), 10), m.caja((13, 0, 13), (15, 3, 15), 10),
             m.caja((1, 4, 0), (2, 7, 16), 8), m.caja((14, 4, 0), (15, 7, 16), 8)]
    if not grave:
        boxes += [m.caja((2, 4, 0), (14, 8, 16), 5)]
        for z in (3, 7, 11, 15):
            boxes.append(m.caja((7.5, 8, z-1), (8.5, 8.2, z), 3 if not fused else 9))
        if part == "head":
            boxes += [m.caja((3, 8, 1), (13, 9, 6), 6), m.caja((2, 4, 0), (14, 11, 1), 4)]
        else:
            boxes += [m.caja((2, 4, 15), (14, 9, 16), 4)]
    else:
        boxes += [m.caja((2, 4, 0), (14, 5, 16), 10)]
        for z in (4, 9, 14):
            boxes.append(m.caja((5, 5, z-1), (11, 5.2, z), 6))
        if part == "head":
            if fused:
                boxes += [m.caja((3, 5, 1), (7.6, 11, 4), 4), m.caja((8.4, 5, 1), (13, 12, 4), 4),
                          m.caja((7.6, 5, 2), (8.4, 11, 3), 3)]
                marcas = (5, 10)
            else:
                boxes += [m.caja((3, 5, 1), (13, 11, 4), 4), m.caja((5, 11, 1), (11, 12, 4), 4)]
                marcas = (7.5,)
            for x in marcas:
                boxes += [m.caja((x, 7, 0.8), (x+1, 10, 1), 3), m.caja((x-1, 9, 0.8), (x+2, 10, 1), 6)]
    return boxes


def generar(escribe, assets):
    tex = os.path.join(assets, "textures", "item")
    g.guardar(os.path.join(tex, "lecho_ceniza.png"), a.arte(LECHO))
    alma = a.arte(ALMA)
    g.guardar(os.path.join(tex, "alma_vinculada.png"), alma)
    doble = g.lienzo(16, 16)
    for y in range(14):
        for x in range(4, 12):
            color = alma[y][x]
            if color[3]:
                doble[y][x-3] = color
                doble[y][x+3] = (color[2], color[1]*3//4, color[0], 255)
    g.guardar(os.path.join(tex, "alma_vinculada_fusionada.png"), doble)
    variantes = {}
    for part in ("head", "foot"):
        for grave in (False, True):
            for fused in (False, True):
                name = f"lecho_ceniza_{part}_{'tumba' if grave else 'vivo'}{'_fusionado' if fused else ''}"
                escribe(os.path.join(assets, "models", "block", name+".json"),
                        {"textures": {"t": m.TEXTURA, "particle": m.TEXTURA}, "elements": elementos(part, grave, fused)})
                for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                    variantes[f"facing={facing},part={part},grave={str(grave).lower()},fusionado={str(fused).lower()}"] = {
                        "model": "theslave:block/"+name, "y": rotation}
    escribe(os.path.join(assets, "blockstates", "lecho_ceniza.json"), {"variants": variantes})
    combined = elementos("head")
    for element in elementos("foot"):
        element = copy.deepcopy(element)
        element["from"][2] += 16
        element["to"][2] += 16
        combined.append(element)
    display = copy.deepcopy(m.DISPLAY)
    for context in display:
        display[context]["scale"] = [.4, .4, .4]
    for name, model in (("lecho_ceniza", combined),
                        ("alma_vinculada", [m.caja((7, 0, 7), (9, 4, 9), 3), m.caja((5, 4, 5), (11, 10, 11), 3),
                                            m.caja((6, 10, 6), (10, 13, 10), 13), m.caja((7, 13, 7), (9, 15, 9), 3)]),
                        ("alma_vinculada_fusionada", [m.caja((3, 3, 6), (7, 11, 10), 3), m.caja((9, 3, 6), (13, 12, 10), 9),
                                                      m.caja((6, 6, 7), (10, 8, 9), 13), m.caja((6.5, 0, 7), (9.5, 6, 9), 3)])):
        escribe(os.path.join(assets, "models", "item", name+".json"),
                {"textures": {"t": m.TEXTURA, "particle": m.TEXTURA}, "elements": model, "display": display if name=="lecho_ceniza" else m.DISPLAY})
        escribe(os.path.join(assets, "models", "item", name+"_icono.json"),
                {"parent": "minecraft:item/generated", "textures": {"layer0": "theslave:item/"+name}})
        escribe(os.path.join(assets, "items", name+".json"), {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui"], "model": {"type": "minecraft:model", "model": "theslave:item/"+name+"_icono"}}],
            "fallback": {"type": "minecraft:model", "model": "theslave:item/"+name}}})
