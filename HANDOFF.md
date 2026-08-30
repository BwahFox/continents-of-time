# Project state — read this first

*(Authoritative for state; update before ending any session that changed anything. This file is written for the
public (the repo is public) — see CLAUDE.md's hygiene rule. Maintenance: HANDOFF first when wrapping up, then commit + push to both remotes.)*

**What this is:** a Fabric mod that puts every era of Minecraft terrain generation into one world — each
historical generator is its own **continent**, separated by big oceans. Sail far enough and you make landfall
in another age of the game. Part of a larger modpack built around VirtualMinecraft (same author).

**How it works (decided 2026-08-29, details in ARCHITECTURE.md):** the mod is an **atlas around
[Moderner Beta](https://codeberg.org/Nostalgica-Reverie/moderner-beta)**, which is a runtime dependency (MIT;
packs install it themselves; nothing of it is copied here). Moderner Beta's eras are data presets over one
generator class; the atlas builds one complete Moderner Beta generator per era, adds vanilla's modern generator
as one more era, and routes every chunk to the era that owns it. The continent layout, the oceans and the seams
under them are this mod's own code and its whole difficulty.

**Version targets:** **26.2 and 1.20.1, from one source tree** (Stonecutter; built 2026-08-30 as the planned
backport). Development happens in the 26.2 form (the unobfuscated client is the reason 26.2 came first), and
every change must build for both — `./gradlew build` does. Moderner Beta ships the same release for both.
Where the two versions differ in the code, and the rules for keeping that small: ARCHITECTURE.md "Two Minecraft
versions from one source".

## The spec (from the author, 2026-08-29)

- **Every world-generation type Moderner Beta offers becomes a continent**, plus the game's own modern
  generator. Default roster, in layout order (26): Classic 0.0.14a_08, Classic 0.30, Indev, Infdev 227, 325,
  415, 420, 611, Alpha 1.1.2_01, Alpha winter mode, Beta 1.1_02, Beta 1.7.3, Beta 1.8.1, Beta 1.9-pre3, 1.0.0, 1.1, 1.2.5, 1.6.4,
  1.12.2, 1.17.1, Modern (vanilla), Pocket Edition, Bedrock 1.2, Bedrock 1.17, Legacy Console (large), Skylands.
  Moderner Beta's *variant* presets (large biomes, amplified, water world, ...) are knobs on those eras, not
  eras; they are not seated by default but any of them can be added to the roster in the config. Exception,
  by the author (2026-08-29): **Alpha winter mode is seated by default** — "it is kind of its own thing to the
  people who play Alpha".
- **Continent size is configurable, default 10,000 × 10,000 blocks, as a maximum.** Continents are shaped by
  noise to look like coastlines, so they are usually smaller than the box; they are never larger than it.
  (`config/continentsoftime.json`: `maxContinentSize`, `oceanWidth`, `eras`, `oceans`, `eraAccurate`.)
- **"No oceans" option** (author, 2026-08-29, built 2026-08-30): `oceans: false` in the config (`"oceans": false`
  in a world preset's settings) keeps the same seats but generates no open water — every column between
  continents belongs to the nearest era, whose own terrain fills the gap up to a hard seam at a chunk boundary
  with its neighbour's (the modern→Infdev 420 seam the author liked). No seabed, no coast band; `oceanWidth` is
  then just spacing. Baked into the world like the rest.
- Config values are baked into a world when it is created (level.dat stores them); editing the config later
  only affects new worlds.

- **The world is infinite (author, 2026-08-30: "we should be able to have more continents... multiple continents
  of the same type... it's an infinite world"; built the same day).** The first pass seats the roster once in
  timeline order around home; beyond it, every further grid cell holds a continent of a seeded-random shaped era,
  each with its own coastline, for ever. Finite levels (Classic, Indev) and Legacy Console appear exactly once —
  their generators are translated to one seat. Oceans still separate everything. Random rather than a repeated
  timeline: the author's words were "multiple continents of the same type"; switching the pick to a repeating
  order is a one-line change in `ContinentLayout.seatAt` if ever wanted.

**The fantasy (author, 2026-08-29): sailing between continents is time travel.** The roster is the timeline; the
layout seats eras in roster order outward from the modern home, so reordering the config roster reorders time.

**Vanilla clients can join a server running the mod** (author found this 2026-08-29): all generation is
server-side and the biomes are data-driven and synced at login. **Keep it that way** — client-side work
(per-continent visuals, a config screen) must be optional; the server must never require the mod on the client.

**Confirmed by the author 2026-08-29:** the runtime dependency on Moderner Beta, and the defaults below.
**Players always spawn in the modern era** — the modern continent holds the origin; **oceans are at least 2,000 blocks**
wide; the Legacy Console seat uses the *large* preset; Skylands is seated (floating islands over open ocean);
finite eras (Classic, Indev) are small islands, since that is what those worlds were.

## State as of 2026-08-30 (session 6, later): 1.0.2 — C2ME's aquifer optimisation vs the Bedrock RNG

**1.0.2 released 2026-08-30** (https://github.com/BwahFox/continents-of-time/releases/tag/v1.0.2, both jars,
text approved by the author): the author's full-radius pregen with C2ME reached the Bedrock 1.17 continent and every chunk
of the three Bedrock-RNG eras failed ("IllegalArgumentException at RandomUtils.getRandom"): C2ME's
`optimizeAquifer` (default on) recognises only vanilla's two positional random factories, and those eras fork
their aquifer random from Moderner Beta's `BedrockRandomSource`. Fixed by `mixin.AquiferSamplerProviderMixin`
(ARCHITECTURE "Threaded world generation"): the aquifer's factory — only it — becomes a vanilla
`LegacyRandomSource` seeded from the Bedrock one. **Mixin lesson paid for in an hour of silence: a bare
`"<init>"` target on a class with two constructors is ambiguous and the mixin is dropped without any log line
at INFO** — target a uniquely named method instead (here `provideAquiferSampler` HEAD; a constructor descriptor
would have written vanilla type names into a `remap = false` mixin, which the 1.20.1 remap leaves wrong).
Verified: Chunky r-96 runs on the bedrock_1_17, pe and bedrock_1_2 seats, zero failed chunks with
`optimizeAquifer` on (with a DIAG build first: 578 provider constructions, every factory caught); harnesses
green; the workaround for players on 1.0.1 is `optimizeAquifer = false` in `config/c2me.toml`.

## State as of 2026-08-30 (session 6): 1.0.1 — safe under C2ME's threaded worldgen

**Release 1.0.1 is published (2026-08-30):** https://github.com/BwahFox/continents-of-time/releases/tag/v1.0.1 — both jars, CHANGELOG's notes.

**Session 6 (2026-08-30):** the author pregenerated a 1.0.0 world with Chunky under C2ME and got "Failed to load
chunk" toasts — two races, both fixed in `AtlasChunkGenerator` (ARCHITECTURE "Threaded world generation"): the
per-era structure state's lazy fill (now completed inside the `computeIfAbsent`) and Moderner Beta's per-chunk
surface context on the shared `SurfaceSystem` (the surface step is now serialised). Also declared the real
loader requirement, ≥ 0.19.5 (built against its Mixin; 0.19.3's MixinExtras 0.5.4 cannot read the mod's
`@Redirect`s and kills Moderner Beta's entrypoint at startup — found when a fresh Prism instance on 0.19.3
crashed). **Compat test recipe with C2ME:** drop `c2me-fabric-mc26.2-*.jar` and `Chunky-Fabric-*.jar` into
`run/26.2/server/mods/` (gitignored), fresh `level-name`, then over RCON `chunky center x z`, `chunky radius r`,
`chunky start`; a run is clean when the log has no "Failed to load chunk" / exception between "Task started"
and "Task finished". The jars stay there (like CTOV's on 1.20.1), so every dev-server run is a C2ME run now.
**Verified 2026-08-30 on a fresh seed-20260829 world:** four Chunky runs, 20,260 chunks — the modern origin
(r 512), the Beta 1.1_02 east coast at (−8500, 828) (r 768: era chunks, coast band and ocean), the Infdev 227
west coast at (9000, 969) (r 512) and the Indev finite level (r 384) — zero failed chunks, 270–375 cps; the
1.0.0 code failed within seconds on the same setup. ~93 cps was the single-threaded rate before.

## State as of 2026-08-30 (end of session 5): 1.0.0 prepared

**Release 1.0.0 is published (2026-08-30):** https://github.com/BwahFox/continents-of-time/releases/tag/v1.0.0 —
both jars, CHANGELOG's notes; the repository is public. Player-facing documentation is README.md (requirements
per version, playing, options, multiplayer, known limitations) and CHANGELOG.md. Before going public the tracked
files and the commit history were swept for personal information (the author keeps her git email as is).

**Everything on the list is built. The layout, the oceans/seams, spawn, `/cot`, the optional client half, Alpha
winter mode, the re-open fix, the "no oceans" option, the infinite atlas, era-accurate structures and cave biomes
were all verified by session 4 (the visuals and the coasts by the author in the dev client). Session 5 did the
1.20.1 backport: the repo is now a two-version Stonecutter build, both jars build, all three harnesses pass on
both nodes, a 1.20.1 dedicated server was probed end to end (details below), and the author tested the 1.20.1
client half in the dev client the same day ("seems to work"). The backport is done.**

- Build: `./gradlew build` (one JDK, Java 25; Loom 1.17; Stonecutter 0.9.7) produces
  `versions/26.2/build/libs/continentsoftime-0.1.0+26.2.jar` (Fabric API 0.158.0+26.2, Moderner Beta
  5.0.0-alpha.3+26.2, Java 25 bytecode) and `versions/1.20.1/build/libs/continentsoftime-0.1.0+1.20.1.jar`
  (Fabric API 0.92.11+1.20.1, Moderner Beta 5.0.0-alpha.3+1.20.1, Mojang mappings, Java 17 bytecode).
  `:26.2:build` / `:1.20.1:build` build one; every task below exists per node the same way (`:1.20.1:runServer`).
  The 1.20.1 game runs fine under Java 25 in the dev environment (no JDK 17 installed or needed).
- Code: `dev.continentsoftime.atlas` — `AtlasChunkGenerator`, `AtlasBiomeSource`, `HostedEra`, `AtlasSettings`,
  `Eras`, `Translated`; `atlas.layout` — `Layout`, `ContinentLayout`, `Footprint`, `Seat`, `Seabed`, `Noise`;
  `mixin` — eight small mixins into Moderner Beta that move anchored eras to their seats; `config.ContinentsConfig`.
  Data: two world presets — `continentsoftime:continents_of_time` (in the world-type list; roster and sizes
  from config) and `continentsoftime:single_era` (one era everywhere; not listed; for verification).
- **The layout.** `ContinentLayout` seats every roster era once on a hex-offset grid with pitch
  `maxContinentSize + oceanWidth` (the ocean gap is guaranteed by construction), home era on the origin, cells
  grown outward from home in **roster order — the roster is the timeline you sail along**; each infinite era is a
  domain-warped-noise continent inside its box, provably never touching the box edge and provably land at the
  centre; finite eras (Classic, Indev) are unshaped boxes the size of their level; Legacy Console is shaped
  inside its own 5120 border. ~0.14 µs per column, cached per chunk. Design and the reasoning behind every
  constant: ARCHITECTURE.md "The layout".
- **Oceans and seams.** Chunks with no land column are ocean chunks the atlas generates itself (modern ocean
  biomes by temperature, `Seabed` floor, water to 63, vanilla surface rules, modern-era decoration). Every era
  chunk touching the sea is clamped into the `Seabed` height band, so ocean and era chunks meet at the same
  seabed height and terrain eases down to the shoreline. Sea columns are the modern ocean biome; it stops at the
  coast. **Anchored eras are translated to their seats** through mixins on Moderner Beta's providers (Classic,
  Indev, Legacy Console). All of it: ARCHITECTURE.md "Oceans and seams".
- **Verified 2026-08-29 (session 3):** `./gradlew layoutTest` passes for four seeds (layout guarantees, seabed
  and coast-band invariants, chunk/column agreement, open water never routed to a finite era). Dedicated server
  on `continentsoftime:continents_of_time`, seed 20260829, world `atlas` (fresh), probed by block tests over
  RCON: home east coast at z=0 — vanilla terrain to x 3119, then the seabed from y 61 at the shoreline down to
  51 by x 3344 with `minecraft:ocean`; deep ocean at x 6000 — floor y 19, water to 63; Infdev 227 coast at
  x 8976 — seabed rising to the shoreline, Infdev land beyond; Classic 0.0.14a seat (box x 5744..6000,
  z -9440..-9184) — its level's terrain (y 62..77) and its own biome across the box, modern ocean around it;
  Legacy Console seat — forest/plains land inside its box, ocean outside; Skylands seat — lifted 64 blocks after
  the author saw the islands half-drowned in the dev client ("more like islands"): full-column dumps show
  bedrock/stone seabed to ~y 18, water to 63, air, islands at y 86..137; the author then confirmed the lifted
  Skylands in the dev client ("works"). No generation exceptions. The author looked at the rest in the dev
  client too: "everything seems to be working".
- **Things the author looked at in-game (2026-08-29, accepted as-is: "everything looks fine"):** the coast band's shape (era terrain higher than
  the shoreline is cut along a quadratic rise; era terrain lower — an era's own sea at the layout coast — is
  filled up to a sandbar at the shoreline); Legacy Console shows ocean-biome patches on land inside its box
  (x -42340..-41560 at z 80976 for seed 20260829), possibly its own height-based biome injection interacting
  with the translated border — decide after seeing it; finite levels' surfaces after the coast clamp; era
  carvers run over the seabed fill in coastal era chunks (small underwater cave pockets seen in a Skylands
  column at y 12..14) — harmless, could be masked by skipping carvers below the seabed.
- **Spawn and the `/cot` command (session 3):** the server's world spawn for seed 20260829 is (0, 73, 0) — on the
  home continent, as the layout guarantees (the origin is always land). `/cot seats` lists every era's seat
  and box, `/cot where` says who owns the chunk you stand in plus the coast field and the nearest continent,
  `/cot seat <era>` teleports you onto that era's continent at its centre (operator level 2; the era argument
  tab-completes from the roster). Verified over RCON except the teleport, which needs a player.
