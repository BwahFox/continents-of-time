# Project state — read this first

*(Authoritative for state; update before ending any session that changed anything. This file is PUBLIC — see
CLAUDE.md's hygiene rule. Maintenance: HANDOFF first when wrapping up, then commit + push to both remotes.)*

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

**Defaults the agent chose, pending the author's word** (change them in `Eras`/`ContinentsConfig` if not):
the **modern era is the home continent** (origin and spawn are on it); **oceans are at least 2,000 blocks**
wide; the Legacy Console seat uses the *large* preset; Skylands is seated (floating islands over open ocean);
finite eras (Classic, Indev) are small islands, since that is what those worlds were.

## State as of 2026-08-29 (session 2)

**Items 1–4 of the original plan are done; the atlas layout (item 5) is next.**

- Build: `./gradlew build` (Java 25, Loom 1.17, Fabric API 0.158.0+26.2, Moderner Beta 5.0.0-alpha.3+26.2 from
  the Modrinth maven) produces `build/libs/continentsoftime-0.1.0.jar`. No Stonecutter yet (26.2 only for now).
- Code: `dev.continentsoftime.atlas` — `AtlasChunkGenerator`, `AtlasBiomeSource`, `HostedEra`, `AtlasSettings`,
  `Eras`, `layout.Layout` (only `single()` so far); `config.ContinentsConfig`. Data: two world presets —
  `continentsoftime:continents_of_time` (in the world-type list; roster and sizes from config) and
  `continentsoftime:single_era` (one era everywhere; not listed; for verification).
- **Verified 2026-08-29:** dedicated server on `level-type=continentsoftime:single_era` (seed 20260829) starts,
  logs `minecraft:overworld hosts 1 era(s): [moderner_beta:beta]`, prepares spawn in 3 s; `/locate biome`
  finds `moderner_beta:beta_forest/desert/plains` near spawn and finds no vanilla `plains`/`forest`. A control
  start on Moderner Beta's own preset shows the same two startup warnings (`moderner_beta:blocksource` empty,
  "Empty height range"), so they are its normal noise, not ours.
- Not yet done in-game: nobody has *looked* at it from a client yet; the multi-era preset currently generates
  era 0 (Classic 0.0.14a_08) everywhere because the layout is `single()`.

**Test recipe (headless):** `run/server/` holds `eula.txt` and `server.properties` (offline mode, port 25599,
RCON on 25598 password `cottest`, view distance 6). `./gradlew runServer`, then drive it with a ten-line RCON
client (the protocol is trivial: auth packet type 3, command type 2) — `locate biome`, `forceload`, `stop`.
The `run/` directory is gitignored; reuse the world there rather than generating fresh ones.

## Next work, in order

1. **The atlas layout** — the mod's heart; **recommend the strongest model tier** for it: novel noise/geometry
   with no pattern in this repo to copy, and a wrong shape decision is expensive to redo. Deliverables:
   a `Layout` built from the seed that (a) seats every roster era exactly once, (b) puts the home era's
   continent around the origin, (c) shapes each continent with domain-warped noise inside a box no larger
   than `maxContinentSize`, (d) keeps at least `oceanWidth` of water between any two continents, and (e) is
   cheap per column (cache per chunk). **Harness first** (`./gradlew layoutTest`, a JavaExec in the test
   source set, no Minecraft): determinism across runs, max-extent and min-gap assertions over sampled
   columns, every era present, origin on the home continent, an ASCII/PNG map dump for eyeballing. Then plug
   it into `AtlasBiomeSource.init(seed)`.
2. **Oceans and seams.** Between continents the atlas must generate open ocean itself (vanilla ocean/deep-ocean
   biomes; ocean floor, water to sea level), and at each coast pull the era's terrain down under the water with
   a falloff by distance-to-coast, so no era ever ends in a cliff. Also `createBiomes` for multi-era worlds
   must fill from the atlas biome source (today it delegates, which is right only for one era). Finite eras
   need no clipping. Skylands: ocean below, islands above.
3. **Spawn on the home continent** and a first *look* from a client (screenshots of two coasts).
4. **Per-continent visuals** — Moderner Beta's old fog/sky/grass colouring is a per-level flag that the atlas
   turns off everywhere; a composite climate sampler would bring it back per continent. Lower priority.
5. **1.20.1 backport** — after the mod is complete (author's call).

## Parked (the author's own calls, 2026-08-29 — do not pull forward)

- **Era-emulation gameplay** (older continents disable newer mechanics) — "might be impossible, I don't
  know"; revisit only when the atlas works.
- **Legacy Console styling** for some continents (re-console / Legacy4J) — rides with era-emulation.
- Seasons, ambient sound, CTOV, Distant Horizons/Voxy, controller support — those are **modpack inclusions**,
  not this mod's code. The CTOV × VirtualMinecraft store compat hook belongs to pack-glue work, not here.
- A config **screen** (the file exists; a GUI does not) — after the layout works.
