# PR #3: Runtime settings, per-world profiles, and datapack migration

This branch is stacked on the draft PR #2 (`feature/neoforge-26-2-integration-plan`).
It must **not** merge before PR #2.

## Approved implementation order

1. **Live changes**: use NeoForge's COMMON config reload event, enqueue
   changes on the logical server thread, synchronize only changed Additions
   scoreboard entries, then run `skyvoid_additions:technical/load` once.
   Without an active local server, edits affect future-world defaults only.
   Changing the selected worldgen/island is **never** hot-applied.
2. **Per-world persistence**: write a versioned, server-authoritative profile
   for worldgen, island, optional packs and Additions. Persist changes to the
   active save. Worldgen pack identity must also be readable before registry
   loading, so SavedData alone is not sufficient.
3. **Legacy migration**: detect worlds created with standalone datapacks,
   preserve pack selection and active scoreboard values on first boot, import
   the snapshot once, then persist and mark migration completed. Never
   automatically remove original datapacks or overwrite existing terrain.

### Safety / acceptance gates

- Singleplayer Additions toggles apply without rejoining; unchanged values
  must not reset schedules.
- Dedicated servers remain authoritative: remote clients cannot apply their
  local COMMON settings to the server.
- Saved world A and world B keep independent config.
- Existing datapack worlds are opened without forcing a new worldgen pack,
  changing their existing Additions settings, or recreating starter islands.
- The initial migration is idempotent and preserves unknown datapacks.
- CI compiles both physical sides; unit tests + manual gameplay verify hot
  changes and save migration before this draft PR can be marked ready.
