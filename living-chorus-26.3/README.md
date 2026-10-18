# Living Chorus v0.17.0 — orthogonal chorus branch growth and normal-sized corners

Minecraft 26.3 · NeoForge 26.3.0.90

Feedback: Screenshot of an exaggerated thick, knotty tendril. The user wants
vanilla-looking right-angle bends: if the Endermite is below the flower,
the branch should first hover over it, then descend. If the prey is to the
right and forward, it should go right, then forward, then down.

## Changes
- Procedural attack path uses separately eased cardinal X then Z then Y legs,
  creating distinct 90-degree elbows instead of a long diagonal coil.
- Retains an initial smooth hinge rotation of the ACTUAL attached chorus
  blocks, with the first shared junction fixed.
- Approaches a lower target at the flower's initial elevation, extends
  horizontally over prey, then drops a perpendicular vertical stalk.
- Recovery closes jaws, retracts Y first, then Z, then X, and returns
  existing blocks to their uninfected visual arrangement.
- Original vanilla chorus_plant texture, 8px stalk width and 14px flower.
  Removed 10px joint collars; same 8px square joint only at REAL directional
  changes (>~35 degrees), not between every straight segment.
- Axis legs use separate full-size cubes at up to 0.88 block steps, never
  nonuniform geometric scaling or stretched UV; limit 96 extra pieces.
- Tall/distant Endermite attacks dynamically extend their charge time to
  let multiple axis stages unfold without visibly jumping.
- The server sends the SAME actual duration to the client in pose START
  and UPDATE packets, preserving synchronization for distant bites.
- Server consumes prey only if the actual jaw reaches its grounded target.
- 18-block horizontal / 24-block vertical hunt detection from 0.16 is
  preserved; manually infected live/dead flowers and spreading stems remain.

## Test
1. Spawn a marked flower above an Endermite in Minecraft 26.3.
2. Capture a side view: outward overhang should precede vertical descent.
3. Move the Endermite right/forward: observe a second square elbow before
   the drop. Inspect the branch width: it should not grow bulbous between
   straight elements.
4. Repeat with 10–20 blocks of vertical separation.
5. Collect latest.log and video if geometry clips or oscillates.

**CI compilation and runtime smoke checks don't establish that visual
angles, UV direction and contact timing look perfect.** Do not merge to main.
Sound work intentionally untouched.
