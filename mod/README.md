# SkyVoid NeoForge 26.2 (development prototype)

> **Experimental / draft PR.** This is not a tested or released mod.
> Structure filtering, rarity weights, world-specific profile persistence and
> Minecraft gameplay validation are **NOT IMPLEMENTED** yet.

## Targets

- Minecraft 26.2
- NeoForge 26.2.0.93
- Java 25
- ModDevGradle 2.0.148, Gradle 9.2.1
- Beet/uv from the repository root (Python >=3.14 per `pyproject.toml`)

## Building

From the repository root, with Java 25, Gradle 9.2.1, `uv` and
the project's Python dependencies available:

```bash
python -m unittest discover -s tests
cd mod
gradle build
```

Gradle runs the existing `uv run beet -c beet-release.yaml` pipeline,
then `tools/prepare_neoforge_resources.py` to stage pack resources
under `mod/build/generated/skyvoid-packs`. The Java mod builds a JAR in
`mod/build/libs/` after staging the resources.

There is not yet a checked-in Gradle wrapper in this new module;
CI installs Gradle 9.2.1 explicitly.

## Prototype configuration

The **Mods > SkyVoid > Config** UI exposes the *new-world default*
worldgen selection, starter island selection, optional pack toggles,
and each `skyvoid_additions` feature toggle.

The Additions feature keys and 0/1 defaults are extracted from
`src/packs/extras/skyvoid_additions/data/skyvoid_additions/function/customize/default_values.mcfunction`.
The Java runtime updates the `skyvoid_config` scoreboard after server
startup and refreshes `skyvoid_additions:technical/load`.

**Known limitations / unsafe for production worlds**

- This prototype reads global COMMON config each time a pack finder is
  constructed. It needs world-specific snapshot storage before any
  released build; changing the defaults may affect existing worlds.
- The first prototype can choose one worldgen and island, but still needs
  world-profile persistence / compatibility validation.
- Structure enable/disable, per-structure relative weights,
  spacing/frequency and exclusion logic are still pending.
- For safety, try only on disposable test worlds until these are done.
- Runtime packaging and build are not yet verified against NeoForge in CI.
- Data/resource packs are staged, but real client/server gameplay has not
  been validated.
- All optional compatibility packs require their matching worldgen mods.

See [integration design](../docs/neoforge-26.2-integration.md) for complete
acceptance criteria and planned worldgen design.
