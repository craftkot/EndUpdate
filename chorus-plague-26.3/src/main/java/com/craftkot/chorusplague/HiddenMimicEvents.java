package com.craftkot.chorusplague;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** No entity is spawned during disguise; no replacement block is rendered. */
public final class HiddenMimicEvents {
    private static final int SCAN_INTERVAL_TICKS = 200;
    private static final int RADIUS_XZ = 8;
    private static final int RADIUS_Y = 22;

    @SubscribeEvent
    public void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // Manually infected plants react in any dimension (also creative test pens).
        if (player.tickCount % 10 == 0) MimicAwakening.seek(level, player);
        // Natural infection and additional Endermite spawning remain End-only.
        if (level.dimension() != Level.END) return;
        EndEndermiteSpawning.tickPlayer(level, player);
        if (player.tickCount % SCAN_INTERVAL_TICKS != 0) return;

        MimicData data = MimicData.get(level);
        BlockPos center = player.blockPosition();
        int minY = Math.max(level.getMinY(), center.getY() - RADIUS_Y);
        int maxY = Math.min(level.getMaxY() - 1, center.getY() + RADIUS_Y);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = center.getX() - RADIUS_XZ; x <= center.getX() + RADIUS_XZ; x++) {
            for (int z = center.getZ() - RADIUS_XZ; z <= center.getZ() + RADIUS_XZ; z++) {
                // Never generate/load distant chunks just for infection.
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    // This check is cheap and does not mutate the world.
                    if (level.getBlockState(cursor).is(Blocks.CHORUS_FLOWER)) {
                        data.discover(level, cursor);
                    } else if (data.isInfected(cursor)) {
                        data.reconcileFlower(level, cursor);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void levelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // Critical: reconcile growth before attack timers and searches can
            // wrongly interpret a grown flower as a destroyed one.
            MimicData.get(level).reconcileAllGrowth(level);
            MimicAwakening.tick(level);
            // Poll every second; tall flowers no longer wait for nearby players.
            if (level.getGameTime() % 20L == 0L)
                MimicAwakening.seekUnattendedEndermites(level);
            if (level.getGameTime() % 20L == 0L)
                MimicData.get(level).spreadAcrossChorus(level);
        }
    }

    /**
     * Breaking the actual infected flower destroys the disguised mimic.
     * Neighbouring vanilla stem blocks and other flowers are left untouched.
     */
    @SubscribeEvent
    public void blockBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !event.getState().is(Blocks.CHORUS_FLOWER)) return;
        BlockPos flower = event.getPos();
        if (MimicData.get(level).isInfected(flower)) {
            MimicData.get(level).defeat(flower);
            MimicAwakening.onFlowerDestroyed(level, flower);
        }
    }

    /**
     * A sleeping flower can be shot down instantly, as a vanilla chorus flower can.
     * This listens to projectiles directly, regardless of whether the default
     * projectile implementation breaks the block by itself.
     */
    @SubscribeEvent
    public void projectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile().level() instanceof ServerLevel level)
            || level.dimension() != Level.END
            || !(event.getRayTraceResult() instanceof BlockHitResult hit)
            || event.getRayTraceResult().getType() != HitResult.Type.BLOCK) return;
        BlockPos flower = hit.getBlockPos();
        if (!level.getBlockState(flower).is(Blocks.CHORUS_FLOWER)) return;
        MimicData data = MimicData.get(level);
        if (!data.isInfected(flower) || !MimicAwakening.isSleeping(level, flower)) return;

        // Keep vanilla-style block drops and do not cancel projectile processing.
        // Once the flower is gone, the server-only marker can no longer animate.
        if (level.destroyBlock(flower, true, event.getProjectile())) {
            data.defeat(flower);
            MimicAwakening.onFlowerDestroyed(level, flower);
        }
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("chorusplague")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("inspect").executes(ctx -> perform(ctx.getSource(), "inspect")))
            .then(Commands.literal("mark").executes(ctx -> perform(ctx.getSource(), "mark")))
            .then(Commands.literal("clear").executes(ctx -> perform(ctx.getSource(), "clear")))
            .then(Commands.literal("provoke").executes(ctx -> perform(ctx.getSource(), "provoke")))
            .then(Commands.literal("spore").executes(ctx -> perform(ctx.getSource(), "spore")))
            .then(Commands.literal("profile").executes(ctx -> perform(ctx.getSource(), "profile")))
            .then(Commands.literal("debug").executes(ctx -> perform(ctx.getSource(), "debug")))
            .then(Commands.literal("stats").executes(ctx -> {
                ServerLevel level = ctx.getSource().getLevel();
                MimicData data = MimicData.get(level);
                int count = data.count();
                int active = MimicAwakening.activeCount(level);
                ctx.getSource().sendSuccess(() -> Component.literal("Hidden infected flowers: " + count
                    + ", infected stems: " + data.infectedStemCount()
                    + ", growing origins: " + data.growingOriginCount()
                    + ", charging: " + active), false);
                return count;
            }))
        );
    }

    private static int perform(CommandSourceStack source, String action) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(6.0, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a chorus flower within 6 blocks."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = blockHit.getBlockPos();
        if (!level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
            source.sendFailure(Component.literal("The target must be a vanilla chorus flower."));
            return 0;
        }
        MimicData data = MimicData.get(level);
        switch (action) {
            case "mark" -> data.mark(level, pos);
            case "clear" -> data.clear(pos);
            case "profile" -> {
                MimicProfile profile = MimicAwakening.profile(level, pos);
                source.sendSuccess(() -> Component.literal("Mimic branch blueprint: " + profile
                    + " | state: " + MimicAwakening.activeAnimation(level, pos)), false);
            }
            case "debug" -> source.sendSuccess(() ->
                Component.literal("Chorus Plague diagnostic: " + MimicAwakening.diagnose(level, pos, player)), false);
            case "spore" -> {
                if (!PlagueSpores.pulse(level, pos, true)) {
                    source.sendFailure(Component.literal("No eligible loaded neighbouring chorus tree (or local infection cap reached)."));
                    return 0;
                }
            }
            case "provoke" -> {
                if (!MimicAwakening.provoke(level, pos, player)) {
                    source.sendFailure(Component.literal("Mark the flower first. Provoke requires a nearby endermite or ender pearls in your inventory; wait until any ongoing attack finishes."));
                    return 0;
                }
            }
            default -> { }
        }
        boolean infected = data.isInfected(pos);
        boolean dead = level.getBlockState(pos).getValue(ChorusFlowerBlock.AGE) >= 5;
        source.sendSuccess(() -> Component.literal("Chorus flower at " + pos.toShortString()
            + " (" + (dead ? "dead" : "living") + "): "
            + (infected ? (MimicAwakening.isSleeping(level, pos) ? "MIMIC (sleeping)" : "MIMIC (awake)") : "normal/cleared")), false);
        return Command.SINGLE_SUCCESS;
    }
}
