# SkyBlock Collection — NeoForge 26.2 integration design

Status: implementation specification; this document is **not** a working mod.
Target: Minecraft **26.2**, NeoForge **26.2.0.93**, Java **25**, ModDevGradle.

## Objectives and boundaries

Preserve the existing Beet/Mecha/Bolt datapack releases and add a **second build target**:
`build/libs/skyvoid-neoforge-<version>.jar`. The JAR must be the only distributed
mod file and carry all first-party gameplay data. Do not flatten the worldgen and
island packs indiscriminately: overlapping `minecraft` datapack paths have
intentional, mutually-exclusive meanings.

The original datapacks remain independently installable. The mod enables a
selected world-generation profile, exactly one starter-island profile, and
optional additions, advancements, islands and compatible integrations.
Defaults must create a playable world without manual datapack selection.

## Inventory from the current repository

- Mandatory world-generation alternatives: every published `skyvoid_worldgen*`
  archive produced from `src/packs/worldgen/`, *not* more than one at a time.
  The variants include `skyvoid_worldgen`, `skyvoid_worldgen_empty`,
  `skyvoid_worldgen_full_lava`, `skyvoid_worldgen_normal_end`,
  `skyvoid_worldgen_noxiousvex`, `skyvoid_worldgen_pinu`,
  `skyvoid_worldgen_portal_room`, `skyvoid_worldgen_single_lava`,
  and `skyvoid_worldgen_structures`.
- Mandatory island alternatives: `skyvoid_island_standard`,
  `skyvoid_island_only_dirt`, or `skyvoid_island_oneblock`.
- Optional content: `skyvoid_additions`, `skyvoid_advancements`,
  `skyvoid_vanilla_oneblock_starter`, `skyvoid_no_end_ship`,
  `skyvoid_sand_island`, `skyvoid_biome_islands`, and compatibility
  packs for Amplified Nether, Nullscape, and Terralith.
- `rp_skyvoid_advancements` is a **client resource pack**, not a datapack;
  ship its assets separately within the JAR and expose a client resource
  pack selection/default where appropriate.

Pack ids should be extracted from each pack's Beet `id` or its release
archive prefix before `_v<version>`, not inferred from the source directory
name. Never merge two alternatives' conflicting worldgen registry entries.

## Suggested layout

```text
src/packs/                                # existing authored datapacks, unchanged
src/plugins/                              # existing Beet build plugins, unchanged
beet-release.yaml                        # existing ZIP/release build
mod/                                      # new 26.2 ModDevGradle project
  build.gradle
  gradle.properties
  gradle/wrapper/
  src/main/java/.../SkyvoidMod.java
  src/main/java/.../SkyvoidConfig.java
  src/main/java/.../SkyvoidWorldProfile.java
  src/main/java/.../SkyvoidAdditionsBridge.java
  src/main/java/.../SkyvoidStructurePackSource.java
  src/main/resources/META-INF/neoforge.mods.toml
tools/prepare_neoforge_resources.py       # new generated resource staging tool
.github/workflows/neoforge.yml            # builds and validates both formats
```

Use the official 26.2 ModDevGradle template and pin NeoForge
`26.2.0.93`; do not use a 1.21.x API example without porting it.
The official 26.2 MDK targets Java 25.

## Runtime datapack model

1. Keep every compiled Beet ZIP unchanged as an independent built-in pack
   inside the JAR, e.g.
   `data/skyvoid/datapacks/skyvoid_worldgen/pack.mcmeta` and
   `data/skyvoid/datapacks/skyvoid_worldgen/data/...`.
2. Use NeoForge `AddPackFindersEvent` for `PackType.SERVER_DATA` and
   register each built-in pack via `event.addPackFinders(...)`.
3. A new world should receive exactly one worldgen pack, exactly one island
   pack and optional packs selected through the configuration UI.
4. Compatibility packs are enabled only when both opted into *and* their
   external prerequisite mods are detected. A missing prerequisite must not
   make world load fail.
5. Use NeoForge `IConfigScreenFactory` /
   `ConfigurationScreen` to expose settings under Mods > SkyVoid > Config.
6. The **title-screen editing profile** uses COMMON or STARTUP settings,
   not SERVER config: NeoForge's SERVER configs cannot be edited in this
   screen until a local world is open.
7. On world creation, **snapshot the chosen profile per world**, including
   addon switches and structure settings. On subsequent loads use that
   persisted profile rather than silently replacing the save's worldgen
   when a different global default is selected. On LAN/multiplayer, the
   server is authoritative. Future-world defaults and existing-world
   settings are different concepts.

The pack finder must re-evaluate the selected world profile when a pack
repository is assembled. Changing the client global default must never
silently change previously generated terrain.

## Structure-generation controls

Expose these controls for each distinct vanilla/worldgen structure or
structure group actually present in the selected pack:

- **Enabled** (boolean); if false the structure must not be selected for
  new chunks.
- **Relative weight** (positive integer where appropriate), used when
  choosing among structures **within one structure set**.
- **Density** (a frequency and/or spacing factor for the structure set),
  with a user-facing global "Sparse / Normal / Frequent" setting and
  optionally advanced per-set spacing controls.

