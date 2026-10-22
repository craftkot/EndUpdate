package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Smooth, attached, CHORUS-style animation for ordinary solid material.
 *
 * Original VANILLA blocks remain fully intact and are masked locally
 * only while awake. The client moves rigid connected pieces, opens an
 * articulated mouth from the same material; NO extra manifest blocks
 * are inserted into world air.
 */
public final class MatterVisualAttack {
    private static final int CHARGE=28, RECOVER=20, MAX_BRANCH=9;
    private static final Map<ServerLevel, Map<Long,Attack>> ACTIVE=new WeakHashMap<>();
    private static final Map<ServerLevel, Map<Long,Long>> COOLDOWNS=new WeakHashMap<>();

    private static final class Attack {
        final BlockPos tip;
        final UUID target;
        final List<BlockPos> branch;
        final MimicKind kind;
        final long started;
        boolean struck;
        Attack(BlockPos tip, UUID target, List<BlockPos> branch, MimicKind kind, long started){
            this.tip=tip.immutable(); this.target=target; this.branch=List.copyOf(branch);
            this.kind=kind; this.started=started;
        }
    }
    private MatterVisualAttack() {}
    public static int activeCount(ServerLevel level) {
        return ACTIVE.getOrDefault(level,Map.of()).size();
    }
    public static boolean start(ServerLevel level, BlockPos tip, ServerPlayer player, boolean forced) {
        if (!level.hasChunkAt(tip) || !MimicData.get(level).isInfected(tip)
            || !MimicData.eligible(level,tip) || (!forced && player.isCreative())) return false;
        MimicKind kind=MimicKind.of(level.getBlockState(tip));
        if (kind==MimicKind.LEAF) return false;
        long now=level.getGameTime();
        Map<Long,Attack> active=ACTIVE.computeIfAbsent(level, ignored -> new HashMap<>());
        Map<Long,Long> cd=COOLDOWNS.computeIfAbsent(level, ignored -> new HashMap<>());
        if (active.containsKey(tip.asLong()) || (!forced && now<cd.getOrDefault(tip.asLong(),0L))) return false;
        if (!forced && player.distanceToSqr(Vec3.atCenterOf(tip)) > kind.range()*kind.range()) return false;

        List<BlockPos> branch=connectedBranch(level,tip,kind);
        // For a single infected block, duplicate its position as a zero-length
        // fixed pivot. ONLY its original cube opens as the mouth; the rig
        // creates no fake supporting segment or fresh air block.
        if (branch.size()<2) branch=List.of(tip,tip);
        for (Attack other:active.values())
            for (int i=1;i<branch.size();i++)
                if (other.branch.contains(branch.get(i))) return false;
        active.put(tip.asLong(),new Attack(tip,player.getUUID(),branch,kind,now));
        cd.put(tip.asLong(), now+CHARGE+RECOVER+80);
        Vec3 target=player.position().add(0,player.getBbHeight()*.50,0);
        send(level,tip,target,kind,MatterPosePacket.START,branch);
        level.playSound(null,tip,SoundEvents.CHORUS_FLOWER_GROW,
            SoundSource.HOSTILE,.8F,kind==MimicKind.STONE?.58F:1.1F);
        return true;
    }

