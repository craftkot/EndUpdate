# Living Matter 0.3.0 — Хтоническая живая материя

Standalone NeoForge mod for **Minecraft 26.3 / NeoForge 26.3.0.90 / Java 25**, ID `livingmatter`. No dependency on EndUpdate or Living Chorus.

## New — Chthonic transformation (0.3.0)

**An infected colony can turn into a REAL hostile mob.** Its source is existing
infected blocks of the world, not a monster conjured from nowhere. The server
collects face-connected infected blocks (excluding the brain itself and any
container/block entity), removes the source blocks, and creates one hostile mob
with pathfinding, melee attacks, health, NBT persistence and a custom renderer.
Its model is assembled from exactly one voxel for each consumed source block:
torsos, an enormous opening mouth, claws and moving tentacles. Existing source
blocks are **consumed permanently**. No new textures are required.

| Consumed blocks | Creature | Characteristics |
| --- | --- | --- |
| 4–11 | Larva | Compact and relatively fast |
| 12–31 | Horror | Larger body, more limbs and jaw |
| 32–63 | Abomination | Heavy, tougher, more powerful |
| 64–96 | Colossus | Huge multi-limbed mass and giant mouth |

HP: `14 + blockCount * 0.95`. Damage: `2.5 + sqrt(blockCount)*1.1`.
Body dimensions increase with tier. Movement speed decreases moderately
with body mass; monsters pursue players using hostile mob AI.
The procedural in-game model deforms with an animated jaw and waving
limbs/tendrils. It is intentionally much less vanilla-looking than the
original simple moving group of cubes.

**Warning:** This feature physically removes infected source blocks from
the map to embody the monster, including blocks that used to be terrain.
This is intended horror gameplay. MAKE A BACKUP of your world before testing.
Containers and other block entities are never harvested. The living brain
is protected and remains planted in the original world location.

## Infection, brains and emergence

An existing solid vanilla block can become a hidden **brain** by command
`/livingmatter brain`. A brain adds up to roughly 1 connected infected world
block per second, growing at most 180 cells in a radius of 12. The changes to
infection are saved across world restarts. The brain does not place blocks;
it marks already existing neighboring blocks as infected.

Roughly once per minute, an infected brain with a viable group of at least
12 infected blocks may attempt to shed a creature, with a chance and local
monster population cap. Only loaded world blocks are traversed. Source terrain
must have an open area above it where the new creature can emerge.

## Commands (operator / creative mode)

Aim at a source block from within 6 blocks:

- `/livingmatter version` — expected `0.3.0-chthonic-mass`.
- `/livingmatter brain` — plant hidden brain in the targeted vanilla block.
- `/livingmatter brains` — number of growing brains and infected cells.
- `/livingmatter colony` — inspect nearby colony.
- `/livingmatter infect` — infect the targeted block directly.
- `/livingmatter mass` — inspect prospective creature mass (infected + test).
- `/livingmatter chthon` — consume a connected infected body and spawn its real chthonic mob (4–96 blocks).
- `/livingmatter chthontest` — explicit operator test: transform 4–96 contiguous **same-type** blocks into a monster without requiring infection beforehand. Does not create any extra world blocks.
- `/livingmatter cure` — clear infection from a target.
- `/livingmatter unbrain` — stop a brain's growth.

Legacy mechanics (including `/livingmatter leaf`, `awaken`, `walktest`)
remain available for separate tests but are not the new chthonic mob system.

**Example:** On a flat dirt/grass surface, build a connected mound of
**16, 40 or 80 deepslate blocks** (all touching by full faces), leave
6+ air blocks over the source group's center, aim at one of its blocks, run
`/livingmatter chthontest`. A real hostile entity should appear and start
hunting. Avoid running tests near valuable structures.

## Known limitations and next steps

- Current body is procedural voxel anatomy, not a fully skinned organic mesh;
  it remains made from the source blocks with chthonic shaping and movement.
- Collision/pathfinding and large-entity animation require in-game client QA.
- Not every source block state has an exact 1-to-1 sprite in the creature;
  dominant material drives its external body texture with darker mouth accents.
- Source terrain is intentionally not restored after the mob dies.
- Project is developed on `feature/living-matter-26-3`; never merge without
  explicit user permission.

## Building

```bash
cd living-matter-26.3
gradle --no-daemon build
```

JAR output: `build/libs/livingmatter-0.3.0.jar`.
Use just one installed version of Living Matter in the active Minecraft instance.
