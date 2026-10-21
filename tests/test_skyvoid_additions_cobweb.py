"""Regression guard for the standalone SkyVoid Additions cobweb mechanic.

These static checks verify the shipped mcfunction wiring and block predicates.
Actual spawn/placement behavior still requires Minecraft gameplay validation.
"""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "src/packs/extras/skyvoid_additions/data/skyvoid_additions"
CLOCK = PACK / "function/mechanics/cobwebs_from_spiders/clock.mcfunction"
PLACE = PACK / "function/mechanics/cobwebs_from_spiders/try_place.mcfunction"
LOAD = PACK / "function/technical/load.mcfunction"
DEFAULTS = PACK / "function/customize/default_values.mcfunction"


class CobwebMechanicTests(unittest.TestCase):
    def test_schedule_and_single_chance_roll(self):
        clock = CLOCK.read_text(encoding="utf-8")
        self.assertIn("cobwebs_from_spiders/clock 2s", clock)
        self.assertEqual(clock.count("predicate=skyvoid_additions:cobwebs_from_spiders/cobweb_chance"), 1)
        self.assertIn("if block ~ ~ ~ #minecraft:air", clock)
        self.assertIn("if block ~ ~-1 ~ #minecraft:air", clock)
        self.assertIn("run function skyvoid_additions:mechanics/cobwebs_from_spiders/try_place", clock)
        chance = json.loads((PACK / "predicate/cobwebs_from_spiders/cobweb_chance.json").read_text())
        self.assertEqual(chance["chance"], 0.05)

    def test_covers_ceiling_and_cardinal_wall_top_edges(self):
        place = PLACE.read_text(encoding="utf-8")
        for direction in ("~1 ~1 ~", "~-1 ~1 ~", "~ ~1 ~1", "~ ~1 ~-1"):
            self.assertIn(f"unless block {direction} #minecraft:air", place)
        self.assertIn("unless block ~ ~1 ~ #minecraft:air", place)
        self.assertEqual(place.count("run setblock ~ ~ ~ minecraft:cobweb keep"), 5)

    def test_defaults_off_and_activated_by_load(self):
        defaults = DEFAULTS.read_text(encoding="utf-8")
        self.assertIn("scoreboard players set cobwebs_from_spiders skyvoid_config 0", defaults)
        load = LOAD.read_text(encoding="utf-8")
        self.assertIn("if score cobwebs_from_spiders skyvoid_config matches 1 run schedule function skyvoid_additions:mechanics/cobwebs_from_spiders/clock", load)


if __name__ == "__main__":
    unittest.main()
