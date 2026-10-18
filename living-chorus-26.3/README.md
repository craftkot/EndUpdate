# Living Chorus — Minecraft 26.3 / NeoForge

Standalone subproject isolated from EndUpdate Forge 1.20.1.
A hostile stationary chorus, based on the supplied four-cube Blockbench model.
Its server-side scheduled tick hits non-creative, non-spectator players within 2.25 blocks for 3 damage every 20 ticks.

## Build
Use the GitHub Actions workflow **Build Living Chorus 26.3**, or in this subfolder with Java 25 + Gradle 9.2.1:
`gradle build`.

## In-game
`/give @s livingchorus:living_chorus` and place on a surface.
No animations yet. This prototype is implemented as a hostile block, not a full health-bearing mob. 
The JSON model uses Minecraft's original chorus plant/flower sprites and the geometry supplied in hors.bbmodel.
