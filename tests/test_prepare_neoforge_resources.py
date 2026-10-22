"""Offline tests for the NeoForge pack staging step.

Run from repository root: python -m unittest discover -s tests
"""
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from tools.prepare_neoforge_resources import (
    ISLANDS,
    WORLDGEN,
    addition_defaults,
    build_catalog,
    stage_pack,
)


class NeoForgeStagingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.build = self.root / "build"
        self.build.mkdir()
        self.staged = self.root / "output"
        self.source = self.root / "default_values.mcfunction"
        self.source.write_text(
            "execute unless score armor_chipping skyvoid_config matches 0..1 "
            "run scoreboard players set armor_chipping skyvoid_config 1\n"
            "execute unless score allay_conversion skyvoid_config matches 0..1 "
            "run scoreboard players set allay_conversion skyvoid_config 0\n",
            encoding="utf-8",
        )

    def add_pack(self, pack_id, version="1_0_0"):
        archive = self.build / f"{pack_id}_v{version}-MC_26_2.zip"
        with ZipFile(archive, "w") as zip_file:
            zip_file.writestr(
                "pack.mcmeta", json.dumps({"pack": {"description": pack_id}})
            )
            if pack_id.startswith("rp_"):
                zip_file.writestr("assets/skyvoid/example.txt", "example")
            else:
                zip_file.writestr("data/skyvoid/example.txt", "example")
        return archive

    def add_required_packs(self):
        for pack_id in WORLDGEN | ISLANDS | {"skyvoid_additions"}:
            self.add_pack(pack_id)

    def test_extracts_defaults_without_overwriting_manual_values(self):
        self.assertEqual(
            addition_defaults(self.source),
            {"armor_chipping": True, "allay_conversion": False},
        )

    def test_stages_isolated_datapacks_and_resourcepack(self):
        self.add_required_packs()
        self.add_pack("rp_skyvoid_advancements")
        self.add_pack("skyvoid_compat_terralith", version="1_0_2+terralith-2_5_5")
        outcome = build_catalog(self.build, self.staged, self.source)
        self.assertEqual(outcome["packs"], len(WORLDGEN) + len(ISLANDS) + 3)
        self.assertEqual(outcome["addition_settings"], 2)
        self.assertTrue((self.staged / "data/skyvoid/datapacks/skyvoid_compat_terralith/pack.mcmeta").is_file())
        for pack_id in WORLDGEN | ISLANDS | {"skyvoid_additions"}:
            self.assertTrue(
                (self.staged / "data/skyvoid/datapacks" / pack_id / "pack.mcmeta").is_file()
            )
        self.assertTrue(
            (self.staged / "assets/skyvoid/resourcepacks"
             / "rp_skyvoid_advancements/pack.mcmeta").is_file()
        )
        self.assertEqual(
            json.loads(
                (self.staged / "assets/skyvoid/config/additions-defaults.json")
                .read_text(encoding="utf-8")
            )["allay_conversion"],
            False,
        )

    def test_staging_emits_pack_scoped_structure_catalog(self):
        self.add_required_packs()
        archive = self.add_pack("skyvoid_biome_islands")
        structure = {
            "structures": [{"structure": "skyvoid_biome_islands:desert", "weight": 1}],
            "placement": {"type": "minecraft:random_spread",
                          "spacing": 20, "separation": 8, "salt": 10387312},
        }
        with ZipFile(archive, "a") as zip_file:
            zip_file.writestr(
                "data/skyvoid_biome_islands/worldgen/structure_set/biome_islands.json",
                json.dumps(structure),
            )
        build_catalog(self.build, self.staged, self.source)
        index = json.loads((
            self.staged / "assets/skyvoid/config/structure-catalog.json"
        ).read_text(encoding="utf-8"))
        self.assertIn("skyvoid_biome_islands", index)
        entry = index["skyvoid_biome_islands"]["skyvoid_biome_islands:biome_islands"]
        self.assertEqual(entry["structures"]["skyvoid_biome_islands:desert"], 1)
        self.assertEqual(entry["placement_type"], "minecraft:random_spread")

    def test_missing_required_pack_fails_before_touching_output(self):
        self.add_pack("skyvoid_additions")
        with self.assertRaisesRegex(ValueError, "Missing required"):
            build_catalog(self.build, self.staged, self.source)
        self.assertFalse(self.staged.exists())

    def test_rejects_multiple_versions_of_same_pack(self):
        self.add_required_packs()
        self.add_pack("skyvoid_worldgen", version="1_0_1")
        with self.assertRaisesRegex(ValueError, "More than one release"):
            build_catalog(self.build, self.staged, self.source)

    def test_rejects_unsafe_zip_entry(self):
        archive = self.build / "bad.zip"
        with ZipFile(archive, "w") as zip_file:
            zip_file.writestr("pack.mcmeta", "{}")
            zip_file.writestr("data/a.txt", "okay")
            zip_file.writestr("../escape.txt", "not okay")
        with self.assertRaisesRegex(ValueError, "Unsafe ZIP entry"):
            stage_pack(archive, self.staged)


if __name__ == "__main__":
    unittest.main()
