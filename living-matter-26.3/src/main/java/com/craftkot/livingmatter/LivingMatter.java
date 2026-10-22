package com.craftkot.livingmatter;

import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

@Mod(LivingMatter.MOD_ID)
public final class LivingMatter {
    public static final String MOD_ID = "livingmatter";
    public static final String BUILD_ID = "0.2.1-walkfix-diagnostic";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final Map<MimicKind, DeferredBlock<MimicManifestBlock>> MANIFESTS = new EnumMap<>(MimicKind.class);

    static {
        for (MimicKind kind : MimicKind.values()) {
            MANIFESTS.put(kind, BLOCKS.registerBlock(kind.modelId(),
                MimicManifestBlock::new, properties ->
                    properties.noCollision().noOcclusion().noLootTable().strength(0.0F)));
        }
    }

    public static Block manifest(MimicKind kind) {
        return MANIFESTS.get(kind).get();
    }

    public LivingMatter(IEventBus modBus) {
        BLOCKS.register(modBus);
        modBus.addListener(MatterPosePacket::register);
        modBus.addListener(MatterWalkPacket::register);
        NeoForge.EVENT_BUS.register(new MimicEvents());
    }
}
