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
  `spawnOriginalMobs`, `getBaseHeight`, `getBaseColumn` and the debug line to the owning era; generates ocean
  chunks itself and clamps every era's coasts into the `Seabed` band (see "Oceans and seams"). Structure
  placement state stays the atlas's (all eras' structure sets are live; biome tags at each position decide).
- `atlas.Translated` and `mixin.*` — the offset that moves an anchored era to its seat, and the Moderner Beta
  mixins that apply it (see "Anchored eras").
- `atlas.layout.Seabed` — the ocean floor and the coast band as functions of the coast field.
- `atlas.HostedEra` — builds an era's generator + biome source from an id: `minecraft:*` ids are vanilla
  noise-settings presets over the overworld multi-noise biome source; anything else is a Moderner Beta settings
  preset.
- `atlas.AtlasSettings` — the roster, `max_continent_size`, `ocean_width`; fields missing from a world preset's
  JSON default to the config file at creation time and are then stored explicitly in level.dat.
- `atlas.Eras` — the default roster (25 eras).
- `atlas.layout.Layout` — `eraAt(blockX, blockZ)` (an era index or `OCEAN`), `nearestEraAt`, and `chunkOwner`:
  ownership is decided once per chunk by its centre column, because every generation step of a chunk must go
  to one hosted generator. `Layout.single()` is one era everywhere (verification worlds).
- `atlas.layout.ContinentLayout` — the real layout, built from the seed at server start (see below).
  `Footprint` is what it knows about an era before seating it (box size cap; shaped or finite); `Seat` is where
  an era ended up. `Noise` is a small seeded gradient noise, original and Minecraft-free, so the layout can be
  checked headlessly.
- `config.ContinentsConfig` — `config/continentsoftime.json`, written with defaults on first run.
- Data: `worldgen/world_preset/continents_of_time.json` (in the world-type list; no explicit settings, so config
  applies) and `single_era.json` (one era, not listed; `level-type=continentsoftime:single_era` on a server).

## The layout (built 2026-08-29)

The layout must seat every era exactly once, keep the home era on the origin, keep every continent inside a box
no larger than `max_continent_size`, keep at least `ocean_width` of water between any two, and be cheap per
column. `ContinentLayout` does it in two steps:

**Placement, by construction rather than by search.** Seats live on a hex-offset grid with pitch
`max_continent_size + ocean_width`; each cell owns a square *region* `max_continent_size` wide, so regions are
`ocean_width` apart in every direction, and a seat's box is jittered anywhere inside its region — the ocean gap
holds whatever the noise does. The home era takes cell (0, 0) with its box centred on the origin. The other
cells are chosen by seeded growth outward from it (each step adds a random cell adjacent to those already
chosen), so the continents form one connected archipelago, and they are handed out in roster order: the roster
*is* the timeline you sail along. Finite eras (Classic, Indev) are seated unshaped in a box the size of their
level.

**Shape, with the guarantees in the arithmetic.** A shaped seat maps its box to normalised coordinates, warps
them with two fractal noises, and evaluates `(1 - r^2) + core + DETAIL * noise - BIAS`; land is where that is
positive. All noises are bounded to [-1, 1], so: the box centre is always land (a bump on the *unwarped*
centre guarantees a solid core), and land can never reach the box edge (`BIAS > DETAIL` makes the rim sea; the
warp can carry a land point at most `WARP` further out, and the box is normalised to `RIM + WARP`). A gain on
the coastline noise, clamped back to the bound, is what turns a tidy blob into lobes, bays, fjords and offshore
islands. Constants are named at the top of `ContinentLayout` with the reasoning next to each.

**Cost.** ~0.14 µs per column uncached (`eraAtColumn`); generation goes through a per-chunk 16×16 cache
(`eraAt`). `fieldAt` exposes the signed field (positive inland, negative at sea) for the ocean/seam work.

**Harness.** `./gradlew layoutTest [-Pseeds=a,b]` builds the default roster's layout for several seeds without
Minecraft and asserts determinism, every era present, origin + a 512-block disc on home, land inside every box
and within the maximum, land-to-land gaps, and chunk/column agreement; it writes `build/layout/<seed>.png`
(whole atlas) and `<seed>-home.png` (home continent at 8 blocks per pixel) for eyeballing.

## Oceans and seams (built 2026-08-29)

**Ownership.** A chunk with any land column belongs to that era (boxes are `ocean_width` apart, so never to
two). A chunk with no land column is an **ocean chunk**, generated by the atlas itself: biomes from the modern
ocean set (temperature from the overworld climate sampler picks frozen/cold/ocean/lukewarm/warm, the coast
field picks deep or shallow), terrain from `Seabed` (stone up to the floor, water to sea level 63), surface
from vanilla's overworld rules (gravel and sand floors, deepslate, bedrock), decoration from the modern era's
generator (kelp, seagrass, ores). No carvers under the open sea.

**The seabed and the coast band** (`atlas.layout.Seabed`, Minecraft-free, checked by the harness). The coast
field gives every column a height band `[lower, upper]`: at sea both bounds are the seabed, which starts two
blocks under sea level at the shoreline and deepens with the field to about y 19 with some relief; in the
coast band (`0 < field < INLAND`) the bounds open up quadratically away from the shoreline; inland they are
the whole world. After an era fills a chunk, `AtlasChunkGenerator.shapeCoast` clamps each column's top solid
block into its band (cut down to water or air, or filled up with stone), floods any air below the waterline
and removes era water above it (Moderner Beta presets put their sea at 64). An ocean chunk and an era chunk
therefore meet at exactly the same seabed height, and an era's terrain eases down to the shoreline instead of
ending in a wall. Eras whose own sea is far below ours (Skylands, sea level 0) get the deep seabed under
everything and are never clipped.

**Biomes at sea.** The ocean simply stops at the coast (author's call): sea columns inside an era chunk are
painted with the modern ocean at the surface step — before the era's surface rules run, so they dress the
seabed as ocean, and again after, because Moderner Beta injects its own biomes (beaches, oceans, cave biomes)
during its surface step. Not at `createBiomes` time: a `ProtoChunk` refuses biome reads until its status is
BIOMES, which is only set after that future completes.

**Anchored eras are translated to their seats.** Most eras are infinite and any region of them is as good as
any other. Three are anchored on the origin: the finite levels (Classic, Indev keep a 256×256 level in memory
around 0,0 and generate "border" outside it) and Legacy Console (an origin-centred 5120-block world border with
an ocean falloff, and an origin-centred fractal biome map). The atlas cannot move a chunk, so it moves the
provider's idea of the origin instead: `Translated` is implemented by mixins on Moderner Beta's `ChunkProvider`
and `BiomeProvider` base classes (an offset, zero unless the atlas sets it), and small mixins shift every
origin-relative test — the finite provider's `inWorldBounds`, `getHeight` and `generateTerrain` level-array
offsets; the noise provider's world-border tests (`skipChunk`, `modifyEdgeDensity`, the base block source, the
two surface passes); the fractal biome provider's lookups; and the biome injector's out-of-bounds predicate.
`HostedEra.footprint` reports such eras as `finite` (the level's size, unshaped) or `bordered` (the border's
width, shaped), the layout snaps their seats to chunk boundaries, and `HostedEra.translateTo` sets the
offsets at server start. These mixins are the one place this mod reaches into Moderner Beta's internals; the
version is pinned, and the targets are listed in each mixin's comment.

## Not yet built

Spawn on the home continent (the origin is guaranteed land, so vanilla's search should already succeed —
verify), a first look from a client, per-continent visuals. See HANDOFF.md "Next work".
