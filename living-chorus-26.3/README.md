# Living Chorus 0.18.3 — stealing Ender Pearls from players

Minecraft 26.3 / NeoForge 26.3.0.90. Feature branch only; main untouched.

- The pre-existing separate player bite animation still triggers only
  for Survival/Adventure players carrying vanilla ENDER_PEARLs anywhere
  in their inventory (including hotbar and offhand) within the short
  pearl-bait range. Creative/spectator players remain excluded.
- A successful player bite now deals the existing 3 points of damage AND
  removes up to THREE actual Ender Pearls from their inventory. It
  searches multiple stacks and synchronizes the inventory to the client.
- Pearls are not removed at detection or animation start, only when
  the server's attack contact tick confirms a valid player carrying
  pearls. A cancelled/missed bite or empty inventory steals nothing.
- Brief reverse-portal particles at the infected flower accompany
  successful theft. The Endermite-only 5-second digestion effect
  remains separate.
- When all pearls are gone, the player no longer attracts a pearl
  bait attack, regardless of the new bite's cooldown.
- The compact non-crane 0.18.2 rig, 1.5 block extension limit, existing
  Endermite feeding, live/dead infection and fixed flower growth
  persistence remain unchanged.

Manual test: in Survival, carry 7 pearls split between inventory slots,
approach a marked flower at <4 blocks and wait for bite. Expected first
successful bite: 3 pearls removed, second successful bite: 3 removed,
third successful bite: last pearl removed. No further pearl-bait attacks.
Do not equate a passing build with a proven in-world bite.
