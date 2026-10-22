package com.craftkot.livingmatter;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
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
    public static final String BUILD_ID = "0.4.0-admin-panel";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<ChthonicMassEntity>> CHTHONIC_MASS =
        ENTITIES.registerEntityType("chthonic_mass", ChthonicMassEntity::new,
            MobCategory.MONSTER, builder -> builder.sized(4.3f, 5.1f).clientTrackingRange(12));
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
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) ->
            event.put(CHTHONIC_MASS.get(),ChthonicMassEntity.createAttributes().build()));
        modBus.addListener(MatterPosePacket::register);
        modBus.addListener(MatterWalkPacket::register);
        modBus.addListener(AdminPanelPacket::register);
        NeoForge.EVENT_BUS.register(new MimicEvents());
    }
}
