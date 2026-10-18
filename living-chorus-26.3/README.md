# Living Chorus — NeoForge 26.3 (experimental)

Standalone subproject of craftkot/EndUpdate, kept isolated from Forge 1.20.1.

## Stage 2 — wake-up, timed attack, and recovery (v0.3.0)

- In The End, ~5% of vanilla `minecraft:chorus_flower` blocks can be silently marked infected. The world block, tooltip, selection, F3+B behavior and vanilla rendering are **unchanged while idle**.
- Marks persist in server world SavedData. Only flowers are marked; one flower on an otherwise normal chorus plant can be a mimic.
- Server checks nearby marked flowers twice a second, in a bounded area around survival players. Every 10 seconds the server discovers new flowers. No forced chunk loading, no fake mob entity, no constant damage.
- A reachable flower within 4 blocks may **wake up**: a chorus sound and portal particles telegraph the attack. After 1.5 seconds it bites for 3 damage if the target is still within 2.8 blocks, alive and unobstructed. Attacks can miss, and attacking flowers have a 6.5–11.5 second cooldown.
- When multiple reachable flowers are infected, one is selected randomly. Additional per-player cooldown avoids simultaneous attacks.
- Removing or clearing the flower during the wind-up cancels the bite. Creative/spectator players are immune.
- **Not yet implemented:** visible bending of stems, petal opening, full V8 animated rig, body growth/shape changes, actual invisible branch swap during attack. Those are separate milestones. All animations currently remain in Blockbench only.
- The legacy `livingchorus:living_chorus` damaging custom block remains for backwards compatibility; it is *not* the hidden mimic.

### Operator-only test commands

Look at a **vanilla chorus flower**, not the stem, from within six blocks:

- `/livingchorus mark` — mark the targeted flower infected.
- `/livingchorus inspect` — check whether the flower is infected.
- `/livingchorus provoke` — force wake-up and an attempted bite, ignoring cooldown.
- `/livingchorus clear` — clear and prevent automatic rediscovery at this position.
- `/livingchorus stats` — count of stored infected flowers and current charging attacks.

For testing, switch to survival after marking; observe that nearby flowers wake, wind up and can miss. Normal play has no admin indicators.

## Build
Requires Java 25, Gradle 9.2.1 and NeoForge 26.3.0.90. GitHub Actions workflow **Build Living Chorus 26.3** builds and uploads the JAR.
