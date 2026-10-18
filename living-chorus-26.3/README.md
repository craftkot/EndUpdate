# Living Chorus — NeoForge 26.3 / experimental

Standalone subproject in craftkot/EndUpdate. Does not modify the original Forge 1.20.1 EndUpdate mod.

## Stage 3: target-specific behaviours and unique branch profiles (v0.4.0)

**V17 Blockbench model is an animation demonstration, not the final in-game mob.**
Do not assume a uniform four-flower shape or use one universal attack animation.

- Dormant mimics remain **vanilla chorus_flower blocks**, with server-only infection markers. They are not separate detectable entities.
- Infection stays at approximately 5% of eligible End chorus flowers and persists with server SavedData.
- Players are attack targets **only while holding at least one ender pearl anywhere in their inventory**. Ender pearls are NOT removed. No pearls = no player-targeting.
- Endermites are food. An infected flower near an endermite will preferentially target it, bend LOW, and swallow it at successful contact. Endermites are removed by successful consumption.
- Separate action/animation identifiers:
  - `bite_player_high`: target player eye/body height;
  - `eat_endermite_low`: target low endermite height.
- Both attacks have charge/telegraph/cooldown, target and distance/obstruction checks, and cancel on destruction of the vanilla flower.
- Deterministic `MimicProfile` assigned to each flower from world seed and position: segment count (2–5), two bends, branch direction, extension units, mouth style, timing variant. Profiles are *data for the future renderer*, not visual changes to the vanilla plant yet. Distinct profiles are likely but exact uniqueness is not mathematically guaranteed.
- A player nearby triggers lightweight server scans twice a second. No forced chunks or permanent mob entities.
- Animation and full per-branch procedural mesh generation **are not implemented in-game yet**. No claim of in-game bending or visual swallowing at this stage.

### Admin testing

Target a vanilla `chorus_flower` within 6 blocks:

- `/livingchorus mark` — set infected
- `/livingchorus inspect` — show marker state
- `/livingchorus profile` — view deterministic shape blueprint and current attack identifier
- `/livingchorus provoke` — force an attempt; prefers a nearby endermite, otherwise needs an ender pearl in your inventory
- `/livingchorus clear` — clear marker and exclude from further infection
- `/livingchorus stats` — infected and currently charging counts

Normal play: have an ender pearl in inventory, enter Survival in The End, approach an infected flower. No pearl: it should ignore you. Spawn an endermite nearby to test the separate feeding branch. The endermite is consumed only if it remains in range and can be reached without intervening solid geometry. Collision, mini-map block appearance and vanilla item ID remain intact until attack visuals are implemented.

### Compatibility
The older test block `livingchorus:living_chorus` still damages nearby players and is NOT the hidden mimicking chorus; use vanilla chorus flowers for this stage.

### Build
GitHub Actions `Build Living Chorus 26.3` uses Java 25, Gradle 9.2.1, NeoForge 26.3.0.90.
