package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cavern-floor mimic: opens a REAL temporary one-block aperture over an
 * existing air cavity; no extra bedrock/terrain is dug and no drops exist.
 * Restoration is immediate at bite completion or on failed initiation.
 *
 * This first version intentionally refuses fully solid earth so that normal
 * player buildings and hidden structures cannot be destroyed.
 */
public final class FloorMawTrap {
    private static final int OPEN_TICKS = 18;
    private record Trap(BlockPos floor, BlockState original, java.util.UUID victim,
                        long closeAt, long lethalAt) {}
    private static final Map<ServerLevel, Map<Long, Trap>> ACTIVE = new WeakHashMap<>();
    private FloorMawTrap() {}

    public static boolean isActive(ServerLevel level, BlockPos floor) {
        return ACTIVE.getOrDefault(level, Map.of()).containsKey(floor.asLong());
    }

    public static boolean tryOpen(ServerLevel level, BlockPos floor, ServerPlayer player) {
        if (!MimicData.get(level).isInfected(floor) || isActive(level, floor)
            || player.isCreative() || player.isSpectator()) return false;
        // Only solid full-cube floors over a NATURAL hollow chamber. Never
        // overwrite cave contents, inventories, fluids or protected blocks.
        if (!level.hasChunkAt(floor) || !level.hasChunkAt(floor.below())) return false;
        BlockState original = level.getBlockState(floor);
        if (!original.isCollisionShapeFullBlock(level, floor)
            || !MimicData.eligible(level, floor)
            || !level.getBlockState(floor.below()).isAir()
            || !level.getBlockState(floor.below(2)).isAir()) return false;
        Vec3 feet = player.position();
        if (Math.abs(feet.x - floor.getX() - .5) > .40
            || Math.abs(feet.z - floor.getZ() - .5) > .40
            || Math.abs(feet.y - floor.getY() - 1.0) > .35) return false;
        if (!level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3)) return false;
        long now = level.getGameTime();
        ACTIVE.computeIfAbsent(level, l -> new HashMap<>())
            .put(floor.asLong(), new Trap(floor.immutable(), original, player.getUUID(),
                now + OPEN_TICKS, now + 10));
        level.sendParticles(ParticleTypes.LARGE_SMOKE, floor.getX()+.5, floor.getY()+.2,
            floor.getZ()+.5, 30,.45,.1,.45,.02);
        level.playSound(null, floor, SoundEvents.SHULKER_BULLET_HIT,
            SoundSource.HOSTILE, 1.0F, 0.45F);
        return true;
    }

    public static void tick(ServerLevel level) {
        Map<Long, Trap> traps = ACTIVE.get(level);
        if (traps == null) return;
        long now = level.getGameTime();
        Iterator<Trap> it = traps.values().iterator();
        while (it.hasNext()) {
            Trap trap = it.next();
            if (!level.hasChunkAt(trap.floor())) continue;
            ServerPlayer victim = level.getServer().getPlayerList().getPlayer(trap.victim());
            if (victim != null && victim.level() == level && victim.isAlive()
                && !victim.isCreative() && !victim.isSpectator()) {
                if (now >= trap.lethalAt()
                    && now <= trap.closeAt()
                    && victim.getY() < trap.floor().getY()+.7
                    && victim.getY() > trap.floor().getY()-2.5
                    && victim.distanceToSqr(Vec3.atCenterOf(trap.floor())) < 7) {
                    victim.hurtServer(level, level.damageSources().cactus(), 1000.0F);
                    level.sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY()+.5, victim.getZ(),
                        20,.3,.3,.3,.08);
                }
            }
            if (now >= trap.closeAt()) {
                if (level.getBlockState(trap.floor()).isAir())
                    level.setBlock(trap.floor(), trap.original(), 3);
                it.remove();
            }
        }
    }
}
