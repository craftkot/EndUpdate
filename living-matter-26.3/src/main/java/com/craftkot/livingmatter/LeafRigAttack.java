package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Chorus-style, SMOOTH foliage animation. Never writes block states.
 * The actual, attached vanilla leaf path is masked CLIENT-SIDE only while
 * active; the corresponding rigid voxels follow a FABRIK bending rig.
 * First pivot is fixed on the original tree. Nothing spawns or detaches.
 */
public final class LeafRigAttack {
    private static final int CHARGE = 32, RECOVER = 19, MAX_DISTANCE = 9;
    private static final Map<ServerLevel, Map<Long, Attack>> ACTIVE = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<Long, Long>> COOLDOWN = new WeakHashMap<>();

    private static final class Attack {
        final BlockPos tip;
        final UUID targetId;
        final List<BlockPos> branch;
        final long start;
        boolean struck;
        Attack(BlockPos tip, UUID victim, List<BlockPos> branch, long start) {
            this.tip = tip.immutable();
            this.targetId = victim;
            this.branch = List.copyOf(branch);
            this.start = start;
        }
    }
    private LeafRigAttack() {}

    public static boolean isHunting(ServerLevel level, ServerPlayer target) {
        for (Attack attack : ACTIVE.getOrDefault(level, Map.of()).values())
            if (attack.targetId.equals(target.getUUID())) return true;
        return false;
    }

    public static int activeCount(ServerLevel level) {
        return ACTIVE.getOrDefault(level, Map.of()).size();
    }

    public static boolean tryBegin(ServerLevel level, BlockPos infectedTip, ServerPlayer victim,
                                   boolean forced) {
        if (!level.hasChunkAt(infectedTip)
            || !level.getBlockState(infectedTip).is(BlockTags.LEAVES)
            || !MimicData.get(level).isInfected(infectedTip)
            || (!forced && (victim.isCreative() || victim.isSpectator()))
            || victim.distanceToSqr(Vec3.atCenterOf(infectedTip)) > MAX_DISTANCE * MAX_DISTANCE)
            return false;
        Map<Long, Attack> active = ACTIVE.computeIfAbsent(level, ignored -> new HashMap<>());
        if (active.containsKey(infectedTip.asLong()) || isHunting(level, victim)) return false;
        long now = level.getGameTime();
        Map<Long, Long> cooldown = COOLDOWN.computeIfAbsent(level, ignored -> new HashMap<>());
        if (!forced && now < cooldown.getOrDefault(infectedTip.asLong(), -1L)) return false;
        List<BlockPos> branch = treePath(level, infectedTip);
        if (branch.size() < 2) return false;
        // No overlapping active leaf piece can be moved by two brains.
        for (Attack other : active.values())
            for (int i=1;i<branch.size();i++)
                if (other.branch.contains(branch.get(i))) return false;
        List<Vec3> rest = branch.stream().map(Vec3::atCenterOf).toList();
        Vec3 destination = victim.position().add(0, victim.getBbHeight() * .55, 0);
        if (!MatterRig.canReach(rest, destination)) return false;
        active.put(infectedTip.asLong(),
            new Attack(infectedTip, victim.getUUID(), branch, now));
        cooldown.put(infectedTip.asLong(), now + CHARGE + RECOVER + 65);
        PacketDistributor.sendToPlayersNear(level, null,
            infectedTip.getX()+.5,infectedTip.getY()+.5,infectedTip.getZ()+.5,64,
            packet(level, infectedTip, destination, MatterPosePacket.START, branch));
        level.playSound(null, infectedTip, SoundEvents.CHORUS_FLOWER_GROW,
            SoundSource.HOSTILE, .8F, .9F);
        return true;
    }

    private static MatterPosePacket packet(ServerLevel level, BlockPos tip,
                                           Vec3 target, byte op, List<BlockPos> path) {
        return new MatterPosePacket(tip, target,
            MatterProfile.forFlower(level.getSeed(), tip).packed(),
            CHARGE, RECOVER, false, MatterPosePacket.PLAYER_BITE, op, path);
    }

