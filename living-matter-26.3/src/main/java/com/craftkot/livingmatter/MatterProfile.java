package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;

/**
 * Deterministic per-flower style descriptor. Not rendered yet.
 *
 * A renderer will convert these parameters into bone segments while leaving
 * all vanilla chorus blocks unchanged during camouflage. The high number of
 * combinations makes exact repetitions less likely; never promise uniqueness.
 */
public record MatterProfile(
    int sectionCount,
    int firstBend,
    int secondBend,
    int direction,
    int extensionUnits,
    int mouthVariant,
    int timingVariant
) {
    public static MatterProfile forFlower(long worldSeed, BlockPos pos) {
        long value = mix(worldSeed ^ pos.asLong() ^ 0x4D494D4943425241L);
        int sections = 2 + (int) (value & 3L); // 2..5
        int bend1 = (int) ((value >>> 2) & 15L);
        int bend2 = (int) ((value >>> 6) & 15L);
        int direction = (int) ((value >>> 10) & 3L);
        int extension = 4 + (int) ((value >>> 12) & 15L);
        int mouth = (int) ((value >>> 16) & 3L);
        int timing = (int) ((value >>> 18) & 7L);
        return new MatterProfile(sections, bend1, bend2, direction, extension, mouth, timing);
    }

    /** Pack all seven non-secret appearance choices into 21 bits. */
    public int packed() {
        return ((sectionCount - 2) & 3)
            | ((firstBend & 15) << 2)
            | ((secondBend & 15) << 6)
            | ((direction & 3) << 10)
            | (((extensionUnits - 4) & 15) << 12)
            | ((mouthVariant & 3) << 16)
            | ((timingVariant & 7) << 18);
    }

    public static MatterProfile fromPacked(int packed) {
        return new MatterProfile(2 + (packed & 3), (packed >>> 2) & 15,
            (packed >>> 6) & 15, (packed >>> 10) & 3,
            4 + ((packed >>> 12) & 15), (packed >>> 16) & 3,
            (packed >>> 18) & 7);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
