package com.craftkot.livingchorus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-only, per-dimension hidden markers: the actual world block remains
 * exactly minecraft:chorus_flower, retaining the vanilla block shape, item ID,
 * client blockstate and minimap appearance.
 */
public final class MimicData extends SavedData {
    public static final SavedDataType<MimicData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(LivingChorus.MOD_ID, "hidden_mimic_flowers"),
        MimicData::new,
        RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().fieldOf("infected").forGetter(data -> new ArrayList<>(data.infected)),
            Codec.LONG.listOf().fieldOf("safe").forGetter(data -> new ArrayList<>(data.safe)),
            Codec.LONG.listOf().optionalFieldOf("stems", List.of())
                .forGetter(data -> new ArrayList<>(data.infectedStems)),
            Codec.LONG.listOf().optionalFieldOf("growthOrigins", List.of())
                .forGetter(data -> new ArrayList<>(data.growthOrigins))
        ).apply(instance, MimicData::new))
    );

    private static final int INFECTION_THRESHOLD = 3277; // approx. 5% of eligible trees
    private static final int MAX_COMPONENT_BLOCKS = 4096; // hard cap, never load chunks
    private final Set<Long> infected;
    private final Set<Long> safe;
    private final Set<Long> infectedStems;
    // A former infected flower which became a stem during vanilla growth.
    // Stored separately so infection survives the gap before new tips appear.
    private final Set<Long> growthOrigins;

    public MimicData() {
        this(List.of(), List.of(), List.of(), List.of());
    }

    private MimicData(List<Long> infected, List<Long> safe, List<Long> stems,
                      List<Long> growthOrigins) {
        this.infected = new HashSet<>(infected);
        this.safe = new HashSet<>(safe);
        this.infectedStems = new HashSet<>(stems);
        this.growthOrigins = new HashSet<>(growthOrigins);
    }

    public static MimicData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isInfected(BlockPos pos) {
        return infected.contains(pos.asLong());
    }

    public int count() {
        return infected.size();
    }

    public int infectedStemCount() {
        return infectedStems.size();
    }

    public int growingOriginCount() {
        return growthOrigins.size();
    }

    /**
     * Vanilla flower growth replaces the old chorus_flower with chorus_plant.
     * Transfer the infection marker to that new stem rather than forgetting it.
     * This method must also be used in the AI's stale-block checks, because
     * those may run earlier than the periodic infection spread task.
     */
    public void reconcileFlower(ServerLevel level, BlockPos pos) {
        long id = pos.asLong();
        if (!infected.contains(id) || !level.hasChunkAt(pos)) return;
        if (level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) return;
        boolean changed = infected.remove(id);
        if (level.getBlockState(pos).is(Blocks.CHORUS_PLANT)) {
            changed |= infectedStems.add(id);
            changed |= growthOrigins.add(id);
            // Do NOT infect all new buds. The next reconciliation chooses
            // exactly ONE descendant tip from this original infected stem.
        }
        if (changed) setDirty();
    }

    /** Examine actual infected flowers before any other code discards them. */
    public void reconcileAllGrowth(ServerLevel level) {
        for (Long id : new ArrayList<>(infected)) {
            BlockPos pos = BlockPos.of(id);
            if (level.hasChunkAt(pos)) reconcileFlower(level, pos);
        }
    }

    /**
     * Traverse just the contiguous vanilla chorus tree in currently loaded
     * chunks. Adjacent touching stalks form one structure by definition.
     * A null result means the search exceeds the safety cap; in that case
     * never create a second infected branch by guessing.
     */
    private static Set<Long> connectedChorus(ServerLevel level, BlockPos start) {
        if (!level.hasChunkAt(start) || !isChorusBlock(level, start)) return Set.of();
        HashSet<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.asLong());
        queue.addLast(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos at = queue.removeFirst();
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = at.relative(direction);
                long key = next.asLong();
                if (seen.contains(key) || !level.hasChunkAt(next)
                    || !isChorusBlock(level, next)) continue;
                if (seen.size() >= MAX_COMPONENT_BLOCKS) return null;
                seen.add(key);
                queue.addLast(next.immutable());
            }
        }
        return seen;
    }

    private static boolean isChorusBlock(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.CHORUS_PLANT)
            || level.getBlockState(pos).is(Blocks.CHORUS_FLOWER);
    }

    /** Choose one actual child of a grown infected stem; other buds stay normal. */
    private BlockPos singleChild(ServerLevel level, long origin) {
        BlockPos stem = BlockPos.of(origin);
        if (!level.hasChunkAt(stem)
            || !level.getBlockState(stem).is(Blocks.CHORUS_PLANT)) return null;
        ArrayList<BlockPos> buds = new ArrayList<>();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos child = stem.relative(direction);
            if (level.hasChunkAt(child) && !safe.contains(child.asLong())
                && level.getBlockState(child).is(Blocks.CHORUS_FLOWER))
                buds.add(child.immutable());
        }
        if (buds.isEmpty()) return null;
        buds.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        int index = (int) Math.floorMod(mix64(level.getSeed() ^ origin), buds.size());
        return buds.get(index);
    }

    /**
     * ONE infection source / terminal branch per physically connected tree.
     * Previous save files can contain many infected flowers and entire
     * infected trunks: collapse them deterministically to a single tip.
     *
     * The only infected stems we retain are the REAL movable leaf path from
     * the chosen tip to (but excluding) its first shared/root junction.
     * An old tip which is growing becomes one temporary growth origin; only
     * one daughter inherits infection when vanilla growth produces flowers.
     *
     * Never touch unrelated vanilla blocks or load chunks. Unloaded records
     * survive until their chunks can be reconciled.
     */
    public void spreadAcrossChorus(ServerLevel level) {
        reconcileAllGrowth(level);
        List<Long> seeds = new ArrayList<>(infected);
        seeds.addAll(growthOrigins);
        Collections.sort(seeds);
        HashSet<Long> visited = new HashSet<>();
        HashSet<Long> keepFlowers = new HashSet<>();
        HashSet<Long> keepOrigins = new HashSet<>();
        HashSet<Long> keepStems = new HashSet<>();

        for (long seed : seeds) {
            if (visited.contains(seed)) continue;
            BlockPos position = BlockPos.of(seed);
            if (!level.hasChunkAt(position)) {
                // Never delete infection because its chunk is unloaded.
                if (infected.contains(seed)) keepFlowers.add(seed);
                if (growthOrigins.contains(seed)) keepOrigins.add(seed);
                continue;
            }
            Set<Long> component = connectedChorus(level, position);
            if (component == null) {
                // Abnormally large modded tree: do not guess or expand.
                for (long key : infected) if (key == seed) keepFlowers.add(key);
                for (long key : growthOrigins) if (key == seed) keepOrigins.add(key);
                continue;
            }
            if (component.isEmpty()) continue;
            visited.addAll(component);

            ArrayList<Long> existingTips = new ArrayList<>();
            ArrayList<Long> pendingOrigins = new ArrayList<>();
            for (long key : component) {
                BlockPos at = BlockPos.of(key);
                if (infected.contains(key)
                    && level.getBlockState(at).is(Blocks.CHORUS_FLOWER))
                    existingTips.add(key);
                if (growthOrigins.contains(key)
                    && level.getBlockState(at).is(Blocks.CHORUS_PLANT))
                    pendingOrigins.add(key);
            }
            Collections.sort(existingTips);
            Collections.sort(pendingOrigins);

            BlockPos chosen = null;
            if (!existingTips.isEmpty()) {
                chosen = BlockPos.of(existingTips.get(0));
            } else if (!pendingOrigins.isEmpty()) {
                // All other growth directions remain healthy.
                long source = pendingOrigins.get(0);
                chosen = singleChild(level, source);
                if (chosen == null) keepOrigins.add(source);
            }
            if (chosen != null) {
                keepFlowers.add(chosen.asLong());
                List<BlockPos> branch = MimicBranchPath.trace(level, chosen);
                for (int i = 1; i < branch.size(); i++) {
                    BlockPos member = branch.get(i);
                    if (level.getBlockState(member).is(Blocks.CHORUS_PLANT))
                        keepStems.add(member.asLong());
                }
            } else if (!pendingOrigins.isEmpty()) {
                // Keep the pre-growth chain, but never extend infection
                // into sibling stalks while the tip has yet to appear.
                for (long key : component)
                    if (infectedStems.contains(key)) keepStems.add(key);
            }
        }
        // Retain records in unloaded chunks instead of turning distant
        // branches into permanently cleared healthy structures.
        for (long key : infected)
            if (!level.hasChunkAt(BlockPos.of(key))) keepFlowers.add(key);
        for (long key : growthOrigins)
            if (!level.hasChunkAt(BlockPos.of(key))) keepOrigins.add(key);
        for (long key : infectedStems)
            if (!level.hasChunkAt(BlockPos.of(key))) keepStems.add(key);

        boolean changed = !infected.equals(keepFlowers)
            || !growthOrigins.equals(keepOrigins)
            || !infectedStems.equals(keepStems);
        if (changed) {
            infected.clear();
            infected.addAll(keepFlowers);
            growthOrigins.clear();
            growthOrigins.addAll(keepOrigins);
            infectedStems.clear();
            infectedStems.addAll(keepStems);
            setDirty();
        }
    }

    /** Copy ids so event handlers may prune invalid flowers during iteration. */
    public java.util.List<Long> infectedIds() {
        return new java.util.ArrayList<>(infected);
    }

    /**
     * Transfer a living infection to a new, physically attached vanilla
     * flower. The old head becomes an infected stalk; no fake flowers or
     * untracked entities survive a save/reload.
     */
    public boolean transferFlower(BlockPos oldFlower, BlockPos newFlower) {
        long oldKey = oldFlower.asLong(), nextKey = newFlower.asLong();
        if (!infected.contains(oldKey) || infected.contains(nextKey)
            || safe.contains(nextKey)) return false;
        infected.remove(oldKey);
        infected.add(nextKey);
        infectedStems.add(oldKey);
        growthOrigins.remove(oldKey);
        setDirty();
        return true;
    }

    /** Explicit operator mark: transfer the one infection to this branch. */
    public boolean mark(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) return false;
        Set<Long> component = connectedChorus(level, pos);
        if (component == null || component.isEmpty()) return false;
        boolean changed = false;
        for (long key : component) {
            if (key != pos.asLong()) changed |= infected.remove(key);
            changed |= growthOrigins.remove(key);
            changed |= infectedStems.remove(key);
        }
        changed |= safe.remove(pos.asLong());
        changed |= infected.add(pos.asLong());
        if (changed) setDirty();
        return true;
    }

    public void clear(BlockPos pos) {
        long key = pos.asLong();
        boolean changed = infected.remove(key) | safe.add(key);
        // A player who explicitly clears the only remaining source should
        // not leave self-reinfecting growth points behind.
        if (infected.isEmpty()) changed |= growthOrigins.removeIf(id -> id == key);
        if (changed) setDirty();
    }

    /**
     * Kill the mimic attached to this flower, not the whole chorus plant.
     * Its old position is temporarily exempt from seed-based reinfection:
     * the same mimic cannot immediately revive at the same coordinates.
     * An operator can still /livingchorus mark a new flower here explicitly.
     */
    public void defeat(BlockPos pos) {
        long key = pos.asLong();
        boolean changed = infected.remove(key) | safe.add(key);
        if (changed) setDirty();
    }

    /** Called only when the flower truly disappeared, not when it grew. */
    public void forget(BlockPos pos) {
        if (infected.remove(pos.asLong())) setDirty();
    }

    /**
     * Deterministic sampling is crucial: a flower that was not infected must
     * not get a fresh lottery ticket every time a player walks past.
     */
    public void discover(ServerLevel level, BlockPos pos) {
        long key = pos.asLong();
        if (!level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
            reconcileFlower(level, pos);
            return;
        }
        if (infected.contains(key) || safe.contains(key)) return;
        // Lottery is deterministic, so ordinary flowers do not repeatedly
        // gain new chances on every scan.
        long hash = mix64(level.getSeed() ^ key ^ 0x4D494D494343484FL);
        if ((hash & 0xFFFFL) >= INFECTION_THRESHOLD) return;

        Set<Long> component = connectedChorus(level, pos);
        if (component == null || component.isEmpty()) return;
        // NO spread from an existing mimic to sibling flowers: the whole
        // connected chorus can have only one living infected branch.
        for (long member : component) {
            if (infected.contains(member) || growthOrigins.contains(member))
                return;
        }
        if (infected.add(key)) setDirty();
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