- **Per-continent visuals (session 4):** the optional client half. The server sends `continentsoftime:atlas_info`
  (seed, sizes, footprints, and the climate-sampling eras' biome settings) with every level info, only to clients
  that registered the channel; a client with the mod rebuilds the layout and those eras' Moderner Beta biome
  providers, and a composite `ContinentClimate` routes Moderner Beta's grass/foliage tints, sky colour and old fog
  weighting to the era that owns the column, vanilla everywhere else (modern continent, open sea, eras without a
  climate). Design, what is deliberately not done (water tint, climate precipitation), and why:
  ARCHITECTURE.md "Per-continent visuals". Which eras get it with Moderner Beta's default config: Beta 1.1_02,
  Beta 1.7.3, Beta 1.8.1, Beta 1.9-pre3, 1.0.0, 1.1 (sky + vegetation; Pocket Edition is off in its config).
  **Verified:** `./gradlew climateTest` (routing, fallbacks, fog gate, single-era layout, payload codec round
  trip); `./gradlew build`; dedicated server starts with the new server mixin. **Verified by the author in the dev
  client afterwards ("this works").** Earlier the session's attempts to load a hand-assembled save (server `level.dat` + synthetic player
  tag) failed (26.2 keeps world-gen settings in `<save>/data/minecraft/world_gen_settings.dat`, and even with it
  the save was corrupt); use a properly created world instead. Two bugs the failed runs still caught and fixed:
  the payload type was not registered, then registered twice.
