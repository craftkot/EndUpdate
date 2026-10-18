# Living Chorus 0.18.0 — immediate hunting, full-branch bends and digestion

Minecraft 26.3, NeoForge 26.3.0.90. Standalone branch in craftkot/EndUpdate; do not merge to main without user approval.

## Updated feeding (after real in-game feedback)
- **No waiting above prey**: branch turns and descends from the beginning of the Endermite attack, with overlapping X/Z/Y phases. It still produces crisp, orthogonal, vanilla-chorus-looking elbows rather than an unstructured diagonal curve.
- Entire *dedicated* infected branch, traced back to its first shared trunk junction, is articulated as the moving bone chain; the shared/root junction stays anchored so other normal flowers are not moved.
- Mite attack charges start from 26 ticks for near targets instead of 46; the extra time for far prey is reduced and capped at 64 ticks. Client receives the actual dynamic charge duration.
- Four flower jaw plates open during approach, reaching full opening before the contact frame; the server consumes a mite only if the calculated mouth reached it.
- **Digestion**: on *successful* Endermite consumption, purple vanilla `PORTAL` and `REVERSE_PORTAL` particles rise from the original infected flower for 100 server ticks (~5 s). The effect continues after the attacking branch retracts, stops on flower destruction, and never plays for missed bites or player attacks. No new sound effects.
- **Decorative thickening retained but sparse**: 8px stalks use 10px vanilla-textured joint collars at real turns or roughly once every 1.4–1.85 block lengths, not at every moving joint. No custom sprite or stretched UV.
- Original live/dead flower infections, flower-to-stem contagion through growth, 18 horizontal / 24 vertical Endermite detection, pearl-based player targeting, and rare End spawning stay intact.

## Smoke tests
1. Use NeoForge 26.3 with ONLY livingchorus-0.18.0.jar. Mark a connected vanilla chorus flower using `/livingchorus mark`.
2. Summon an Endermite on End stone directly below or diagonally beside the flower.
3. Observe that the **whole terminal branch** moves, with a prompt descent instead of lingering overhead, and sparse thicker joints.
4. After a successful swallow, watch the ORIGINAL blossom for portal motes for approximately five seconds, even after it returns to resting form.
5. A missed bite and a sleeping/shot flower must NOT emit digestion particles.
6. Record a side-view video and upload latest.log if the model clips, flickers, or appears disconnected.

GitHub Actions compilation and client startup are not substitutes for visual motion verification; inspect actual gameplay frames.