Important: relative `weight` by itself **does not affect absolute density**
if one structure is the only choice in its structure set. Vanilla
`worldgen/structure_set` JSON has placement settings including
`spacing`, `separation`, and `frequency`. Adjust these in a
high-priority **generated override datapack** assembled from the selected
profile before the world's dynamic registries are loaded.

Prefer copying and modifying registry JSONs rather than changing
already-frozen registries during chunk generation. Determine exact
26.2 structure set names from the 26.2 vanilla data used by Beet;
do not assume every `structure` maps 1:1 to a `structure_set`.
Keep strongholds/portal access, bastion gameplay and mob-spawn bounding
boxes meaningful with special-case rules.

For global de-cluttering, spacing changes per independent structure set
cannot guarantee that unrelated structures never generate near one another.
If that **guarantee** becomes a requirement, use a shared custom placement
strategy or a supported cross-set exclusion policy and test the impact on
locate/biome constraints. Document the density behavior rather than
claiming weights enforce a minimum distance.

Changes to worldgen selection, placements or weights affect newly generated
chunks, never retroactively delete structures already generated. Treat
changes to these values as **world-restart-required**.

## SkyVoid Additions configuration bridge

Source of truth:
`src/packs/extras/skyvoid_additions/data/skyvoid_additions/function/customize/default_values.mcfunction`.

The default file contains all current `skyvoid_config` fake-player scoreboard
ids and their default 0/1 values. Extract those at **build time** with a
strict parser and generate one NeoForge boolean config entry per setting.
There are dependencies (e.g. trader additions for trader extras,
netherrack portals for nylium portals, suspicious sniffers for bastion
suspicious gravel). Enforce or clearly surface dependencies.

At world creation / first start (after datapack load functions created
the `skyvoid_config` objective), copy the chosen booleans into the
respective fake-player scores, and then refresh the feature scheduling
using `skyvoid_additions:technical/load` or an audited dedicated
refresh function. Never overwrite settings before the scoreboard objective
exists. Where changing settings during gameplay is supported, update
scoreboards and scheduling coherently; otherwise mark for world restart.

The current `/function skyvoid_additions:customize/update_config/open_menu`
can remain functional in datapack-only installations. In the modded
installation the NeoForge config UI is the primary control and the mod's
synchronization policy must define what happens if both are used.

## Build, release, compatibility

- Continue building individual ZIPs with
  `uv run beet -c beet-release.yaml`.
- Copy/unpack validated ZIPs into the NeoForge resource staging directory
  **without** merging their `data/` trees across pack boundaries.
- Build the JAR with the 26.2 ModDevGradle Gradle wrapper under `mod/`.
- Include original license notices and credits (repository is MIT,
  copyright BPR02).
- CI must verify: all required profile ZIPs exist, exactly one pack per
  required category is active in a new world, metadata loads, no duplicate
  singleton pack ids, Java 25 compiles, and game tests pass on
  NeoForge 26.2.0.93.
- Validate mod on a dedicated server as well as singleplayer.
  Client UI must not be loaded on the physical dedicated server.
- Build-time inputs must have deterministic version names and a manifest
  of included pack versions, so update failures are visible.

## Test matrix

1. Default world: void overworld, starter island, correct Nether/End modes.
2. Each of the three island profiles paired with compatible worldgen
   profiles; reject clearly invalid combinations where needed.
3. Every optional datapack toggled off/on at **new world** creation.
4. Every `skyvoid_additions` boolean: default matches the datapack;
   disable/enable persists after restart and changes actual mechanics.
5. Each supported structure disabled: absent in newly generated chunks;
   enabling it again restores placement in new chunks.
6. Sparse structure profile in a fixed seed: measure actual encounters
   across a repeatable chunk area; include biome-constrained structures.
7. Existing world re-opened after global preferences change: no silent
   worldgen-profile substitution.
8. Dedicated server with and without compatible external worldgen mods.
9. Resource-pack visuals for `rp_skyvoid_advancements`.
10. Data pack-only ZIP releases unchanged relative to the previous pipeline.

## Implementation checkpoints

- [ ] MDK module and CI compile targeting 26.2.0.93 / Java 25.
- [ ] ZIP-to-built-in-pack staging and pack manifest; reproducible builds.
- [ ] NeoForge title-screen config and single-world profile persistence.
- [ ] Selection of exactly one worldgen and one island pack.
- [ ] Optional datapacks and compatible mod detection.
- [ ] SkyVoid Additions scoreboard bridge generated from the source defaults.
- [ ] Dynamic structure-set override with enable / weight / density controls.
- [ ] Gameplay, density, existing-world and dedicated-server tests.
- [ ] Released single JAR plus unchanged individual datapack ZIPs.

## References

- 26.2 official MDK:
  https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle
- NeoForge configuration documentation:
  https://docs.neoforged.net/docs/misc/config/
- NeoForge 26.2 AddPackFindersEvent source:
  https://github.com/neoforged/NeoForge/blob/26.2.x/src/main/java/net/neoforged/neoforge/event/AddPackFindersEvent.java
- NeoForge Maven:
  https://maven.neoforged.net/releases/net/neoforged/neoforge/26.2.0.93/
