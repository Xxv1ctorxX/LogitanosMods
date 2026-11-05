"""Verificaciones de los assets pixel-art: python tools/test_whip_assets.py."""
import json
import math
from pathlib import Path
import unittest
import generate_assets as g


class WhipAssetsTest(unittest.TestCase):
    def test_logarithmic_spiral_and_tangent(self):
        pts = g.espiral(960)
        radii = [math.hypot(x - g.ESP_C[0], y - g.ESP_C[1]) for x, y, *_ in pts]
        ratio = g.ESP_RHO ** (1 / 960)
        for a, b in zip(radii, radii[1:]):
            self.assertAlmostEqual(b / a, ratio, places=12)
        x0, y0, x1, y1 = g.mango_geom()
        self.assertAlmostEqual(math.hypot(x1 - x0, y1 - y0), g.MANGO_LARGO)
        _, _, _, tx, ty = pts[0]
        self.assertAlmostEqual((x1 - x0) * ty - (y1 - y0) * tx, 0)
        self.assertGreater(x1, x0)
        self.assertGreater(y1, y0)
        self.assertGreaterEqual(g.ESP_VUELTAS, 2)
        self.assertLessEqual(g.ESP_VUELTAS, 3)

    def test_turns_do_not_intersect(self):
        pts = g.espiral(960)
        for i, (x, y, w, *_) in enumerate(pts):
            for xx, yy, ww, *_ in pts[i + 100:]:
                self.assertGreater(math.hypot(x - xx, y - yy), w + ww)

    def test_pixel_icon_is_one_compact_object(self):
        for size in (16, 32):
            icon = g.latigo(size)
            solid = {(x, y) for y, row in enumerate(icon) for x, pixel in enumerate(row) if pixel[3]}
            self.assertTrue(solid)
            self.assertTrue(all(0 < x < size - 1 and 0 < y < size - 1 for x, y in solid))
            connected = {next(iter(solid))}
            pending = list(connected)
            while pending:
                x, y = pending.pop()
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        p = x + dx, y + dy
                        if p in solid and p not in connected:
                            connected.add(p)
                            pending.append(p)
            self.assertEqual(connected, solid, f"Hay pixeles desconectados a {size}x{size}")
            self.assertTrue(all(c[3] in (0, 255) for row in icon for c in row))

    def test_packaged_geometry_matches_icon(self):
        data = json.loads((Path(g.ASSETS) / 'latigo_geometry.json').read_text())
        for p, (x, y, w, *_) in zip(data['points'], g.espiral(180), strict=True):
            self.assertAlmostEqual(p[0] * 32, x)
            self.assertAlmostEqual(32 - p[1] * 32, y)
            self.assertAlmostEqual(p[3] * 32, w)
        item = json.loads((Path(g.ASSETS) / 'items/latigo.json').read_text())['model']
        self.assertEqual(item['property'], 'minecraft:display_context')
        self.assertEqual(item['cases'][0]['when'], 'gui')
        self.assertTrue(item['cases'][0]['model']['model'].endswith('/latigo_icon'))
        model = g.latigo_modelo()
        for element in model['elements']:
            if 'rotation' in element:
                self.assertIn(element['rotation']['angle'], (-45, -22.5, 0, 22.5, 45))


if __name__ == '__main__':
    unittest.main()
