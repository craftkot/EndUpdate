# Living Chorus — Minecraft 26.3 / NeoForge

An isolated subproject in craftkot/EndUpdate (does not affect the main Forge 1.20.1 EndUpdate mod).

## New: Stage 1 — hidden mimic markers

- In **The End**, about 5% of vanilla `minecraft:chorus_flower` blocks near players become silently marked as hidden mimics.
- The random choice is deterministic per world seed + position, not rerolled every scan.
- Hidden markers persist per dimension using `SavedData`; vanilla blockstate, collision, texture, F3+B, selection outline and entity radar remain unchanged.
- Vanilla chorus continues growing normally; one flower/branch may be infected while the others are normal.
- No active disguise entity, animation or bite is spawned yet. This is groundwork for the future activated mob/animation.
- Nearby flowers are inspected in a bounded area once every 10 seconds per player. No forced chunk loading.
- Breaking a flower removes its infection marker. If another flower grows in the same location it may later be infected again.

### Operator debugging

Look directly at a **vanilla chorus flower** from within 6 blocks:

- `/livingchorus inspect` — is this flower marked?
- `/livingchorus mark` — force this flower to be infected (still looks vanilla).
- `/livingchorus clear` — force this location to remain normal.
- `/livingchorus stats` — count stored infections in the current dimension.

Use commands with cheats/OP enabled. No messages or visual effects appear in normal play.

## Existing prototype

The old `livingchorus:living_chorus` custom block with a passive proximity-damage tick remains available for compatibility. It is **not** the hidden mimic and is not the intended final implementation.

## Build

Java 25 and Gradle 9.2.1 with NeoForge 26.3.0.90; GitHub Actions **Build Living Chorus 26.3** uploads a JAR artifact.
