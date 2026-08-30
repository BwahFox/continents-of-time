# Project state — read this first

*(Authoritative for state; update before ending any session that changed anything. This file is written for the
public (the repo is private until release) — see CLAUDE.md's hygiene rule. Maintenance: HANDOFF first when wrapping up, then commit + push to both remotes.)*

**What this is:** a Fabric mod that puts every era of Minecraft terrain generation into one world — each
historical generator is its own **continent**, separated by big oceans. Sail far enough and you make landfall
in another age of the game. Part of a larger modpack built around VirtualMinecraft (same author).

**How it works (decided 2026-08-29, details in ARCHITECTURE.md):** the mod is an **atlas around
[Moderner Beta](https://codeberg.org/Nostalgica-Reverie/moderner-beta)**, which is a runtime dependency (MIT;
packs install it themselves; nothing of it is copied here). Moderner Beta's eras are data presets over one
generator class; the atlas builds one complete Moderner Beta generator per era, adds vanilla's modern generator
as one more era, and routes every chunk to the era that owns it. The continent layout, the oceans and the seams
under them are this mod's own code and its whole difficulty.

**Version targets (revised 2026-08-29):** **26.2 first** — development happens here because the 26.2 client is
unobfuscated. **1.20.1 comes as a backport after the mod is complete**, not in parallel. Moderner Beta ships
the same release for both, so the backport is a Stonecutter/branch job over our own code only; keep
Minecraft-API-touching code in few, obvious places so that job stays small.

## The spec (from the author, 2026-08-29)

- **Every world-generation type Moderner Beta offers becomes a continent**, plus the game's own modern
  generator. Default roster, in layout order (25): Classic 0.0.14a_08, Classic 0.30, Indev, Infdev 227, 325,
  415, 420, 611, Alpha 1.1.2_01, Beta 1.1_02, Beta 1.7.3, Beta 1.8.1, Beta 1.9-pre3, 1.0.0, 1.1, 1.2.5, 1.6.4,
  1.12.2, 1.17.1, Modern (vanilla), Pocket Edition, Bedrock 1.2, Bedrock 1.17, Legacy Console (large), Skylands.
  Moderner Beta's *variant* presets (large biomes, amplified, water world, ...) are knobs on those eras, not
  eras; they are not seated by default but any of them can be added to the roster in the config.
- **Continent size is configurable, default 10,000 × 10,000 blocks, as a maximum.** Continents are shaped by
  noise to look like coastlines, so they are usually smaller than the box; they are never larger than it.
  (`config/continentsoftime.json`: `maxContinentSize`, `oceanWidth`, `eras`.)
- Config values are baked into a world when it is created (level.dat stores them); editing the config later
  only affects new worlds.

**The fantasy (author, 2026-08-29): sailing between continents is time travel.** The roster is the timeline; the
layout seats eras in roster order outward from the modern home, so reordering the config roster reorders time.

**Confirmed by the author 2026-08-29:** the runtime dependency on Moderner Beta, and the defaults below.
**Players always spawn in the modern era** — the modern continent holds the origin; **oceans are at least 2,000 blocks**
wide; the Legacy Console seat uses the *large* preset; Skylands is seated (floating islands over open ocean);
finite eras (Classic, Indev) are small islands, since that is what those worlds were.

## State as of 2026-08-29 (session 3)

**Items 1–4 of the original plan are done, and the atlas layout (session 3) is built and verified; oceans and
seams are next.**

- Build: `./gradlew build` (Java 25, Loom 1.17, Fabric API 0.158.0+26.2, Moderner Beta 5.0.0-alpha.3+26.2 from
  the Modrinth maven) produces `build/libs/continentsoftime-0.1.0.jar`. No Stonecutter yet (26.2 only for now).
- Code: `dev.continentsoftime.atlas` — `AtlasChunkGenerator`, `AtlasBiomeSource`, `HostedEra`, `AtlasSettings`,
  `Eras`; `atlas.layout` — `Layout`, `ContinentLayout`, `Footprint`, `Seat`, `Noise`; `config.ContinentsConfig`.
  Data: two world presets — `continentsoftime:continents_of_time` (in the world-type list; roster and sizes
  from config) and `continentsoftime:single_era` (one era everywhere; not listed; for verification).
- **The layout (session 3).** `ContinentLayout` seats every roster era once on a hex-offset grid with pitch
  `maxContinentSize + oceanWidth` (the ocean gap is guaranteed by construction), home era on the origin, cells
  grown outward from home in **roster order — the roster is the timeline you sail along**; each infinite era is a
  domain-warped-noise continent inside its box, provably never touching the box edge and provably land at the
  centre; finite eras (Classic, Indev) are unshaped boxes the size of their level. ~0.14 µs per column, cached
  per chunk. Ownership is per chunk (centre column); **open ocean is routed to the nearest *shaped* continent's era for
  now** (never to a finite era, whose generator only makes its water-and-`the_void` border outside its level),
  so between continents you still see that era's terrain, ending in a hard chunk-boundary cliff, until item 1
  below lands. Seen in the dev client 2026-08-29: modern forest dropping into flat water at the seam — expected. Design and the
  reasoning behind every constant: ARCHITECTURE.md "The layout".
- **Verified 2026-08-29 (session 3):** `./gradlew layoutTest` passes for four seeds (determinism, all 25 eras
  seated, origin + 512-block disc on home, land inside every box and ≤ 10,000 wide, land-to-land gaps ≥ 2,000 —
  observed ≥ 3,800 — chunk/column agreement); the rendered maps show real coastlines (bays, fjords, peninsulas,
  offshore islands). Dedicated server on `continentsoftime:continents_of_time`, seed 20260829, world `atlas`:
  logs the seat table; at the origin the nearest `minecraft:plains` is 71 blocks away and the nearest
  `moderner_beta:beta_forest` 7,198; at the Beta 1.7.3 seat's centre (-12324, -659) beta biomes are within
  300–900 blocks and vanilla biomes ~7,700 away. The seed-20260829 seat table is in that server log and in the
  harness output (same seats: the harness models the default roster exactly).
- **Finding for item 1:** Moderner Beta's finite provider (`ChunkProviderFinite`) is anchored to the world
  origin — its level spans ±width/2 around (0, 0), everything outside is `generateBorder`, and `getHeight`
  returns sea level there. A finite era seated anywhere but the origin currently generates border. The atlas
  must translate finite eras to their seat (a coordinate offset around the hosted generator, or a small mixin
  on `ChunkProviderFinite.inWorldBounds`/`generateTerrain`'s offsets) — part of the oceans/seams work.

**Test recipe (headless):** `run/server/` holds `eula.txt` and `server.properties` (offline mode, port 25599,
RCON on 25598 password `cottest`, view distance 6). It now points at `level-name=atlas`,
`level-type=continentsoftime:continents_of_time` (the multi-era world, 9 MB; the single-era world is still in
`run/server/world`, switch `level-name`/`level-type` back to use it). `./gradlew runServer`, then drive it with
a ten-line RCON client (the protocol is trivial: auth packet type 3, command type 2) — `locate biome`,
`execute positioned X Y Z run locate biome ...` to probe a seat, `forceload`, `stop`. The `run/` directory is
gitignored; reuse the worlds there rather than generating fresh ones.

**Layout harness:** `./gradlew layoutTest` (`-Pseeds=1,2,3` to choose seeds; ~16 s per seed) — assertions plus
`build/layout/<seed>.png` (whole atlas, 32 blocks/pixel) and `<seed>-home.png` (home continent, 8 blocks/pixel).

**Dev client:** `./gradlew runClient` (run dir `run/client`, Fabric API and Moderner Beta already on the
classpath; offline "Player###" account). `./gradlew runClient --args="--quickPlaySingleplayer single_era_beta"`
opens straight into a copy of the verified beta world (`run/client/saves/single_era_beta`, copied from the
server world, so recreate it from `run/server/world` if it is ever missing). Verified 2026-08-29: the client
loads it and renders beta 1.7.3 terrain, and the author confirmed **Continents of Time appears in the Create
World → World Type list** and works from there.

## Next work, in order

1. **Oceans and seams.** Between continents the atlas must generate open ocean itself instead of routing ocean
   chunks to the nearest era. **The ocean is the modern ocean** (author, 2026-08-29): vanilla ocean/deep-ocean
   biomes and vanilla ocean floor, water to sea level; **it simply stops at each coast** — the era's own biomes
   begin at the coastline, no biome blending (a transition is parked, below). At each coast pull the era's
   terrain down under the water with a falloff by distance-to-coast, so no era ever ends in a cliff. `ContinentLayout.fieldAt(x, z)` is the signed coast field to drive that (positive inland,
   negative at sea, crossing zero at the coast). `createBiomes` for multi-era worlds must fill from the atlas
   biome source (today it delegates, which is right only on land). **Finite eras must be translated to their
   seat** (see the finding above); Skylands: ocean below, islands above. Recommend the strongest model tier
   again: it is cross-generator terrain surgery with no pattern in this repo.
2. **Spawn on the home continent** and a first *look* from a client (screenshots of two coasts). The origin is
   guaranteed land, so vanilla spawn search should already land on home; verify, then screenshots.
3. **Per-continent visuals** — Moderner Beta's old fog/sky/grass colouring is a per-level flag that the atlas
   turns off everywhere; a composite climate sampler would bring it back per continent. Lower priority.
4. **1.20.1 backport** — after the mod is complete (author's call).

## Parked (the author's own calls, 2026-08-29 — do not pull forward)

- **Era-emulation gameplay** (older continents disable newer mechanics) — "might be impossible, I don't
  know"; revisit only when the atlas works.
- **Legacy Console styling** for some continents (re-console / Legacy4J) — rides with era-emulation.
- Seasons, ambient sound, CTOV, Distant Horizons/Voxy, controller support — those are **modpack inclusions**,
  not this mod's code. The CTOV × VirtualMinecraft store compat hook belongs to pack-glue work, not here.
- A config **screen** (the file exists; a GUI does not) — after the oceans work.
- **Coast biome transition** (author, 2026-08-29): once the ocean and the era biomes are settled, add a
  transition between them instead of the hard ocean-stops-here line. After the oceans work.
- **"No oceans" option** (author, 2026-08-29, on seeing the hard seam between modern and Infdev 420 terrain in
  the dev client: "i like this"): a config switch that skips ocean generation and leaves continents butting
  against each other at chunk seams. Base functions first; revisit after.
- **Far Lands** (author, 2026-08-29: "not quite sure how I want to handle" them). Moderner Beta has a per-preset
  toggle; at ±12.5M blocks they lie far outside every seat, so the layout forecloses nothing. Undecided.
- **Era-accurate structures, optional** (author, 2026-08-29): structures that did not exist in an era's version
  should not generate on its continent. Feasible: the atlas owns structure placement state, so it can filter
  structure sets per era behind a config flag. After the oceans work.
- **Era-accurate caves, optional** (author, 2026-08-29). Probably already true: `applyCarvers` is routed to the
  hosted era's generator and Moderner Beta ships each era's own carvers — verify on a beta and an infdev
  continent before writing any code.
