# Continents of Time

Every era of Minecraft terrain generation, in one world.

Each historical generator — from Classic 0.0.14a to today's — gets its own **continent**, and big oceans separate
the eras. Sail far enough and you make landfall in another age of the game: the terrain changes under your feet,
the horizon starts rolling the way it used to, and the world you knew is a boat trip behind you. The roster is
the timeline: continents are seated outward from the modern home in chronological order, so a voyage is time
travel.

Built around [Moderner Beta](https://modrinth.com/mod/moderner-beta) (MIT), which provides every historical
generator; this mod is the atlas around them.

## Requirements

| | Minecraft 26.2 | Minecraft 1.20.1 |
|---|---|---|
| Jar | `continentsoftime-<version>+26.2.jar` | `continentsoftime-<version>+1.20.1.jar` |
| Java | 25 | 17 or newer |
| Fabric Loader | ≥ 0.19.5 | ≥ 0.19.5 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | any | any |
| [Moderner Beta](https://modrinth.com/mod/moderner-beta) | 5.0.0-alpha.3 **+26.2** | 5.0.0-alpha.3 **+1.20.1** |

Install the jar for your Minecraft version together with Fabric API and Moderner Beta's build *for the same
version*. Moderner Beta is not bundled — packs and players install it themselves.

## Playing

1. Create a new world and pick **World Type: Continents of Time**.
2. Optionally press **Customize** to change the era roster (which eras are seated and in what order), the maximum
   continent size, the ocean width, and the two switches below. Values are baked into the world when it is
   created; the config file only sets the defaults for new worlds.
3. You spawn on the modern continent. Everything else is a sail away: `/cot seats` (operator) lists where every
   era's continent is, `/cot where` tells you what you are standing on, and `/cot seat <era>` teleports you to
   an era's continent.

**The default roster (26 continents), in timeline order:** Classic 0.0.14a_08, Classic 0.30, Indev, Infdev 227,
Infdev 325, Infdev 415, Infdev 420, Infdev 611, Alpha 1.1.2_01, Alpha (winter mode), Beta 1.1_02, Beta 1.7.3,
Beta 1.8.1, Beta 1.9-pre3, 1.0.0, 1.1, 1.2.5, 1.6.4, 1.12.2, 1.17.1, Modern, Pocket Edition, Bedrock 1.2,
Bedrock 1.17, Legacy Console (large), Skylands. Any other Moderner Beta preset (large biomes, amplified, ...) can be
seated too. Continents are shaped by noise into coastlines and are up to 10,000 × 10,000 blocks by default,
with at least 2,000 blocks of ocean between them. The world is infinite: past the first pass of every era,
continents keep coming for ever (eras repeat with new coastlines; the finite Classic and Indev levels exist once).

**Options** (Customize screen or `config/continentsoftime.json`):

- `maxContinentSize` (blocks, default 10000) and `oceanWidth` (blocks, default 2000).
- `oceans: false` — **no oceans**: no open water at all; each gap belongs to the nearest era and continents meet
  at hard seams.
- `eraAccurate` (default on) — structures and cave biomes that did not exist in an era's version stay off its
  continent: no villages on Alpha, no ocean monuments before 1.8, no lush caves under 1.17, no sulfur caves
  before 26.2. `/cot structures <era>` shows what an era can place.
- `eras` — the roster, in order. Ids are Moderner Beta settings presets (`moderner_beta:beta`, ...) or
  `minecraft:overworld` for the modern generator, which is also the home continent whenever it is seated.

## Multiplayer and the client

All generation is server-side and the biomes are data-driven, so **a vanilla client can join a server running
this mod**. Installing the mod on the client is optional: with it, each continent also gets the visuals its era
had where Moderner Beta provides them — Beta's climate-based grass and foliage tints, its sky colour and old fog
on the Beta and early-release continents; vanilla colours on the modern continent and the open sea.

## Known limitations

- `/locate` searches the whole world's structure set, so on an era-accurate world it can point at a spot on an
  old continent where nothing is placed. `/cot structures <era>` is the reliable answer.
- Mods that add their own structure sets are allowed on every continent (the atlas only dates vanilla's sets).
  Mods that inject into vanilla's sets — CTOV puts its villages into `minecraft:villages` — follow the vanilla
  dating and work as intended.
- The atlas ocean sits at sea level 63 while the modern continent's own oceans top out one block lower, so a
  one-block step can show where the two meet. Legacy Console's continent can show ocean-biome patches on land
  (its own height-based biome rules). Both are cosmetic.
- The Far Lands are Moderner Beta's per-preset business; a continent seated inside them looks accordingly.

## Building

`./gradlew build` builds both jars (`versions/26.2/build/libs`, `versions/1.20.1/build/libs`) from one source
tree with Stonecutter, using a single Java 25 JDK. `./gradlew layoutTest climateTest timelineTest` runs the
headless harnesses for both versions. How it works: [ARCHITECTURE.md](ARCHITECTURE.md); project state and
history: [HANDOFF.md](HANDOFF.md); changes: [CHANGELOG.md](CHANGELOG.md).

## AI disclosure

Like [VirtualMinecraft](https://github.com/BwahFox/VirtualMinecraft) before it, this mod is developed
**collaboratively between a human and an AI**: design, direction and testing by
[BwahFox](https://github.com/BwahFox); most code and documentation written by Claude (Anthropic's AI) under
that direction. It will not be published on Modrinth, whose policy restricts AI-generated content. This
notice exists so you can decide for yourself how you feel about that.

## License

[LGPL-3.0-or-later](COPYING.LESSER). Use it, ship it in packs, learn from it — derivatives stay open. Moderner
Beta, which this mod depends on at runtime, is MIT and is not included here.
