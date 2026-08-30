# Continents of Time — agent entry point

**If the user says "Continue" (or anything like it) with no other context: FIRST Read `HANDOFF.md` in full,
then resume from its "Next work, in order" section. Do not ask what to do — the answer is in that file. Say in
one line what you are resuming.**

HANDOFF.md is the authoritative project state and next-work order; update it before ending any session that
changed anything (then commit + push to BOTH remotes: `origin` on GitHub and `server`, the LAN backup).
**Also Read `private/HANDOFF.md` if present** — it is a separate, private repo (the whole `private/` directory
is gitignored here) holding the personal context this public repo must never contain; update and push it (its
own private remotes) alongside this one at every wrap-up.

**THIS REPO IS PUBLIC.** Every file in it — HANDOFF included — is written for strangers from the first line:
the author is "BwahFox", never a first name; no schedules, hardware setups, home-network details, or personal
context in any tracked file. Personal context belongs in the agent's memory directory, which is not in git.
This rule exists because its absence cost a scramble on the sibling project; do not relearn it.

Standing rules (carried from VirtualMinecraft, details in memory):
- **AI disclosure stays in the README**; never publish to Modrinth (its AI policy) — GitHub releases only.
- Third-party mods and code are **reference-only**: read them (outside this repo), never copy or vendor them.
  Moderner Beta is the reference for era generators; the code here is original. LGPL-3.0.
- All GPU work on the RTX 5090 only, never the 4070. Keep disk use well under 200 GB.
- The user has a self-declared feature-creep habit: park ideas in HANDOFF's "Parked" section, finish the
  current item. Era-emulation gameplay and Legacy Console styling are ALREADY PARKED — do not pull them in.
- Model budget: default **Opus**; Fable is for work with no pattern to copy (novel noise/terrain math, thorny
  cross-version abstractions) — say why when recommending it.
- Verify before calling anything done; build the harness culture early (the sibling project's eight
  no-Minecraft-needed harnesses are the model to copy).
