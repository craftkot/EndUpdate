package com.craftkot.livingchorus;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LivingChorus.MOD_ID)
public final class LivingChorus {
    public static final String MOD_ID = "livingchorus";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredBlock<Block> LIVING_CHORUS = BLOCKS.registerBlock(
        "living_chorus", LivingChorusBlock::new,
        properties -> properties.strength(2.5f).noOcclusion()
    );
    public static final DeferredItem<BlockItem> LIVING_CHORUS_ITEM =
        ITEMS.registerSimpleBlockItem("living_chorus", LIVING_CHORUS);

    public LivingChorus(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
