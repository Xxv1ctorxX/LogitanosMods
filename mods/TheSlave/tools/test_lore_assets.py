"""Pruebas de recursos sin arrancar el juego: python tools/test_lore_assets.py."""
from pathlib import Path
import hashlib
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/theslave"

class LoreAssetsTest(unittest.TestCase):
    def test_protected_assets(self):
        protected=json.loads((ROOT/"tools/protected_assets.json").read_text())
        self.assertEqual(len(protected),9)
        for relative,expected in protected.items():
            self.assertEqual(hashlib.sha256((ROOT/relative).read_bytes()).hexdigest(),expected,relative)

    def test_model_textures_and_extents(self):
        for path in (ASSETS/"models").rglob("*.json"):
            model=json.loads(path.read_text(encoding="utf-8"))
            for tex in model.get("textures",{}).values():
                if tex.startswith("theslave:"):
                    self.assertTrue((ASSETS/"textures"/(tex.split(":",1)[1]+".png")).is_file(),str(path)+tex)
            for element in model.get("elements",[]):
                for a,b in zip(element["from"],element["to"]):
                    self.assertLessEqual(a,b,str(path))
                    self.assertGreaterEqual(a,-16,str(path))
                    self.assertLessEqual(b,32,str(path))

    def test_rotated_candles(self):
        parts=json.loads((ASSETS/"blockstates/altar_ceniza.json").read_text())["multipart"]
        for i in range(4):
            flames=[p for p in parts if p.get("when",{}).get(f"vela_{i}")=="true"]
            self.assertEqual({p["when"]["facing"] for p in flames},{"north","east","south","west"})
            self.assertEqual({p["apply"]["y"] for p in flames},{0,90,180,270})

    def test_hollow_altar(self):
        elements=json.loads((ASSETS/"models/block/altar_ceniza.json").read_text())["elements"]
        self.assertFalse(any(e["from"][0]<8<e["to"][0] and e["from"][2]<8<e["to"][2] and e["from"][1]<10<e["to"][1] for e in elements))

    def test_personal_home_models(self):
        states=json.loads((ASSETS/"blockstates/lecho_ceniza.json").read_text())["variants"]
        self.assertEqual(len(states),32)
        for key,value in states.items():
            self.assertIn("part=",key)
            self.assertIn("grave=",key)
            self.assertIn("fusionado=",key)
            self.assertTrue((ASSETS/"models"/(value["model"].split(":",1)[1]+".json")).is_file())
        for name in ("lecho_ceniza","alma_vinculada","alma_vinculada_fusionada"):
            self.assertTrue((ASSETS/"items"/(name+".json")).is_file())
            raw=(ASSETS/"textures/item"/(name+".png")).read_bytes()
            self.assertEqual(raw[:8],b"\x89PNG\r\n\x1a\n")
            self.assertEqual(int.from_bytes(raw[16:20],"big"),16)
            self.assertEqual(int.from_bytes(raw[20:24],"big"),16)

if __name__=="__main__":unittest.main()
