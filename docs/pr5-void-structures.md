# PR #5 — Void with Structures: seed-deterministic structure replacement

**Status:** high-priority draft, stacked on PR #4; no merge to `main`.
This replaces the old intention to use PR #5 for unrelated configuration UX
backlog; those tasks remain deferred to a later, separate PR.

## Scope / terminology

This is **not** the optional `skyvoid_biome_islands` pack. PR #4's
20-island enable/weight/density controls remain intact.

The selected worldgen profile is `skyvoid_worldgen_structures`. Its Beet
pipeline `src.plugins.worldgen.get_empty_biomes.structures` preserves
the biome distribution and vanilla structure selection, while suppressing
terrain, carvers, and most features. Its existing structure templates/
bounding-box behavior must be preserved wherever feasible.

### Desired worldgen pipeline

1. At the original natural structure start location, query the **current**
   Minecraft registry for vanilla and mod-provided structures.
2. Discard disabled or zero-weight entries and candidates outside the current
   dimension, biome eligibility, and any additional placement constraints.
3. Rank the remaining structures by **nonreplacement weighted lottery**,
   derived from world seed + chunk X/Z + dimension + original structure id,
   with stable registry-ID sorting. If one structure fails to find a valid
   start, try the next weighted candidate.
4. Generate exactly one candidate at the original attempt location and
   record its actual `StructureStart` / pieces / bounding boxes, respecting
   its own biome and height constraints. Do not fake a source structure ID or
   reuse a bounding box that doesn't match the replacement's pieces.
5. Suppress physical placement of the original where replacement succeeds.
   Where no candidate is valid, preserve the original as a **voided placeholder
   only when its `StructureStart` and bounds can be kept valid without
   generating blocks**. Otherwise skip it explicitly rather than inventing
   a persistent phantom bounding box.

### Architecture decision

**Datapack responsibilities:** Void with Structures biome distribution and
terrain clearing; any safe resource-level transformations of structure sets,
templates, or flags that can be performed before registry load. Do not alter
the optional Biome Islands structure pack.

**Mod responsibilities (required):** dynamic discovery of vanilla/modded
registry entries, per-structure configuration UI, eligibility checks using
the engine's `Structure` biome holders and actual generation conditions,
seed-deterministic weighted fallback, and injection at chunk structure-start
creation. NeoForge 26.2 exposes no documented general-purpose cancellable
structure-start replacement event; a carefully scoped mixin/transformer may
be required. A generic datapack cannot inspect per-world registry contents
or replace arbitrary `StructureStart` objects at generation time.

**Not equivalent:** merely adjusting vanilla `structure_set` weights.
Vanilla weights choose among entries within a shared structure set; changing
those alone cannot replace, for example, a village candidate with a
biome-eligible structure from another set, while handling the original slot.

### First implementation (in this PR)

`SkyvoidVoidStructureSelector` contains the pure deterministic weighted
ranking/fallback policy with tests for seed reproducibility, biome/dimension
filtering, disabled structures, registry-order independence, and fallback.
It is **not yet attached to Minecraft's structure-start generation hook**.
No claim of working in-game replacement until that integration and tests pass.

### Blocking acceptance gates

- [ ] Build catalog from live `Registries.STRUCTURE` and structure sets,
      retaining mod namespaces and server-side rules.
- [ ] Ordered, searchable configuration UI with numeric nonnegative weights;
      zero disables generation for Void with Structures only.
- [ ] Capture original candidate position, inspect biome/dimension validity,
      seed deterministic selection, and atomically replace the start.
- [ ] Only one actual structure is placed per original attempt, without
      double-generation or fake bounding boxes; inspect `/locate`,
      StructureManager, spacing, and cross-chunk references.
- [ ] Verify void-only placeholders for disabled originals, if Minecraft's
      structure-start lifecycle allows retaining valid bounds without pieces.
- [ ] Restart and world-reopen persistence, including pre-existing worlds
      without retroactively changing generated chunks.
- [ ] Test vanilla + modded structures, snowy/desert/nether/end cases,
      mod removal, world seeds, and pack conflicts.
- [ ] Preserve all optional Biome Islands settings unchanged.
- [ ] Production dedicated-server and client tests + manual worldgen tests.

## Structure weights UI prototype

The Common Config section `Void with Structures: Replacement` provides an
experimental enable switch and the **Browse Structure Weights** editor. When
a world is loaded, this editor discovers vanilla/modded structure identifiers
from the client's synchronized structure registry, sorts vanilla first and
modded second, and exposes a numeric weight for each. Weight 0 disables an
entry; 1 is the default; up to 1000 increases its weighted selection chance.

The UI edits the global COMMON configuration until the early-readable,
per-world profile system is fully implemented. Editing weights while a world
is open requires leaving and reopening it to apply; this is **not** an
immediate generator refresh. Without a loaded world, the same editor supports
adding namespaced `id=weight` entries manually. An absent entry uses 1.

This is only for `skyvoid_worldgen_structures`, entirely separate from the
optional Biome Islands pack and its configuration. In-game structure starts,
reference management and placeholder bounds still require intensive validation.
