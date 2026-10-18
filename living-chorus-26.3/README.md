# Living Chorus 0.7.0 — procedural motion groundwork

Minecraft 26.3 · NeoForge. Standalone directory, do not merge without approval.

### Actual in-game step
- `MimicRig` calculates deterministic, articulated per-flower branch joints from `MimicProfile`, its vanilla flower anchor and the target's position.
- The head smoothly bows toward targets; the last portion of the movement snaps into a bite; recovery retracts toward its sleeping flower.
- Distinct aim heights: player chest/head at about 1.55 blocks and endermite at roughly half the creature's low body height.
- Segment number increases with reach to keep segments short. Future UVs will be applied to **rigid tiles**; no nonuniform stem or texture stretch.
- While charging, 1 vanilla portal particle per computed joint every 3 server ticks gives a temporary **visual motion preview**; the mouth indicates opening with reverse-portal particles. In sleep state there are no particles or entities.
- Spawn/feeding/infection mechanics v0.6 remain, including rare Endermite spawning in The End, player bait requiring ender pearls, and one-shot death of sleeping infected flowers.

### Not yet finished
This is **not the final textured procedural model**. Vanilla chorus blocks stay rendered while moving, and particle markers only show the computed moving rig. Rendering textured stems and four independently hinged jaw plates will require client-side geometry submission and attack-state synchronization, which is the next phase.

### Tests
With survival cheats enabled, look at a live or dead vanilla chorus flower, use `/livingchorus mark`, then summon an Endermite or carry an ender pearl and run `/livingchorus provoke`. Distinct high / low particle motions should appear; no particles while asleep.

GitHub Actions build compiles JAR; runtime client test is still required.
