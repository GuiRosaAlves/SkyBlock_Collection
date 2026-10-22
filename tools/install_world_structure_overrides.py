#!/usr/bin/env python3
"""Install a structure-set override into ONE offline Minecraft world.

Close Minecraft before running. This never edits level.dat, any vanilla
datapacks, existing chunks or the source pack. The generated pack must be
enabled by the world on its next load (use the Datapacks screen / command if
necessary). This tool is an explicit bridge until PR #3 implements early
per-world pack selection inside the mod.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from tempfile import TemporaryDirectory

if __package__:
    from .structure_overrides import compile_overrides
else:
    from structure_overrides import compile_overrides

PACK_NAME = "skyvoid_structure_overrides"


def install(world: Path, packs: list[Path], config: dict,
            replace: bool = False) -> Path:
    if not (world / "level.dat").is_file():
        raise ValueError("Select the actual save folder containing level.dat")
    datapacks = world / "datapacks"
    destination = datapacks / PACK_NAME
    backup = datapacks / (PACK_NAME + ".backup")
    if destination.exists() and not replace:
        raise FileExistsError(f"{destination} already exists; use --replace to back it up")
    if replace and backup.exists():
        raise FileExistsError(f"{backup} already exists; move it before replacing again")

    # Build and validate fully before creating/modifying the world's datapacks
    # directory. TemporaryDirectory is outside the save, protecting failed runs.
    with TemporaryDirectory(prefix="skyvoid-structure-") as temporary:
        staging = Path(temporary) / PACK_NAME
        changes = compile_overrides(packs, config, staging)
        if not changes:
            raise ValueError("Configuration produced no structure overrides; world unchanged")
        datapacks.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            destination.rename(backup)
        try:
            # Same filesystem is not guaranteed, so copy first to a hidden
            # path on the target volume, then rename into the final location.
            import shutil
            incoming = datapacks / ("." + PACK_NAME + ".incoming")
            if incoming.exists():
                raise FileExistsError(f"Temporary destination already exists: {incoming}")
            try:
                shutil.copytree(staging, incoming)
                incoming.rename(destination)
            finally:
                if incoming.exists():
                    shutil.rmtree(incoming)
        except Exception:
            if backup.exists() and not destination.exists():
                backup.rename(destination)
            raise
    return destination


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--world", type=Path, required=True)
    parser.add_argument("--pack", type=Path, action="append", required=True,
                        help="the selected compiled Beet datapack folder (repeatable)")
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--replace", action="store_true",
                        help="back up an existing generated override, preserving all other packs")
    args = parser.parse_args()
    location = install(args.world, args.pack,
                       json.loads(args.config.read_text(encoding="utf-8")),
                       replace=args.replace)
    print(f"Installed structure overlay at {location}. Open the world and ensure "
          f"file/{PACK_NAME} is enabled. Only newly generated chunks are affected.")


if __name__ == "__main__":
    main()
