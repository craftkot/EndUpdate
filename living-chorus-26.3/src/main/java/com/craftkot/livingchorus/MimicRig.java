package com.craftkot.livingchorus;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Real chorus-plant block centers become actual rigid articulated bone joints.
 * Affected joints are chosen by reach; extra units slide out as rigid 16px
 * telescopic segments, never by scaling a cube or UVs.
 */
public final class MimicRig {
    public record Pose(List<Vec3> joints, Vec3 mouth, double opening, double attackProgress) {}

    private MimicRig() {}

    public static Pose evaluateBranch(MimicProfile style, List<Vec3> resting,
                                      Vec3 target, double fraction, boolean recovering) {
        if (resting.size() < 2) return new Pose(List.copyOf(resting),
            resting.isEmpty() ? target : resting.get(resting.size()-1), 0, 0);
        double phase = clamp(fraction);
        double movement;
        if (recovering) movement = 1.0 - smooth(phase);
        else if (phase < .46) movement = .42 * smooth(phase / .46);
        else movement = .42 + .58 * smooth((phase - .46) / .54);

        int last = resting.size() - 1;
        Vec3 head = resting.get(last);
        double distance = head.distanceTo(target);
        // Bend the MINIMUM useful portion of the existing infected branch.
        // More distant prey involve more real chorus blocks automatically.
        int needed = Math.max(1, Math.min(last, (int)Math.ceil(distance / .78) + 1));
        int pivotIndex = Math.max(0, last - needed);
        ArrayList<Vec3> joints = new ArrayList<>(resting);
        Vec3 pivot = resting.get(pivotIndex);
        double available = 0;
        for (int i = pivotIndex+1; i <= last; i++)
            available += resting.get(i).distanceTo(resting.get(i-1));
        // Limit rotation to the actual stalk length, then extend telescopically.
        Vec3 towards = target.subtract(pivot);
        double targetDistance = towards.length();
        Vec3 direction = targetDistance > 1e-5 ? towards.scale(1.0 / targetDistance)
            : new Vec3(0, 1, 0);
        Vec3 approach = targetDistance > available
            ? pivot.add(direction.scale(available * .985)) : target;
        Vec3 intendedHead = head.lerp(approach, movement);

        // FABRIK: solve a fixed-length chain anchored to the real junction.
        ArrayList<Vec3> solved = new ArrayList<>();
        for (int i=pivotIndex; i<=last; i++) solved.add(resting.get(i));
        int movable = solved.size()-1;
        double[] lengths = new double[movable];
        for (int i=0;i<movable;i++)
            lengths[i] = resting.get(pivotIndex+i).distanceTo(resting.get(pivotIndex+i+1));
        for (int iteration=0; iteration<12; iteration++) {
            solved.set(movable, intendedHead);
            for (int i=movable-1;i>=0;i--) {
                Vec3 delta = solved.get(i).subtract(solved.get(i+1));
                double len = delta.length();
                if (len > 1e-5)
                    solved.set(i, solved.get(i+1).add(delta.scale(lengths[i]/len)));
            }
            solved.set(0, pivot);
            for (int i=1;i<=movable;i++) {
                Vec3 delta = solved.get(i).subtract(solved.get(i-1));
                double len = delta.length();
                if (len > 1e-5)
                    solved.set(i, solved.get(i-1).add(delta.scale(lengths[i-1]/len)));
            }
        }
        for (int i=1;i<=movable;i++) joints.set(pivotIndex+i, solved.get(i));
        Vec3 tip = joints.get(joints.size()-1);

        // Stage 2: telescope extra full-sized pieces only after most of the
        // branch has rotated to face prey. Individual cubes translate; no UV warp.
        if (!recovering && phase >= .46 && targetDistance > available) {
            double out = smooth((phase - .46) / .54);
            double extraDistance = Math.min(style.extensionUnits() / 16.0 + 3.0,
                Math.max(0, tip.distanceTo(target) - .05)) * out;
            int pieces = Math.min(8, (int)Math.ceil(extraDistance));
            Vec3 v = target.subtract(tip);
            if (v.lengthSqr() > 1e-5) {
                Vec3 unit = v.normalize();
                for (int i=0;i<pieces;i++) {
                    // Overlap full-length cubes as they telescope, avoiding
                    // a shortened or stretched final piece.
                    double dist = Math.min(extraDistance, i+1.0);
                    joints.add(tip.add(unit.scale(dist)));
                }
                tip = joints.get(joints.size()-1);
            }
        }
        double opening = recovering ? 1.0 - smooth(phase / .6)
            : smooth((phase - .84)/.16);
        return new Pose(List.copyOf(joints), tip, clamp(opening), movement);
    }

    // Keep legacy helper for existing server particle debugging only.
    public static Pose evaluate(MimicProfile style, Vec3 root, Vec3 head,
                                Vec3 target, double fraction, boolean recovering) {
        return evaluateBranch(style, List.of(root, head), target, fraction, recovering);
    }

    private static double smooth(double p) { p=clamp(p); return p*p*(3-2*p); }
    private static double clamp(double x) { return Math.max(0,Math.min(1,x)); }
}
