# Architecture

*(How the mod is put together and why. Public file; keep it stranger-readable. Companion to HANDOFF.md, which holds
state and next work.)*

## The one-paragraph version

Continents of Time is an **atlas** around other people's era generators. [Moderner Beta](https://codeberg.org/Nostalgica-Reverie/moderner-beta)
(MIT) already runs every historical Minecraft terrain generator on modern versions, as **data presets** over three
chunk-provider implementations; this mod depends on it at runtime, builds one complete Moderner Beta generator per
era, adds vanilla's own modern generator as one more era, and routes every chunk to the era that owns it. The
continent layout, the oceans between, and the seams beneath the water are this mod's own code; the eras are not.

## Why a runtime dependency, not a reimplementation (decided 2026-08-29)

- Moderner Beta 5.0 ships the **same release for 26.2 and 1.20.1** (`5.0.0-alpha.3+<mc>-fabric`), the two versions
  this mod targets. Its API is alpha; the version is pinned in `gradle.properties`.
- Its eras are data: ~70 settings presets (`data/moderner_beta/moderner_beta/settings_preset/*.json`) over three
  Java providers (`noise_3d`, `finite_2d`, `infdev_227`). Reimplementing that is years of someone else's work;
  reading it is a study pass.
- License is MIT since its commit 55519d1 (LGPL before). Depending on it copies nothing; packs install it
  themselves. Our code stays LGPL-3.0 and original.
- The one 26.2-specific cost: `NoiseBasedChunkGenerator` is final, so extending it needs an access widener
  (`continentsoftime.accesswidener`). Moderner Beta does the same.

## Moderner Beta, as seen from outside (study pass, 5.0.0-alpha.3)

The parts we touch, all public:

| Piece | What it is | How we use it |
|---|---|---|
| `ModernBetaChunkGenerator` | extends `NoiseBasedChunkGenerator`; holds ONE `ChunkProvider` chosen by `provider_settings` | one instance per hosted era |
| `ModernBetaBiomeSource` | one `BiomeProvider` + one cave-biome provider, from `provider_settings` / `cave_provider_settings` | one instance per hosted era |
| `ModernBetaSettingsPreset` | record of three `ModernBetaSettings` groups (chunk / biome / cave biome); registry `moderner_beta:settings_preset` | `ModernBetaSettingsPreset.referenced(id, lookup)` gives settings that resolve the preset by id, lazily |
| `initProvider(seed)` on both | creates the provider for the seed; Moderner Beta calls it on `SERVER_STARTING` for generators it finds in the dimension registry | it only looks for its own generator class, so the atlas calls it on every hosted era itself |
| `ChunkProvider` | `provideChunk`, `provideSurface`, `getHeight`, `skipChunk`, world min-Y/height/sea-level | not called directly; the hosted generator drives it |
| `CodecUtil.registryLookupCodec()` / `retrieveLookup` | codec helpers for registry access at decode time | used by `AtlasBiomeSource.CODEC` |

Facts that shaped the design:

- **Heights line up.** Moderner Beta's old-era noise settings already start at y=-64 (`overworld_128` is -64..128,
  `overworld_256` is -64..256, `finite_2d` is 0..256, `sky_128` is 0..128). Every era fits inside the modern
  overworld dimension, so the atlas keeps the modern height range and sea level 63 world-wide.
- **Three kinds of era.** Infinite noise eras (Infdev 415 through Release 1.17.1, PE/Bedrock, Legacy Console);
  **finite** eras (Classic, Indev: a 256×256 level, `finite_level_properties`), which are small islands by nature;
  and Skylands (floating islands, sea level 0, nothing below). The layout must clip the infinite ones and can leave
  the finite ones alone.
- **Per-level state.** Moderner Beta flags a `ServerLevel` as "modded" (old fog, sky and grass colouring, climate
  sampling for temperature) only when the level's generator *is* `ModernBetaChunkGenerator`. With the atlas in
  front, no level is flagged, so those level-wide visuals are off everywhere. Making them per-continent is a later,
  client-side job (a composite climate sampler); noted, not started.
- **Surface rules** flow through a Moderner Beta mixin on vanilla's `SurfaceSystem`, keyed per chunk by the provider
  it is given (`modernerBeta$setupChunkContext`). Delegating `buildSurface` to the hosted generator keeps that
  working unchanged.
- **`RandomState`** is built once per dimension from the dimension generator's `NoiseGeneratorSettings` if it is a
  `NoiseBasedChunkGenerator`, else from a dummy. Hosted eras receive it. The modern era needs the real overworld
  one; Moderner Beta eras use its surface system (default block stone, sea level 63; their own presets say 64 —
  a one-block mismatch inside vanilla's surface rules only, accepted for now).
- **`getOrCreateNoiseChunk` caches per chunk.** Whoever creates a chunk's `NoiseChunk` first wins, and Moderner
  Beta creates its own subclass. So every step for a chunk must go to the same hosted generator, including
  `createBiomes` — the atlas never creates a noise chunk of its own.

## The code (`dev.continentsoftime`)

- `atlas.AtlasBiomeSource` — owns the era roster as `HostedEra`s and the `Layout`; answers `getNoiseBiome` by
  asking the era that owns the column; `possibleBiomes` is the union. Its codec carries `AtlasSettings` and
  fetches the registries hosted eras need, so the whole atlas rebuilds from level.dat.
- `atlas.AtlasChunkGenerator` — extends `NoiseBasedChunkGenerator` over `minecraft:overworld` settings (for the
  `RandomState`), routes `createBiomes`, `fillFromNoise`, `buildSurface`, `applyCarvers`, `applyBiomeDecoration`,
  `spawnOriginalMobs`, `getBaseHeight`, `getBaseColumn` and the debug line to the owning era. Structure placement
  state stays the atlas's (all eras' structure sets are live; biome tags at each position decide).
- `atlas.HostedEra` — builds an era's generator + biome source from an id: `minecraft:*` ids are vanilla
  noise-settings presets over the overworld multi-noise biome source; anything else is a Moderner Beta settings
  preset.
- `atlas.AtlasSettings` — the roster, `max_continent_size`, `ocean_width`; fields missing from a world preset's
  JSON default to the config file at creation time and are then stored explicitly in level.dat.
- `atlas.Eras` — the default roster (25 eras).
- `atlas.layout.Layout` — `eraAt(blockX, blockZ)`. Today only `Layout.single()`.
- `config.ContinentsConfig` — `config/continentsoftime.json`, written with defaults on first run.
- Data: `worldgen/world_preset/continents_of_time.json` (in the world-type list; no explicit settings, so config
  applies) and `single_era.json` (one era, not listed; `level-type=continentsoftime:single_era` on a server).

## Not yet built

The layout itself (continents from the seed, oceans, seams), spawn on the home continent, per-continent visuals.
See HANDOFF.md "Next work".
