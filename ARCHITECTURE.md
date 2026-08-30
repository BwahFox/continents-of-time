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
- `NoiseBasedChunkGenerator` is final, so extending it needs an access widener (`continentsoftime.accesswidener`;
  a Mojang-mapped twin for 1.20.1). Moderner Beta does the same.

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
  front, no level is flagged, so those level-wide visuals are off everywhere. The client-side ones are brought back
  per continent by this mod's optional client half (see "Per-continent visuals"); the server-side ones (precipitation
  and snow/ice by sampled climate) stay vanilla, driven by the biomes' own data.
- **Surface rules** flow through a Moderner Beta mixin on vanilla's `SurfaceSystem`, keyed per chunk by the provider
  it is given (`modernerBeta$setupChunkContext`). Delegating `buildSurface` to the hosted generator keeps that
  working unchanged.
- **`RandomState`** is built once per dimension from the dimension generator's `NoiseGeneratorSettings` if it is a
  `NoiseBasedChunkGenerator`, else from a dummy. Hosted eras receive it. The modern era needs the real overworld
  one; Moderner Beta eras use its surface system (default block stone, sea level 63; their own presets say 64 —
  a one-block mismatch inside vanilla's surface rules only, accepted for now).
- **Presets resolve late.** The world preset (and so the atlas biome source and every hosted era) is decoded
  while the worldgen registries are still loading; Moderner Beta's `settings_preset` registry is unbound at that
  moment, which is why its own settings only *reference* a preset and resolve it in `initProvider`. Anything the
  atlas needs from a resolved preset (the cave-biome map, footprints from providers) must wait for server start:
  `AtlasBiomeSource.init` → `HostedEra.resolved` → `HostedEra.init`. Resolving at decode time fails the whole
  registry load ("unbound value ... moderner_beta:classic_0_0_14a_08"; learned 2026-08-30).
- **The feature sorter refuses the union.** `ChunkGenerator` lazily feature-sorts its biome source's biomes
  (`featuresPerStep`); vanilla forces that sort in `validate()` when a client re-opens a world. Over every era's
  biomes it fails with a "feature order cycle" (vanilla's `desert` and Moderner Beta's `beta_desert` order shared
  features differently). The atlas never decorates from its own sort — each chunk is decorated by its owning era's
  generator, ocean chunks by the modern era's (or an unseated modern generator when the roster has none) — and
  `validate()` validates the hosted generators instead. Found 2026-08-29 on the first re-open of a world.
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
- `command.CotCommand` — `/cot seats | where | seat <era>`: the seat table, what the atlas thinks of where you
  stand, and a teleport onto any era's continent. Operator level 2.
- `client.screen.*` and `mixin.client.WorldCreationUiStateMixin` — the Customize button on the Create World screen
  (see "The Customize screen").
- `network.AtlasInfoPayload` and `mixin.PlayerListMixin` — the server's description of an atlas level for a client
  that has this mod (seed, sizes, footprints, and the climate-sampling eras' biome settings), sent with every level
  info; `client.*` and `mixin.client.*` — the optional client half (see "Per-continent visuals").
- `atlas.HostedEra` — builds an era's generator + biome source from an id: `minecraft:*` ids are vanilla
  noise-settings presets over the overworld multi-noise biome source; anything else is a Moderner Beta settings
  preset.
- `atlas.timeline.EraVersion` / `EraStructures` / `EraCaveBiomes` — where an era sits on the timeline and which
  vanilla structure sets and cave biomes existed then (see "Era-accurate structures and cave biomes").
- `atlas.AtlasSettings` — the roster, `max_continent_size`, `ocean_width`, `oceans`, `era_accurate`; fields missing from a world preset's
  JSON default to the config file at creation time and are then stored explicitly in level.dat.
- `atlas.Eras` — the default roster (26 eras).
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

**Beyond the first pass (built 2026-08-30).** The world is infinite, so the grid does not stop at the roster: every
cell outside the grown cluster gets a seat on demand (`seatAt`, cached), for an era drawn by a per-cell seeded
random from the roster's *repeatable* eras — shaped and not anchored — placed with the same size/aspect/offset
draws and its own per-cell coastline noise. Finite levels and bordered eras cannot repeat (one translated
generator, one seat). `nearestEraAt` looks at the 5×5 cells around the column instead of the roster (a box never
leaves its region, so nothing nearer can hide further out), falling back to the first pass if no candidate exists.
`seats()`, `/cot seats` and the client payload describe the first pass only; far cells rebuild identically on the
client from the same seed.

**No oceans (option, built 2026-08-30).** `ContinentLayout(..., oceans = false)` seats everything identically but,
when it fills the per-chunk cache, hands every gap column to `nearestEraAt` and reports the field as `1` (inland)
everywhere. Nothing downstream changes: no column is sea, so the atlas never generates an ocean chunk or clamps a
coast, and each era's infinite terrain simply runs on to the seam where the nearest-era decision flips — a hard
chunk-boundary seam, which is the look the author asked for. Finite levels keep their box (and are never "nearest",
as before); the gap around them goes to the nearest shaped era. Stored in `AtlasSettings.oceans`, sent to modded
clients in the atlas payload so their climate routing matches.

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
ending in a wall. Eras whose own sea is far below ours (Skylands, sea level 0) are **lifted** instead — the whole
column moved up so the era's sea level lands one block above ours (64 blocks for Skylands), then the deep seabed,
water and a bedrock floor go under everything; islands float over open sea and are never clipped.
`getBaseHeight`/`getBaseColumn` apply the same lift.

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

## Per-continent visuals (built 2026-08-29, client-side and optional)

**What Moderner Beta does on its own.** Its visuals are a client-side pipeline over ONE climate sampler: on every
level info (`PlayerList.sendLevelInfo`: join, respawn, dimension change) the server sends `BiomeProviderInfoPayload`
(is this a Moderner Beta level; the biome provider's id, settings NBT and seed); the client rebuilds that biome
provider locally and, if it is a `ClimateSampler`, installs it in two static singletons — `BlockColorSampler`
(grass, foliage and water tints, through block tint sources it registers for the vanilla blocks) and
`SkyColorSampler` (an `EnvironmentAttributeSystem` positional layer over the biome sky colour) — and marks the
`ClientLevel` "modded" (old fog colour weighting, precipitation by sampled climate). All of it samples by block
`(x, z)`. Only the Beta (`moderner_beta:beta`), Beta-fractal and Pocket Edition biome providers sample a climate;
the `single` provider (Classic through Alpha, Skylands) and the plain fractal biome maps (1.2.5 onward, Bedrock,
Legacy Console) do not, so on their own those eras have vanilla colouring anyway. Whether to tint at all is the
player's Moderner Beta config (`beta_climatic_colors` etc.: sky and vegetation on, water off by default).

On an atlas the generator is not Moderner Beta's, so that payload says "not a Moderner Beta level" and the
pipeline is idle. The server never needs a client for anything: a vanilla client joins and sees vanilla colours.

**What the atlas adds.** The same idea for a roster, with one composite sampler in place of one provider:

- `AtlasInfoPayload` (`continentsoftime:atlas_info`): the seed, the layout sizes and home index, every era's
  footprint, and the biome settings NBT of every era whose provider samples a climate. `PlayerListMixin` sends it at
  the tail of `sendLevelInfo` — after Moderner Beta's own payload has reset the client's samplers for the level —
  and only to clients that registered the channel. A non-atlas level sends an empty roster.
- `client.ClientAtlas` rebuilds the `ContinentLayout` from the seed and footprints (it is deterministic and
  Minecraft-free, so the client gets the identical map), builds each climate era's biome provider the way Moderner
  Beta builds its one (same settings, same seed; anchored ones translated to their seats), and installs a
  `client.ContinentClimate` in both singletons.
- `ContinentClimate` implements `ClimateSampler` and `ClimateSamplerSky`: every sample goes to the era that owns
  the column; columns outside any climate era get the vanilla biome's own temperature and downfall (what the
  colormaps would read anyway). Moderner Beta's "tint at all?" flags are not positional, so the composite says yes
  for vegetation whenever any era would, and four small client mixins make the decision per column:
  `GrassBlockTintSourceMixin` and `FoliageTintSourceMixin` (Moderner Beta's tint sources; outside a climate era they
  return vanilla's blended biome colour, exactly Moderner Beta's own off-path), `EnvironmentAttributeSystemMixin`
  (the atlas's own positional sky layer, next to Moderner Beta's, which stays inert because the composite reports no
  sky colouring), and `AtmosphericFogEnvironmentMixin` (Moderner Beta's old fog weighting when the camera stands on
  any Moderner Beta continent, honouring its config switch; the modern continent and the open sea keep vanilla's).
  The gates mean the modern continent keeps vanilla's biome-blended grass, which a single global flag would lose.
- `./gradlew climateTest` checks the routing headlessly with stub era samplers over a real layout, plus the
  payload codec round trip.

Not done, and why: **water tint** stays vanilla everywhere (Moderner Beta's default is off, and its water paths have
no positional hook short of two more mixins); **precipitation and snow/ice by sampled climate** stay vanilla (the
"modded level" flag drives them level-wide on both sides, and the sampler's height scaling is per provider); the
**climate distribution** flags are not positional either (fuzzy grass if any era wants it; smooth borders only if
every climate era does). Debug text: Moderner Beta's F3 entries read its own level state, so they stay silent on an
atlas; `/cot where` is the atlas's own.

## The Customize screen (built 2026-08-30, client-side and optional)

Vanilla's Create World screen shows a **Customize** button when the world-creation state finds a `PresetEditor`
for the selected world type; the editors live in an immutable map (flat and single-biome worlds), so
`mixin.client.WorldCreationUiStateMixin` answers the lookup for `continentsoftime:continents_of_time` — the same
hook Moderner Beta uses for its own preset; each speaks only for its own, so they coexist. `client.screen.AtlasPresetEditor`
supplies the editor and applies the result: the screen edits a copy of the generator's `AtlasSettings` (every
Moderner Beta settings preset plus the modern generator as candidates; seated eras first in roster order — the
timeline — with seat/unseat and move up/down; the two sizes as stepped sliders; the two switches; a reset to the
config file's values), and Done rebuilds the overworld generator through the atlas codec: a hand-built
`{"type": "continentsoftime:atlas", "biome_source": {"type": ..., "settings": ...}}` parsed with registry ops, i.e.
exactly the path a world preset takes at load, so a customized world and a config-default one are the same thing
with different numbers. The values are baked into the world like the config's would be. Servers and the config
file need none of this. `client.screen.ContinentsCustomizeScreen` uses only layout and widget classes that are
the same on 1.20.1 and 26.2; the list widget's constructor, entry drawing and clicks, and the 1.20.1 background
are the `//?` spots.

## Era-accurate structures and cave biomes (built 2026-08-30, optional, default on)

Vanilla places structures from a `ChunkGeneratorStructureState`: the level makes one from its generator's
`createState(structureSets, randomState, seed)` — the sets whose structures can occur in the biome source's
biomes, plus stronghold ring positions — and `createStructures` walks that state's sets per chunk, validating the
biome at the candidate through the generator's **own** biome source. The atlas keeps `createStructures` (so the
atlas biome source stays the authority, including the modern ocean at every coast) and swaps only the state per
chunk: `stateFor(era)` asks the owning era's **hosted generator** for a state — Moderner Beta's generator applies
its preset's structure overrides and removals there (the Beta ocean shrine, Legacy Console's stronghold rings) —
over a `HolderLookup<StructureSet>` filtered by `EraStructures`: a vanilla set is allowed when the era's
`EraVersion` (parsed from the preset id; variants inherit their base era's; PE, Bedrock and Legacy Console mapped
to the Java versions with the same structures; the vanilla era and unknown ids are "modern") is not before the
version that introduced it. Unknown sets (other mods', Moderner Beta's own) are allowed everywhere. Ocean chunks
use the ocean era's state. The level's own state stays the union, which is what `/locate` reads — so `/locate` can
name a place on an old continent where nothing is then placed; accepted. `/cot structures <era>` shows an era's
live sets; `./gradlew timelineTest` checks the timeline and the filters headlessly.

**Cave biomes.** Moderner Beta's presets from Beta on inject vanilla's modern underground biomes (lush and
dripstone caves 1.18, the deep dark 1.19, sulfur caves 26.2) through a voronoi map in the preset's cave-biome
settings; the author saw sulfur springs on a Beta 1.8.1 continent. With `era_accurate`, `HostedEra.create` reads
the preset's resolved voronoi map, drops the points whose biome is newer than the era (`EraCaveBiomes`), and
overlays the trimmed map on the preset reference the biome source is built from (or switches the era to
Moderner Beta's "no cave biomes" provider when no biome survives) — through Moderner Beta's public settings API,
no mixin. `/cot structures <era>` also lists the era's remaining cave biomes.

## Two Minecraft versions from one source (built 2026-08-30)

The mod is built for **26.2** and **1.20.1** from the same `src/` tree with [Stonecutter](https://stonecutter.kikugie.dev)
(the same tool Moderner Beta uses for its own versions): `settings.gradle` declares one node per version
(`versions/1.20.1/`, `versions/26.2/`, each holding only a `gradle.properties` with that version's Fabric API,
Moderner Beta and Java), and the shared `build.gradle` runs once per node. The source as committed is in its
26.2 form (the VCS version); Stonecutter rewrites a processed copy per node at build time, and can switch `src/`
itself for IDE work (`Set active project to 1.20.1` / `Reset active project` in the Gradle `stonecutter` group —
reset before committing). `./gradlew build` builds both jars (`versions/<mc>/build/libs/continentsoftime-<ver>+<mc>.jar`);
`:26.2:build` one. The harness tasks run per node too (`./gradlew layoutTest` checks both; `:1.20.1:layoutTest` one).

**Toolchain.** One JDK, Java 25: it compiles 26.2 natively and 1.20.1 with `--release 17` (the game's minimum
there; the jar is Java 17 bytecode), and runs both dev servers. 26.1+ ships unobfuscated, so that node uses
Loom's plain plugin with no mappings; 1.20.1 is obfuscated, so its node uses Loom's remapping plugin against
Mojang's official mappings — the same names as the unobfuscated game, which is what makes one source tree
possible at all. Loom 1.17 remaps the mixins' targets and the access widener into the jar (no annotation
processor, no refmap). The access widener exists twice (`continentsoftime.accesswidener` in the `official`
namespace, `continentsoftime-named.accesswidener` for Mojang-mapped 1.20.1; same lines) and the build ships the
one it used. Run directories are per version: `run/<mc>/server`, `run/<mc>/client`.

**Where the versions differ in the code — kept in few, obvious places:**

- Plain renames are string replacements in `build.gradle` (`Identifier` ↔ `ResourceLocation` and `.identifier()`
  ↔ `.location()`; `net.minecraft.util.Util` ↔ `net.minecraft.Util`; `BlockAndTintGetter`'s package;
  `.getMinY()` ↔ `.getMinBuildHeight()`; and, only in the two files that opt in with a `//~ map_codec` header,
  `MapCodec` ↔ `Codec` for the generator and biome-source codecs).
- Everything else is a `//? if <version>` Stonecutter condition. Most live in `util.Compat` (id factories,
  block placement during generation, chunk coordinates, seed and registry access, the command permission).
  The rest sit where a whole method's shape differs: the chunk-generation step signatures in
  `AtlasChunkGenerator` (the executor argument before 1.21, the carving step before 1.21.2, the dimension key
  in `createStructures`, no `validate()` on 1.20.1); the network payload (`AtlasInfoPayload` is a typed
  `CustomPacketPayload` with a stream codec from 1.20.5, a hand-written channel message before; `AtlasChannel`
  and the client entry point register/send/receive accordingly); the four client mixins (Moderner Beta's tint
  sources are `BlockTintSource.colorInWorld` on 26.1+ and `BlockColor.getColor` before; the sky is an environment
  attribute layer on 1.21.11+ and a local in `ClientLevel.getSkyColor` before; the fog weight lives in
  `AtmosphericFogEnvironment.getBaseColor` on 1.21.11+ and in the static `FogRenderer.setupColor` before —
  mirroring Moderner Beta's own version split for each); and one synthetic lambda name in
  `ChunkProviderNoiseMixin` (Moderner Beta's 1.20.1 release was compiled by a JDK whose javac numbers lambdas
  class-wide: `lambda$getBaseBlockSource$3` there, `$0` on 26.2 — checked against each release jar).
- Per-version resources: `fabric.mod.json` and the mixin config are templates expanded by `processResources`
  (Minecraft range, Java version, which access widener).

Adding a Minecraft version means: a `versions/<mc>/gradle.properties`, a line in `settings.gradle`, and a pass
over the replacements, `Compat`, and every `//?` site — `grep -rn '//?' src` lists them all.

## Not yet built

The in-game checks listed in HANDOFF.md ("things to look at"), and water tint and climate precipitation per
continent (above). See HANDOFF.md "Next work".
