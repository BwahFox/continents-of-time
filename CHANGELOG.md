# Changelog

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
