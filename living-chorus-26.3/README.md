# Living Chorus 0.9.0 — awake-only vanilla mesh masking and aiming jaws

Status: early NeoForge 26.3 client prototype, compile-verified only.

## What was added
- `ClientMimicBlockMask` wraps existing vanilla baked state models for chorus flower/plant, without replacing block IDs, block states, drops, collisions or creating a dormant mimic entity.
- Server START for an infected flower hides its original baked flower and up to four **unbranched terminal** chorus stem blocks on that client, while a textured mobile mesh is drawn.
- Chunk sections touched by a mask are explicitly scheduled for rebuild. STOP, timeout and dimension change remove the mask and rebuild the original vanilla visuals.
- Junctions are never hidden. Other flowers and shared chorus stems remain intact; a long/shared trunk might therefore still be visible while the mobile branch moves.
- The mouth yaw turns toward the target instead of always pointing north.
- All existing gameplay rules retained: Endermites spawn rarely near chorus forests in The End, player bites require ender pearls in inventory, Endermite consumption bends low, living/dead flower infection, sleeping projectile instant kill.

## Still needs real-client visual testing
- The in-game renderer in v0.8 uses four simple model parts with provisional UVs; they are *not* a final translation of the V17 demonstration model.
- The original stem texture and flower UV faces need visual inspection; active 3D model alignment, light, block/mesh transition must be checked.
- The mesh has no active moving hitbox/health, and masking only handles terminal parts to avoid hiding neighboring normal branches.
- The existing portal particles remain as a fallback. Multiplayer packet delivery and actual client frame testing not yet confirmed.

GitHub Actions compilation confirms syntactic/API compatibility only; it does not prove correct animation or appearance.
