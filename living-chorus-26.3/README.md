# Living Chorus 0.8.0 — first client-rendered attacks

Minecraft 26.3 / NeoForge — isolated from Forge 1.20.1 EndUpdate.

## New rendering milestone
- Server now sends `MimicPosePacket` START, UPDATE and STOP over a NeoForge play-to-client payload.
- Each packet identifies a single infected flower, its action, target location and its *packed* deterministic morphology. The world seed and lists of sleeping hidden mimics are never sent.
- Client creates render snapshots in `ExtractLevelRenderStateEvent`, then submits mesh parts via `SubmitCustomGeometryEvent` as required by 26.3 rendering.
- Stem: rigid 4×16×4 model cubes positioned and rotated along a Bezier joint chain. Length and UV are NEVER scaled; overlapping cubes hide joints for this first pass.
- Flower: starts as a solid 14×14×14 vanilla-textured cube; during attack the closed cube is replaced by four hinged jaw panels rather than extra overlay walls.
- Both living and dead flowers use *existing vanilla* `minecraft:textures/block/chorus_flower.png` or `chorus_flower_dead.png`; stems use `chorus_plant.png`. No newly drawn textures.
- Player attacks aim high, Endermite feeding aims low, and each infected flower has a separate profile.
- Rendering is only present while awake; hidden mimic markers still remain server-side and untouched vanilla blocks keep their collision, HUD, minimap and F3+B properties.

## Known limitations
- This is an **early textured mesh prototype**, not production-ready artwork. Block geometry and four-panel UVs still need in-game visual review, lighting integration and correction.
- Original vanilla blocks remain visible under the animation. A client-only mask for the active attacked flower/branch is future work.
- Preview portal particles from 0.7.0 are retained as a debugging fallback until in-game renderer is visually verified.
- The V17 BBModel is a reference animation rig, not the final model nor direct input to this runtime renderer.
- Runtime client start, multiplayer packet delivery, graphical output, actual damage and collision were not yet verified; GitHub Actions verifies compilation only.

## Test
Install NeoForge 26.3, enter The End in survival, target a flower with `/livingchorus mark`. Carry an ender pearl to test `bite_player_high`, or place an Endermite within range for the `eat_endermite_low` animation. With permissions you can invoke `/livingchorus provoke`.
