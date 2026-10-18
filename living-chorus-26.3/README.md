# Living Chorus 0.10.0 — articulated jaws and world lighting

Experimental port to Minecraft 26.3 NeoForge, living-chorus-26.3 only. No changes to EndUpdate's Forge 1.20.1 main game.

- The active flower is still reconstructed from original *vanilla* chorus plant, flower and dead flower textures; asleep vanilla blocks are untouched.
- NEW: head now closes into a single 14px cube. Opening changes over to four strictly non-overlapping cuboids: top, bottom, left and right. Jaw rotations have pivots at the **outside edges** of the flower, not in its center. Left/right yaw open around the Y axis, not Z.
- Core and small internal teeth appear only when the mouth is open. These details do not remain visible in idle/sleep and are not separate facade overlay walls.
- Rigid branch segments are 10px long (matching roughly 0.64-block Bezier sample spacing) instead of 16px segments piled over each other. No mesh scaling / UV stretching.
- Per-segment and per-flower world packed light is sampled during the NeoForge extraction stage at current positions and used in submit stage. **No fullbright**.
- Previous net-sync, flower masking, different aim heights for players/Endermites, rare End spawns, autonomous feeding and sleeping projectile one-hit destruction retained.

## Limitations
- Existing vanilla 16x16 sprite is still used as an **entity-style box UV**. The complete per-face UV retopology is not yet correct: large cuboids may repeat or crop texture portions. This remains the next art/renderer task; no new pixels added.
- Active moving collision/health is absent. Distinct branches still use profile-generated joints, not a rendered full multi-flower tree.
- CI validates compilation. An actual game-client screenshot/video test has not been performed; visual seams or clipping remain possible. Sounds are intentionally deferred per user request.