- **Re-opening a world from the client failed (found and fixed in session 4):** vanilla's `validate()` on
  re-open feature-sorts the atlas's union of all eras' biomes and hits a "feature order cycle" (desert vs
  beta_desert); creating a world skips that path, which is why it had never shown. The atlas now validates its
  hosted generators instead and never sorts the union (ARCHITECTURE "as seen from outside"). The author saw it
  as "safe mode got enabled / my test world corrupted" — that dialog is the generic load failure; read the log.
- **Lesson from session 4:** chunks generated in an earlier run stay on disk — the chunk pyramid pre-generates
  a radius around every loaded chunk — so a fix can look intermittent when re-probed on a used world. Verify
  on fresh chunks or a fresh world.
- **1.20.1 backport (session 5, verified 2026-08-30):** dedicated 1.20.1 server (`:1.20.1:runServer`, world
  `run/1.20.1/server/atlas`, seed 20260829, fresh), probed over RCON: the seat table is identical to the layout
  harness's on both nodes (same seed, same layout — it is Minecraft-free); `/cot seats|where|structures` answer;
  era-accurate structures hold (Beta 1.8.1: mineshafts, strongholds, villages + Moderner Beta's ocean shrine, no
  cave biomes; Alpha: shrine only; modern: 16 sets); home east coast at z 0 — seabed from y 62 at x 3600 down to 53
  by x 3800 with the modern ocean biomes; deep ocean at x 6000 — floor y 19, water to 63; Infdev 227 landfall at
  x 10250 (land y 65..69, the era's own biomes); Classic 0.0.14a seat (box x 8560..8816, z -10336..-10080) — its
  level's terrain (y 64..75) and its own biome inside the box, warm ocean outside (the finite-era translation
  mixins work on 1.20.1); Skylands seat — bedrock/stone seabed to y 18, water to 63, islands at y 96..113 (the
  lift works); Legacy Console's centre row is that era's own ocean for this seed (water to 62, floors 30..48,
  land-biome patches underwater — the same height-based injection noted on 26.2). No generation errors in the
  log. **Cross-checked against a fresh 26.2 world with the same seed, same rows:** the seat table, every column's
  biome, the Classic level, the Skylands islands (y 96..113) and the Skylands centre column are identical; the
  only differences are 1..3-block "top block" deltas at sea and coast columns, which are per-version vanilla
  decoration (kelp/seagrass counted as a top block) and the home continent being each version's own vanilla
  generator — not the atlas. Also observed (both versions, pre-existing): the atlas ocean's water surface is y 63 while the home
  continent's own vanilla oceans top out at y 62 — a one-block step where the two meet; the author accepted the
  coasts as-is, so it is only noted here.
