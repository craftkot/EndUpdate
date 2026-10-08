package com.ehtid.endupdate.registry;

import com.ehtid.endupdate.EndUpdateMod;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModPotions {
    private static final int TELEPORT_DURATION = 20 * 10;

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, EndUpdateMod.MODID);

    public static final RegistryObject<Potion> TELEPORTATION = POTIONS.register("teleportation",
            () -> new Potion("teleportation",
                    new MobEffectInstance(ModEffects.CHORUS_TELEPORT.get(), TELEPORT_DURATION)));

    public static void register(IEventBus bus) {
        POTIONS.register(bus);
    }

    public static void registerBrewing(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BrewingRecipeRegistry.addRecipe(
                    StrictNBTIngredient.of(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.THICK)),
                    Ingredient.of(Items.CHORUS_FRUIT),
                    PotionUtils.setPotion(new ItemStack(Items.POTION), TELEPORTATION.get()));
        });
    }

    private ModPotions() {}
}
