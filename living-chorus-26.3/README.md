# Living Chorus 0.16.0 — tall chorus hunting range

Minecraft 26.3 · NeoForge 26.3.0.90.

Bug report: user screenshot shows tall infected chorus flower overlooking Endermites on End stone below it, while 0.15.0 would not attack them because all prey searches and consumption were hard-coded to 5.2 block Euclidean distance.

Changes:
- Endermites can be detected as far as 18 blocks horizontally and 24 blocks vertically below/above the flower, with a maximum 30 block direct distance.
- Four related checks now share the same range rules: automatic nearby-player seek, independent server tick search, /livingchorus provoke, and attack-contact server logic. /livingchorus debug now shows the same reach numbers and line-of-sight result.
- Independent infected-flower scan runs every 20 server ticks even if a player is not near the flower's tip. Bounded 128 flowers per scan, loaded chunks only; no forced chunk generation.
- The IK telescopic branch can deploy up to 48 full-length, original-textured segments, 0.70 blocks apart, covering tall flower-to-ground differences; server still requires jaw-to-prey contact within 0.65 blocks and an unobstructed approach. No remote instant-kill.
- No change to ender pearl player-bait radius (remains short range).
- Prior flower-origin contagious stems, natural End Endermite spawning, live/dead flower markers, vanilla UV, 8px stalks, 10px joints and smooth movement retained. Sounds still deferred.

Manual test: mark a tall chorus flower in The End. Summon Endermites on exposed End stone 10–20 blocks below it, within ~18 blocks horizontally. A healthy connected branch should bend and extend downward, then swallow only at contact. Inspect /livingchorus debug while aiming at the flower if detection is still blocked by geometry. Build success is not proof of game rendering.
