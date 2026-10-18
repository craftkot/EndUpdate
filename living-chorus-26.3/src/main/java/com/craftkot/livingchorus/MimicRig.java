package com.craftkot.livingchorus;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Smooth, articulated chorus branch. Existing vanilla chorus_plant block
 * centers are rest joints. Only a tail portion bends, while the shared root
 * stays fixed. Every rendered element stays an unscaled 16-pixel segment.
 */
public final class MimicRig {
    public record Pose(List<Vec3> joints, Vec3 mouth, double opening, double attackProgress) { }

    private static final double TELESCOPIC_SPACING = .70;
    private static final int MAX_EXTENSIONS = 32;

    private MimicRig() { }

    /** Stable choice made ONCE at attack start, not changed each frame. */
    public static int neededSegments(List<Vec3> rest, Vec3 target) {
        if (rest.size() < 2) return 0;
        return Math.max(1, Math.min(rest.size() - 1,
            (int) Math.ceil(rest.get(rest.size() - 1).distanceTo(target) / .90) + 1));
    }

    public static Pose evaluateBranch(MimicProfile style, List<Vec3> resting,
                                      Vec3 target, double fraction, boolean recovering) {
        return evaluateBranch(style, resting, target, fraction, recovering,
            neededSegments(resting, target));
    }

    public static Pose evaluateBranch(MimicProfile style, List<Vec3> resting,
                                      Vec3 target, double fraction, boolean recovering,
                                      int affectedSegments) {
        if (resting.size() < 2) {
            Vec3 head = resting.isEmpty() ? target : resting.get(resting.size()-1);
            return new Pose(List.copyOf(resting), head, 0, 0);
        }

        double p = clamp(fraction);
        // Approach: orient -> bend down -> extend -> bite. The jaw holds open
        // at impact; during recovery it closes first, then retracts and rests.
        double bend = recovering ? 1.0 - smooth((p - .22) / .78)
            : smooth(p / .61);
        double extension = recovering ? 1.0 - smooth((p - .17) / .70)
            : smooth((p - .46) / .43);
        double opening = recovering ? 1.0 - smooth((p - .04) / .34)
            : smooth((p - .76) / .18);

        int last = resting.size() - 1;
        int pivotIndex = last - Math.max(1, Math.min(last, affectedSegments));
        Vec3 pivot = resting.get(pivotIndex);
        Vec3 sleepHead = resting.get(last);

        ArrayList<Vec3> result = new ArrayList<>(resting);
        int count = last - pivotIndex;
        double[] lengths = new double[count];
        double available = 0;
        for (int i = 0; i < count; i++) {
            lengths[i] = resting.get(pivotIndex+i).distanceTo(resting.get(pivotIndex+i+1));
            available += lengths[i];
        }

        Vec3 outward = target.subtract(pivot);
        double distance = outward.length();
        Vec3 direction = distance > 1.0e-6
            ? outward.scale(1.0 / distance) : new Vec3(0, -1, 0);
        Vec3 achievable = pivot.add(direction.scale(Math.min(distance, available * .995)));
        Vec3 desiredTip = sleepHead.lerp(achievable, bend);

        // A deterministic fixed-iteration FABRIK pass. Fixed affected segment
        // count + continuously eased aim prevent "switching bones" mid-bite.
        ArrayList<Vec3> solved = new ArrayList<>(resting.subList(pivotIndex, last+1));
        for (int iter = 0; iter < 15; iter++) {
            solved.set(count, desiredTip);
            for (int i = count - 1; i >= 0; i--) {
                Vec3 delta = solved.get(i).subtract(solved.get(i+1));
                double length = delta.length();
                if (length > 1e-7)
                    solved.set(i, solved.get(i+1).add(delta.scale(lengths[i] / length)));
            }
            solved.set(0, pivot);
            for (int i = 1; i <= count; i++) {
                Vec3 delta = solved.get(i).subtract(solved.get(i-1));
                double length = delta.length();
                if (length > 1e-7)
                    solved.set(i, solved.get(i-1).add(delta.scale(lengths[i-1] / length)));
            }
        }
        for (int i = 1; i <= count; i++)
            result.set(pivotIndex+i, solved.get(i));

        Vec3 bentTip = result.get(last);
        Vec3 remaining = target.subtract(bentTip);
        double remainingLength = remaining.length();
        if (remainingLength > 1e-6 && extension > 1e-5) {
            Vec3 towards = remaining.scale(1.0 / remainingLength);
            // The target is at most 5.2 blocks from the flower at wake-up;
            // extra rigid pieces cover the missing length of a tall branch.
            // Use short *overlaps*, never scaled cubes/UVs.
            double covered = Math.min(remainingLength, MAX_EXTENSIONS * TELESCOPIC_SPACING)
                * extension;
            int steps = Math.min(MAX_EXTENSIONS,
                (int)Math.ceil(covered / TELESCOPIC_SPACING));
            for (int i = 1; i <= steps; i++)
                result.add(bentTip.add(towards.scale(
                    Math.min(covered, i * TELESCOPIC_SPACING))));
        }
        Vec3 mouth = result.get(result.size()-1);
        return new Pose(List.copyOf(result), mouth, clamp(opening), bend);
    }

    /** The jaws are 14px high; place their lower edge on prey ground level. */
    public static Vec3 endermiteGroundAim(double x, double feetY, double z) {
        return new Vec3(x, feetY + 7.0 / 16.0 + .015, z);
    }

    // Legacy helper for debug previews, with same smooth pose.
    public static Pose evaluate(MimicProfile profile, Vec3 root, Vec3 head,
                                Vec3 target, double p, boolean recovering) {
        return evaluateBranch(profile, List.of(root, head), target, p, recovering);
    }

    private static double smooth(double t) {
        t = clamp(t);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double t) {
        return Math.max(0, Math.min(1, t));
    }
}
