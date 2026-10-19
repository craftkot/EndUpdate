# Living Chorus 0.19.0 — intangible active branches and real chorus remodeling

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

## v0.18.4 — bite contact correction

- Player bites now use the actual server-evaluated articulated flower mouth at the hit tick; the former 4-block radius around the stationary flower no longer decides whether the player is hit.
- Contact requires the mouth to intersect the player hitbox (with a small 0.35-block allowance for the flower head), plus the existing line-of-sight and Survival/Adventure pearl checks.
- Up to 3 Ender Pearls are removed **only if the damage event succeeds**. Misses, invulnerability and lost pearls do not trigger theft.
- No movement/animation geometry, 1.5-block extension limit, infection, Endermite digestion or sound effects changed.
- Still needs a real in-world Survival test: try split pearl stacks, dodge just before contact, leave the original flower's 4-block radius while the moving mouth reaches you, and verify no remote theft.

## v0.18.5 — repeated bites while Ender Pearls remain

- A player carrying Ender Pearls can attract another bite after recovery and a short rearm delay. The flower now has 93-113 ticks from attack start to next player bite (35 tick charge + 18 tick recovery + 40-60 tick rearm), rather than 150-249 ticks; the existing target cooldown stays at 95 ticks.
- Unreachable Endermites are filtered before taking priority over pearl carriers in player-adjacent scans. Unattended hunting also ignores unreachable Endermites, so a physically inaccessible mite cannot occupy the next-attack choice indefinitely.
- Looking at a flower and running `/livingchorus debug` now shows the player's remaining pearl count, eligible game mode, wake range, line-of-sight, articulated mouth reach, and flower and target cooldowns.
- No pearls means no further player-bait attacks, by design. No sounds, render geometry, infection, or movement limits changed.
- Manual Survival verification is still required: use 7 Ender Pearls in several stacks, verify three successive successful bites steal 3+3+1; check movement/dodging and spectator/Creative exclusions separately. A successful CI build/client smoke does not prove this scenario.

## v0.19.0 — physical moving-branch collision and branch relocation

- While a mimic attacks, its vanilla original flower and **owned** leaf stems have no server/client collision and no vanilla pick outline. Shared forks and dormant plants retain normal vanilla block behavior. The animated renderer already masks their original pixels; a targeted vanilla block-shape mixin now removes the previously invisible physical obstacle on both sides.
- Successful player bites or Endermite swallows may move the real infected vanilla flower to a new safe empty position attached to a different point on its own non-shared chorus stalk. The former flower becomes vanilla chorus_plant and carries infection as a stem; no healthy branch, shared junction or unrelated block is changed. If no stable attached position exists, the plant is untouched. No extra items are dropped.
- The articulated animation now initializes with per-flower lateral/vertical offsets, giving different bend and twist silhouettes, but still preserves actual segment lengths, the shared fixed root and <=1.5-block extension. Sleeping plants remain exactly vanilla in both appearance and selection.
- No new sound files or audio changes. New NeoForge mixin config is bundled in the jar for targeted shape override.
- **Manual tests mandatory**: walk through an active branch in Survival; no targeting outline while awake; vanilla hitbox returns once asleep; observe reattachment after successful bite/swallow; verify healthy adjacent branches and survival over world save/reload; verify server/client agree on collision.
