# Living Chorus 0.12.0 — Endermite wake-up visibility fix

Minecraft 26.3 / NeoForge 26.3.0.90; experimental branch only.

FIX: Infection is still tied to a vanilla living/dead chorus_flower block.
The automatic Endermite seeking ray now ignores collisions with that flower and
with up to four blocks of its OWN vertical chorus stem, while still stopping
at solid terrain such as End stone. Previously the flower/own stem could
occlude the Endermite underneath, preventing automatic wake-up.
Manual /livingchorus provoke still allows testing without the visibility gate.

NEW: With cheats, look at an infected chorus flower within six blocks and run
/livingchorus debug. The output includes whether the flower is active, nearest
live Endermite range, line-of-sight result, and both flower/prey cooldown timers.
Inspect /livingchorus stats for active attacks after summoning prey.

Existing Minecraft 26.3 client visuals remain experimental. The real-world
CI run #54 confirmed mark/infection and active masking, but did NOT yet
visually validate the complete textured model. Sound work is deferred.

Manual smoke:
1. Place chorus plant with a living or dead chorus flower at its tip.
2. Aim at FLOWER and run /livingchorus mark; inspect must say MIMIC (sleeping).
3. Summon an Endermite on nearby End stone, within about four blocks of
   the flower's center. Do not use Peaceful.
4. Check /livingchorus debug, which should show path clear=true.
5. Observe the flower for 3–6 seconds; if still idle capture /livingchorus debug.
6. To isolate client rendering from AI, point at flower and /livingchorus provoke
   while an Endermite is still alive.

This build must pass Gradle CI; actual client gameplay remains to be checked.
