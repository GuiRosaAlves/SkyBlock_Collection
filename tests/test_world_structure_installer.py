import json
import tempfile
import unittest
from pathlib import Path

from tools.install_world_structure_overrides import PACK_NAME, install


class WorldStructureInstallerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        base = Path(self.temp.name)
        self.world = base / "world"
        self.world.mkdir()
        (self.world / "level.dat").write_bytes(b"dummy level data")
        self.pack = base / "source"
        source = self.pack / "data/skyvoid_biome_islands/worldgen/structure_set/biome_islands.json"
        source.parent.mkdir(parents=True)
        source.write_text(json.dumps({
            "structures": [{"structure": "skyvoid_biome_islands:desert", "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": 20,
                          "separation": 8, "salt": 10387312}
        }))
        (self.pack / "pack.mcmeta").write_text(json.dumps({
            "pack": {"min_format": [1, 0], "max_format": [99, 0], "description": "test"}
        }))
        self.controls = {"structure_sets": {"skyvoid_biome_islands:biome_islands": {"density": "rare"}}}

    def test_installs_only_to_selected_world(self):
        path = install(self.world, [self.pack], self.controls)
        self.assertEqual(path, self.world / "datapacks" / PACK_NAME)
        data = json.loads((path / "data/skyvoid_biome_islands/worldgen/structure_set/biome_islands.json").read_text())
        self.assertEqual(data["placement"]["spacing"], 30)
        self.assertEqual((self.world / "level.dat").read_bytes(), b"dummy level data")

    def test_requires_backup_opt_in_for_replacing(self):
        destination = install(self.world, [self.pack], self.controls)
        with self.assertRaises(FileExistsError):
            install(self.world, [self.pack], self.controls)
        install(self.world, [self.pack], {"structure_sets": {
            "skyvoid_biome_islands:biome_islands": {"density": "very_rare"}}}, replace=True)
        self.assertTrue((self.world / ".skyvoid-backups" / PACK_NAME).exists())
        with self.assertRaises(FileExistsError):
            install(self.world, [self.pack], self.controls, replace=True)

    def test_rejects_wrong_world_and_noop_without_writing(self):
        with self.assertRaises(ValueError):
            install(self.world / "datapacks", [self.pack], self.controls)
        with self.assertRaisesRegex(ValueError, "no structure overrides"):
            install(self.world, [self.pack], {"structure_sets": {}})
        self.assertFalse((self.world / "datapacks").exists())

    def test_rejects_invalid_settings_without_world_modifications(self):
        with self.assertRaises(ValueError):
            install(self.world, [self.pack], {"structure_sets": {"wrong:identifier": {}}})
        self.assertFalse((self.world / "datapacks").exists())


if __name__ == "__main__":
    unittest.main()
