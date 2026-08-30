# Continents of Time

Every era of Minecraft terrain generation, in one world.

Each historical generator — from the earliest days to modern — gets its own **continent**, and big oceans
separate the eras. Sail far enough and you make landfall in another age of the game: the terrain changes under
your feet, the horizon starts rolling the way it used to, and the world you knew is a boat trip behind you.

**Status: early development.** The continent layout, the oceans between continents and the coasts are built
and verified on a server (every era seated from the seed, modern ocean between, terrain eased under the water at
each coast, finite and bordered eras relocated to their seats), and players spawn on the modern continent.
Everything is generated server-side, so **a vanilla client can join a server running the mod**; installing the
mod on the client too is optional and adds per-continent visuals (an era's own grass, foliage and sky colouring,
and its old fog, where Moderner Beta has them). Nothing is playable in the intended sense yet.

## Plans

- A Fabric mod for **Minecraft 26.2** first; a **1.20.1** backport follows once the mod is complete.
- One master world type that partitions the world into era-continents and blends the seams beneath the oceans.
- **Requires [Moderner Beta](https://modrinth.com/mod/moderner-beta)** (MIT), which provides every historical
  generator; this mod is the atlas around them and contains only its own code. Every world-generation type it
  offers gets a continent, plus the game's modern generator.
- Continent size is configurable (`config/continentsoftime.json`; default up to 10,000 × 10,000 blocks —
  continents are coastline-shaped and usually smaller).
- Part of a larger modpack built around **VirtualMinecraft**, a computers mod by the same author.

## AI disclosure

Like [VirtualMinecraft](https://github.com/BwahFox/VirtualMinecraft) before it, this mod is developed
**collaboratively between a human and an AI**: design, direction and testing by
[BwahFox](https://github.com/BwahFox); most code and documentation written by Claude (Anthropic's AI) under
that direction. It will not be published on Modrinth, whose policy restricts AI-generated content. This
notice exists so you can decide for yourself how you feel about that.

## License

[LGPL-3.0-or-later](COPYING.LESSER). Use it, ship it in packs, learn from it — derivatives stay open.
