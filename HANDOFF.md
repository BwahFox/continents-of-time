# Project state — read this first

*(Authoritative for state; update before ending any session that changed anything. This file is PUBLIC — see
CLAUDE.md's hygiene rule. Maintenance: HANDOFF first when wrapping up, then commit + push to both remotes.)*

**What this is:** a Fabric mod that puts every era of Minecraft terrain generation into one world — each
historical generator is its own **continent**, separated by big oceans. Sail far enough and you make landfall
in another age of the game. Part of a larger modpack built around VirtualMinecraft (same author).

**Version targets, decided 2026-08-29:** developed for **26.2 and 1.20.1 in parallel from day one** — the
modpack targets 1.20.1, and dual-targeting from the start is what keeps the backport from ever becoming a
mountain. The multi-version build strategy (Stonecutter vs. branches) is an early decision, not yet made.

**Reference, not source:** Moderner Beta (LGPL) is prior art for running historical generators on modern
Minecraft. Read it to learn; write our own. Reference checkouts live outside this repo.

## State as of 2026-08-29

**Pre-development.** The repo holds README (with AI disclosure), LGPL-3.0 license texts, .gitignore, and this
scaffolding. No code yet. Remotes: `origin` (github.com/BwahFox/continents-of-time, public) and `server` (LAN
backup). No Gradle project, no world, nothing to run.

## Next work, in order

1. **The design conversation (the user answers, the agent asks).** Before any code: which eras make the
   continent list (candidates: infdev, alpha, beta 1.7.3, release-era pre-1.18, modern 1.18+ — and whether
   variants like PE/large-biomes earn a seat)? Roughly how large is a continent, and how wide an ocean? Is
   the modern era the "home" continent players spawn on? Write the answers into this file as the spec.
2. **Build scaffolding.** Fabric mod template; then the dual-version strategy — evaluate
   [Stonecutter](https://stonecutter.kikugie.dev/) (one codebase, version-switched builds, used widely for
   multi-version Fabric mods) against plain branches. Decide, document the decision here, wire CI-free local
   builds for both targets. A `./gradlew build` that produces jars for 26.2 and 1.20.1 ends this item.
3. **Study pass on Moderner Beta** (reference checkout outside the repo): how it hosts one historical
   generator on a modern chunk-generator interface, per target version. Output: notes in ARCHITECTURE.md on
   what our per-era generator interface must look like.
4. **First playable: one era, whole world.** A world type that generates a single chosen historical era
   everywhere, on both game versions. This proves the per-era generator plumbing before any atlas exists.
5. **The atlas.** The master generator: continent layout from the seed, per-region delegation to era
   generators, oceans between, seam blending under the water. This is the mod's heart and its hardest code —
   expect it to need the strongest model tier and its own harness (continent-map determinism, border checks,
   no-cliff assertions at seams).

## Parked (the user's own calls, 2026-08-29 — do not pull forward)

- **Era-emulation gameplay** (older continents disable newer mechanics) — "might be impossible, I don't
  know"; revisit only when the atlas works.
- **Legacy Console styling** for some continents (re-console / Legacy4J) — rides with era-emulation.
- Seasons, ambient sound, CTOV, Distant Horizons/Voxy, controller support — those are **modpack inclusions**,
  not this mod's code. The CTOV × VirtualMinecraft store compat hook belongs to pack-glue work, not here.
