# Living Matter 0.2.1 — Living Blocks / Живая материя

**Standalone NeoForge mod** for Minecraft **26.3**, NeoForge **26.3.0.90**, Java 25.
Its mod ID is `livingmatter`, with its own assets, Java sources and Gradle build. **It does not need Living Chorus or EndUpdate installed.**

## Core idea

Ordinary world blocks conceal autonomous predators. Their dormant block state remains completely vanilla; infection is a server-side saved marker. They suddenly reveal a material-specific body in adjacent *empty* space, strike after a short visible wind-up, then vanish back into the environment. No original block is transformed or deleted for the animation. All visible reveal blocks expire automatically after their three keyframes.

**This is not a chorus port**. It has its own material classifier, save data, attack logic, non-destructive temporary bodies and Blockbench prototypes.

## Seven distinct mimic types

| Material | Manifestation | Behavior on a confirmed hit |
|---|---|---|
| Stone, cobble, bricks, terracotta | Multiple stone pieces combine into an open jaw | 8 damage plus up to 6 more from adjacent stone; strong knockback |
| Logs, planks, wood | Jagged living root claws | 3 damage; strong slowness |
| Sand, dirt, gravel, mud | Sand jaw around a dark sinkhole | 4 damage; slowness and mining fatigue |
| Ore and metal blocks | Crystal-like metallic stinger | 6 damage; weakness |
| Leaves, vines, moss | Clinging thorn tendrils | 2 damage; slowness |
| Fungi, sculk, mycelium | Inflating spore pod | 3 damage; poison and weakness |
| Everything else eligible | Dark shapeshifting jaw with an eye | 5 damage; short weakness |

**Important:** "Any block" refers to blocks safe for hidden infection, not literally *every* Minecraft block. Fluids, unbreakable blocks, tile/block entities (including chests, furnaces and signs), and technical blocks are excluded, because overwriting or tampering with them risks world corruption. Modded blocks without a block entity can use the default category.

## Infections, appearance & safety

- A deterministic rare infection seed is assigned near players (roughly 0.09% per eligible coordinate). The same location does not reroll randomly each scan.
- The infection survives world saves and reloads and works in vanilla dimensions.
- A successful strike can propagate infection to one directly adjacent compatible block (50% chance), with a 12-infected-position cap in an 8-block neighbourhood. Logs can infect leaves, stone can infect ore, spores can infect leaves.
- Player proximity or the *first attempt* to mine an infected block can wake it; a second mining attempt during cooldown can defeat it and obtain normal original loot.
- Only the original block's existing adjacent air spaces receive the temporary jaw/roots; nearby original blocks are not destroyed or modified.
- No forced chunk loading or persistent extra hostile entities.
- Creative and Spectator players cannot be attacked automatically.
- Server authority handles damage/status effects only on the delayed hit frame and only within reach and unobstructed.
- Multiplayer is supported architecturally; authoritative server-world interactions.
- Optional active shape variants animate through 3 discrete block-state keyframes, not smooth skeletal animation.

## Commands (operators)

Look at any eligible block from within 6 blocks.

- `/livingmatter infect` — infect target block, no visible change yet
- `/livingmatter inspect` — show infected/clean and behavior classification
- `/livingmatter awaken` — force a reveal animation targeting yourself (can be used in Creative for visual tests; Creative will not receive damage)
- `/livingmatter spread` — force propagation into one compatible adjacent block from the targeted infected source
- `/livingmatter cure` — remove infection and make the coordinate safe
- `/livingmatter stats` — display infected block count

## Building

```bash
cd living-matter-26.3
gradle --no-daemon build
```

Expected result: `build/libs/livingmatter-0.1.0.jar`. GitHub Actions builds this branch automatically and uploads the compiled JAR.

**Blockbench projects** are in `blockbench/*.bbmodel`, one for each behavior family, with editable prototype geometry. The Minecraft blockstate models in `src/main/resources/assets/livingmatter/models/block` contain three corresponding attack poses for each. The Blockbench files are editable *prototypes*, not compiled into runtime animations. For a future smooth articulated version, block-entity or entity rendering would be required.

## Limitations and next checks

- The automatic classifier is heuristic; unusual modded blocks will use the fallback animation, not their exact textures.
- In the current MVP, stone material pieces appear in adjacent air space; original stone blocks are **not physically extracted and merged**.
- Need in-world testing of survival damage, mine-wake, all seven animation sequences, safe temporary block cleanup on chunk save/reload, latency and multiplayer.
- All visual models use curated vanilla material textures rather than copying the source block's exact texture.
- Do not co-install with incompatible older versions unless tested.

## Version check and walking test (0.2.1)

Remove ALL previous `livingmatter-*.jar` and `LivingMatter_*.jar` copies from the active instance's `mods` directory first. Install just one copy of `livingmatter-0.2.1.jar` for both client and server. **Restart the game**, not merely rejoin the world. Test `/livingmatter version` and confirm it prints `0.2.1-walkfix-diagnostic` — the old 0.1.0 did not have this command.

`/livingmatter walktest`: look at an isolated, pre-existing 2x2 group of stone blocks standing on dirt/grass, with air on all lateral faces and the top. The command will infect the group, then activate it. `/livingmatter walk` auto-infects the same kind of body if the targeted block is not infected; already infected bodies receive detailed failure reasons. Avoid structures, same-material floors, and brain-occupied blocks.

**Known limitation:** The walker remains an experimental 3–8 block creature. Normal terrain connected to it is deliberately not eligible for detachment.
