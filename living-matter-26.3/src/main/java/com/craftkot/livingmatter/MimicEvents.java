package com.craftkot.livingmatter;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-authoritative attacks. Dormant blocks remain 100% vanilla, even when
 * they are modded blocks. On waking, only temporary air-space visual geometry
 * is added; source blocks and their saved states are never modified.
 */
public final class MimicEvents {
    private static final int NATURAL_SCAN_INTERVAL = 200;
    private static final int BEHAVIOUR_SCAN_INTERVAL = 10;
    private static final int ATTACK_WINDUP = 10;
    private static final int COOLDOWN = 140;
    private static final Map<ServerLevel, Map<Long, Long>> COOLDOWNS = new WeakHashMap<>();
    private static final Map<ServerLevel, List<Strike>> ACTIVE = new WeakHashMap<>();

    private record Strike(BlockPos origin, Block original, UUID target, MimicKind kind, long hitTick) {}

    @SubscribeEvent
    public void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.isSpectator()) return;
        MimicData data = MimicData.get(level);
        BlockPos center = player.blockPosition();

        if (player.tickCount % NATURAL_SCAN_INTERVAL == 0) {
            // A bounded window: does not generate or load chunks.
            for (int x = center.getX() - 6; x <= center.getX() + 6; x++) {
                for (int z = center.getZ() - 6; z <= center.getZ() + 6; z++) {
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    for (int y = Math.max(level.getMinY(), center.getY() - 3);
                        y <= Math.min(level.getMaxY() - 1, center.getY() + 3); y++) {
                        data.discover(level, new BlockPos(x, y, z));
                    }
                }
            }
        }

        if (player.isCreative() || player.tickCount % BEHAVIOUR_SCAN_INTERVAL != 0) return;
        for (int x = center.getX() - 3; x <= center.getX() + 3; x++) {
            for (int z = center.getZ() - 3; z <= center.getZ() + 3; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = Math.max(level.getMinY(), center.getY() - 2);
                    y <= Math.min(level.getMaxY() - 1, center.getY() + 3); y++) {
                    BlockPos spot = new BlockPos(x, y, z);
                    if (!data.isInfected(spot)) continue;
                    if (!MimicData.eligible(level, spot)) {
                        data.cure(spot);
                        continue;
                    }
                    double distance = player.distanceToSqr(x + .5, y + .5, z + .5);
                    MimicKind kind = MimicKind.of(level.getBlockState(spot));
                    if (distance <= kind.range() * kind.range() && visible(level, spot, player)) {
                        if (awaken(level, spot, player, false)) return; // max one per 10 ticks per player
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void breakBlock(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        BlockPos pos = event.getPos();
        MimicData data = MimicData.get(level);
        if (!data.isInfected(pos)) return;
        // The first mining attempt exposes the impostor. Once it has recoiled
        // it can be killed with a normal block break (original vanilla loot).
        if (awaken(level, pos, player, true)) {
            event.setCanceled(true);
        } else {
            data.cure(pos);
        }
    }

    @SubscribeEvent
    public void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<Strike> active = ACTIVE.get(level);
        if (active == null || active.isEmpty()) return;
        Iterator<Strike> it = active.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            if (level.getGameTime() < strike.hitTick()) continue;
            it.remove();
            if (!level.hasChunkAt(strike.origin())
                || !MimicData.get(level).isInfected(strike.origin())
                || level.getBlockState(strike.origin()).getBlock() != strike.original()) continue;
            Entity target = level.getEntity(strike.target());
            if (!(target instanceof ServerPlayer player) || !player.isAlive()
                || player.isCreative() || player.isSpectator()) continue;
            if (player.distanceToSqr(Vec3.atCenterOf(strike.origin()))
                > Math.pow(strike.kind().range() + .6, 2)
                || !visible(level, strike.origin(), player)) continue;

            MimicKind kind = strike.kind();
            float damage = kind.damage();
            if (kind == MimicKind.STONE) damage += Math.min(6.0F, countAdjacentStone(level, strike.origin()) * 1.5F);
            boolean hit = player.hurtServer(level, level.damageSources().cactus(), damage);
            if (!hit) continue;
            MimicData.get(level).spreadAfterHunt(level, strike.origin(), kind, false);
            switch (kind) {
                case WOOD -> player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
                case SAND -> {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
                    player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 120, 1));
                }
                case ORE -> player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 90, 1));
                case LEAF -> player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70, 1));
                case SPORE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                }
                case STONE -> {
                    Vec3 push = player.position().subtract(Vec3.atCenterOf(strike.origin()));
                    player.knockback(1.25D, -push.x, -push.z, level.damageSources().cactus(), 0.0F);
                }
                case WILD -> player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
            }
            level.sendParticles(kind == MimicKind.SPORE ? ParticleTypes.PORTAL : ParticleTypes.CRIT,
                player.getX(), player.getY() + player.getBbHeight() * .5, player.getZ(),
                22, .5, .4, .5, .08);
            level.playSound(null, strike.origin(), SoundEvents.SHULKER_BULLET_HIT,
                SoundSource.HOSTILE, 1.0F, kind == MimicKind.STONE ? .6F : .95F);
        }
    }

    private static boolean visible(ServerLevel level, BlockPos pos, ServerPlayer target) {
        // A legitimate hit must not reach through a wall. Hitting the origin
        // block itself is permitted because the mimic hides inside that block.
        BlockHitResult hit = level.clip(new ClipContext(
            target.getEyePosition(), Vec3.atCenterOf(pos),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.MISS
            || (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos));
    }

    private static int countAdjacentStone(ServerLevel level, BlockPos at) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = at.relative(direction);
            if (level.hasChunkAt(neighbour)
                && MimicKind.of(level.getBlockState(neighbour)) == MimicKind.STONE) count++;
        }
        return count;
    }

    private static boolean awaken(ServerLevel level, BlockPos pos, ServerPlayer target, boolean force) {
        MimicData data = MimicData.get(level);
        if (!data.isInfected(pos) || !MimicData.eligible(level, pos)) return false;
        long time = level.getGameTime(), key = pos.asLong();
        Map<Long, Long> cooldown = COOLDOWNS.computeIfAbsent(level, ignored -> new HashMap<>());
        if (time < cooldown.getOrDefault(key, 0L)) return false;
        MimicKind kind = MimicKind.of(level.getBlockState(pos));
        if (!force && target.distanceToSqr(Vec3.atCenterOf(pos)) > kind.range() * kind.range())
            return false;
        cooldown.put(key, time + COOLDOWN);
        if (cooldown.size() > 8192) cooldown.entrySet().removeIf(e -> e.getValue() < time);

        ACTIVE.computeIfAbsent(level, ignored -> new ArrayList<>())
            .add(new Strike(pos.immutable(), level.getBlockState(pos).getBlock(),
                target.getUUID(), kind, time + ATTACK_WINDUP));
        reveal(level, pos, target, kind);
        return true;
    }

    private static void reveal(ServerLevel level, BlockPos source, ServerPlayer player, MimicKind kind) {
        // STONE moves several connected-looking jaw pieces; other materials
        // exhibit unique morphologies. No existing world block is overwritten.
        double dx = player.getX() - source.getX();
        double dz = player.getZ() - source.getZ();
        Direction towards = Math.abs(dx) > Math.abs(dz)
            ? (dx >= 0 ? Direction.EAST : Direction.WEST)
            : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        Direction[] order = {towards, Direction.UP, towards.getClockWise(),
            towards.getCounterClockWise(), Direction.DOWN};
        int pieces = kind == MimicKind.STONE ? 3 : kind == MimicKind.WOOD ? 2 : 1;
        int placed = 0;
        for (Direction direction : order) {
            BlockPos target = source.relative(direction);
            if (!level.hasChunkAt(target) || !level.getBlockState(target).isAir()) continue;
            level.setBlock(target, LivingMatter.manifest(kind).defaultBlockState(), 3);
            if (++placed >= pieces) break;
        }
        level.sendParticles(kind == MimicKind.SPORE ? ParticleTypes.PORTAL : ParticleTypes.SMOKE,
            source.getX() + .5, source.getY() + .5, source.getZ() + .5,
            kind == MimicKind.SAND ? 32 : 14, .45, .45, .45, .04);
        level.playSound(null, source, SoundEvents.CHORUS_FLOWER_GROW,
            SoundSource.HOSTILE, .75F, kind == MimicKind.STONE ? .55F : 1.15F);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("livingmatter")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("infect").executes(ctx -> doAction(ctx.getSource(), "infect")))
            .then(Commands.literal("spread").executes(ctx -> doAction(ctx.getSource(), "spread")))
            .then(Commands.literal("cure").executes(ctx -> doAction(ctx.getSource(), "cure")))
            .then(Commands.literal("inspect").executes(ctx -> doAction(ctx.getSource(), "inspect")))
            .then(Commands.literal("awaken").executes(ctx -> doAction(ctx.getSource(), "awaken")))
            .then(Commands.literal("stats").executes(ctx -> {
                int count = MimicData.get(ctx.getSource().getLevel()).count();
                ctx.getSource().sendSuccess(() -> Component.literal("Living Matter infected positions: " + count), false);
                return count;
            }))
        );
    }

    private static int doAction(CommandSourceStack source, String action) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(6.0, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a solid block within six blocks."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = blockHit.getBlockPos();
        MimicData data = MimicData.get(level);
        switch (action) {
            case "infect" -> {
                if (!data.mark(level, pos)) {
                    source.sendFailure(Component.literal("Block is not eligible (fluid, block entity, unbreakable, or unloaded)."));
                    return 0;
                }
            }
            case "spread" -> {
                if (!data.spreadAfterHunt(level, pos, MimicKind.of(level.getBlockState(pos)), true)) {
                    source.sendFailure(Component.literal("Infect first. Neighbour must be compatible, loaded and free; local cap 12."));
                    return 0;
                }
            }
            case "cure" -> data.cure(pos);
            case "awaken" -> {
                if (!awaken(level, pos, player, true)) {
                    source.sendFailure(Component.literal("Infect first, or wait for attack cooldown."));
                    return 0;
                }
            }
            default -> { }
        }
        String message = "Living Matter " + action + ": " + pos.toShortString()
            + " | infected=" + data.isInfected(pos)
            + " | kind=" + MimicKind.of(level.getBlockState(pos)).name();
        source.sendSuccess(() -> Component.literal(message), false);
        return Command.SINGLE_SUCCESS;
    }
}
