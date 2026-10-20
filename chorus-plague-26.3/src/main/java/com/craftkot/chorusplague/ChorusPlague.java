package com.craftkot.chorusplague;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ChorusPlague.MOD_ID)
public final class ChorusPlague {
    public static final String MOD_ID = "chorusplague";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredBlock<Block> PLAGUE_CORE = BLOCKS.registerBlock(
        "plague_core", PlagueCoreBlock::new,
        properties -> properties.strength(2.5f).noOcclusion()
    );
    public static final DeferredItem<BlockItem> PLAGUE_CORE_ITEM =
        ITEMS.registerSimpleBlockItem("plague_core", PLAGUE_CORE);

    public ChorusPlague(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(MimicPosePacket::register);
        NeoForge.EVENT_BUS.register(new HiddenMimicEvents());
    }
}
