# Living Chorus v0.15.0 – Natural-looking branches and smoother feeding

Changes:
- Thickened client stalk cubes from 4×4px to 8×8px and added 10px block-textured joint collars, both using the original vanilla chorus_plant sprite without stretched UVs.
- Smoothed discrete target updates on the client instead of snapping the branch every ~7 server ticks.
- Fixed the number of pivot/IK branch segments at attack start to avoid sudden joint switching.
- Split low Endermite feeding into turning, lowering, telescopic extending, contact, mouth-closing and retracting phases with continuous ease-in/out curves.
- Added up to 32 rigid 16px segments at 0.7-block spacing to cover the ground target even when the infected flower begins several blocks high, within the configured prey detection range.
- The Endermite attack target is now grounded: jaw center is Endermite feet + 7/16 block, so the *bottom* of the 14px flower touches its standing surface rather than clipping through it.
- Removed the hard-coded 0.14 radian mouth opening jump; orientation uses the final segment when the mouth reaches its target so it does not snap to north.
- Existing flower-origin spreading infection, live/dead buds, custom per-branch length, peaceful checks, asleep projectile vulnerability and naturally spawning End Endermites are kept.
- Sounds unchanged, per user request.

Limits: a real gameplay capture is needed to verify appearance and contact under different terrain elevations. This phase does not add ground-clipping avoidance for the whole stalk, new combat health or sound design.
