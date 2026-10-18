# Living Chorus 0.11.0 — pixel-aligned vanilla-face UV

Minecraft 26.3 / NeoForge. Standalone experimental project in craftkot/EndUpdate.
Sound design remains intentionally deferred.

## Exact vanilla sprite mapping
Earlier versions put a normal **entity-style box UV atlas** on an ordinary
16×16 vanilla *block* sprite. ModelPart normally lays out all six faces as a
large strip, causing repeated/cropped flowers and conspicuous white seams.

Version 0.11.0 bakes the six faces of every moving cuboid **separately**:
- Only the matching Direction face is enabled for each of the six coincident
  ModelPart cuboids. This creates six textured faces (not six overlapping
  rendered whole cubes).
- Each face sets a per-direction texOffs so that it samples the correct
  contiguous part of the original 16×16 `minecraft:chorus_flower.png`,
  `chorus_flower_dead.png` or `chorus_plant.png`, directly.
- Coordinates of a 14px flower are mapped 1:1 to pixel [1,15], preserving
  the existing outer 1px border. Each jaw panel inherits the same UV placement
  as its surface area on a closed flower.
- During an attack, four hinged plates unfold independently without changing
  geometry or stretching pixels. Closed state displays a single cube; teeth
  and central interior are only submitted when open.
- No new images, recolouring, custom texture atlas or sampled replacement
  block texture were added.

### Still pending
**In-game screenshot/client run required.** Code and CI cannot prove the
model's UV orientations are aesthetically correct. The model still uses
one 14px flower cube and simple jaw plates rather than full V17 procedural
petal geometry. During attacks only terminal blocks can be hidden safely;
complex shared-stem topology needs future work. Active collision/hitpoints,
animated full-tree variations, networking smoke tests and sound effects
are still open tasks.

Preserved: rare Endermite spawning in The End, autonomous prey feeding, high
player / low Endermite trajectories, living/dead flowers as infection anchors,
sleeping one-hit projectile defeat, world-light sampling and client sync.