- **Verifying the 1.20.1 client half (for the author):** `./gradlew :1.20.1:runClient` (run dir `run/1.20.1/client`;
  first launch creates it), create a Continents of Time world or join a `:1.20.1:runServer` on 127.0.0.1:25599
  (`--args="--quickPlayMultiplayer 127.0.0.1:25599"`), then `/cot seat moderner_beta:beta` — Beta grass/foliage
  tints, the old sky colour and old fog on that continent, vanilla colours back on the modern one. The client log
  should say "client climate installed for 7 of 26 eras" (the six Beta/early-release eras and Pocket Edition's
  sampler).

**Test recipe (headless):** `run/<mc>/server/` (`run/26.2/server`, `run/1.20.1/server`) holds `eula.txt` and
`server.properties` (offline mode, port 25599, RCON on 25598 password `cottest`, view distance 6). It points at
`level-name=atlas`, `level-type=continentsoftime:continents_of_time` (the multi-era world; the 26.2 single-era
world is still in `run/26.2/server/world`, switch `level-name`/`level-type` back to use it). Both versions use
the same ports, so run one at a time. `./gradlew :<mc>:runServer`, then drive it with
a ten-line RCON client (the protocol is trivial: auth packet type 3, command type 2). The most useful probe is
a cross-section script: `forceload add x0 z x1 z` (one chunk row; the command refuses more than 256 chunks),
wait until `execute if block` stops answering "not loaded", then per column scan `execute if block x y z
minecraft:air` downward for the top block, test `minecraft:water` for the water surface, and `execute if biome
x y z <id or #tag>` for the biome. Seat coordinates for a seed are in the server log's seat table and in the
harness's "crossings" lines. The `run/` directory is gitignored; reuse the worlds there, but remember that
previously generated chunks stay as they were — verify fixes on fresh chunks (delete the world if needed; it
is ~10 MB). The 26.2 `atlas` world predates the Alpha-winter roster change, so its baked seat table differs from
a fresh world's; the 1.20.1 `atlas` world is fresh (2026-08-30).

