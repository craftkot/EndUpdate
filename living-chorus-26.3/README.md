# Living Chorus 0.13.0 — real infected block-based branches (NeoForge 26.3)

## User-reported bug fixed
The user's 50s Minecraft video showed the test structure in the OVERWORLD.
Prior event handlers were gated to The End, so manually infected flowers
could never attack Endermites in that test environment.

Now all **manually infected** chorus flowers run AI in ALL dimensions.
Random discovery/infection and extra Endermite natural spawns stay End-only.

## Flower-rooted infection
- Infected live/dead chorus flowers are anchors.
- Every 20 ticks infection progressively moves into CONNECTED vanilla
  chorus_plant blocks (saved in the `stems` field of MimicData SavedData).
- Once a contagious stem touches another vanilla chorus_flower it can
  infect that flower, giving it a new independent attacking branch.
- Neighboring disconnected chorus plants are not infected.
- Normal minecraft block IDs, collision and item drops remain unchanged.
- All stem markers are stored and restored; orphan loaded stems are pruned.

## Actual branch as appendage
- `MimicBranchPath.trace` starts at the flower, follows connected
  chorus_plant positions back to the first branching junction or base.
- Trace returns **actual block centers from fixed root to flower**, capped
  at 24 positions. Never mask a shared root junction.
- START/UPDATE packets carry the actual block path to clients. The renderer
  masks only its own moving stalk/flower during attacks, leaving adjacent
  healthy branches intact.
- The procedural IK solver chooses as many real chain joints as necessary
  for target distance, rotates the chosen chain, telescopes short full-size
  chorus segments (no stretched UV), then opens the jaw to swallow near
  the END of the attack.
- Player/high target and Endermite/low target remain separate.
- All manual `/livingchorus mark, inspect, provoke, debug` commands still
  work. /livingchorus debug now includes branch block count.

## Tests
Manual reproduction (including the Overworld):
1. Build connected vanilla chorus_plant stem with a living/dead flower.
2. Mark targeted flower using /livingchorus mark.
3. /livingchorus debug should list branch blocks and target range.
4. Summon an Endermite within 2–4 blocks, not in Peaceful.
5. Capture 5s video, send latest.log if invisible.
6. /livingchorus provoke while pointing at flower isolates animation from AI.
Run additional test in The End for natural infection/spawns.

CI compilation is NOT proof of correct in-game rendering.
Sounds are intentionally deferred as requested.
