# Living Chorus — Minecraft 26.3 NeoForge (experimental)

Kept in a standalone subproject of craftkot/EndUpdate. Not merged into main.

## 0.5.0 — flower-rooted infection, dormant one-hit kills

### Flower, not whole plant
- A mimic infection is tied to **one vanilla minecraft:chorus_flower position**.
- A live flower (AGE 0–4) and a dead flower (AGE 5) are both eligible; these are block states of the same block.
- Each infected flower is the anchor of **its own infected branch**, but neighbouring branches and flowers remain normal unless independently marked.
- Breaking or shooting the anchor flower while it sleeps instantly removes its mimic identity. Remaining chorus_plant stem blocks remain vanilla and harmless.
- The killed flower position is excluded from repeat deterministic infection until explicitly marked. No separate mob is spawned while sleeping.
- Other infected flowers on the same grown chorus plant survive independently.

### Projectile behavior
- NeoForge ProjectileImpactEvent checks impacts against sleeping marked flowers.
- A shot destroying an infected dormant flower uses ordinary block destruction with drops; no special entity hitbox exists.
- Projectile hits on ordinary vanilla chorus flowers remain vanilla.
- Projectiles during an ACTIVE attack are **not** covered by the sleeping one-hit mechanic, though vanilla projectile/block interactions may still break the underlying flower while the animated combat entity has not yet been implemented. Active HP/defense comes in a later stage.
- Breaking by a player removes infection via BreakBlockEvent. Scanning invalidated positions also removes obsolete infection markers when a flower grows into a stem.

### Hunting (previous milestone)
- Target players only if ender pearls are in inventory: high `bite_player_high` action.
- Eat nearby endermites: separate `eat_endermite_low` action with low head target.
- Target choice, charge, range, occlusion checks, and cooldown are server-side; no actual animated branch is rendered yet.
- Per-flower MimicProfile is seeded independently, and is a descriptor for future varied branch geometry.

### Operator tests

1. `/livingchorus mark` on a living or dead vanilla chorus flower.
2. `/livingchorus inspect` shows `living` or `dead` and `MIMIC (sleeping)`.
3. Shoot the flower with an arrow before it wakes. It should break in one hit.
4. `/livingchorus stats` should show one less hidden infected flower.
5. Other marked flowers remain infected; the untouched plant stems remain in their vanilla form.
6. Repeat with a dead flower (give/place chorus_flower with age=5 or find a naturally dead one).
7. Test `/livingchorus provoke` with nearby endermite or ender pearl to see the attack scheduling; attack visuals are pending.

### Limits
The V17 .bbmodel is a *reference*, not the final creature; dynamic branches, active hitboxes, synchronized animations and complete client runtime testing remain outstanding.

Build with Java 25, Gradle 9.2.1, NeoForge 26.3.0.90 using GitHub Actions.
