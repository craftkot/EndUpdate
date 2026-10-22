package com.craftkot.livingmatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-only permission-gated admin inspection and local visualization. */
public final class AdminPanelService {
    private static final Set<UUID> HIGHLIGHTED = new HashSet<>();
    public static final int RADIUS=24;
    private AdminPanelService() {}

    public static void send(ServerPlayer player) {
        ServerLevel level=player.level();
        BlockPos focus=player.blockPosition();
        List<BlockPos> near=MimicData.get(level).nearby(focus,RADIUS,768);
        // Exact count nearby is capped to avoid trusting a huge unbounded reply.
        List<BlockPos> brains=BrainData.get(level).brains();
        int localBrains=0;
        for(BlockPos pos:brains) if(pos.distSqr(focus)<=RADIUS*RADIUS) localBrains++;
        int beasts=level.getEntitiesOfClass(ChthonicMassEntity.class,
            new AABB(focus).inflate(RADIUS)).size();
        near.sort(Comparator.comparingDouble(p->p.distSqr(focus)));
        PacketDistributor.sendToPlayer(player,new AdminPanelPacket(
            MimicData.get(level).count(),near.size(),brains.size(),localBrains,
            beasts,HIGHLIGHTED.contains(player.getUUID()),
            List.copyOf(near.subList(0,Math.min(24,near.size())))));
    }

    public static boolean toggle(ServerPlayer player) {
        boolean enabled;
        if (!HIGHLIGHTED.add(player.getUUID())) {
            HIGHLIGHTED.remove(player.getUUID()); enabled=false;
        } else enabled=true;
        send(player);
        return enabled;
    }

    public static int purgeNearby(ServerPlayer player) {
        ServerLevel level=player.level();
        int count=0;
        List<BlockPos> infected=MimicData.get(level).nearby(player.blockPosition(),12,2048);
        for(BlockPos pos:infected) {
            MimicData.get(level).cure(pos);
            if(BrainData.get(level).isBrain(pos)) BrainData.get(level).remove(pos);
            count++;
        }
        send(player);
        return count;
    }

    public static void tick(ServerLevel level) {
        if(level.getGameTime()%20!=0 || HIGHLIGHTED.isEmpty()) return;
        for(ServerPlayer player:level.players()) {
            if(!HIGHLIGHTED.contains(player.getUUID())) continue;
            BlockPos focus=player.blockPosition();
            List<BlockPos> nearby=MimicData.get(level).nearby(focus,24,64);
            for(BlockPos pos:nearby) {
                // Only show to the operator. Do NOT reveal infection to
                // everybody in multiplayer or generate additional world blocks.
                level.sendParticles(player,ParticleTypes.END_ROD,false,true,
                    pos.getX()+.5,pos.getY()+.6,pos.getZ()+.5,
                    1,.08,.08,.08,.002);
            }
            for(BlockPos brain:BrainData.get(level).brains()) {
                if(brain.distSqr(focus)>24*24)continue;
                level.sendParticles(player,ParticleTypes.SOUL_FIRE_FLAME,false,true,
                    brain.getX()+.5,brain.getY()+.9,brain.getZ()+.5,
                    3,.15,.15,.15,.004);
            }
        }
    }
}
