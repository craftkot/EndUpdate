package com.craftkot.livingchorus;

import net.minecraft.core.BlockPos;

/**
 * Deterministic per-flower style descriptor. Not rendered yet.
 *
 * A renderer will convert these parameters into bone segments while leaving
 * all vanilla chorus blocks unchanged during camouflage. The high number of
 * combinations makes exact repetitions less likely; never promise uniqueness.
 */
public record MimicProfile(
    int sectionCount,
    int firstBend,
    int secondBend,
    int direction,
    int extensionUnits,
    int mouthVariant,
    int timingVariant
) {
    public static MimicProfile forFlower(long worldSeed, BlockPos pos) {
        long value = mix(worldSeed ^ pos.asLong() ^ 0x4D494D4943425241L);
        int sections = 2 + (int) (value & 3L); // 2..5
        int bend1 = (int) ((value >>> 2) & 15L);
        int bend2 = (int) ((value >>> 6) & 15L);
        int direction = (int) ((value >>> 10) & 3L);
        int extension = 4 + (int) ((value >>> 12) & 15L);
        int mouth = (int) ((value >>> 16) & 3L);
        int timing = (int) ((value >>> 18) & 7L);
        return new MimicProfile(sections, bend1, bend2, direction, extension, mouth, timing);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