    public static void tick(ServerLevel level) {
        var current=ACTIVE.get(level);
        if (current==null || current.isEmpty()) return;
        long now=level.getGameTime();
        Iterator<Attack> iter=current.values().iterator();
        while(iter.hasNext()) {
            Attack a=iter.next();
            ServerPlayer victim=level.getServer().getPlayerList().getPlayer(a.target);
            boolean alive=victim!=null && victim.isAlive() && victim.level()==level
                && !victim.isSpectator()
                && level.hasChunkAt(a.tip)
                && MimicData.get(level).isInfected(a.tip)
                && MimicData.eligible(level,a.tip);
            for(BlockPos p:a.branch) {
                if (!level.hasChunkAt(p) || !MimicData.eligible(level,p)) {alive=false;break;}
            }
            if(!alive || now-a.started>=CHARGE+RECOVER){
                send(level,a.tip,Vec3.atCenterOf(a.tip),a.kind,MatterPosePacket.STOP,a.branch);
                iter.remove();
                continue;
            }
            Vec3 aim=victim.position().add(0,victim.getBbHeight()*.50,0);
            if((now-a.started)%7==0)
                send(level,a.tip,aim,a.kind,MatterPosePacket.UPDATE,a.branch);
            if(!a.struck && now-a.started>=CHARGE-3){
                a.struck=true;
                if(victim.isCreative()) continue;
                List<Vec3> rest=a.branch.stream().map(Vec3::atCenterOf).toList();
                Vec3 head=MatterRig.evaluateBranch(
                    MatterProfile.forFlower(level.getSeed(),a.tip),rest,aim,.98,false).mouth();
                if (head.distanceToSqr(aim)>2.5*2.5) continue;
                float damage=a.kind.damage();
                if(a.kind==MimicKind.STONE) damage+=Math.min(4.0F,a.branch.size());
                if(victim.hurtServer(level,level.damageSources().cactus(),damage)){
                    MimicData.get(level).spreadAfterHunt(level,a.tip,a.kind,false);
                    if(a.kind==MimicKind.WOOD || a.kind==MimicKind.SAND)
                        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,70,1));
                    if(a.kind==MimicKind.SPORE)
                        victim.addEffect(new MobEffectInstance(MobEffects.POISON,80,0));
                    if(a.kind==MimicKind.ORE || a.kind==MimicKind.WILD)
                        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,60,0));
                    level.playSound(null,a.tip,SoundEvents.SHULKER_BULLET_HIT,
                        SoundSource.HOSTILE,1.0F,.75F);
                }
            }
        }
    }

    private static void send(ServerLevel level,BlockPos tip,Vec3 aim,MimicKind kind,
                             byte operation,List<BlockPos> branch) {
        PacketDistributor.sendToPlayersNear(level,null,
            tip.getX()+.5,tip.getY()+.5,tip.getZ()+.5,64,
            new MatterPosePacket(tip,aim,
                MatterProfile.forFlower(level.getSeed(),tip).packed(),
                CHARGE,RECOVER,false,(byte)(20+kind.ordinal()),operation,branch));
    }

    /** Face-connected, originally existing infected branch. No floating cubes.
     * If only one block is infected its own geometry opens instead. */
    private static List<BlockPos> connectedBranch(ServerLevel level,BlockPos tip,MimicKind kind){
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        Map<Long,BlockPos> parent=new HashMap<>();
        Map<Long,Integer> distance=new HashMap<>();
        Set<Long> seen=new HashSet<>();
        queue.add(tip);
        seen.add(tip.asLong());
        distance.put(tip.asLong(),0);
        BlockPos far=tip;
        while(!queue.isEmpty() && seen.size()<48){
            BlockPos current=queue.removeFirst();
            int d=distance.get(current.asLong());
            if(d>distance.get(far.asLong())) far=current;
            if(d>=MAX_BRANCH-1) continue;
            for(Direction side:Direction.values()){
                BlockPos next=current.relative(side);
                if(!seen.add(next.asLong())) continue;
                if(!level.hasChunkAt(next)
                    || !MimicData.get(level).isInfected(next)
                    || !MimicData.eligible(level,next)
                    || MimicKind.of(level.getBlockState(next))!=kind) continue;
                parent.put(next.asLong(),current);
                distance.put(next.asLong(),d+1);
                queue.addLast(next);
            }
        }
        ArrayList<BlockPos> path=new ArrayList<>();
        BlockPos p=far;
        path.add(p);
        while(!p.equals(tip) && path.size()<MAX_BRANCH){
            p=parent.get(p.asLong());
            if(p==null)break;
            path.add(p);
        }
        return List.copyOf(path);
    }
}
