# Blockbench models — FIXED / Исправлено

These are **real visible editable Blockbench projects**, eight files with 3D cubes and embedded genuine vanilla Minecraft textures, not resource names. No custom textures generated.

- Open Blockbench -> File -> Open Model -> choose any .bbmodel from this directory.
- Elements and Outliner now contain visible cubes; origin and rotation use valid array syntax.
- Embedded PNG source data means textures display even without a Minecraft installation or external resource packs.
- Minecraft vanilla texture source: https://github.com/InventivetalentDev/minecraft-assets/tree/1.21.1/assets/minecraft/textures/block
- These are 3D reference prototypes and are *not* proof of an in-game skeletal animation. The gameplay code currently uses keyframed blockstates.

## Pixel-perfect vanilla UV / Пиксельная плотность без растягивания

**Open in Blockbench 5.1 or newer.** This version supports **Texture Wrap Mode: Repeat**.

- All eight models enable **Auto UV** (cube `autouv: 1`) so resizing geometry recomputes UV size.
- Every cube face has explicit UV coordinates proportional to the physical face dimensions at **1 vanilla texture pixel per 1 model unit** (16 pixels / full block).
- Each embedded authentic vanilla texture uses `wrap_mode: "repeat"`. When UV extends outside the 16x16 texture (for larger future parts), **Repeat** tiles the texture instead of stretching or clamping.
- To inspect: select any cube -> Outliner **Auto UV** on; double-click its texture -> **Wrap Mode: Repeat**.
- No custom PNGs, no rescaling/repainting of the vanilla images. Different face directions preserve their own geometric proportions.

**Note:** This is an editable Blockbench project. Plain Minecraft Java block-model export may have different UV wrap restrictions. The Blockbench tiling configuration is not a substitute for implementation-specific in-game render testing.
