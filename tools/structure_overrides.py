#!/usr/bin/env python3
"""Compile SkyVoid structure-set settings to a high-priority data pack.

This operates on *selected* Beet pack output only. Minecraft's vanilla
structure sets are not represented by these standalone archives; their
registries must be resolved separately before exposing editable controls.
This tool does not hot-swap worldgen or modify any existing save.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

DENSITY = {"very_rare": 2.0, "rare": 1.5, "normal": 1.0,
           "common": 0.75, "very_common": 0.5}
PROTECTED_STRUCTURE_SETS = {"strongholds", "stronghold"}
ROOT_KEY = "structure_sets"


def structure_set_files(packs: list[Path]) -> dict[str, Path]:
    found: dict[str, Path] = {}
    for pack in packs:
        for file in sorted((pack / "data").glob("*/worldgen/structure_set/*.json")):
            namespace = file.relative_to(pack / "data").parts[0]
            identifier = namespace + ":" + file.stem
            if identifier in found:
                raise ValueError(f"Structure set {identifier} appears in multiple selected packs")
            found[identifier] = file
    return found


def structure_catalog(packs: list[Path]) -> dict[str, dict]:
    result = {}
    for ident, path in structure_set_files(packs).items():
        data = json.loads(path.read_text(encoding="utf-8"))
        result[ident] = {
            "placement_type": data["placement"]["type"],
            "structures": {entry["structure"]: entry["weight"] for entry in data["structures"]},
            "protected": ident.split(":", 1)[1] in PROTECTED_STRUCTURE_SETS,
        }
    return result


def apply_rules(name: str, original: dict, rules: dict) -> dict:
    """Modify entries within one structure set, preserving unknown JSON keys."""
    if not isinstance(rules, dict):
        raise ValueError(f"Invalid controls for {name}")
    if set(rules) - {"density", "structures"}:
        raise ValueError(f"Unknown structure set option for {name}")
    data = copy.deepcopy(original)
    existing = {entry["structure"] for entry in data["structures"]}
    controls = rules.get("structures", {})
    if not isinstance(controls, dict) or set(controls) - existing:
        raise ValueError(f"Unknown structure id for {name}")
    protected = name.split(":", 1)[1] in PROTECTED_STRUCTURE_SETS
    entries = []
    for entry in data["structures"]:
        setting = controls.get(entry["structure"], {})
        if not isinstance(setting, dict) or set(setting) - {"enabled", "weight"}:
            raise ValueError(f"Invalid structure options for {entry['structure']}")
        enabled = setting.get("enabled", True)
        if not isinstance(enabled, bool):
            raise ValueError(f"Enabled must be boolean for {entry['structure']}")
        if not enabled:
            if protected:
                raise ValueError(f"Cannot disable progression-critical structure set {name}")
            continue
        weight = setting.get("weight", entry["weight"])
        if type(weight) is not int or not 1 <= weight <= 1000:
            raise ValueError(f"Weight out of range [1,1000] for {entry['structure']}")
        entry["weight"] = weight
        entries.append(entry)
    if not entries:
        raise ValueError(f"Cannot disable every entry in {name}; an empty structure set is invalid")
    data["structures"] = entries
    density = rules.get("density", "normal")
    if density not in DENSITY:
        raise ValueError(f"Unknown density preset for {name}: {density}")
    if density != "normal":
        placement = data["placement"]
        if placement.get("type") != "minecraft:random_spread":
            raise ValueError(
                f"Density presets require random_spread; {name} uses {placement.get('type')}")
        separation = placement["separation"]
        spacing = placement["spacing"]
        if not isinstance(separation, int) or not isinstance(spacing, int) or spacing <= separation:
            raise ValueError(f"Invalid vanilla placement spacing/separation in {name}")
        # Never violate spacing > separation. Leave salt/spread_type/frequency
        # untouched. Relative weights alone do not change absolute density.
        placement["spacing"] = min(4096, max(separation + 1,
                                             round(spacing * DENSITY[density])))
    return data


def compile_overrides(packs: list[Path], config: dict, output: Path) -> dict:
    """Write a distributable overlay pack with *only changed* structure sets."""
    if set(config) - {ROOT_KEY}:
        raise ValueError("Unknown top-level structure controls")
    wanted = config.get(ROOT_KEY, {})
    if not isinstance(wanted, dict):
        raise ValueError("structure_sets must be an object")
    files = structure_set_files(packs)
    if set(wanted) - set(files):
        raise ValueError(f"Unknown selected structure sets: {sorted(set(wanted) - set(files))}")
    if output.exists() and any(output.iterdir()):
        raise ValueError(f"Overlay output must be empty: {output}")
    output.mkdir(parents=True, exist_ok=True)
    # Match pack format bounds from the selected source archive.
    manifest = next((p / "pack.mcmeta" for p in packs if (p / "pack.mcmeta").is_file()), None)
    if manifest is None:
        raise ValueError("Missing source pack.mcmeta")
    meta = json.loads(manifest.read_text(encoding="utf-8"))
    meta["pack"]["description"] = "SkyVoid structure overrides (new chunks only)"
    (output / "pack.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    written = {}
    for ident, rules in sorted(wanted.items()):
        path = files[ident]
        original = json.loads(path.read_text(encoding="utf-8"))
        patched = apply_rules(ident, original, rules)
        if patched != original:
            namespace, filename = ident.split(":", 1)
            destination = output / "data" / namespace / "worldgen" / "structure_set" / f"{filename}.json"
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(json.dumps(patched, indent=2) + "\n", encoding="utf-8")
            written[ident] = str(destination.relative_to(output))
    return written


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, action="append", required=True,
                        help="selected compiled pack directory (may repeat)")
    parser.add_argument("--config", type=Path, required=True, help="JSON controls")
    parser.add_argument("--output", type=Path, required=True, help="new empty overlay directory")
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    result = compile_overrides(args.pack, config, args.output)
    print(f"Generated {len(result)} structure-set overrides; add the output as a "
          "higher-priority datapack BEFORE world registries load.")


if __name__ == "__main__":
    main()
