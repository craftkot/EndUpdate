package com.craftkot.livingchorus;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
        if (level.dimension() != Level.END) return;
        if (player.tickCount % 10 == 0) MimicAwakening.seek(level, player);
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
                        data.forget(cursor);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void levelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.END) {
            MimicAwakening.tick(level);
        }
    }

    @SubscribeEvent
    public void blockBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getState().is(Blocks.CHORUS_FLOWER)) {
            MimicData.get(level).forget(event.getPos());
        }
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("livingchorus")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("inspect").executes(ctx -> perform(ctx.getSource(), "inspect")))
            .then(Commands.literal("mark").executes(ctx -> perform(ctx.getSource(), "mark")))
            .then(Commands.literal("clear").executes(ctx -> perform(ctx.getSource(), "clear")))
            .then(Commands.literal("provoke").executes(ctx -> perform(ctx.getSource(), "provoke")))
            .then(Commands.literal("profile").executes(ctx -> perform(ctx.getSource(), "profile")))
            .then(Commands.literal("stats").executes(ctx -> {
                ServerLevel level = ctx.getSource().getLevel();
                int count = MimicData.get(level).count();
                int active = MimicAwakening.activeCount(level);
                ctx.getSource().sendSuccess(() -> Component.literal("Hidden infected flowers: " + count + ", charging: " + active), false);
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
            case "provoke" -> {
                if (!MimicAwakening.provoke(level, pos, player)) {
                    source.sendFailure(Component.literal("Mark the flower first. Provoke requires a nearby endermite or ender pearls in your inventory; wait until any ongoing attack finishes."));
                    return 0;
                }
            }
            default -> { }
        }
        boolean infected = data.isInfected(pos);
        source.sendSuccess(() -> Component.literal("Chorus flower at " + pos.toShortString()
            + ": " + (infected ? "MIMIC (hidden)" : "normal/cleared")), false);
        return Command.SINGLE_SUCCESS;
    }
}
