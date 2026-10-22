#!/usr/bin/env python3
"""Stage Beet release ZIPs as isolated NeoForge built-in packs.

This does not build the Java mod or configure pack selection. It prepares
resources to be consumed by the planned ModDevGradle source set.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path
from zipfile import ZipFile

from tools.structure_overrides import structure_catalog

WORLDGEN = {
    "skyvoid_worldgen",
    "skyvoid_worldgen_empty",
    "skyvoid_worldgen_full_lava",
    "skyvoid_worldgen_normal_end",
    "skyvoid_worldgen_noxiousvex",
    "skyvoid_worldgen_pinu",
    "skyvoid_worldgen_portal_room",
    "skyvoid_worldgen_single_lava",
    "skyvoid_worldgen_structures",
}
ISLANDS = {
    "skyvoid_island_standard",
    "skyvoid_island_only_dirt",
    "skyvoid_island_oneblock",
}
OPTIONAL = {
    "skyvoid_additions",
    "skyvoid_advancements",
    "skyvoid_biome_islands",
    "skyvoid_no_end_ship",
    "skyvoid_sand_island",
    "skyvoid_vanilla_oneblock_starter",
}
RESOURCES = {"rp_skyvoid_advancements"}
VERSIONED_ARCHIVE = re.compile(
    r"^(?P<name>(?:skyvoid|rp_skyvoid)[a-z0-9_]+)"
    r"_v(?P<version>[0-9][0-9_a-zA-Z+.-]*)-MC_26_2\.zip$"
)
DEFAULT_SCORE = re.compile(
    r"^execute unless score (?P<setting>[a-z0-9_]+) skyvoid_config "
    r"matches 0\.\.1 run scoreboard players set "
    r"(?P=setting) skyvoid_config (?P<value>[01])$"
)


def pack_group(pack_id: str) -> str | None:
    if pack_id in WORLDGEN:
        return "worldgen"
    if pack_id in ISLANDS:
        return "island"
    if pack_id in OPTIONAL or pack_id.startswith("skyvoid_compat_"):
        return "optional"
    if pack_id in RESOURCES:
        return "resource"
    return None


def addition_defaults(source: Path) -> dict[str, bool]:
    """Extract the on/off values directly from the datapack source."""
    if not source.is_file():
        raise FileNotFoundError(f"Missing SkyVoid Additions source: {source}")
    defaults: dict[str, bool] = {}
    for line in source.read_text(encoding="utf-8").splitlines():
        match = DEFAULT_SCORE.fullmatch(line.strip())
        if match:
            setting = match["setting"]
            if setting in defaults:
                raise ValueError(f"Duplicate addition setting: {setting}")
            defaults[setting] = match["value"] == "1"
    if not defaults:
        raise ValueError("No SkyVoid Additions default values could be extracted")
    return defaults


MENU_SOURCE = Path(
    "src/packs/extras/skyvoid_additions/data/skyvoid_additions/"
    "function/customize/update_config/open_menu.mcfunction"
)


def additions_descriptions(source: Path = MENU_SOURCE) -> dict[str, dict[str, str]]:
    """Read the original datapack's human-authored config-menu catalog.

    The open_menu file uses Mecha nesting: its all_settings literal is valid
    JSON, so we extract and decode it rather than duplicating labels manually.
    """
    if not source.is_file():
        return {}
    contents = source.read_text(encoding="utf-8")
    match = re.search(r"(?ms)^all_settings = (\[.*?^\])\s*page_size", contents)
    if match is None:
        raise ValueError("Unable to find Additions menu setting catalog")
    data = json.loads(match.group(1))
    result = {}
    for item in data:
        key = item["id"]
        if key in result:
            raise ValueError(f"Duplicate Additions menu setting: {key}")
        result[key] = {"name": item["name"], "description": item["desc"].replace("**", "")}
    return result


def config_translations(catalog: list[dict], additions: dict[str, bool],
                        metadata: dict[str, dict[str, str]]) -> tuple[dict, dict]:
    """Return English text and Portuguese UI translations with safe fallbacks."""
    en = {
        "skyvoid.configuration.title": "SkyVoid Configuration",
        "skyvoid.configuration.new_world": "New World Defaults",
        "skyvoid.configuration.new_world.button": "Choose...",
        "skyvoid.configuration.new_world.tooltip":
            "Defaults for newly created worlds only. Existing worlds keep their saved generation.",
        "skyvoid.configuration.optional_packs": "Optional Datapacks",
        "skyvoid.configuration.optional_packs.tooltip":
            "Pack selection is resolved before world generation; existing worlds are not modified live.",
        "skyvoid.configuration.additions": "Gameplay Additions",
        "skyvoid.configuration.additions.tooltip":
            "Configure SkyVoid Additions gameplay mechanics. In singleplayer, changes to active mechanics apply when saved.",
        "skyvoid.configuration.structures": "Structure Generation",
        "skyvoid.config.new_world.worldgen": "World Generation Profile",
        "skyvoid.config.new_world.worldgen.tooltip":
            "Choose the world generation used for future worlds. Changing this value will not convert an existing save.",
        "skyvoid.config.new_world.island": "Starting Island",
        "skyvoid.config.new_world.island.tooltip":
            "Choose the starter island for future worlds. Existing islands are never regenerated.",
    }
    pt = {
        "skyvoid.configuration.title": "Configuração SkyVoid",
        "skyvoid.configuration.new_world": "Padrões para Novos Mundos",
        "skyvoid.configuration.new_world.button": "Selecionar...",
        "skyvoid.configuration.new_world.tooltip":
            "Padrões apenas para mundos novos. Mundos existentes preservam sua geração.",
        "skyvoid.configuration.optional_packs": "Datapacks Opcionais",
        "skyvoid.configuration.optional_packs.tooltip":
            "A seleção de datapacks é feita antes de carregar o mundo; mundos existentes não são alterados imediatamente.",
        "skyvoid.configuration.additions": "Mecânicas Adicionais",
        "skyvoid.configuration.additions.tooltip":
            "Configure as mecânicas de SkyVoid Additions. Em um mundo local, as alterações podem ser aplicadas ao salvar.",
        "skyvoid.configuration.structures": "Geração de Estruturas",
        "skyvoid.config.new_world.worldgen": "Geração de Mundo",
        "skyvoid.config.new_world.worldgen.tooltip":
            "Selecione a geração para futuros mundos. Isso não converte mundos existentes.",
        "skyvoid.config.new_world.island": "Ilha Inicial",
        "skyvoid.config.new_world.island.tooltip":
            "Selecione a ilha inicial para novos mundos. Ilhas existentes não são regeneradas.",
    }
    # The values stored in skyvoid-common.toml stay unchanged; these keys
    # provide readable labels for the cycle-button selector only.
    worldgen_labels = {
        "skyvoid_worldgen": "SkyVoid (Default)",
        "skyvoid_worldgen_empty": "Completely Empty",
        "skyvoid_worldgen_full_lava": "Full Lava",
        "skyvoid_worldgen_normal_end": "Normal End",
        "skyvoid_worldgen_noxiousvex": "NoxiousVex Variant",
        "skyvoid_worldgen_pinu": "Pinu Variant",
        "skyvoid_worldgen_portal_room": "Portal Room",
        "skyvoid_worldgen_single_lava": "Single Lava",
        "skyvoid_worldgen_structures": "Void with Structures",
    }
    island_labels = {
        "skyvoid_island_standard": "Standard Skyblock",
        "skyvoid_island_only_dirt": "Dirt Only",
        "skyvoid_island_oneblock": "OneBlock",
    }
    for key, name in worldgen_labels.items():
        en["skyvoid.select.worldgen." + key] = name
    for key, name in island_labels.items():
        en["skyvoid.select.island." + key] = name
    for item in catalog:
        if item["group"] != "optional":
            continue
        pack_id = item["id"]
        key = "skyvoid.config.optional_packs." + pack_id
        display_name = pack_id.removeprefix("skyvoid_").replace("_", " ").title()
        en[key] = display_name
        en[key + ".tooltip"] = ("Include " + display_name + " for newly created worlds. "
                                "Existing-world pack selection requires a world reload.")
    for key in additions:
        text = metadata.get(key, {})
        translation_key = "skyvoid.config.additions." + key
        en[translation_key] = text.get("name", key.replace("_", " ").title())
        description = text.get("description",
                               key.replace("_", " ") + " gameplay mechanic.")
        en[translation_key + ".tooltip"] = (description + "\n"
            "Applied to the active world's SkyVoid Additions scoreboard when saved.")
    return en, pt


def stage_pack(archive: Path, destination: Path) -> str:
    """Safely copy ZIP pack members into a private built-in-pack directory."""
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    with ZipFile(archive) as zip_file:
        members = zip_file.infolist()
        names = {item.filename for item in members}
        if "pack.mcmeta" not in names:
            raise ValueError(f"{archive.name} is missing pack.mcmeta")
        if not any(name.startswith(("data/", "assets/")) for name in names):
            raise ValueError(f"{archive.name} does not contain game resources")
        for member in members:
            member_path = Path(member.filename)
            if (
                member_path.is_absolute()
                or ".." in member_path.parts
                or "\\" in member.filename
            ):
                raise ValueError(f"Unsafe ZIP entry in {archive.name}: {member.filename}")
            # Exclude license/readme duplicates, caches, and unneeded packaging files.
            if not (
                member.filename == "pack.mcmeta"
                or member.filename.startswith(("data/", "assets/", "pack.png"))
            ):
                continue
            if member.is_dir():
                continue
            output = destination.joinpath(*member_path.parts)
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(zip_file.read(member))
    return digest


def build_catalog(build_dir: Path, output_dir: Path, source_file: Path) -> dict:
    """Recreate output deterministically; fail when an expected pack is absent."""
    if not build_dir.is_dir():
        raise FileNotFoundError(f"Missing Beet release directory: {build_dir}")
    archives: dict[str, tuple[Path, str]] = {}
    for archive in sorted(build_dir.glob("*.zip")):
        match = VERSIONED_ARCHIVE.fullmatch(archive.name)
        if not match:
            continue
        pack_id = match["name"]
        if pack_group(pack_id) is None:
            continue
        if pack_id in archives:
            raise ValueError(f"More than one release ZIP for {pack_id}")
        archives[pack_id] = (archive, match["version"].replace("_", "."))
    mandatory = WORLDGEN | ISLANDS | {"skyvoid_additions"}
    missing = sorted(mandatory - archives.keys())
    if missing:
        raise ValueError("Missing required release ZIPs: " + ", ".join(missing))

    if output_dir.exists():
        shutil.rmtree(output_dir)
    catalog: list[dict] = []
    for pack_id, (archive, version) in sorted(archives.items()):
        group = pack_group(pack_id)
        assert group is not None
        if group == "resource":
            target = output_dir / "assets" / "skyvoid" / "resourcepacks" / pack_id
        else:
            target = output_dir / "data" / "skyvoid" / "datapacks" / pack_id
        sha256 = stage_pack(archive, target)
        catalog.append(
            {"id": pack_id, "group": group, "version": version, "sha256": sha256}
        )

    additions = addition_defaults(source_file)
    settings_dir = output_dir / "assets" / "skyvoid" / "config"
    settings_dir.mkdir(parents=True, exist_ok=True)
    (settings_dir / "datapack-catalog.json").write_text(
        json.dumps(catalog, indent=2) + "\n", encoding="utf-8"
    )
    (settings_dir / "additions-defaults.json").write_text(
        json.dumps(additions, indent=2) + "\n", encoding="utf-8"
    )
    # Each profile owns its own registry entries. Do not flatten all variants
    # into one manifest; different worldgen choices can share the same
    # registry identifier with intentionally different definitions.
    structures_by_pack = {}
    for item in catalog:
        if item["group"] == "resource":
            continue
        pack_path = output_dir / "data" / "skyvoid" / "datapacks" / item["id"]
        entries = structure_catalog([pack_path])
        if entries:
            structures_by_pack[item["id"]] = entries
    (settings_dir / "structure-catalog.json").write_text(
        json.dumps(structures_by_pack, indent=2) + "\n", encoding="utf-8"
    )
    # Use the authors' original mechanic names/descriptions instead of
    # technical config ids. Missing menu items receive a readable fallback.
    menu_metadata = additions_descriptions()
    en, pt = config_translations(catalog, additions, menu_metadata)
    language_dir = output_dir / "assets" / "skyvoid" / "lang"
    language_dir.mkdir(parents=True, exist_ok=True)
    for locale, translations in (("en_us", en), ("pt_br", pt)):
        (language_dir / (locale + ".json")).write_text(
            json.dumps(translations, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8"
        )
    return {"packs": len(catalog), "addition_settings": len(additions)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--build-dir", type=Path, default=Path("build"),
        help="directory with Beet release ZIP files"
    )
    parser.add_argument(
        "--output-dir", type=Path, default=Path("mod/build/generated/skyvoid-packs"),
        help="generated NeoForge resources directory"
    )
    parser.add_argument(
        "--additions-source",
        type=Path,
        default=Path(
            "src/packs/extras/skyvoid_additions/data/skyvoid_additions/"
            "function/customize/default_values.mcfunction"
        ),
    )
    args = parser.parse_args()
    outcome = build_catalog(args.build_dir, args.output_dir, args.additions_source)
    print(f"Staged {outcome['packs']} packs and "
          f"{outcome['addition_settings']} additions settings.")


if __name__ == "__main__":
    main()
