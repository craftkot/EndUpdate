# Chorus Plague 0.1.0 — standalone NeoForge mod

For Minecraft **26.3**, NeoForge **26.3.0.90**, Java **25**.
Dedicated Gradle project, mod ID **chorusplague**, independently compiled from EndUpdate.

## Gameplay

An infection occupies one ordinary vanilla chorus flower in a connected chorus tree. When sleeping, nothing in the plant model betrays the infection. When prey appears, an articulated branch bends toward its target. Shared forks and all non-infected branches remain unaffected.

- Endermites are hunted with a dedicated low-feeding animation.
- Survival/Adventure players carrying Ender Pearls are bait: a real contact bite causes damage and steals up to three pearls. Creative and Spectator cannot be bait.
- Following a successful bite or swallow, spores may infect a **different** nearby chorus tree; never an additional branch on the already infected tree.
- Transmission radius: 10 blocks horizontally / 6 vertically; 1/3 chance per completed capture, 50-second cooldown per origin, at most 8 infected tips inside a 16-block area. Only loaded chunks are touched.
- Natural infection seeds in The End are deterministic, about 1% of scanned flower positions; infection survives world reload and normal growth.
- A successful hunt may also reposition the *real* infected vanilla flower on its own branch. No structures, other dimensions, or non-chorus blocks are overwritten.

## Operator commands

Aim at a vanilla chorus flower (up to 6 blocks away).

- `/chorusplague mark` — infect a flower / transfer to another tip of the same tree.
- `/chorusplague inspect` — inspect the targeted flower.
- `/chorusplague spore` — immediately attempt cross-tree spore infection (The End; at least one other nearby loaded tree).
- `/chorusplague clear` — clear and protect the flower from re-infection.
- `/chorusplague provoke` — trigger an attack if prey or an Ender Pearl carrier is available.
- `/chorusplague debug`, `/chorusplague profile`, `/chorusplague stats`.

## Building

```bash
cd chorus-plague-26.3
gradle --no-daemon build
```

Expected output: `build/libs/chorusplague-0.1.0.jar`.
The module's GitHub Actions workflow builds and publishes an artifact automatically.

Install only with Minecraft + NeoForge 26.3. Both client and server need this mod for the articulated animation networking. **Do not install it alongside Living Chorus** because both hook into the same vanilla chorus visuals while active.

## Verification status

Compile and resource assembly should be verified with GitHub Actions. Live client/server tests remain necessary to validate movement, pose masking, pellet/pearl collision and cross-tree transmission, especially at chunk boundaries. A green Gradle build alone is not a gameplay guarantee. EndUpdate `main` is not altered.
