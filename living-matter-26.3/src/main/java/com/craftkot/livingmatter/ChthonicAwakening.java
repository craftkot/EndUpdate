package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Source of every beast = REAL infected blocks from the world.
 * No ordinary terrain is consumed; the living brain itself remains anchored.
 * Each consumed source block contributes ONE voxel to the monster model.
 */
public final class ChthonicAwakening {
    private ChthonicAwakening() {}

    private static List<BlockPos> connected(ServerLevel level, BlockPos start,
                                            int cap, boolean onlyInfected) {
        if (!level.hasChunkAt(start)) return List.of();
        BlockState seed = level.getBlockState(start);
        if (!MimicData.eligible(level,start) || BrainData.get(level).isBrain(start))
            return List.of();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        ArrayList<BlockPos> collected = new ArrayList<>();
        queue.add(start.immutable());
        visited.add(start.asLong());
        while (!queue.isEmpty() && collected.size() < cap) {
            BlockPos position = queue.removeFirst();
            if (!level.hasChunkAt(position)
                || !MimicData.eligible(level,position)
                || BrainData.get(level).isBrain(position)) continue;
            BlockState state = level.getBlockState(position);
            if (onlyInfected && !MimicData.get(level).isInfected(position)) continue;
            if (!onlyInfected && state.getBlock() != seed.getBlock()) continue;
            if (position.distSqr(start) > 160) continue;
            collected.add(position.immutable());
            for (Direction side : Direction.values()) {
                BlockPos next = position.relative(side);
                if (visited.add(next.asLong())) queue.addLast(next);
            }
        }
        return List.copyOf(collected);
    }

    /** A usable size-preview for operators, without damaging the world. */
    public static int preview(ServerLevel level, BlockPos origin, boolean infected) {
        return connected(level,origin,ChthonicMassEntity.MAX_BLOCKS,infected).size();
    }

    public static String awaken(ServerLevel level, BlockPos origin, boolean test) {
        if (!level.hasChunkAt(origin) || !MimicData.eligible(level,origin))
            return "Aim at a loaded solid, nontechnical block.";
        if (BrainData.get(level).isBrain(origin))
            return "The selected block is the living brain; it cannot leave the colony.";
        if (!test && !MimicData.get(level).isInfected(origin))
            return "Source block is not infected. Use /livingmatter brain, infect or chthontest.";
        // Test mode harvests only an explicitly selected connected patch of
        // the SAME block type. It never absorbs nearby unrelated blocks.
        List<BlockPos> body=connected(level,origin,ChthonicMassEntity.MAX_BLOCKS,test?false:true);
        int n=body.size();
        if (n < ChthonicMassEntity.MIN_BLOCKS)
            return "Only "+n+" source blocks. At least 4 are needed; 12/32/64 unlock larger horrors.";

        // The entity spawns at an exposed upper surface. Refuse a sealed cave
        // rather than trapping a large creature in solid rock.
        double sumX=0,sumZ=0;
        int top=Integer.MIN_VALUE;
        int[] composition=new int[MimicKind.values().length];
        Map<BlockPos,BlockState> original=new HashMap<>();
        for (BlockPos pos:body) {
            BlockState state=level.getBlockState(pos);
            original.put(pos,state);
            sumX+=pos.getX()+.5;
            sumZ+=pos.getZ()+.5;
            top=Math.max(top,pos.getY());
            composition[MimicKind.of(state).ordinal()]++;
        }
        double x=sumX/n,z=sumZ/n;
        BlockPos above=BlockPos.containing(x,top+1,z);
        // Require a real open sky pocket at the exit, but do not load chunks.
        for (int y=0;y<6;y++) {
            BlockPos check=above.above(y);
            if (!level.hasChunkAt(check) || !level.getBlockState(check).isAir())
                return "No open air above body at "+check.toShortString()
                    + ". Choose a surface colony, not a sealed cave.";
        }

        int material=0;
        for(int i=1;i<composition.length;i++)
            if(composition[i]>composition[material]) material=i;

        ChthonicMassEntity monster=new ChthonicMassEntity(
            LivingMatter.CHTHONIC_MASS.get(),level);
        monster.setPos(x,top+1.0,z);
        monster.assemble(n,MimicKind.values()[material]);
        if (!level.addFreshEntity(monster)) return "Could not spawn the chthonic creature.";

        // World edits happen ONLY after the entity is safely registered.
        // If any edit unexpectedly fails, roll back and remove the mob.
        for (BlockPos pos:body) {
            if (!level.setBlock(pos,Blocks.AIR.defaultBlockState(),3)) {
                for (var entry:original.entrySet())
                    if (level.getBlockState(entry.getKey()).isAir())
                        level.setBlock(entry.getKey(),entry.getValue(),3);
                monster.discard();
                return "Transformation aborted; vanilla source blocks restored.";
            }
        }
        for (BlockPos pos:body) MimicData.get(level).consume(pos);
        level.playSound(null,above,SoundEvents.WARDEN_EMERGE,SoundSource.HOSTILE,
            Math.min(3.0F,1.0F+n/40F),.45F);
        level.sendParticles(ParticleTypes.SOUL,monster.getX(),monster.getY()+1.0,monster.getZ(),
            Math.min(90,n*2),2.0,1.5,2.0,.02);
        String tier=n>=64?"COLOSSUS":n>=32?"ABOMINATION":n>=12?"HORROR":"LARVA";
        return "Created "+tier+" from "+n+" infected blocks: "
            + Math.round(monster.getMaxHealth())+" HP, "
            + (int)monster.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
            +" damage. The original blocks became its body.";
    }

    /** Every minute a sufficiently large infected brain can shed a creature.
     * World generation and new blocks never provide free creature mass. */
    public static void natural(ServerLevel level) {
        if(level.getGameTime()%1200!=0 || level.getRandom().nextInt(3)!=0) return;
        int formed=0;
        for(BlockPos brain:BrainData.get(level).brains()) {
            if (!level.hasChunkAt(brain)) continue;
            if (level.getEntitiesOfClass(ChthonicMassEntity.class,
                    new AABB(brain).inflate(48)).size() >= 2) continue;
            BlockPos candidate=null;
            // Search directly attached branches first; the core itself cannot
            // be absorbed by its children.
            for(Direction direction:Direction.values()) {
                BlockPos next=brain.relative(direction);
                if (level.hasChunkAt(next) && MimicData.get(level).isInfected(next)
                    && !BrainData.get(level).isBrain(next)
                    && preview(level,next,true)>=12) {
                    candidate=next;break;
                }
            }
            if(candidate!=null && awaken(level,candidate,false).startsWith("Created"))
                if(++formed>=2) return;
        }
    }
}
