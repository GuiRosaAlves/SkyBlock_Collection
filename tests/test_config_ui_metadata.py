"""Translations and data-driven descriptions from SkyVoid Additions."""
import unittest
from pathlib import Path
from tools.prepare_neoforge_resources import (
    MENU_SOURCE, additions_defaults, additions_descriptions, config_translations,
)

ROOT = Path(__file__).resolve().parents[1]
DEFAULTS = ROOT / (
    "src/packs/extras/skyvoid_additions/data/skyvoid_additions/"
    "function/customize/default_values.mcfunction"
)


class ConfigurationUIMetadataTests(unittest.TestCase):
    def test_human_labels_and_dependencies_match_original_datapack(self):
        data = additions_descriptions(ROOT / MENU_SOURCE)
        self.assertIn("cobwebs_from_spiders", data)
        self.assertEqual(data["cobwebs_from_spiders"]["name"], "Spiders Generate Cobwebs")
        self.assertIn("Requires Additional Wandering Trades",
                      data["sniffer_eggs_from_traders"]["description"])

    def test_all_boolean_settings_have_translatable_labels(self):
        defaults = additions_defaults(DEFAULTS)
        metadata = additions_descriptions(ROOT / MENU_SOURCE)
        en, pt = config_translations([], defaults, metadata)
        for setting in defaults:
            key = "skyvoid.config.additions." + setting
            self.assertIn(key, en)
            self.assertIn(key + ".tooltip", en)
        for group in ("new_world", "optional_packs", "additions"):
            self.assertIn("skyvoid.configuration." + group, en)
            self.assertIn("skyvoid.configuration." + group, pt)
        self.assertNotIn("**", en["skyvoid.config.additions.sniffer_eggs_from_traders.tooltip"])


if __name__ == "__main__":
    unittest.main()