    public static void tick(ServerLevel level) {
        Map<Long, Attack> active = ACTIVE.get(level);
        if (active == null || active.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Attack> it = active.values().iterator();
        while (it.hasNext()) {
            Attack a = it.next();
            ServerPlayer victim = level.getServer().getPlayerList().getPlayer(a.targetId);
            boolean valid = victim != null && victim.isAlive()
                && victim.level() == level && !victim.isSpectator()
                && level.hasChunkAt(a.tip)
                && level.getBlockState(a.tip).is(BlockTags.LEAVES)
                && MimicData.get(level).isInfected(a.tip);
            if (valid) {
                for (BlockPos p : a.branch) {
                    if (!level.hasChunkAt(p) || !level.getBlockState(p).is(BlockTags.LEAVES)) {
                        valid = false;
                        break;
                    }
                }
            }
            if (!valid || now - a.start >= CHARGE + RECOVER) {
                PacketDistributor.sendToPlayersNear(level, null,
                    a.tip.getX()+.5,a.tip.getY()+.5,a.tip.getZ()+.5,64,
                    packet(level, a.tip, Vec3.atCenterOf(a.tip),
                        MatterPosePacket.STOP, a.branch));
                it.remove();
                continue;
            }
            Vec3 aim = victim.position().add(0, victim.getBbHeight() * .55, 0);
            if ((now - a.start) % 7 == 0) {
                PacketDistributor.sendToPlayersNear(level, null,
                    a.tip.getX()+.5,a.tip.getY()+.5,a.tip.getZ()+.5,64,
                    packet(level, a.tip, aim, MatterPosePacket.UPDATE, a.branch));
            }
            if (!a.struck && now - a.start >= CHARGE - 4) {
                a.struck = true;
                if (victim.isCreative()) continue; // admins may preview safely
                List<Vec3> rest = a.branch.stream().map(Vec3::atCenterOf).toList();
                Vec3 head = MatterRig.evaluateBranch(
                    MatterProfile.forFlower(level.getSeed(), a.tip), rest,
                    aim, .98, false).mouth();
                if (head.distanceToSqr(aim) > 2.5*2.5) continue;
                if (victim.hurtServer(level, level.damageSources().cactus(), 3.0F)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 65, 2));
                    Vec3 pull = Vec3.atCenterOf(a.branch.get(0)).subtract(victim.position());
                    if (pull.lengthSqr() > .04) {
                        Vec3 velocity = pull.normalize().scale(.33);
                        victim.push(velocity.x, .05, velocity.z);
                    }
                    level.playSound(null, a.tip, SoundEvents.SHULKER_BULLET_HIT,
                        SoundSource.HOSTILE, 1.0F, .7F);
                }
            }
        }
    }

    /**
     * Shortest attached foliage path tip -> trunk, then reverse to
     * trunk-side pivot -> infected tip. All nodes are PREEXISTING
     * identical leaf blocks, linked face-to-face, in loaded chunks.
     */
    private static List<BlockPos> treePath(ServerLevel level, BlockPos tip) {
        var leafBlock = level.getBlockState(tip).getBlock();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        Map<Long, BlockPos> parents = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        todo.add(tip);
        seen.add(tip.asLong());
        BlockPos rootSide = null;
        while (!todo.isEmpty() && seen.size() < 160) {
            BlockPos current = todo.removeFirst();
            for (Direction side : Direction.values()) {
                BlockPos near = current.relative(side);
                if (level.hasChunkAt(near) && level.getBlockState(near).is(BlockTags.LOGS)) {
                    rootSide = current;
                    break;
                }
            }
            if (rootSide != null) break;
            int distance = 0;
            BlockPos p = current;
            while (!p.equals(tip) && distance <= 12) {
                p = parents.get(p.asLong());
                if (p == null) break;
                distance++;
            }
            if (distance >= 10) continue;
            for (Direction side : Direction.values()) {
                BlockPos near = current.relative(side);
                if (!seen.contains(near.asLong()) && level.hasChunkAt(near)
                    && near.distSqr(tip) <= 85
                    && level.getBlockState(near).getBlock() == leafBlock) {
                    seen.add(near.asLong());
                    parents.put(near.asLong(), current);
                    todo.addLast(near);
                }
            }
        }
        if (rootSide == null) return List.of();
        ArrayList<BlockPos> path = new ArrayList<>();
        BlockPos p = rootSide;
        path.add(p);
        while (!p.equals(tip) && path.size() < 24) {
            p = parents.get(p.asLong());
            if (p == null) return List.of();
            path.add(p);
        }
        // If the infected tip hugs the trunk, extend a short branch
        // laterally only if it already exists. Never synthesize pieces.
        return List.copyOf(path);
    }
}
