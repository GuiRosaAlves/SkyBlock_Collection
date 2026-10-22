"""Unit tests for SkyVoid's exact structure-set override semantics."""
import json
import tempfile
import unittest
from pathlib import Path

from tools.structure_overrides import DENSITY, apply_rules, compile_overrides, structure_catalog


SAMPLE = {
    "structures": [
        {"structure": "skyvoid_biome_islands:badlands", "weight": 1},
        {"structure": "skyvoid_biome_islands:desert", "weight": 1},
    ],
    "placement": {
        "type": "minecraft:random_spread",
        "spacing": 20,
        "separation": 8,
        "salt": 10387312,
        "spread_type": "triangular",
    }
}


class StructureOverridesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.pack = root / "pack"
        src = self.pack / "data/skyvoid_biome_islands/worldgen/structure_set/biome_islands.json"
        src.parent.mkdir(parents=True)
        src.write_text(json.dumps(SAMPLE), encoding="utf-8")
        (self.pack / "pack.mcmeta").write_text(
            json.dumps({"pack": {"min_format": [1, 0], "max_format": [99, 0],
                                  "description": "source"}}), encoding="utf-8")
        self.output = root / "overlay"
        self.name = "skyvoid_biome_islands:biome_islands"

    def test_catalog(self):
        catalog = structure_catalog([self.pack])
        self.assertEqual(catalog[self.name]["placement_type"], "minecraft:random_spread")
        self.assertEqual(len(catalog[self.name]["structures"]), 2)

    def test_default_preserves_original(self):
        self.assertEqual(apply_rules(self.name, SAMPLE, {}), SAMPLE)
        self.assertEqual(compile_overrides([self.pack], {"structure_sets": {}}, self.output), {})
        self.assertTrue((self.output / "pack.mcmeta").exists())

    def test_disables_one_and_adjusts_relative_weight(self):
        patched = apply_rules(self.name, SAMPLE, {
            "structures": {"skyvoid_biome_islands:badlands": {"enabled": False},
                           "skyvoid_biome_islands:desert": {"weight": 5}}})
        self.assertEqual(patched["structures"], [
            {"structure": "skyvoid_biome_islands:desert", "weight": 5}])
        self.assertEqual(patched["placement"], SAMPLE["placement"])

    def test_density_alters_spacing_not_salt_or_biome_filters(self):
        for preset in DENSITY:
            out = apply_rules(self.name, SAMPLE, {"density": preset})
            self.assertGreater(out["placement"]["spacing"], out["placement"]["separation"])
            self.assertEqual(out["placement"]["salt"], SAMPLE["placement"]["salt"])
        self.assertGreater(apply_rules(self.name, SAMPLE, {"density": "rare"})["placement"]["spacing"], 20)
        self.assertLess(apply_rules(self.name, SAMPLE, {"density": "common"})["placement"]["spacing"], 20)

    def test_generates_real_registry_paths(self):
        written = compile_overrides([self.pack],
                                    {"structure_sets": {self.name: {"density": "rare"}}},
                                    self.output)
        self.assertIn(self.name, written)
        file = self.output / written[self.name]
        self.assertEqual(json.loads(file.read_text())["placement"]["spacing"], 30)

    def test_rejects_invalid_inputs(self):
        with self.assertRaisesRegex(ValueError, "empty structure set"):
            apply_rules(self.name, SAMPLE, {"structures": {
                key["structure"]: {"enabled": False} for key in SAMPLE["structures"]}})
        with self.assertRaisesRegex(ValueError, "out of range"):
            apply_rules(self.name, SAMPLE, {"structures": {
                "skyvoid_biome_islands:desert": {"weight": 0}}})
        with self.assertRaisesRegex(ValueError, "Unknown selected structure"):
            compile_overrides([self.pack], {"structure_sets": {
                "minecraft:unknown": {}}}, self.output)

    def test_does_not_silently_modify_stronghold_rings(self):
        stronghold = {"placement": {"type": "minecraft:concentric_rings",
                                    "count": 128, "distance": 32, "spread": 3},
                      "structures": [{"structure": "skyvoid_worldgen:stronghold", "weight": 1}]}
        with self.assertRaisesRegex(ValueError, "require random_spread"):
            apply_rules("skyvoid_worldgen:strongholds", stronghold, {"density": "rare"})
        with self.assertRaisesRegex(ValueError, "progression-critical"):
            apply_rules("skyvoid_worldgen:strongholds", stronghold, {"structures": {
                "skyvoid_worldgen:stronghold": {"enabled": False}}})


if __name__ == "__main__":
    unittest.main()