**Harnesses** (each runs for both versions from the root; `:<mc>:` prefix for one): `./gradlew climateTest`
(`-Pseeds=...`) — the client's composite climate over a real layout with stub era samplers, plus the `atlas_info`
payload round trip (stream codec on 26.2, hand-written buffer on 1.20.1); ~2 s. `./gradlew layoutTest`
(`-Pseeds=1,2,3`; ~18 s per seed per version) — assertions plus `build/layout/<seed>.png` (whole atlas, 32
blocks/pixel) and `<seed>-home.png` (home continent, 8 blocks/pixel), written at the repository root.
`./gradlew timelineTest` — era versions and the structure/cave-biome filters. All pass on both nodes (2026-08-30).

**Dev client:** `./gradlew :26.2:runClient` (run dir `run/26.2/client`, Fabric API and Moderner Beta already on
the classpath; offline "Player###" account). `--args="--quickPlaySingleplayer single_era_beta"` opens straight
into a copy of the verified beta world (`run/26.2/client/saves/single_era_beta`, copied from the server world, so
recreate it from `run/26.2/server/world` if it is ever missing). Verified 2026-08-29: the client loads it and
renders beta 1.7.3 terrain, and the author confirmed **Continents of Time appears in the Create World → World
Type list** and works from there. `:1.20.1:runClient` is the same for 1.20.1 (not yet launched by anyone).

