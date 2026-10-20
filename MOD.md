# SkyVoid NeoForge distribution

The `skyvoid` jar packages each supported SkyVoid release datapack as a built-in pack. Create a world, open the Data Packs / Experiments selector, and choose exactly one `Sky Void Worldgen` pack and one `Sky Void ... Island` pack. Optional packs and compatibility packs appear in the same selector. The companion Sky Void Advancements resource pack appears in the resource-pack selector.

The NeoForge Mods screen exposes every `skyvoid_additions` setting. Its global values are applied when a world starts and the selected `Sky Void Additions` datapack is active. Existing in-world menu changes are overwritten the next time that world loads.

The server config contains `structure_rules`. A rule has the form `namespace:structure=enabled,density,weight`. Density is reserved for the generated worldgen override stage; native weight only affects structures that compete within the same Minecraft structure set. It is stored in the per-world `serverconfig/skyvoid-server.toml` location by NeoForge.

GitHub Actions is the only supported build and verification route. The workflow first builds all Beet packs, then stages them inside the jar and verifies every catalog entry has a `pack.mcmeta` file.
