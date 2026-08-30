# Changelog

## 1.0.2 — 2026-08-30

- **C2ME's aquifer optimisation no longer fails the Pocket and Bedrock continents.** Those eras fork their
  aquifer random from Moderner Beta's own Bedrock random source, a type C2ME's `optimizeAquifer` (on by default)
  does not recognise, so every one of their chunks failed to generate under C2ME. The aquifer — and only the
  aquifer — of those eras now uses vanilla's legacy random, seeded from the era's own; terrain, caves and surface
  are untouched. Found on a full-radius pregeneration the moment it reached a Bedrock continent.

## 1.0.1 — 2026-08-30

- **Threaded world generation (C2ME) no longer corrupts chunks.** Two races, both in how the atlas drives many
  generators through objects vanilla assumes belong to one: each era's structure-placement state is now
  completed before any worker can use it (vanilla pre-fills its own on the server thread; the atlas's per-era
  states were filled lazily by whichever workers arrived first — "Failed to load chunk … ArrayIndexOutOfBounds"),
  and the surface step runs one chunk at a time because Moderner Beta keeps its per-chunk surface context on the
  dimension's single surface system (two eras surfacing at once overwrote each other — "… rand is null").
  Pregeneration with C2ME's threaded worldgen is now about three times faster than single-threaded generation on
  an 8-core machine, with no failed chunks.
- **Requires Fabric Loader 0.19.5 or newer** on both versions (it always did: the mod is built against Loader
  0.19.5's Mixin, and 0.19.3's bundled MixinExtras cannot read its `@Redirect`s — Moderner Beta's startup died
  with "ArrayList cannot be cast to AnnotationNode"). The requirement is now declared, so the loader says so
  instead of crashing.

## 1.0.0 — 2026-08-30

First release, for Minecraft 26.2 and 1.20.1 (one jar each, built from one source tree).

- **The atlas:** every world-generation type Moderner Beta offers, plus the game's modern generator, seated as
  continents on an infinite hex grid — the default roster of 26 in timeline order outward from the modern home,
  then endless repeats with fresh coastlines. Noise-shaped coastlines inside a configurable maximum size
  (default 10,000 blocks), oceans of a configurable minimum width (default 2,000) between them.
- **Oceans and seams:** the modern ocean between continents, a seabed that eases every era's terrain down to the
  shoreline, Skylands lifted over open sea, and the finite Classic/Indev levels and Legacy Console's bordered
  world moved to their seats.
- **No-oceans option:** continents meet at hard seams instead of open water.
- **Era-accurate structures and cave biomes** (default on): each continent gets only the structures and
  underground biomes its version had.
- **Customize screen:** the Create World screen's Customize button edits the roster, sizes and switches; the
  `config/continentsoftime.json` file sets the defaults for new worlds.
- **`/cot` command:** `seats`, `where`, `seat <era>`, `structures <era>`.
- **Vanilla clients can join** servers running the mod; the optional client half adds per-continent grass,
  foliage, sky and fog where Moderner Beta has them.
- Verified compatible with ChoiceTheorem's Overhauled Village (its villages follow vanilla's dating).