## Next work, in order

The author's plan after the Customize screen (2026-08-30: "then we'll handle releasing it + mod testing with
other mods installed (again this is for a modpack)"):

1. **Mod-compatibility testing** — the mod alongside the other mods the modpack will carry (the author picks
   the list; VirtualMinecraft and the parked modpack inclusions in the sibling project's plan are the obvious
   candidates). Look for: world-type list conflicts, other worldgen mods' biome/structure injection into hosted
   eras, TerraBlender-style surface-rule hooks (Moderner Beta has its own compat for that), and client-side
   colour/fog mods fighting the per-continent visuals.
   **Done 2026-08-30 — CTOV (ChoiceTheorem's Overhauled Village), the one the author expected trouble from
   ("CTOV villages only should spawn in areas that spawn normal villages"):** compatible by construction, verified
   live. CTOV ships no structure sets of its own; Lithostitched `add_structure_set_entries` puts its 78 village
   structures into vanilla's `minecraft:villages` and its outposts into `minecraft:pillager_outposts`, and the
   atlas dates sets by id — so on a 1.20.1 server with CTOV 3.4.14 + Lithostitched 1.4.11, `/cot structures`
   shows no village set on Alpha or Beta 1.7.3, `minecraft:villages` from Beta 1.8.1 on, outposts from 1.14, and
   `/locate structure #ctov:village` from the modern spawn finds `ctov:large/village_plains_fortified` 384 blocks
   away. The two jars stay in `run/1.20.1/server/mods/` (gitignored) for future compat runs; delete them to test
   without. **Not covered:** mods that ship their *own* structure sets (Towns and Towers, Repurposed Structures,
   YUNG's...) are "unknown" to `EraStructures` and allowed on every continent. A generic fix, if wanted: date an
   unknown set by the vanilla structure tags its structures carry (`#minecraft:village`, `mineshaft`,
   `shipwreck`, `ocean_ruin`, `ruined_portal`, `eye_of_ender_located` exist on both versions) plus a config map
   for the rest — an hour's work in `EraStructures.filtered`, with a harness check on the config path.
2. **First release — 1.0.0 published 2026-08-30.** Later releases: bump `mod_version` in `gradle.properties`, add a CHANGELOG
   entry, `./gradlew clean build layoutTest climateTest timelineTest`, tag `v<version>`, push both remotes,
   `gh release create v<version> versions/*/build/libs/continentsoftime-<version>+*.jar --notes-file <notes>`.
   GitHub releases only, never Modrinth.

Built 2026-08-30 (session 5): **the 1.20.1 backport** — Stonecutter two-version build, verified on a 1.20.1 server
(state section above); how the versions differ in the code is in ARCHITECTURE.md. Then **the Customize screen**
(pulled forward from Parked by the author): vanilla's Customize button on the Create World screen opens
`ContinentsCustomizeScreen` — roster (seat/unseat/reorder every Moderner Beta preset plus the modern generator),
continent size, ocean width, oceans, era-accurate, reset to config — and bakes the result into the world through the
atlas codec (ARCHITECTURE "The Customize screen"). Verified by the author in the 26.2 dev client the same day
(created a no-oceans world through it: "no oceans seems to work"); builds for 1.20.1 too, not yet clicked
through there.

Built 2026-08-30: **era-accurate cave biomes** (same `eraAccurate` flag; the author saw sulfur springs on Beta
1.8.1 — Moderner Beta's presets inject vanilla's modern cave biomes under every era from Beta on; now trimmed per
era at build time through Moderner Beta's settings API: lush/dripstone 1.18, deep dark 1.19, sulfur caves 26.2;
`/cot structures <era>` lists what remains; `./gradlew timelineTest`).

Built 2026-08-30: **era-accurate structures** (`eraAccurate`, default on, baked as
`era_accurate`): each chunk's structure placement uses a state built by its era's own generator (so
Moderner Beta's per-preset overrides — ocean shrine, Legacy Console strongholds — still apply) over the structure
sets that existed in the era's version (`atlas.structure.EraVersion`/`EraStructures`; vanilla sets dated, unknown
sets allowed everywhere; non-Java eras mapped: PE→1.4, Bedrock by number, Legacy Console→1.13, Skylands→Beta
1.7.3). `/cot structures <era>` lists what an era can place. Caveat: `/locate` uses the level's union state, so
it can point to a spot on an old continent where the filter then places nothing. `./gradlew timelineTest`.

Built 2026-08-30: the **"no oceans" option** (verified on a scratch server: gap columns owned by the nearest era,
field 1.0, no atlas ocean) and the **infinite atlas** (harness-verified, and probed live on the seed-20260829 server: (100000, 100000) has a
Release 1.6.4 repeat as its nearest continent, (100000, −130000) is inland on a Release 1.1 repeat and generates
stone/air normally — both far outside the first pass). `/cot seat` teleports to first-pass seats only; use
`/cot where` or `/tp` to explore repeats. Both features need a new world (the layout is built at server start from
the baked settings, so an old world's far cells do get repeats on first visit, but no-oceans is baked).

Closed 2026-08-29 without code: **era-accurate caves** are already true by construction — `applyCarvers` goes to
the owning era's generator, and Moderner Beta's generator applies each preset's own cave rules there (its Beta cave
and canyon carvers swapped in where the preset forces them, no carving at all where the preset says so — Infdev
420 has none, as in that version — and noise caves off in old presets).

## Parked (the author's own calls, 2026-08-29 — do not pull forward)

- **VirtualMinecraft integration: a "time travel" program** (author, 2026-08-30, "just an idea for now"): a
  program on a VirtualMinecraft computer that copies the chunk you stand in and moves it to the nearest area of
  the era you picked — you type a date (e.g. "June 19, 2021") and it takes you to whatever version was current
  then (1.17). Pack-glue between the two mods, not this mod's core; needs a date→version table (the timeline
  package already knows era versions), a chunk copy, and a "nearest continent of era X" query, which
  `ContinentLayout` can answer.
- **Old textures on pre-1.14 continents** (author, 2026-08-30, "if possible"): the verdict given was "only
  crudely" — textures cannot vary by position, so the most the client could do is auto-enable the built-in
  Programmer Art pack when the player crosses onto a pre-1.14 continent and disable it on the way back, at the
  cost of a full resource reload (a multi-second freeze) at every crossing. Client-only and optional if ever
  built; the author would accept the crude version ("I don't mind") but parked it: "just ideas".
- **Join from older game versions and spawn in that version's continent** (author, 2026-08-30, "just an idea
  for later"; Interloper-inspired): a client on, say, Beta 1.7.3 connects to the server and appears on the Beta
  1.7.3 continent. The author's pointer is what ViaVersion/ViaFabric do (protocol translation). Not this mod's
  code as it stands — it would be protocol work on top of the atlas, and needs its own study pass — so parked.

- **Era-emulation gameplay** (older continents disable newer mechanics) — "might be impossible, I don't
  know"; revisit only when the atlas works.
- **Legacy Console styling** for some continents (re-console / Legacy4J) — rides with era-emulation.
- Seasons, ambient sound, CTOV, Distant Horizons/Voxy, controller support — those are **modpack inclusions**,
  not this mod's code. The CTOV × VirtualMinecraft store compat hook belongs to pack-glue work, not here.
- **Coast biome transition** (author, 2026-08-29): once the ocean and the era biomes are settled, add a
  transition between them instead of the hard ocean-stops-here line. After the oceans work.
- **Far Lands** (author, 2026-08-29: "not quite sure how I want to handle" them). Moderner Beta has a per-preset
  toggle; at ±12.5M blocks they lie far outside every seat, so the layout forecloses nothing. Undecided. An idea
  **Seen by the author 2026-08-30 at x 12,563,023: the Far Lands generate (Moderner Beta's per-preset toggle,
  untouched), and a continent that happens to be seated inside them "looks pretty trippy... that's neat"** — so
  they work as-is; nothing to do unless the author wants a policy. An idea
  from the author (2026-08-30), parked by the author in the same breath: "what if all the continents began to
  clash there? like... fighting over the same generation? making everything look messy... maybe not, i don't
  want to add more features to this than what i already have planned".
