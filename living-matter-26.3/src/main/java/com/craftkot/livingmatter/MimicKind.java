package com.craftkot.livingmatter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Classification is data-agnostic: unrecognized eligible blocks become WILD mimics. */
public enum MimicKind {
    STONE("stone_maw", 8.0F, 3.0),
    WOOD("root_grasp", 3.0F, 3.25),
    SAND("sand_sink", 4.0F, 3.0),
    ORE("ore_stinger", 6.0F, 2.8),
    LEAF("leaf_tendril", 2.0F, 3.5),
    SPORE("spore_pod", 3.0F, 3.0),
    WILD("wild_bite", 5.0F, 2.8);

    private final String modelId;
    private final float damage;
    private final double range;

    MimicKind(String modelId, float damage, double range) {
        this.modelId = modelId;
        this.damage = damage;
        this.range = range;
    }

    public String modelId() { return modelId; }
    public float damage() { return damage; }
    public double range() { return range; }

    public static MimicKind of(BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (name.contains("ore") || name.contains("raw_") || name.contains("metal")
            || name.contains("copper_block") || name.contains("iron_block")
            || name.contains("gold_block") || name.contains("diamond_block")) return ORE;
        if (name.contains("log") || name.contains("wood") || name.contains("plank")
            || name.contains("stem") || name.contains("hyphae") || name.contains("bamboo")) return WOOD;
        if (name.contains("leaves") || name.contains("vine") || name.contains("moss")
            || name.contains("azalea")) return LEAF;
        if (name.contains("mushroom") || name.contains("wart") || name.contains("fungus")
            || name.contains("mycelium") || name.contains("sculk")) return SPORE;
        if (name.contains("sand") || name.contains("gravel") || name.contains("dirt")
            || name.contains("grass_block") || name.contains("mud") || name.contains("clay")
            || name.contains("soul_soil")) return SAND;
        if (name.contains("stone") || name.contains("deepslate") || name.contains("cobble")
            || name.contains("brick") || name.contains("tuff") || name.contains("basalt")
            || name.contains("blackstone") || name.contains("andesite")
            || name.contains("granite") || name.contains("diorite")
            || name.contains("obsidian") || name.contains("quartz")
            || name.contains("terracotta")) return STONE;
        return WILD;
    }
}
