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

## State as of 2026-08-29 (session 3, continued)

**The layout and the oceans/seams are built and verified on a server; spawn and a first client look are next.**

- Build: `./gradlew build` (Java 25, Loom 1.17, Fabric API 0.158.0+26.2, Moderner Beta 5.0.0-alpha.3+26.2 from
  the Modrinth maven) produces `build/libs/continentsoftime-0.1.0.jar`. No Stonecutter yet (26.2 only for now).
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
  bedrock/stone seabed to ~y 18, water to 63, air, islands at y 86..137. No generation exceptions. The author
  looked at the rest in the dev client: "everything seems to be working".
- **Things to look at in-game (not verified visually yet):** the coast band's shape (era terrain higher than
  the shoreline is cut along a quadratic rise; era terrain lower — an era's own sea at the layout coast — is
  filled up to a sandbar at the shoreline); Legacy Console shows ocean-biome patches on land inside its box
  (x -42340..-41560 at z 80976 for seed 20260829), possibly its own height-based biome injection interacting
  with the translated border — decide after seeing it; finite levels' surfaces after the coast clamp; era
  carvers run over the seabed fill in coastal era chunks (small underwater cave pockets seen in a Skylands
  column at y 12..14) — harmless, could be masked by skipping carvers below the seabed.
- **Lesson from this session:** chunks generated in an earlier run stay on disk — the chunk pyramid pre-generates
  a radius around every loaded chunk — so a fix can look intermittent when re-probed on a used world. Verify
  on fresh chunks or a fresh world.

**Test recipe (headless):** `run/server/` holds `eula.txt` and `server.properties` (offline mode, port 25599,
RCON on 25598 password `cottest`, view distance 6). It points at `level-name=atlas`,
`level-type=continentsoftime:continents_of_time` (the multi-era world; the single-era world is still in
`run/server/world`, switch `level-name`/`level-type` back to use it). `./gradlew runServer`, then drive it with
a ten-line RCON client (the protocol is trivial: auth packet type 3, command type 2). The most useful probe is
a cross-section script: `forceload add x0 z x1 z` (one chunk row; the command refuses more than 256 chunks),
wait until `execute if block` stops answering "not loaded", then per column scan `execute if block x y z
minecraft:air` downward for the top block, test `minecraft:water` for the water surface, and `execute if biome
x y z <id or #tag>` for the biome. Seat coordinates for a seed are in the server log's seat table and in the
harness's "crossings" lines. The `run/` directory is gitignored; reuse the worlds there, but remember that
previously generated chunks stay as they were — verify fixes on fresh chunks (delete the world if needed; it
is ~10 MB).

**Layout harness:** `./gradlew layoutTest` (`-Pseeds=1,2,3` to choose seeds; ~16 s per seed) — assertions plus
`build/layout/<seed>.png` (whole atlas, 32 blocks/pixel) and `<seed>-home.png` (home continent, 8 blocks/pixel).

**Dev client:** `./gradlew runClient` (run dir `run/client`, Fabric API and Moderner Beta already on the
classpath; offline "Player###" account). `./gradlew runClient --args="--quickPlaySingleplayer single_era_beta"`
opens straight into a copy of the verified beta world (`run/client/saves/single_era_beta`, copied from the
server world, so recreate it from `run/server/world` if it is ever missing). Verified 2026-08-29: the client
loads it and renders beta 1.7.3 terrain, and the author confirmed **Continents of Time appears in the Create
World → World Type list** and works from there.

## Next work, in order

1. **Spawn on the home continent** and a first *look* from a client. The origin is guaranteed land, so vanilla
   spawn search should already succeed; verify, then take screenshots of two coasts (modern → sea, and a
   Moderner Beta era → sea), the Classic island at its seat, and the Legacy Console coast; fix what looks
   wrong (see "Things to look at in-game" above). A `/cot seat <era>` teleport command would make this and all
   future looking cheap — small, worth doing first.
2. **Per-continent visuals** — Moderner Beta's old fog/sky/grass colouring is a per-level flag that the atlas
   turns off everywhere; a composite climate sampler would bring it back per continent. Lower priority.
3. **1.20.1 backport** — after the mod is complete (author's call).

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
