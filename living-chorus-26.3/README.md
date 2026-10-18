# Living Chorus — Minecraft 26.3 / NeoForge (experimental)

Separate project inside `craftkot/EndUpdate`; the Forge 1.20.1 EndUpdate code is not changed.

## v0.6.0 — End endermites + autonomous feeding

- Endermites now get an **additional rare spawn opportunity in The End** on exposed End stone near chorus plants. This mod uses vanilla Endermite entities, not replacements.
- About once every 30 seconds per eligible nearby player the mod tries a spawning roll; only 1 of 3 rolls proceeds, and 6 local candidate locations are sampled.
- Candidates must be in **already loaded chunks**, 18–36 blocks diagonally away from the player, on solid End stone with air above and some nearby chorus vegetation.
- A local cap of 6 Endermites within about 56 horizontal blocks prevents the new spawn system from filling the End.
- No spawning in peaceful difficulty; spectators do not cause spawning. Endermites may still despawn normally.
- Newly infected chorus flowers remain vanilla blocks when sleeping. Living and dead flowers can both carry infection and may be instantly destroyed by projectiles while sleeping.
- Sleeping/active state uses the infection marker associated with the exact flower, not with the entire plant.
- **Autonomous endermite feeding:** every 2 seconds, a small round-robin budget of at most 128 stored infected flowers in loaded chunks is checked for nearby Endermites. A flower can consume a mite without any player standing within attack distance.
- Endermite feeding uses `eat_endermite_low` and differs from `bite_player_high`, which is only eligible for players carrying ender pearls. These are separate server behaviors; procedural meshes and their visible animations are still not implemented.
- Every infected flower has a deterministic `MimicProfile` for future variable branch morphology.
- Current V17 Blockbench rig is a demonstration and not the final shape.

### Smoke test
1. Open The End in Survival; wait around chorus forests. Endermites should occasionally spawn on End stone away from you (a rare event, not guaranteed every minute).
2. `/livingchorus mark` on a vanilla chorus flower.
3. Place/spawn an Endermite nearby or allow one to wander there; it should get eaten after the independent charge time even without a player close to the infected flower.
4. Check `/livingchorus stats`, `/livingchorus profile`, and `/livingchorus provoke` for debugging.

### Not yet implemented
Custom spawned mobs are not synchronized to V17 animated models. No procedural client renderer/animated collision meshes yet. Gameplay tests still required after Gradle CI compilation.
