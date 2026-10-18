# Living Chorus 0.14.0 — infection survives vanilla chorus growth

Fix user-reported loss of infection when a growing chorus_flower becomes a
chorus_plant block. The original flower position is now recognized as a
persistent infected stem and temporarily stores a saved growth origin marker.
Its new child flower(s) inherit the infection immediately, while other buds
connected through previously infected stems may be infected progressively.

- Reconciliation runs before server AI each tick, and whenever a formerly
  infected flower fails the flower blockstate check.
- SavedData codec includes an optional backwards-compatible `growthOrigins`
  field. Existing v0.13 saves load unchanged.
- The spread system no longer deletes all infected stem records merely
  because the flower-to-stem transition temporarily leaves no infected tip.
- Loaded invalid growth origins are pruned, without forcing chunk loads.
- `/livingchorus stats` now reports infected flower, stem, and growing-origin
  counts, making growth migration verifiable from gameplay.
- Manually marked flowers can attack in the Overworld; natural discovery
  and supplemental Endermite spawning remain exclusive to The End.
- Branch length, individual real-block kinematics, player versus Endermite
  attacks, sleeping projectile vulnerability and deferred sound work remain.

Quick in-game regression test: mark a young flower, verify stats; apply random
ticks until it grows a new bud; verify that infected stems grow and that the
new tip is listed as infected. A successful Gradle build does NOT prove that
the complete survival game transition has been tested.
