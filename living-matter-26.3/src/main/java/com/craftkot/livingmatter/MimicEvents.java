package com.craftkot.livingmatter;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
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
                        y <= Math.min(level.getMaxY() - 1, center.getY() + 8); y++) {
                        BrainData.get(level).discover(level, new BlockPos(x, y, z));
                    }
                }
            }
        }

        if (player.isCreative() || player.tickCount % BEHAVIOUR_SCAN_INTERVAL != 0) return;
        // Rare natural awakening of an ALREADY INFECTED, isolated 3-8-block
        // cluster. Ordinary trees/walls/houses are never eligible walkers.
        if (player.tickCount % 120 == 0 && level.getRandom().nextInt(5) == 0) {
            boolean awakened = false;
            for (int x = center.getX()-3; x <= center.getX()+3 && !awakened; x++)
                for (int z=center.getZ()-3; z<=center.getZ()+3 && !awakened; z++) {
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    for (int y=center.getY()-1; y<=center.getY()+2; y++) {
                        BlockPos p = new BlockPos(x,y,z);
                        if (data.isInfected(p) && WalkingColony.tryStart(level,p,player)) {
                            awakened=true; break;
                        }
                    }
                }
            if (awakened) return;
        }
        // A concealed floor mimic is only triggered by someone actually
        // stepping directly on its face, never by distant approach.
        BlockPos footFloor = BlockPos.containing(player.getX(), player.getY() - .12, player.getZ());
        if (data.isInfected(footFloor) && level.getRandom().nextInt(5) == 0
            && FloorMawTrap.tryOpen(level, footFloor, player)) return;
        // Trees can reach from the canopy even if no infected leaf is close
        // enough for the ordinary short-range block attack. One hunt/player.
        if (!LeafRigAttack.isHunting(level, player)) {
            BlockPos nearest = null;
            double best = 81.0;
            for (int x = center.getX() - 8; x <= center.getX() + 8; x++) {
                for (int z = center.getZ() - 8; z <= center.getZ() + 8; z++) {
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    for (int y = Math.max(level.getMinY(), center.getY() - 3);
                        y <= Math.min(level.getMaxY() - 1, center.getY() + 8); y++) {
                        BlockPos leaf = new BlockPos(x, y, z);
                        if (!data.isInfected(leaf) || !level.getBlockState(leaf).is(BlockTags.LEAVES))
                            continue;
                        double d = player.distanceToSqr(x + .5, y + .5, z + .5);
                        if (d < best) { best = d; nearest = leaf; }
                    }
                }
            }
            if (nearest != null && LeafRigAttack.tryBegin(level, nearest, player, false)) return;
        }
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
                    // Vanilla leaves use their own attached, moving canopy,
                    // not a free-floating temporary manifest block.
                    if (level.getBlockState(spot).is(BlockTags.LEAVES)) continue;
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
        // The first mining attempt can be cancelled by an ambush. Never
        // kill the brain until the real vanilla block is actually breakable.
        if (!data.isInfected(pos)) {
            BrainData.get(level).remove(pos);
            return;
        }
        // The first mining attempt exposes the impostor. Once it has recoiled
        // it can be killed with a normal block break (original vanilla loot).
        // Real foliage never creates manifest geometry. Mining an infected
        // leaf either wakes its attached tree or yields the ordinary block.
        if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
            if (LeafRigAttack.tryBegin(level, pos, player, true))
                event.setCanceled(true);
            else {
                data.cure(pos);
                BrainData.get(level).remove(pos);
            }
            return;
        }
        if (awaken(level, pos, player, true)) {
            event.setCanceled(true);
        } else {
            data.cure(pos);
            BrainData.get(level).remove(pos);
        }
    }

    @SubscribeEvent
    public void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        FloorMawTrap.tick(level);
        LeafRigAttack.tick(level);
        BrainData.get(level).tick(level);
        WalkingColony.tick(level);
        MatterVisualAttack.tick(level);
        AdminPanelService.tick(level);
        ChthonicAwakening.natural(level);
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

    /**
     * The old fake-block reveal has been replaced by the real chorus-style
     * pose renderer. All displayed pieces are original connected vanilla
     * material. There is no new block in neighboring air and no entity.
     */
    private static boolean awaken(ServerLevel level, BlockPos pos, ServerPlayer target,
                                  boolean force) {
        if (!MimicData.get(level).isInfected(pos)
            || !MimicData.eligible(level,pos)) return false;
        if (level.getBlockState(pos).is(BlockTags.LEAVES))
            return LeafRigAttack.tryBegin(level,pos,target,force);
        return MatterVisualAttack.start(level,pos,target,force);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("livingmatter")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("panel").executes(ctx -> {
                AdminPanelService.send(ctx.getSource().getPlayerOrException());
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("vision").executes(ctx -> {
                ServerPlayer p=ctx.getSource().getPlayerOrException();
                boolean enabled=AdminPanelService.toggle(p);
                ctx.getSource().sendSuccess(() -> Component.literal(
                    enabled?"Подсветка заражённых блоков включена.":"Подсветка заражённых блоков выключена."),false);
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("purgearea").executes(ctx -> {
                ServerPlayer p=ctx.getSource().getPlayerOrException();
                int cleaned=AdminPanelService.purgeNearby(p);
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "Заражение очищено в радиусе 12 блоков: "+cleaned+" блоков."),false);
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("infect").executes(ctx -> doAction(ctx.getSource(), "infect")))
            .then(Commands.literal("spread").executes(ctx -> doAction(ctx.getSource(), "spread")))
            .then(Commands.literal("cure").executes(ctx -> doAction(ctx.getSource(), "cure")))
            .then(Commands.literal("inspect").executes(ctx -> doAction(ctx.getSource(), "inspect")))
            .then(Commands.literal("awaken").executes(ctx -> doAction(ctx.getSource(), "awaken")))
            .then(Commands.literal("leaf").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                HitResult hit = player.pick(6.0, 1.0F, false);
                if (!(hit instanceof BlockHitResult blockHit)
                    || hit.getType() != HitResult.Type.BLOCK) return 0;
                ServerLevel level = ctx.getSource().getLevel();
                BlockPos pos = blockHit.getBlockPos();
                if (!level.getBlockState(pos).is(BlockTags.LEAVES)) {
                    ctx.getSource().sendFailure(Component.literal("Aim at vanilla tree leaves."));
                    return 0;
                }
                boolean started = MimicData.get(level).mark(level, pos)
                    && LeafAmbush.tryBegin(level, pos, player, true);
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "Leaf ambush: " + (started ? "moving existing attached leaves"
                        : "need 10+ connected leaves and a trunk, or a hunt is active")), false);
                return started ? 1 : 0;
            }))
            .then(Commands.literal("floor").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                BlockPos ground = player.blockPosition().below();
                boolean opened = FloorMawTrap.tryOpen(ctx.getSource().getLevel(), ground, player);
                ctx.getSource().sendSuccess(() -> Component.literal("Floor maw: " + (opened ? "opened" : "requires infected solid floor above a two-block air cavity, Survival and centered player")), false);
                return opened ? 1 : 0;
            }))
            .then(Commands.literal("brain").executes(ctx -> doAction(ctx.getSource(), "brain")))
            .then(Commands.literal("unbrain").executes(ctx -> doAction(ctx.getSource(), "unbrain")))
            .then(Commands.literal("colony").executes(ctx -> doAction(ctx.getSource(), "colony")))
            .then(Commands.literal("version").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "Living Matter " + LivingMatter.BUILD_ID
                    + " | chthon, chthontest, mass; remove previous Living Matter JARs"), false);
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("chthon").executes(ctx -> doAction(ctx.getSource(), "chthon")))
            .then(Commands.literal("chthontest").executes(ctx -> doAction(ctx.getSource(), "chthontest")))
            .then(Commands.literal("chthonat")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> {
                        ServerLevel level = ctx.getSource().getLevel();
                        BlockPos chosen = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                        String result = ChthonicAwakening.awaken(level,chosen,true);
                        if (!result.startsWith("Created")) {
                            ctx.getSource().sendFailure(Component.literal(result));
                            return 0;
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(result),false);
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(Commands.literal("mass").executes(ctx -> doAction(ctx.getSource(), "mass")))
            .then(Commands.literal("walk").executes(ctx -> doAction(ctx.getSource(), "walk")))
            .then(Commands.literal("walktest").executes(ctx -> doAction(ctx.getSource(), "walktest")))
            .then(Commands.literal("brains").executes(ctx -> {
                BrainData brains = BrainData.get(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "Living Matter brains: " + brains.brainCount()
                    + ", grown infected cells: " + brains.memberCount()), false);
                return brains.brainCount();
            }))
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
            case "mass" -> {
                int infected = ChthonicAwakening.preview(level,pos,true);
                int structure = ChthonicAwakening.preview(level,pos,false);
                source.sendSuccess(() -> Component.literal(
                    "Chthonic body: " + infected + " already infected blocks; "
                    + structure + " matching existing blocks for test."
                    + " Tiers: 4 / 12 / 32 / 64. Hard limit 96."),false);
            }
            case "chthon", "chthontest" -> {
                String result = ChthonicAwakening.awaken(level,pos,action.equals("chthontest"));
                if (!result.startsWith("Created")) {
                    source.sendFailure(Component.literal("Chthonic mutation: "+result));
                    return 0;
                }
                source.sendSuccess(() -> Component.literal(result),false);
                return Command.SINGLE_SUCCESS;
            }
            case "walk" -> {
                if (!data.isInfected(pos)) {
                    String problem = WalkingColony.prepareAndWalk(level, pos, player);
                    if (problem != null) {
                        source.sendFailure(Component.literal("Walking creature: " + problem));
                        return 0;
                    }
                    break;
                }
                String problem = WalkingColony.diagnose(level, pos);
                if (problem != null) {
                    source.sendFailure(Component.literal("Walking creature: " + problem
                        + " | For an isolated new group use /livingmatter walktest."));
                    return 0;
                }
                if (!WalkingColony.tryStart(level, pos, player)) {
                    source.sendFailure(Component.literal("Walking creature cannot start now (try again)."));
                    return 0;
                }
            }
            case "walktest" -> {
                String problem = WalkingColony.prepareAndWalk(level, pos, player);
                if (problem != null) {
                    source.sendFailure(Component.literal("Walking test: " + problem));
                    return 0;
                }
            }
            case "brain" -> {
                if (!BrainData.get(level).seed(level, pos)) {
                    source.sendFailure(Component.literal(
                        "Cannot seed brain: target not eligible, another brain within 14 blocks, or colony cap reached."));
                    return 0;
                }
            }
            case "unbrain" -> {
                if (!BrainData.get(level).remove(pos)) {
                    source.sendFailure(Component.literal("Target block is not a brain."));
                    return 0;
                }
            }
            case "colony" -> {
                int cells = BrainData.get(level).localCount(pos, 12);
                var body = BrainData.get(level).bodyAt(level, pos, 20);
                source.sendSuccess(() -> Component.literal(
                    "Colony: " + cells + " infected brain-connected cells nearby, "
                    + body.size() + " connected same-material body cells"), false);
            }
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
                boolean awake = level.getBlockState(pos).is(BlockTags.LEAVES)
                    ? LeafAmbush.tryBegin(level, pos, player, true)
                    : awaken(level, pos, player, true);
                if (!awake) {
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
